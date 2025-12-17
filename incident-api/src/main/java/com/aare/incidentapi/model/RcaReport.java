package com.aare.incidentapi.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "rca_report")
public class RcaReport {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    private RcaStatus status;

    @Column(name = "root_cause_summary", columnDefinition = "TEXT")
    private String rootCauseSummary;

    @Column(name = "likely_trigger", columnDefinition = "TEXT")
    private String likelyTrigger;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_fixes", columnDefinition = "jsonb")
    private List<String> recommendedFixes;

    private BigDecimal confidence;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
