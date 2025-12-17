package com.aare.collector.service;

import com.aare.collector.model.ApiEvent;
import com.aare.collector.repo.ApiEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for ApiEventListener.
 *
 * Goal:
 * - Ensure a valid incoming API event payload is mapped + persisted (apiEventRepository.save called).
 * - Avoid hardcoding exchange/queue names here (listener config may change).
 */
@ExtendWith(MockitoExtension.class)
class ApiEventListenerTest {

    @Mock
    private ApiEventRepository apiEventRepository;

    // If your ApiEventListener doesn't use RabbitTemplate, this mock won't hurt.
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ApiEventListener apiEventListener;

    @Test
    void shouldProcessIncomingApiEvent_withoutHardcodingMethodOrExchangeNames() {
        // Build a payload that matches what the listener mapper expects.
        // We include BOTH camelCase and snake_case keys to be resilient to mapping changes.
        Map<String, Object> payload = new HashMap<>();

        payload.put("serviceName", "target-api");
        payload.put("service_name", "target-api");

        payload.put("environment", "local");

        payload.put("path", "/ping");
        payload.put("method", "GET");

        payload.put("statusCode", 200);
        payload.put("status_code", 200);

        payload.put("latencyMs", 12L);
        payload.put("latency_ms", 12L);

        payload.put("requestId", "req-123");
        payload.put("request_id", "req-123");

        payload.put("query", "");

        payload.put("reqHeaders", Map.of());
        payload.put("req_headers", Map.of());
        payload.put("resHeaders", Map.of());
        payload.put("res_headers", Map.of());

        payload.put("reqBodySample", "");
        payload.put("req_body_sample", "");
        payload.put("resBodySample", "");
        payload.put("res_body_sample", "");

        payload.put("schemaFingerprint", "fp-abc");
        payload.put("schema_fingerprint", "fp-abc");

        // IMPORTANT: your stacktrace shows LocalDateTime.parse(...) being used
        // so timestamp must be ISO-8601 like: 2025-12-17T13:56:40
        payload.put("timestamp", "2025-12-17T13:56:40");

        // repository save should return the entity (common pattern)
        when(apiEventRepository.save(any(ApiEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Call the listener method (based on your stack trace: onApiEvent(...))
        apiEventListener.onApiEvent(payload);

        // Verify it saved an ApiEvent
        ArgumentCaptor<ApiEvent> captor = ArgumentCaptor.forClass(ApiEvent.class);
        verify(apiEventRepository, times(1)).save(captor.capture());

        ApiEvent saved = captor.getValue();
        assertThat(saved).isNotNull();

        // Basic sanity assertions (won't break if minor mapping changes)
        // If your entity fields differ, you can remove these assertions.
        // (Main goal is that save() happens.)
        // NOTE: If ApiEvent uses Lombok and getters exist, these will work.
        assertThat(saved.getPath()).isEqualTo("/ping");
        assertThat(saved.getMethod()).isEqualTo("GET");
        assertThat(saved.getEnvironment()).isEqualTo("local");
        assertThat(saved.getServiceName()).isEqualTo("target-api");
    }
}
