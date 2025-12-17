package com.aare.gateway.filter;

import com.aare.gateway.config.RabbitConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class TrafficCaptureFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TrafficCaptureFilter.class);

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String SERVICE_NAME_HEADER = "X-Service-Name";
    private static final int MAX_BODY_SIZE = 8 * 1024; // 8KB

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "\"(authorization|cookie|password|token|card_number|email)\"\\s*:\\s*(?:\"[^\"]*\"|'[^']*'|\\d+|true|false|null)",
            Pattern.CASE_INSENSITIVE
    );

    private final RabbitTemplate rabbitTemplate;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;

    public TrafficCaptureFilter(RabbitTemplate rabbitTemplate, Tracer tracer, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.tracer = tracer;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Span span = tracer.spanBuilder("trafficCaptureFilter")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            LocalDateTime timestamp = LocalDateTime.now();
            long startTime = System.currentTimeMillis();

            ServerHttpRequest request = exchange.getRequest();
            String requestId = getRequestId(request);
            String serviceName = getServiceName(request);

            Mono<String> requestBodyMono = captureRequestBody(request);

            BodyCaptureResponse responseDecorator = new BodyCaptureResponse(exchange.getResponse());

            return requestBodyMono.flatMap(reqBody -> {
                        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
                        ServerHttpRequestDecorator reqDecorator = buildRequestDecorator(bufferFactory, request, reqBody);

                        ServerWebExchange mutated = exchange.mutate()
                                .request(reqDecorator)
                                .response(responseDecorator)
                                .build();

                        // IMPORTANT: Force Mono<Void> (fixes Mono<Object> inference)
                        return chain.filter(mutated)
                                .then(Mono.<Void>fromRunnable(() -> {
                                    long latency = System.currentTimeMillis() - startTime;

                                    Map<String, Object> apiEvent = buildApiEvent(
                                            requestId, timestamp, request, reqBody, responseDecorator, latency, serviceName
                                    );

                                    publishApiEvent(apiEvent);
                                }));
                    })
                    .doFinally(signalType -> span.end());
        } catch (Exception e) {
            span.recordException(e);
            throw e;
        }
    }

    private String getRequestId(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }

    private String getServiceName(ServerHttpRequest request) {
        String serviceName = request.getHeaders().getFirst(SERVICE_NAME_HEADER);
        if (serviceName == null) {
            URI uri = request.getURI();
            String path = uri.getPath();
            if (path != null && path.startsWith("/")) {
                int secondSlash = path.indexOf('/', 1);
                if (secondSlash > 0) {
                    serviceName = path.substring(1, secondSlash);
                }
            }
        }
        return serviceName != null ? serviceName : "unknown";
    }

    private Mono<String> captureRequestBody(ServerHttpRequest request) {
        if (request.getMethod() == HttpMethod.GET || request.getMethod() == HttpMethod.DELETE) {
            return Mono.just("");
        }

        return DataBufferUtils.join(request.getBody())
                .map(dataBuffer -> {
                    try {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        Channels.newChannel(outputStream).write(dataBuffer.asByteBuffer().asReadOnlyBuffer());
                        String body = outputStream.toString(StandardCharsets.UTF_8);
                        return redactSensitiveInfo(body);
                    } catch (Exception e) {
                        log.error("Error capturing request body", e);
                        return "[Error capturing request body]";
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .defaultIfEmpty("");
    }

    private ServerHttpRequestDecorator buildRequestDecorator(
            DataBufferFactory bufferFactory,
            ServerHttpRequest request,
            String body
    ) {
        return new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                if (body == null || body.isEmpty()) {
                    return Flux.empty();
                }
                return Flux.just(bufferFactory.wrap(body.getBytes(StandardCharsets.UTF_8)));
            }
        };
    }

    private Map<String, Object> buildApiEvent(
            String requestId,
            LocalDateTime timestamp,
            ServerHttpRequest request,
            String reqBody,
            BodyCaptureResponse responseDecorator,
            long latency,
            String serviceName
    ) {
        Map<String, Object> apiEvent = new LinkedHashMap<>();
        apiEvent.put("requestId", requestId);
        apiEvent.put("timestamp", timestamp.toString());
        apiEvent.put("method", request.getMethod() != null ? request.getMethod().name() : "UNKNOWN");
        apiEvent.put("path", request.getURI().getPath());
        apiEvent.put("query", request.getURI().getQuery());
        apiEvent.put("statusCode", responseDecorator.getStatusCode() != null ? responseDecorator.getStatusCode().value() : 0);
        apiEvent.put("latencyMs", latency);
        apiEvent.put("serviceName", serviceName);
        apiEvent.put("environment", "local");

        apiEvent.put("reqHeaders", filterAndRedactHeaders(request.getHeaders()));
        apiEvent.put("reqBodySample", sampleAndRedactBody(reqBody));
        apiEvent.put("resHeaders", filterAndRedactHeaders(responseDecorator.getHeaders()));

        String resBody = responseDecorator.getCapturedBody(MAX_BODY_SIZE);
        apiEvent.put("resBodySample", sampleAndRedactBody(resBody));
        apiEvent.put("schemaFingerprint", getSchemaFingerprint(resBody, responseDecorator.getHeaders()));

        return apiEvent;
    }

    private Map<String, String> filterAndRedactHeaders(HttpHeaders headers) {
        Set<String> whitelist = Set.of("Content-Type", "Accept", "User-Agent");
        return headers.entrySet().stream()
                .filter(entry -> whitelist.contains(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> redactSensitiveInfo(String.join(",", entry.getValue()))
                ));
    }

    private String sampleAndRedactBody(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String sampledBody = body.length() > MAX_BODY_SIZE
                ? body.substring(0, MAX_BODY_SIZE) + "... (truncated)"
                : body;
        return redactSensitiveInfo(sampledBody);
    }

    private String redactSensitiveInfo(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        Matcher matcher = SENSITIVE_PATTERN.matcher(input);
        // Keep JSON valid: "key":"[REDACTED]"
        return matcher.replaceAll("\"$1\":\"[REDACTED]\"");
    }

    private String getSchemaFingerprint(String responseBody, HttpHeaders headers) {
        if (responseBody == null || responseBody.isEmpty() || !isJson(headers)) {
            return null;
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            String structuralSchema = generateStructuralFingerprint(jsonNode);
            return String.valueOf(structuralSchema.hashCode());
        } catch (JsonProcessingException e) {
            log.warn("Could not parse JSON for schema fingerprint: {}", e.getMessage());
            return null;
        }
    }

    private String generateStructuralFingerprint(JsonNode node) {
        StringBuilder b = new StringBuilder();

        if (node.isObject()) {
            b.append("{");
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);

            for (String f : fieldNames) {
                b.append("\"").append(f).append("\":");
                b.append(generateStructuralFingerprint(node.get(f)));
                b.append(",");
            }
            if (!fieldNames.isEmpty()) b.deleteCharAt(b.length() - 1);
            b.append("}");
        } else if (node.isArray()) {
            b.append("[");
            if (node.elements().hasNext()) {
                b.append(generateStructuralFingerprint(node.elements().next()));
            }
            b.append("]");
        } else if (node.isTextual()) {
            b.append("string");
        } else if (node.isNumber()) {
            b.append("number");
        } else if (node.isBoolean()) {
            b.append("boolean");
        } else if (node.isNull()) {
            b.append("null");
        } else {
            b.append("unknown");
        }

        return b.toString();
    }

    private boolean isJson(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        return contentType != null &&
                (contentType.isCompatibleWith(MediaType.APPLICATION_JSON) ||
                        contentType.isCompatibleWith(MediaType.parseMediaType("application/*+json")));
    }

    private void publishApiEvent(Map<String, Object> apiEvent) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.API_EVENTS_EXCHANGE,
                    RabbitConfig.API_EVENTS_ROUTING_KEY,
                    apiEvent
            );
            log.info("Published ApiEvent: {}", apiEvent.get("requestId"));
        } catch (Exception e) {
            log.error("Failed to publish ApiEvent for requestId {}: {}",
                    apiEvent.get("requestId"), e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
