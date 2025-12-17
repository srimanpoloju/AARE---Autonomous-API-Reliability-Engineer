# AARE - Autonomous API Reliability Engineer

A production-grade API reliability monitoring and root-cause analysis system that automatically detects incidents, analyzes API traffic, and generates AI-driven root cause reports.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Client / Users                               │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                    ┌────────▼────────┐
                    │   UI (Next.js)  │ (Port 3001)
                    └────────┬────────┘
                             │
                    ┌────────▼─────────────────┐
         ┌──────────┤   Incident API (Spring Boot)    │ (Port 8088)
         │          │  - CRUD Incidents       │
         │          │  - Evidence Mgmt        │
         │          │  - RCA Reports          │
         │          └────────┬─────────────────┘
         │                   │
    ┌────▼──────────────┐    │
    │  PostgreSQL DB    │◄───┘
    │                   │
    │ - api_events      │
    │ - incidents       │
    │ - baselines       │
    │ - rca_reports     │
    │ - schema_versions │
    │ - users           │
    └────┬──────────────┘
         │
    ┌────▼────────────────────────┐
    │   Gateway (Spring Boot)      │ (Port 8080)
    │ - Routes requests            │
    │ - Captures metadata          │
    │ - Publishes to RabbitMQ      │
    └────┬───────────────┬─────────┘
         │               │
    ┌────▼─────┐    ┌────▼──────────┐
    │Target API │    │   Collector   │ (Port 8082)
    │(Spring)   │    │ (Spring Boot) │
    │ - /orders │    │ - Ingests     │
    │ - /checkout│   │ - Redacts     │
    │ - /payment│    │ - Stores      │
    │ - /profile│    │ - Publishes   │
    │ - /inventory│   └────┬─────────┘
    └───────────┘         │
                    ┌─────▼──────────┐
                    │   RabbitMQ     │ (Port 5672)
                    │ - Events Queue │
                    └─────┬──────────┘
                          │
                   ┌──────▼──────────┐
                   │   Analyzer      │
                   │   (Spring Boot) │
                   │                 │
                   │ - Metrics       │
                   │ - Baselines     │
                   │ - Detects:      │
                   │   * Error spike │
                   │   * Latency ↑   │
                   │   * Contract ✗  │
                   │   * Traffic ↓   │
                   │ - AI RCA        │
                   │ - Updates DB    │
                   └─────────────────┘

Observability:
  ├─ OpenTelemetry (all services)
  ├─ Jaeger (Port 16686) - Tracing
  ├─ Prometheus (Port 9090) - Metrics
  └─ Grafana (Port 3000/grafana) - Dashboards
```

## Key Features

- **Real-time traffic capture** via Spring Cloud Gateway
- **Automated incident detection**:
  - `ERROR_SPIKE`: 5m error rate > baseline × factor
  - `LATENCY_REGRESSION`: p95 latency surge
  - `CONTRACT_BREAK`: Schema changes detected
  - `TRAFFIC_DROP`: Request volume decline
- **AI-driven RCA**: OpenAI integration for root-cause analysis
- **Evidence-based**: incidents backed by metrics, errors, and schema diffs
- **Full observability**: OpenTelemetry, Jaeger, Prometheus, Grafana
- **Secure defaults**: Redaction of secrets, PII, tokens in logs
- **Production-ready**: PostgreSQL, RabbitMQ, health checks, retries, idempotency

## Quick Start

### Prerequisites

- Docker & Docker Compose
- (Optional) OpenAI API key for AI RCA

### 1. Clone and setup

```bash
git clone <repo>
cd aare
cp .env.example .env
```

Edit `.env` to add your `OPENAI_API_KEY` if desired.

### 2. Start all services

```bash
make up
```

This will:
- Start PostgreSQL, RabbitMQ, Jaeger, Prometheus, Grafana
- Build and start all microservices
- Initialize database with Flyway migrations
- Expose services on their ports

### 3. Access dashboards

- **UI Dashboard**: http://localhost:3001
  - Login: admin / admin (configurable)
- **Grafana**: http://localhost:3000/grafana
  - Login: admin / admin
- **Jaeger**: http://localhost:16686
- **Prometheus**: http://localhost:9090
- **RabbitMQ Management**: http://localhost:15672
  - Login: guest / guest

### 4. Generate test traffic

```bash
make seed-traffic
```

This will:
- Send normal traffic through the gateway
- Trigger error conditions
- Trigger schema changes
- Trigger latency spikes

Monitor in the UI for incidents.

### 5. Stop services

```bash
make down
```

## How It Works

### 1. Traffic Capture (Gateway → Collector)

**Gateway** (Spring Cloud Gateway) intercepts HTTP requests:
- Captures: method, path, query, status, latency, headers, sampled body
- Publishes event to RabbitMQ `api.events` queue
- Forwards request to target service

**Collector** (Spring Boot microservice):
- Listens on RabbitMQ for events
- **Redacts** secrets: Authorization, cookies, card numbers, emails
- **Limits** body size (max 8KB)
- Stores raw event in `api_event` table
- Re-publishes to `api.analysis` queue for analyzer

### 2. Incident Detection (Analyzer)

**Analyzer** (Spring Boot service):

Every 1 minute (configurable):
1. **Compute rolling metrics** per endpoint (method + path):
   - Last 5m, 30m, 24h windows
   - Error rate, p50/p95/p99 latency, request count
   - Infer types from response JSON

2. **Update baselines**:
   - Merge historical data
   - Store in `endpoint_baseline` table

3. **Detect incidents**:
   - Compare 5m metrics to baseline
   - Check thresholds for each incident type
   - Create `incident` + `incident_evidence` records if triggered
   - Idempotent: same endpoint+type+window = no duplicates

4. **Trigger RCA**:
   - Publish `rca.requested` event
   - Store RCA job in queue

5. **Generate RCA report**:
   - Build evidence-grounded prompt
   - Call OpenAI API
   - Store result in `rca_report` table
   - If no API key: mark as "SKIPPED_NO_KEY"

### 3. Incident Management (Incident API)

REST API endpoints:

```
GET  /api/incidents?status=OPEN&type=ERROR_SPIKE&q=search
GET  /api/incidents/{id}
GET  /api/incidents/{id}/evidence
GET  /api/incidents/{id}/rca
POST /api/incidents/{id}/ack
POST /api/incidents/{id}/resolve
GET  /api/health
```

### 4. Dashboard (UI)

**Pages**:
- `/login` - JWT auth
- `/incidents` - Table with filters, pagination
- `/incidents/[id]` - Full incident details:
  - Timeline chart (error rate, latency, volume)
  - Metrics snapshot
  - Schema diff (if CONTRACT_BREAK)
  - Sample errors (if ERROR_SPIKE)
  - AI RCA report
  - Action buttons (acknowledge, resolve)

## Database Schema

### Core Tables

**api_event**
```
id UUID PRIMARY KEY
timestamp TIMESTAMP
method VARCHAR
path VARCHAR
status_code INT
latency_ms INT
req_body_sample TEXT (redacted)
res_body_sample TEXT (redacted)
schema_fingerprint VARCHAR
service_name VARCHAR
environment VARCHAR
```

**endpoint_baseline**
```
id UUID PRIMARY KEY
endpoint_id VARCHAR (method+path hash)
metric_window VARCHAR (5m, 30m, 24h)
error_rate_pct DECIMAL
p50_latency_ms INT
p95_latency_ms INT
p99_latency_ms INT
request_count INT
last_computed TIMESTAMP
```

**incident**
```
id UUID PRIMARY KEY
endpoint_id VARCHAR
type VARCHAR (ERROR_SPIKE, LATENCY_REGRESSION, CONTRACT_BREAK, TRAFFIC_DROP)
status VARCHAR (OPEN, ACKNOWLEDGED, RESOLVED)
severity VARCHAR (LOW, MEDIUM, HIGH, CRITICAL)
triggered_at TIMESTAMP
detected_at TIMESTAMP
acknowledged_at TIMESTAMP
resolved_at TIMESTAMP
```

**incident_evidence**
```
id UUID PRIMARY KEY
incident_id UUID
evidence_type VARCHAR (metrics, schema_diff, sample_errors, timeline)
data JSONB
created_at TIMESTAMP
```

**rca_report**
```
id UUID PRIMARY KEY
incident_id UUID
status VARCHAR (PENDING, GENERATED, FAILED, SKIPPED_NO_KEY)
root_cause_summary TEXT
likely_trigger TEXT
recommended_fixes JSONB
confidence DECIMAL
created_at TIMESTAMP
updated_at TIMESTAMP
```

**schema_version**
```
id UUID PRIMARY KEY
endpoint_id VARCHAR
schema_hash VARCHAR
schema_snapshot JSONB (flattened JSON structure)
inferred_types JSONB
is_breaking_change BOOLEAN
version INT
first_seen TIMESTAMP
last_seen TIMESTAMP
```

**users**
```
id UUID PRIMARY KEY
username VARCHAR UNIQUE
password_hash VARCHAR
role VARCHAR (admin, viewer)
created_at TIMESTAMP
```

## API Event Lifecycle

1. Client sends request to `http://localhost:8080/api/*`
2. Gateway intercepts, logs metadata
3. Gateway forwards to target service
4. Gateway publishes event to RabbitMQ `api.events`
5. Collector receives, redacts, stores, re-publishes to `api.analysis`
6. Analyzer receives, updates metrics, detects incidents
7. If incident triggered:
   - Create incident record
   - Create evidence records
   - Trigger RCA job
8. UI queries incident-api for incidents and displays

## Configuration

### Environment Variables

**Database**
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`

**RabbitMQ**
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`

**Auth**
- `JWT_SECRET` - for incident-api
- `ADMIN_USERNAME`, `ADMIN_PASSWORD` - initial admin user

**AI/OpenAI**
- `OPENAI_API_KEY` - optional; if not set, RCA reports are skipped

**Observability**
- `OTEL_EXPORTER_OTLP_ENDPOINT` - Jaeger OTLP endpoint

## Testing

### Unit Tests

```bash
make test
```

Runs tests in all services:
- `gateway/pom.xml` (Spring Boot tests)
- `collector/pom.xml` (Spring Boot tests)
- `target-api/pom.xml` (Spring Boot tests)
- `analyzer/pom.xml` (Spring Boot tests)
- `incident-api/pom.xml` (Spring Boot tests)


### Integration Tests

See `scripts/integration-test.sh` for end-to-end verification:
1. Traffic flows through gateway
2. Events stored in DB
3. Analyzer detects incidents
4. UI displays incidents
5. RCA reports generated

### Acceptance Tests

```bash
./scripts/acceptance-test.sh
```

Verifies:
- [ ] Error spike detection
- [ ] Latency regression detection
- [ ] Contract break detection
- [ ] Traffic drop detection
- [ ] AI RCA report generation
- [ ] UI incident display

## Monitoring & Observability

### Prometheus Metrics

Auto-scraped from all services:

**Gateway**
- `http_requests_total` - Total requests
- `http_request_duration_seconds` - Latency histogram
- `http_requests_errors_total` - Error count
- `rabbitmq_publish_lag_seconds` - Event publish lag

**Analyzer**
- `incidents_created_total` - Incident creation rate
- `metrics_compute_duration_seconds` - Analysis latency
- `baseline_update_lag_seconds` - How stale baselines are

**Incident API**
- `api_requests_total` - API call count
- `db_query_duration_seconds` - DB latency

### Jaeger Tracing

All services export OpenTelemetry traces:
- Request ID propagated across services
- Service dependencies visible
- Latency per service segment

### Grafana Dashboards

Pre-provisioned dashboards:
- **AARE Overview** - incident count, error rate, latency trends
- **Gateway Metrics** - request volume, latency distribution, error rate
- **Analyzer Performance** - detection latency, baseline staleness
- **API Health** - DB connection pool, queue depth, response times

## Incident Response Workflow

1. **Incident Detected** (Analyzer)
   - Threshold breached
   - Record created in DB
   - Status: OPEN

2. **Dashboard Alert** (UI)
   - Incident appears in UI
   - Engineer clicks to view

3. **Evidence Review**
   - View metrics snapshot
   - See sample errors or schema diff
   - Read AI RCA report

4. **Acknowledge** (Manual)
   - Engineer marks as acknowledged
   - Status: ACKNOWLEDGED
   - Timestamp recorded

5. **Resolve** (Manual)
   - Engineer deploys fix or finds false positive
   - Mark as resolved
   - Status: RESOLVED
   - Store resolution notes (future enhancement)

## Production Deployment

### Kubernetes

Included files:
- `infra/k8s/` - Helm charts
- Each service has `deployment.yaml`, `service.yaml`, `hpa.yaml`

Deploy:
```bash
helm install aare ./infra/k8s/aare
```

### Cloud Setup

- Use managed PostgreSQL (AWS RDS, Azure Database, GCP Cloud SQL)
- Use managed RabbitMQ (AWS MQ, Azure Service Bus, CloudAMQP)
- Deploy services to ECS / Kubernetes / Cloud Run
- Use cloud-native Jaeger (Lightstep, Datadog, New Relic)
- Use cloud-native Prometheus / Grafana (Datadog, Splunk, etc.)

Update `.env` with cloud endpoints.

## Development

### Project Structure

```
aare/
├── docker-compose.yml          # Local dev environment
├── .env.example                # Config template
├── Makefile                    # Commands
├── README.md                   # This file
│
├── gateway/                    # Spring Cloud Gateway
│   ├── pom.xml
│   ├── src/
│   └── Dockerfile
│
├── target-api/                 # Sample API (Spring Boot)
│   ├── pom.xml
│   ├── src/
│   └── Dockerfile
│
├── collector/                  # Event collector (Spring Boot)
│   ├── pom.xml
│   ├── src/
│   └── Dockerfile
│
├── analyzer/                   # Incident analyzer (Spring Boot)
│   ├── pom.xml
│   ├── src/
│   └── Dockerfile
│
├── incident-api/               # REST API (Spring Boot)
│   ├── pom.xml
│   ├── src/
│   └── Dockerfile
│
├── ui/                         # Next.js dashboard
│   ├── package.json
│   ├── app/
│   └── Dockerfile
│
├── infra/
│   ├── postgres/               # DB migrations (Flyway)
│   │   └── migrations/
│   ├── prometheus/             # Prometheus config
│   │   └── prometheus.yml
│   ├── grafana/                # Grafana provisioning
│   │   ├── dashboards/
│   │   └── datasources/
│   └── jaeger/                 # Jaeger config
│
├── scripts/
│   ├── generate-traffic.sh     # Synthetic traffic
│   ├── integration-test.sh
│   └── acceptance-test.sh
│
└── .github/
    └── workflows/
        └── ci.yml              # GitHub Actions
```

### Running Services Locally (non-Docker)

For development without Docker:

```bash
# Terminal 1: PostgreSQL
docker run -d --name pg -p 5432:5432 -e POSTGRES_PASSWORD=aarepass postgres:15

# Terminal 2: RabbitMQ
docker run -d --name rmq -p 5672:5672 -p 15672:15672 rabbitmq:3.12-management-alpine

# Terminal 3: Target API
cd target-api && mvn spring-boot:run

# Terminal 4: Gateway
cd gateway && mvn spring-boot:run

# Terminal 5: Collector
cd collector && mvn spring-boot:run

# Terminal 6: Analyzer
cd analyzer && mvn spring-boot:run

# Terminal 7: Incident API
cd incident-api && mvn spring-boot:run

# Terminal 8: UI
cd ui && npm install && npm run dev
```

## Troubleshooting

### Services not starting

Check logs:
```bash
docker-compose logs <service-name>
docker-compose ps  # See status
```

### Database migration fails

```bash
docker-compose exec postgres psql -U aare -d aare
\d  # List tables
```

### No incidents detected

1. Check traffic is flowing:
   ```bash
   curl -v http://localhost:8080/api/orders
   ```

2. Check analyzer logs:
   ```bash
   docker-compose logs analyzer
   ```

3. Check events in DB:
   ```sql
   SELECT COUNT(*) FROM api_event;
   SELECT * FROM incident;
   ```

### AI RCA reports not generating

- If `OPENAI_API_KEY` not set, status is `SKIPPED_NO_KEY` (expected)
- If API key is set, check logs for errors
- Rate limits: OpenAI may throttle; analyzer retries with backoff

## Performance Tuning

### Incident Detection Tuning

Edit `analyzer/src/main/resources/application.yml` to tune detection thresholds:
```yaml
aare:
  incident:
    detection:
      error-spike:
        threshold: 0.1 # 10% error rate
        factor: 2.0 # 2x the baseline
        min-requests: 20
      latency-regression:
        p95-factor: 1.5 # 1.5x the baseline p95
        min-requests: 20
```

### Database Indexes

Add indexes for common queries in migrations:
```sql
CREATE INDEX idx_api_event_timestamp ON api_event(timestamp);
CREATE INDEX idx_api_event_endpoint ON api_event(method, path);
CREATE INDEX idx_incident_status ON incident(status);
```

### Queue Tuning

Edit `docker-compose.yml`:
```yaml
RABBITMQ_QUEUE_PREFETCH: 10  # How many messages to prefetch
```

## Contributing

1. Fork repo
2. Create feature branch
3. Make changes
4. Run tests: `make test`
5. Submit PR

## License

MIT

## Support

For issues or questions:
- Check GitHub issues
- Review logs: `docker-compose logs`
- Read this README
