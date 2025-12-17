package com.aare.analyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "endpoint_baseline")
public class EndpointBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "endpoint_id")
    private String endpointId; // Hash of method + path

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_window")
    private MetricWindowType metricWindow;

    @Column(name = "error_rate_pct")
    private BigDecimal errorRatePct;

    @Column(name = "p50_latency_ms")
    private Integer p50LatencyMs;

    @Column(name = "p95_latency_ms")
    private Integer p95LatencyMs;

    @Column(name = "p99_latency_ms")
    private Integer p99LatencyMs;

    @Column(name = "request_count")
    private Integer requestCount;

    @Column(name = "last_computed")
    private LocalDateTime lastComputed;
}