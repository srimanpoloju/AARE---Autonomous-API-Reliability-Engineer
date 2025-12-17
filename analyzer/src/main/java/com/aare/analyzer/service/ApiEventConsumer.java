package com.aare.analyzer.service;

import com.aare.analyzer.config.RabbitConfig;
import com.aare.analyzer.model.*;
import com.aare.analyzer.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class ApiEventConsumer {

    @Autowired
    private IncidentRepository incidentRepository;
    @Autowired
    private IncidentEvidenceRepository incidentEvidenceRepository;
    @Autowired
    private EndpointBaselineRepository endpointBaselineRepository;
    @Autowired
    private SchemaVersionRepository schemaVersionRepository;
    @Autowired
    private RcaReportRepository rcaReportRepository;
    @Autowired
    private OpenAIService openAIService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private Tracer tracer;

    // In-memory store for rolling metrics (endpointId -> MetricWindow)
    private final ConcurrentMap<String, ConcurrentMap<MetricWindowType, MetricWindow>> endpointMetrics = new ConcurrentHashMap<>();

    // Store for last received schema fingerprint per endpoint
    private final ConcurrentMap<String, String> lastSchemaFingerprint = new ConcurrentHashMap<>();

    // Temporary map to hold the last ApiEvent received per endpoint for schema processing
    private final ConcurrentMap<String, ApiEvent> lastApiEventPerEndpoint = new ConcurrentHashMap<>();

    // Configuration from application.yml
    @Value("${aare.incident.detection.error-spike.threshold}")
    private BigDecimal errorSpikeThreshold;
    @Value("${aare.incident.detection.error-spike.factor}")
    private BigDecimal errorSpikeFactor;
    @Value("${aare.incident.detection.error-spike.min-requests}")
    private int errorSpikeMinRequests;

    @Value("${aare.incident.detection.latency-regression.p95-factor}")
    private BigDecimal latencyRegressionP95Factor;
    @Value("${aare.incident.detection.latency-regression.min-requests}")
    private int latencyRegressionMinRequests;

    @Value("${aare.incident.detection.traffic-drop.factor}")
    private BigDecimal trafficDropFactor;
    @Value("${aare.incident.detection.traffic-drop.min-requests-baseline}")
    private int trafficDropMinRequestsBaseline;

    @RabbitListener(queues = RabbitConfig.API_ANALYSIS_QUEUE)
    public void receiveApiEvent(Map<String, Object> eventMap) {
        Span span = tracer.spanBuilder("receiveApiEvent-analyzer").setSpanKind(SpanKind.CONSUMER).startSpan();
        try (Scope scope = span.makeCurrent()) {
            ApiEvent apiEvent = objectMapper.convertValue(eventMap, ApiEvent.class);
            log.debug("Analyzer received ApiEvent for requestId: {}", apiEvent.getRequestId());

            String endpointId = generateEndpointId(apiEvent.getMethod(), apiEvent.getPath());
            boolean isError = apiEvent.getStatusCode() >= 400;

            endpointMetrics
                    .computeIfAbsent(endpointId, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(MetricWindowType.FIVE_MINUTES, k -> new MetricWindow(5 * 60 * 1000))
                    .addEvent(apiEvent.getLatencyMs(), isError);
            endpointMetrics
                    .get(endpointId)
                    .computeIfAbsent(MetricWindowType.THIRTY_MINUTES, k -> new MetricWindow(30 * 60 * 1000))
                    .addEvent(apiEvent.getLatencyMs(), isError);
            endpointMetrics
                    .get(endpointId)
                    .computeIfAbsent(MetricWindowType.TWENTY_FOUR_HOURS, k -> new MetricWindow(24 * 60 * 60 * 1000))
                    .addEvent(apiEvent.getLatencyMs(), isError);

            if (apiEvent.getSchemaFingerprint() != null && !apiEvent.getSchemaFingerprint().isEmpty()) {
                String oldFingerprint = lastSchemaFingerprint.put(endpointId, apiEvent.getSchemaFingerprint());
                if (oldFingerprint != null && !oldFingerprint.equals(apiEvent.getSchemaFingerprint())) {
                    log.info("Schema fingerprint changed for endpoint {}. Old: {}, New: {}",
                            endpointId, oldFingerprint, apiEvent.getSchemaFingerprint());
                }
            }

            lastApiEventPerEndpoint.put(endpointId, apiEvent);

        } catch (Exception e) {
            span.recordException(e);
            log.error("Error processing ApiEvent for requestId {}: {}", eventMap.get("requestId"), e.getMessage(), e);
        } finally {
            span.end();
        }
    }

    @Scheduled(fixedRateString = "${aare.analyzer.schedule.fixed-rate-ms:60000}")
    public void analyzeMetricsAndDetectIncidents() {
        Span span = tracer.spanBuilder("analyzeMetricsAndDetectIncidents").startSpan();
        try (Scope scope = span.makeCurrent()) {
            log.info("Running scheduled incident detection and baseline update...");
            LocalDateTime now = LocalDateTime.now();

            endpointMetrics.forEach((endpointId, metricWindows) -> {
                MetricWindow fiveMinWindow = metricWindows.get(MetricWindowType.FIVE_MINUTES);
                MetricWindow thirtyMinWindow = metricWindows.get(MetricWindowType.THIRTY_MINUTES);
                MetricWindow twentyFourHourWindow = metricWindows.get(MetricWindowType.TWENTY_FOUR_HOURS);

                if (fiveMinWindow == null || fiveMinWindow.getRequestCount() == 0) {
                    return;
                }

                updateBaseline(endpointId, fiveMinWindow, MetricWindowType.FIVE_MINUTES, now);
                updateBaseline(endpointId, thirtyMinWindow, MetricWindowType.THIRTY_MINUTES, now);
                updateBaseline(endpointId, twentyFourHourWindow, MetricWindowType.TWENTY_FOUR_HOURS, now);

                detectErrorSpike(endpointId, fiveMinWindow, now);
                detectLatencyRegression(endpointId, fiveMinWindow, now);
                detectTrafficDrop(endpointId, fiveMinWindow, twentyFourHourWindow, now);
                detectContractBreak(endpointId, now);
            });

            log.info("Finished scheduled incident detection and baseline update.");
        } finally {
            span.end();
        }
    }

    private void updateBaseline(String endpointId, MetricWindow currentWindow, MetricWindowType windowType, LocalDateTime now) {
        if (currentWindow == null || currentWindow.getRequestCount() == 0) return;

        Span span = tracer.spanBuilder("updateBaseline").startSpan();
        try (Scope scope = span.makeCurrent()) {
            Optional<EndpointBaseline> existingBaselineOpt =
                    endpointBaselineRepository.findByEndpointIdAndMetricWindow(endpointId, windowType);

            EndpointBaseline baseline = existingBaselineOpt.orElseGet(EndpointBaseline::new);

            baseline.setEndpointId(endpointId);
            baseline.setMetricWindow(windowType);

            BigDecimal newErrorRate = currentWindow.getErrorRatePct();
            Integer newP50 = currentWindow.getP50Latency();
            Integer newP95 = currentWindow.getP95Latency();
            Integer newP99 = currentWindow.getP99Latency();
            Integer newRequestCount = (int) currentWindow.getRequestCount();

            if (existingBaselineOpt.isPresent()) {
                baseline.setErrorRatePct(baseline.getErrorRatePct().add(newErrorRate)
                        .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
                baseline.setP50LatencyMs((baseline.getP50LatencyMs() + newP50) / 2);
                baseline.setP95LatencyMs((baseline.getP95LatencyMs() + newP95) / 2);
                baseline.setP99LatencyMs((baseline.getP99LatencyMs() + newP99) / 2);
                baseline.setRequestCount((baseline.getRequestCount() + newRequestCount) / 2);
            } else {
                baseline.setErrorRatePct(newErrorRate);
                baseline.setP50LatencyMs(newP50);
                baseline.setP95LatencyMs(newP95);
                baseline.setP99LatencyMs(newP99);
                baseline.setRequestCount(newRequestCount);
            }

            baseline.setLastComputed(now);
            endpointBaselineRepository.save(baseline);
        } finally {
            span.end();
        }
    }

    private void detectErrorSpike(String endpointId, MetricWindow fiveMinWindow, LocalDateTime now) {
        if (fiveMinWindow.getRequestCount() < errorSpikeMinRequests) return;

        BigDecimal currentErrorRate = fiveMinWindow.getErrorRatePct();
        Optional<EndpointBaseline> baselineOpt =
                endpointBaselineRepository.findByEndpointIdAndMetricWindow(endpointId, MetricWindowType.TWENTY_FOUR_HOURS);

        if (baselineOpt.isPresent() && currentErrorRate.compareTo(errorSpikeThreshold) > 0) {
            BigDecimal baselineErrorRate = baselineOpt.get().getErrorRatePct();
            if (currentErrorRate.compareTo(baselineErrorRate.multiply(errorSpikeFactor)) > 0) {
                createIncident(endpointId, IncidentType.ERROR_SPIKE, IncidentSeverity.HIGH, now,
                        Map.of("currentErrorRate", currentErrorRate,
                                "baselineErrorRate", baselineErrorRate,
                                "factor", errorSpikeFactor));
            }
        }
    }

    private void detectLatencyRegression(String endpointId, MetricWindow fiveMinWindow, LocalDateTime now) {
        if (fiveMinWindow.getRequestCount() < latencyRegressionMinRequests) return;

        int currentP95 = fiveMinWindow.getP95Latency();
        Optional<EndpointBaseline> baselineOpt =
                endpointBaselineRepository.findByEndpointIdAndMetricWindow(endpointId, MetricWindowType.TWENTY_FOUR_HOURS);

        if (baselineOpt.isPresent()) {
            int baselineP95 = baselineOpt.get().getP95LatencyMs();
            if (currentP95 > (baselineP95 * latencyRegressionP95Factor.doubleValue())) {
                createIncident(endpointId, IncidentType.LATENCY_REGRESSION, IncidentSeverity.MEDIUM, now,
                        Map.of("currentP95Latency", currentP95,
                                "baselineP95Latency", baselineP95,
                                "factor", latencyRegressionP95Factor));
            }
        }
    }

    private void detectTrafficDrop(String endpointId, MetricWindow fiveMinWindow, MetricWindow twentyFourHourWindow, LocalDateTime now) {
        if (fiveMinWindow.getRequestCount() == 0) return;

        long currentRequestCount = fiveMinWindow.getRequestCount();
        Optional<EndpointBaseline> baselineOpt =
                endpointBaselineRepository.findByEndpointIdAndMetricWindow(endpointId, MetricWindowType.TWENTY_FOUR_HOURS);

        if (baselineOpt.isPresent()) {
            long baselineRequestCount = baselineOpt.get().getRequestCount();
            if (baselineRequestCount >= trafficDropMinRequestsBaseline
                    && currentRequestCount < (baselineRequestCount * trafficDropFactor.doubleValue())) {
                createIncident(endpointId, IncidentType.TRAFFIC_DROP, IncidentSeverity.LOW, now,
                        Map.of("currentRequestCount", currentRequestCount,
                                "baselineRequestCount", baselineRequestCount,
                                "factor", trafficDropFactor));
            }
        }
    }

    private void detectContractBreak(String endpointId, LocalDateTime now) {
        // keep your existing implementation (no compile issues shown here)
    }

    private void createIncident(String endpointId,
                                IncidentType type,
                                IncidentSeverity severity,
                                LocalDateTime detectedAt,
                                Map<String, Object> evidenceData) {
        Span span = tracer.spanBuilder("createIncident").startSpan();
        try (Scope scope = span.makeCurrent()) {

            // ✅ Define the window correctly
            LocalDateTime windowStart = detectedAt.minusMinutes(5);
            LocalDateTime windowEnd = detectedAt;

            // ✅ Check for existing OPEN incident of same type in the window
            List<Incident> existing = incidentRepository
                    .findByEndpointIdAndTypeAndStatusAndDetectedAtBetween(
                            endpointId,
                            type,
                            IncidentStatus.OPEN,
                            windowStart,
                            windowEnd
                    );

            if (!existing.isEmpty()) {
                log.info("Active incident of type {} already exists for endpoint {} in last 5 minutes. Skipping.",
                        type, endpointId);
                return;
            }

            Incident incident = new Incident();
            incident.setEndpointId(endpointId);
            incident.setType(type);
            incident.setStatus(IncidentStatus.OPEN);
            incident.setSeverity(severity);
            incident.setTriggeredAt(detectedAt);
            incident.setDetectedAt(detectedAt);

            Incident savedIncident = incidentRepository.save(incident);
            log.warn("INCIDENT DETECTED: Type={}, Endpoint={}, Severity={}", type, endpointId, severity);

            IncidentEvidence evidence = new IncidentEvidence();
            evidence.setIncidentId(savedIncident.getId());
            evidence.setEvidenceType(EvidenceType.METRICS);
            evidence.setData(evidenceData);
            evidence.setCreatedAt(detectedAt);
            incidentEvidenceRepository.save(evidence);

            triggerRcaGeneration(savedIncident.getId());

        } finally {
            span.end();
        }
    }

    @Async
    public void triggerRcaGeneration(UUID incidentId) {
        Span span = tracer.spanBuilder("triggerRcaGeneration").setSpanKind(SpanKind.PRODUCER).startSpan();
        try (Scope scope = span.makeCurrent()) {
            Optional<Incident> incidentOpt = incidentRepository.findById(incidentId);
            if (incidentOpt.isEmpty()) {
                log.warn("Incident {} not found for RCA generation.", incidentId);
                return;
            }

            Incident incident = incidentOpt.get();
            List<IncidentEvidence> evidences = incidentEvidenceRepository.findByIncidentId(incidentId);

            RcaReport rcaReport = openAIService.generateRcaReport(incident, evidences);
            rcaReportRepository.save(rcaReport);

            log.info("RCA report generated for incident {}. Status: {}", incidentId, rcaReport.getStatus());
        } catch (Exception e) {
            span.recordException(e);
            log.error("Error triggering RCA generation for incident {}: {}", incidentId, e.getMessage(), e);
        } finally {
            span.end();
        }
    }

    public String generateEndpointId(String method, String path) {
        return (method + ":" + path).toLowerCase();
    }
}
