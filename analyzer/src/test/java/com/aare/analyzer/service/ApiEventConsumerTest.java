package com.aare.analyzer.service;

import com.aare.analyzer.model.*;
import com.aare.analyzer.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class ApiEventConsumerTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private IncidentEvidenceRepository incidentEvidenceRepository;
    @Mock
    private EndpointBaselineRepository endpointBaselineRepository;
    @Mock
    private SchemaVersionRepository schemaVersionRepository;
    @Mock
    private RcaReportRepository rcaReportRepository;
    @Mock
    private OpenAIService openAIService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private Tracer tracer;

    @Spy
    private ObjectMapper objectMapper; // Use a spy to allow partial mocking if needed

    @InjectMocks
    private ApiEventConsumer apiEventConsumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock Tracer calls
        when(tracer.spanBuilder(anyString())).thenReturn(mock(io.opentelemetry.api.trace.SpanBuilder.class));
        when(tracer.spanBuilder(anyString()).startSpan()).thenReturn(mock(io.opentelemetry.api.trace.Span.class));
        when(tracer.spanBuilder(anyString()).startSpan().makeCurrent()).thenReturn(mock(io.opentelemetry.context.Scope.class));

        // Inject @Value properties for incident detection thresholds
        ReflectionTestUtils.setField(apiEventConsumer, "errorSpikeThreshold", BigDecimal.valueOf(0.1));
        ReflectionTestUtils.setField(apiEventConsumer, "errorSpikeFactor", BigDecimal.valueOf(2.0));
        ReflectionTestUtils.setField(apiEventConsumer, "errorSpikeMinRequests", 20);
        ReflectionTestUtils.setField(apiEventConsumer, "latencyRegressionP95Factor", BigDecimal.valueOf(1.5));
        ReflectionTestUtils.setField(apiEventConsumer, "latencyRegressionMinRequests", 20);
        ReflectionTestUtils.setField(apiEventConsumer, "trafficDropFactor", BigDecimal.valueOf(0.5));
        ReflectionTestUtils.setField(apiEventConsumer, "trafficDropMinRequestsBaseline", 50);

        // Clear in-memory maps before each test
        ((ConcurrentHashMap) ReflectionTestUtils.getField(apiEventConsumer, "endpointMetrics")).clear();
        ((ConcurrentHashMap) ReflectionTestUtils.getField(apiEventConsumer, "lastSchemaFingerprint")).clear();
    }

    private Map<String, Object> createTestEvent(String method, String path, int statusCode, long latency, String schemaFingerprint) {
        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("requestId", UUID.randomUUID().toString());
        eventMap.put("timestamp", LocalDateTime.now().toString());
        eventMap.put("method", method);
        eventMap.put("path", path);
        eventMap.put("statusCode", statusCode);
        eventMap.put("latencyMs", latency);
        eventMap.put("serviceName", "test-service");
        eventMap.put("environment", "local");
        eventMap.put("schemaFingerprint", schemaFingerprint);
        return eventMap;
    }

    @Test
    void receiveApiEvent_shouldUpdateMetrics() {
        Map<String, Object> event = createTestEvent("GET", "/test", 200, 100, "fingerprint1");
        apiEventConsumer.receiveApiEvent(event);

        String endpointId = apiEventConsumer.generateEndpointId("GET", "/test");
        ConcurrentMap<MetricWindowType, MetricWindow> metrics = ((ConcurrentHashMap) ReflectionTestUtils.getField(apiEventConsumer, "endpointMetrics")).get(endpointId);

        assertNotNull(metrics);
        assertEquals(1, metrics.get(MetricWindowType.FIVE_MINUTES).getRequestCount());
    }

    @Test
    void analyzeMetricsAndDetectIncidents_shouldDetectErrorSpike() {
        String endpointId = apiEventConsumer.generateEndpointId("GET", "/error");
        // Simulate enough requests to meet min-requests and cause an error spike
        for (int i = 0; i < 30; i++) {
            apiEventConsumer.receiveApiEvent(createTestEvent("GET", "/error", 200, 50, null));
        }
        for (int i = 0; i < 10; i++) {
            apiEventConsumer.receiveApiEvent(createTestEvent("GET", "/error", 500, 50, null)); // 25% error rate
        }

        // Setup baseline
        EndpointBaseline baseline = new EndpointBaseline();
        baseline.setEndpointId(endpointId);
        baseline.setMetricWindow(MetricWindowType.TWENTY_FOUR_HOURS);
        baseline.setErrorRatePct(BigDecimal.valueOf(5.0)); // 5% baseline error rate
        baseline.setRequestCount(100);
        when(endpointBaselineRepository.findByEndpointIdAndMetricWindow(endpointId, MetricWindowType.TWENTY_FOUR_HOURS))
                .thenReturn(Optional.of(baseline));
        
        when(incidentRepository.findByEndpointIdAndTypeAndStatusAndDetectedAtBetween(
            eq(endpointId), eq(IncidentType.ERROR_SPIKE), eq(IncidentStatus.OPEN), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Optional.empty());

        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident inc = invocation.getArgument(0);
            inc.setId(UUID.randomUUID());
            return inc;
        });

        RcaReport mockRcaReport = new RcaReport();
        mockRcaReport.setStatus(RcaStatus.GENERATED);
        when(openAIService.generateRcaReport(any(Incident.class), anyList())).thenReturn(mockRcaReport);
        when(rcaReportRepository.save(any(RcaReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Run the scheduled task
        apiEventConsumer.analyzeMetricsAndDetectIncidents();

        // Verify incident created
        ArgumentCaptor<Incident> incidentCaptor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository, times(1)).save(incidentCaptor.capture());
        assertEquals(IncidentType.ERROR_SPIKE, incidentCaptor.getValue().getType());
        assertEquals(IncidentSeverity.HIGH, incidentCaptor.getValue().getSeverity());

        // Verify RCA triggered and saved
        verify(openAIService, times(1)).generateRcaReport(any(Incident.class), anyList());
        verify(rcaReportRepository, times(1)).save(argThat(report -> report.getStatus() == RcaStatus.GENERATED));
    }

    @Test
    void analyzeMetricsAndDetectIncidents_shouldDetectLatencyRegression() {
        String endpointId = apiEventConsumer.generateEndpointId("GET", "/latency");
        // Simulate enough requests to meet min-requests
        for (int i = 0; i < 30; i++) {
            apiEventConsumer.receiveApiEvent(createTestEvent("GET", "/latency", 200, 200, null)); // Baseline latency
        }
        // Then some requests with higher latency
        for (int i = 0; i < 25; i++) {
            apiEventConsumer.receiveApiEvent(createTestEvent("GET", "/latency", 200, 500, null)); // Spiked latency
        }

        // Setup baseline
        EndpointBaseline baseline = new EndpointBaseline();
        baseline.setEndpointId(endpointId);
        baseline.setMetricWindow(MetricWindowType.TWENTY_FOUR_HOURS);
        baseline.setP95LatencyMs(200); // Baseline P95 latency
        baseline.setRequestCount(100);
        when(endpointBaselineRepository.findByEndpointIdAndMetricWindow(endpointId, MetricWindowType.TWENTY_FOUR_HOURS))
                .thenReturn(Optional.of(baseline));
        
        when(incidentRepository.findByEndpointIdAndTypeAndStatusAndDetectedAtBetween(
            eq(endpointId), eq(IncidentType.LATENCY_REGRESSION), eq(IncidentStatus.OPEN), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Optional.empty());

        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident inc = invocation.getArgument(0);
            inc.setId(UUID.randomUUID());
            return inc;
        });

        RcaReport mockRcaReport = new RcaReport();
        mockRcaReport.setStatus(RcaStatus.GENERATED);
        when(openAIService.generateRcaReport(any(Incident.class), anyList())).thenReturn(mockRcaReport);
        when(rcaReportRepository.save(any(RcaReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Run the scheduled task
        apiEventConsumer.analyzeMetricsAndDetectIncidents();

        // Verify incident created
        ArgumentCaptor<Incident> incidentCaptor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository, times(1)).save(incidentCaptor.capture());
        assertEquals(IncidentType.LATENCY_REGRESSION, incidentCaptor.getValue().getType());
        assertEquals(IncidentSeverity.MEDIUM, incidentCaptor.getValue().getSeverity());
        verify(openAIService, times(1)).generateRcaReport(any(Incident.class), anyList());
        verify(rcaReportRepository, times(1)).save(argThat(report -> report.getStatus() == RcaStatus.GENERATED));
    }

    @Test
    void analyzeMetricsAndDetectIncidents_shouldDetectTrafficDrop() {
        String endpointId = apiEventConsumer.generateEndpointId("GET", "/traffic-drop");
        // Simulate enough requests for a healthy baseline
        for (int i = 0; i < 100; i++) {
            apiEventConsumer.receiveApiEvent(createTestEvent("GET", "/traffic-drop", 200, 100, null));
        }

        // Setup baseline with high request count
        EndpointBaseline baseline = new EndpointBaseline();
        baseline.setEndpointId(endpointId);
        baseline.setMetricWindow(MetricWindowType.TWENTY_FOUR_HOURS);
        baseline.setRequestCount(100); // 100 requests in 24h baseline
        baseline.setErrorRatePct(BigDecimal.ZERO);
        when(endpointBaselineRepository.findByEndpointIdAndMetricWindow(endpointId, MetricWindowType.TWENTY_FOUR_HOURS))
                .thenReturn(Optional.of(baseline));

        // Simulate a drop in traffic in the 5-min window
        // Current window will have fewer than baseline * trafficDropFactor (100 * 0.5 = 50)
        for (int i = 0; i < 40; i++) { // 40 requests in current 5-min window
             apiEventConsumer.receiveApiEvent(createTestEvent("GET", "/traffic-drop", 200, 100, null));
        }
        
        when(incidentRepository.findByEndpointIdAndTypeAndStatusAndDetectedAtBetween(
            eq(endpointId), eq(IncidentType.TRAFFIC_DROP), eq(IncidentStatus.OPEN), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Optional.empty());

        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident inc = invocation.getArgument(0);
            inc.setId(UUID.randomUUID());
            return inc;
        });

        RcaReport mockRcaReport = new RcaReport();
        mockRcaReport.setStatus(RcaStatus.GENERATED);
        when(openAIService.generateRcaReport(any(Incident.class), anyList())).thenReturn(mockRcaReport);
        when(rcaReportRepository.save(any(RcaReport.class))).thenAnswer(invocation -> invocation.getArgument(0));


        // Run the scheduled task
        apiEventConsumer.analyzeMetricsAndDetectIncidents();

        // Verify incident created
        ArgumentCaptor<Incident> incidentCaptor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository, times(1)).save(incidentCaptor.capture());
        assertEquals(IncidentType.TRAFFIC_DROP, incidentCaptor.getValue().getType());
        assertEquals(IncidentSeverity.LOW, incidentCaptor.getValue().getSeverity());
        verify(openAIService, times(1)).generateRcaReport(any(Incident.class), anyList());
        verify(rcaReportRepository, times(1)).save(argThat(report -> report.getStatus() == RcaStatus.GENERATED));
    }

    @Test
    void analyzeMetricsAndDetectIncidents_shouldDetectContractBreak() {
        String endpointId = apiEventConsumer.generateEndpointId("GET", "/profile");
        // Simulate initial schema
        apiEventConsumer.receiveApiEvent(createTestEvent("GET", "/profile", 200, 100, "fingerprint-v1"));
        
        when(schemaVersionRepository.findTopByEndpointIdOrderByVersionDesc(endpointId))
            .thenReturn(Optional.empty()); // No previous schema

        when(schemaVersionRepository.save(any(SchemaVersion.class))).thenAnswer(invocation -> {
            SchemaVersion sv = invocation.getArgument(0);
            sv.setId(UUID.randomUUID());
            return sv;
        });

        // Run analysis to save first schema version
        apiEventConsumer.analyzeMetricsAndDetectIncidents();
        
        // Simulate schema change
        apiEventConsumer.receiveApiEvent(createTestEvent("GET", "/profile", 200, 100, "fingerprint-v2"));

        SchemaVersion v1 = new SchemaVersion();
        v1.setEndpointId(endpointId);
        v1.setSchemaHash("fingerprint-v1");
        v1.setVersion(1);
        when(schemaVersionRepository.findTopByEndpointIdOrderByVersionDesc(endpointId))
            .thenReturn(Optional.of(v1));

        when(incidentRepository.findByEndpointIdAndTypeAndStatusAndDetectedAtBetween(
            eq(endpointId), eq(IncidentType.CONTRACT_BREAK), eq(IncidentStatus.OPEN), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Optional.empty());

        // Run analysis again
        apiEventConsumer.analyzeMetricsAndDetectIncidents();

        // Verify incident created
        ArgumentCaptor<Incident> incidentCaptor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository, times(1)).save(incidentCaptor.capture());
        assertEquals(IncidentType.CONTRACT_BREAK, incidentCaptor.getValue().getType());
        assertEquals(IncidentSeverity.CRITICAL, incidentCaptor.getValue().getSeverity());
    }

    @Test
    void analyzeMetricsAndDetectIncidents_shouldNotCreateDuplicateIncident() {
        String endpointId = apiEventConsumer.generateEndpointId("GET", "/duplicate");
        // Simulate conditions for incident
        for (int i = 0; i < 30; i++) {
            apiEventConsumer.receiveApiEvent(createTestEvent("GET", "/duplicate", 200, 50, null));
        }
        for (int i = 0; i < 10; i++) {
            apiEventConsumer.receiveApiEvent(createTestEvent("GET", "/duplicate", 500, 50, null)); // 25% error rate
        }

        EndpointBaseline baseline = new EndpointBaseline();
        baseline.setEndpointId(endpointId);
        baseline.setMetricWindow(MetricWindowType.TWENTY_FOUR_HOURS);
        baseline.setErrorRatePct(BigDecimal.valueOf(5.0)); // 5% baseline error rate
        baseline.setRequestCount(100);
        when(endpointBaselineRepository.findByEndpointIdAndMetricWindow(endpointId, MetricWindowType.TWENTY_FOUR_HOURS))
                .thenReturn(Optional.of(baseline));

        // Simulate an existing open incident
        Incident existingIncident = new Incident();
        existingIncident.setId(UUID.randomUUID());
        existingIncident.setEndpointId(endpointId);
        existingIncident.setType(IncidentType.ERROR_SPIKE);
        existingIncident.setStatus(IncidentStatus.OPEN);
        existingIncident.setDetectedAt(LocalDateTime.now().minusMinutes(2)); // Within the 5-min window
        when(incidentRepository.findByEndpointIdAndTypeAndStatusAndDetectedAtBetween(
            eq(endpointId), eq(IncidentType.ERROR_SPIKE), eq(IncidentStatus.OPEN), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Optional.of(existingIncident));

        apiEventConsumer.analyzeMetricsAndDetectIncidents();

        verify(incidentRepository, never()).save(any(Incident.class)); // No new incident should be saved
    }
}
