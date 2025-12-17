package com.aare.collector.service;

import com.aare.collector.config.RabbitConfig;
import com.aare.collector.model.ApiEvent;
import com.aare.collector.repo.ApiEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ApiEventListener {

    private static final Logger log = LoggerFactory.getLogger(ApiEventListener.class);

    private static final int MAX_BODY_SIZE = 8 * 1024; // 8KB
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "\"(authorization|cookie|password|token|card_number|email)\"\\s*:\\s*(?:\"[^\"]*\"|'[^']*'|\\d+|true|false|null)",
            Pattern.CASE_INSENSITIVE
    );

    private final ApiEventRepository apiEventRepository;

    public ApiEventListener(ApiEventRepository apiEventRepository) {
        this.apiEventRepository = apiEventRepository;
    }

    @RabbitListener(queues = RabbitConfig.API_EVENTS_QUEUE)
    public void onApiEvent(Map<String, Object> eventMap) {
        try {
            ApiEvent apiEvent = mapToApiEvent(eventMap);
            apiEventRepository.save(apiEvent);
            log.info("Saved ApiEvent: {}", apiEvent.getRequestId());
        } catch (Exception e) {
            Object rid = (eventMap != null) ? eventMap.get("requestId") : null;
            log.error("Failed to save ApiEvent requestId={}: {}", rid, e.getMessage(), e);
        }
    }

    private String sampleAndRedactBody(String body) {
        if (body == null || body.isEmpty()) return "";
        String sampled = body.length() > MAX_BODY_SIZE
                ? body.substring(0, MAX_BODY_SIZE) + "... (truncated)"
                : body;
        return redactSensitiveInfo(sampled);
    }

    private String redactSensitiveInfo(String input) {
        if (input == null || input.isEmpty()) return input;
        Matcher matcher = SENSITIVE_PATTERN.matcher(input);
        // keep JSON valid:
        return matcher.replaceAll("\"$1\":\"[REDACTED]\"");
    }

    @SuppressWarnings("unchecked")
    private ApiEvent mapToApiEvent(Map<String, Object> eventMap) {
        ApiEvent apiEvent = new ApiEvent();

        apiEvent.setRequestId((String) eventMap.get("requestId"));
        apiEvent.setTimestamp(LocalDateTime.parse((String) eventMap.get("timestamp")));
        apiEvent.setMethod((String) eventMap.get("method"));
        apiEvent.setPath((String) eventMap.get("path"));
        apiEvent.setQuery((String) eventMap.get("query"));

        Object sc = eventMap.get("statusCode");
        apiEvent.setStatusCode(sc == null ? 0 : ((Number) sc).intValue());

        Object lat = eventMap.get("latencyMs");
        apiEvent.setLatencyMs(lat == null ? 0L : ((Number) lat).longValue());

        apiEvent.setServiceName((String) eventMap.get("serviceName"));
        apiEvent.setEnvironment((String) eventMap.get("environment"));

        // headers maps -> store as Map (converter will serialize to JSON for jsonb)
        apiEvent.setReqHeaders((Map<String, String>) eventMap.get("reqHeaders"));
        apiEvent.setResHeaders((Map<String, String>) eventMap.get("resHeaders"));

        apiEvent.setReqBodySample(sampleAndRedactBody((String) eventMap.get("reqBodySample")));
        apiEvent.setResBodySample(sampleAndRedactBody((String) eventMap.get("resBodySample")));
        apiEvent.setSchemaFingerprint((String) eventMap.get("schemaFingerprint"));

        return apiEvent;
    }
}
