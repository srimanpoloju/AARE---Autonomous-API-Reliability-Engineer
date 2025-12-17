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
@Table(name = "schema_version")
public class SchemaVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "endpoint_id")
    private String endpointId;

    @Column(name = "schema_hash")
    private String schemaHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> schemaSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inferred_types", columnDefinition = "jsonb")
    private Map<String, String> inferredTypes; // e.g., "fieldName": "STRING"

    @Column(name = "is_breaking_change")
    private Boolean isBreakingChange;

    private Integer version;

    @Column(name = "first_seen")
    private LocalDateTime firstSeen;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;
}