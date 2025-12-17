package com.aare.analyzer.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

// This is a DTO, not a JPA Entity, as the Analyzer receives this from the Collector
@Data
public class ApiEvent {
    private String requestId;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime timestamp;
    private String method;
    private String path;
    private String query;
    private Integer statusCode;
    private Long latencyMs;
    private Map<String, String> reqHeaders;
    private Map<String, String> resHeaders;
    private String reqBodySample;
    private String resBodySample;
    private String schemaFingerprint;
    private String serviceName;
    private String environment;
}