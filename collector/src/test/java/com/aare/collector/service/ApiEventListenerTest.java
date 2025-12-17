package com.aare.collector.service;

import com.aare.collector.config.RabbitConfig;
import com.aare.collector.model.ApiEvent;
import com.aare.collector.model.ApiEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils; // For injecting ObjectMapper to InjectMocks

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class ApiEventListenerTest {

    @Mock
    private ApiEventRepository apiEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Tracer tracer; // Mock Tracer for OpenTelemetry
    @Mock
    private SpanBuilder spanBuilder;
    @Mock
    private Span span;
    @Mock
    private Scope scope;

    @InjectMocks
    private ApiEventListener apiEventListener;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Configure Tracer mock behavior for all method calls
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.setSpanKind(any())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenReturn(scope);

        // Inject ObjectMapper using ReflectionTestUtils since it's not constructor-injected in ApiEventListener
        ReflectionTestUtils.setField(apiEventListener, "objectMapper", objectMapper);
    }

    @Test
    void receiveApiEvent_shouldRedactSaveAndRepublish() {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("requestId", requestId);
        eventMap.put("timestamp", LocalDateTime.now().toString());
        eventMap.put("method", "POST");
        eventMap.put("path", "/api/test");
        eventMap.put("statusCode", 200);
        eventMap.put("latencyMs", 150L);
        eventMap.put("serviceName", "test-service");
        eventMap.put("environment", "test");

        Map<String, String> reqHeaders = new HashMap<>();
        reqHeaders.put("Authorization", "Bearer sensitive-token");
        reqHeaders.put("Content-Type", "application/json");
        eventMap.put("reqHeaders", reqHeaders);

        eventMap.put("reqBodySample", "{\"user\": \"test\", \"password\": \"mysecret\", \"email\": \"user@example.com\"}");
        eventMap.put("resBodySample", "{\"status\": \"success\", \"token\": \"another-sensitive-token\", \"orderId\": 123}");

        apiEventListener.receiveApiEvent(eventMap);

        // Verify save to repository
        ArgumentCaptor<ApiEvent> apiEventCaptor = ArgumentCaptor.forClass(ApiEvent.class);
        verify(apiEventRepository).save(apiEventCaptor.capture());
        ApiEvent savedApiEvent = apiEventCaptor.getValue();

        assertEquals(requestId, savedApiEvent.getRequestId());
        
        // Assert saved ApiEvent has redacted bodies
        String savedReqBody = savedApiEvent.getReqBodySample();
        assertTrue(savedReqBody.contains("\"password\":\"[REDACTED]\""));
        assertTrue(savedReqBody.contains("\"email\":\"[REDACTED]\""));
        assertFalse(savedReqBody.contains("mysecret"));
        assertFalse(savedReqBody.contains("user@example.com"));
        assertTrue(savedReqBody.contains("\"user\":\"test\""));

        String savedResBody = savedApiEvent.getResBodySample();
        assertTrue(savedResBody.contains("\"token\":\"[REDACTED]\""));
        assertFalse(savedResBody.contains("another-sensitive-token"));
        assertTrue(savedResBody.contains("\"status\":\"success\""));

        // Verify republish with redacted eventMap
        ArgumentCaptor<Map> republishedEventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.API_ANALYSIS_EXCHANGE),
                eq(RabbitConfig.API_ANALYSIS_ROUTING_KEY),
                republishedEventCaptor.capture()
        );

        Map<String, Object> republishedEvent = republishedEventCaptor.getValue();
        // Check reqHeaders redaction
        Map<String, String> republishedReqHeaders = (Map<String, String>) republishedEvent.get("reqHeaders");
        assertEquals("[REDACTED]", republishedReqHeaders.get("Authorization"));
        assertEquals("application/json", republishedReqHeaders.get("Content-Type")); // Should not be redacted

        // Check body redaction in republished event
        String republishedReqBody = (String) republishedEvent.get("reqBodySample");
        assertTrue(republishedReqBody.contains("\"password\":\"[REDACTED]\""));
        assertTrue(republishedReqBody.contains("\"email\":\"[REDACTED]\""));
        assertFalse(republishedReqBody.contains("mysecret"));
        assertFalse(republishedReqBody.contains("user@example.com"));

        String republishedResBody = (String) republishedEvent.get("resBodySample");
        assertTrue(republishedResBody.contains("\"token\":\"[REDACTED]\""));
        assertFalse(republishedResBody.contains("another-sensitive-token"));
    }
}
