package com.aare.collector.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "api_event")
public class ApiEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "request_id")
    private String requestId;

    private LocalDateTime timestamp;

    private String method;
    private String path;

    @Column(columnDefinition = "text")
    private String query;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "service_name")
    private String serviceName;

    private String environment;

    @Column(name = "req_body_sample", columnDefinition = "text")
    private String reqBodySample;

    @Column(name = "res_body_sample", columnDefinition = "text")
    private String resBodySample;

    @Column(name = "schema_fingerprint")
    private String schemaFingerprint;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "req_headers", columnDefinition = "jsonb")
    private Map<String, String> reqHeaders;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "res_headers", columnDefinition = "jsonb")
    private Map<String, String> resHeaders;

    // --- getters/setters ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }

    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getReqBodySample() { return reqBodySample; }
    public void setReqBodySample(String reqBodySample) { this.reqBodySample = reqBodySample; }

    public String getResBodySample() { return resBodySample; }
    public void setResBodySample(String resBodySample) { this.resBodySample = resBodySample; }

    public String getSchemaFingerprint() { return schemaFingerprint; }
    public void setSchemaFingerprint(String schemaFingerprint) { this.schemaFingerprint = schemaFingerprint; }

    public Map<String, String> getReqHeaders() { return reqHeaders; }
    public void setReqHeaders(Map<String, String> reqHeaders) { this.reqHeaders = reqHeaders; }

    public Map<String, String> getResHeaders() { return resHeaders; }
    public void setResHeaders(Map<String, String> resHeaders) { this.resHeaders = resHeaders; }
}
