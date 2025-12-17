-- V1__init.sql

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- api_event table
CREATE TABLE IF NOT EXISTS api_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    request_id VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    method VARCHAR(10) NOT NULL,
    path VARCHAR(255) NOT NULL,
    query TEXT,
    status_code INTEGER NOT NULL,
    latency_ms BIGINT NOT NULL,
    req_headers JSONB,
    res_headers JSONB,
    req_body_sample TEXT,
    res_body_sample TEXT,
    schema_fingerprint VARCHAR(255),
    service_name VARCHAR(255),
    environment VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_api_event_timestamp ON api_event(timestamp);
CREATE INDEX IF NOT EXISTS idx_api_event_service_name ON api_event(service_name);
CREATE INDEX IF NOT EXISTS idx_api_event_method_path ON api_event(method, path);


-- endpoint_baseline table
CREATE TABLE IF NOT EXISTS endpoint_baseline (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    endpoint_id VARCHAR(255) NOT NULL,
    metric_window VARCHAR(50) NOT NULL,
    error_rate_pct NUMERIC(5,2) NOT NULL,
    p50_latency_ms INTEGER NOT NULL,
    p95_latency_ms INTEGER NOT NULL,
    p99_latency_ms INTEGER NOT NULL,
    request_count INTEGER NOT NULL,
    last_computed TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    UNIQUE (endpoint_id, metric_window)
);

CREATE INDEX IF NOT EXISTS idx_endpoint_baseline_endpoint_id ON endpoint_baseline(endpoint_id);


-- incident table
CREATE TABLE IF NOT EXISTS incident (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    endpoint_id VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    triggered_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    detected_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    acknowledged_at TIMESTAMP WITHOUT TIME ZONE,
    resolved_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_incident_endpoint_id ON incident(endpoint_id);
CREATE INDEX IF NOT EXISTS idx_incident_type ON incident(type);
CREATE INDEX IF NOT EXISTS idx_incident_status ON incident(status);
CREATE INDEX IF NOT EXISTS idx_incident_detected_at ON incident(detected_at);


-- incident_evidence table
CREATE TABLE IF NOT EXISTS incident_evidence (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id UUID NOT NULL REFERENCES incident(id),
    evidence_type VARCHAR(50) NOT NULL,
    data JSONB,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_incident_evidence_incident_id ON incident_evidence(incident_id);


-- rca_report table
CREATE TABLE IF NOT EXISTS rca_report (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    incident_id UUID NOT NULL REFERENCES incident(id),
    status VARCHAR(50) NOT NULL,
    root_cause_summary TEXT,
    likely_trigger TEXT,
    recommended_fixes JSONB,
    confidence NUMERIC(3,2),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rca_report_incident_id ON rca_report(incident_id);


-- schema_version table
CREATE TABLE IF NOT EXISTS schema_version (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    endpoint_id VARCHAR(255) NOT NULL,
    schema_hash VARCHAR(255) NOT NULL,
    schema_snapshot JSONB,
    inferred_types JSONB,
    is_breaking_change BOOLEAN NOT NULL DEFAULT FALSE,
    version INTEGER NOT NULL,
    first_seen TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_seen TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    UNIQUE (endpoint_id, version)
);

CREATE INDEX IF NOT EXISTS idx_schema_version_endpoint_id ON schema_version(endpoint_id);


-- users table (for UI authentication)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
