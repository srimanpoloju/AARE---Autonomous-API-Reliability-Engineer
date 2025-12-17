package com.aare.analyzer.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "incident_evidence")
public class IncidentEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "incident_id")
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type")
    private EvidenceType evidenceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> data;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}