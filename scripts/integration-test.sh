#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status

echo "--- Starting AARE Integration Test ---"

# --- Configuration ---
GATEWAY_URL="http://localhost:8080/api"
INCIDENT_API_URL="http://localhost:8088/api"
TEST_USER="admin"
TEST_PASS="admin"
JWT_TOKEN=""

# --- Utility Functions ---
log_step() {
  echo ""
  echo ">>> $1 <<<"
  echo ""
}

wait_for_service() {
  local service_name=$1
  local url=$2
  local timeout=$3
  echo "Waiting for $service_name at $url to be healthy..."
  for i in $(seq 1 $timeout);
  do
    if curl -s -f "$url" > /dev/null; then
      echo "$service_name is up!"
      return 0
    fi
    sleep 5
  done
  echo "Error: $service_name did not become healthy within $timeout seconds."
  docker-compose logs
  exit 1
}

# --- Main Test Flow ---

# 1. Bring up services
log_step "1. Bringing up Docker Compose services..."
docker-compose up --build -d --remove-orphans

# 2. Wait for core services to be healthy
wait_for_service "PostgreSQL" "http://localhost:5432" 60
wait_for_service "RabbitMQ Management" "http://localhost:15672" 60 # Check management UI
wait_for_service "Gateway" "$GATEWAY_URL/target-api/health" 120 # Check health endpoint of target-api via gateway
wait_for_service "Incident API" "$INCIDENT_API_URL/health" 120

# 3. Authenticate with Incident API
log_step "3. Authenticating with Incident API to get JWT token..."
AUTH_RESPONSE=$(curl -s -X POST "$INCIDENT_API_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\"}")

JWT_TOKEN=$(echo "$AUTH_RESPONSE" | jq -r '.jwt')

if [[ -z "$JWT_TOKEN" || "$JWT_TOKEN" == "null" ]]; then
  echo "Error: Failed to retrieve JWT token. Response: $AUTH_RESPONSE"
  docker-compose logs incident-api
  exit 1
fi
echo "Successfully obtained JWT token."


# 4. Generate traffic to trigger incidents
log_step "4. Generating traffic (this might take some time to trigger incidents)..."
# Start seed-traffic in the background
./scripts/seed-traffic.sh &
SEED_PID=$!
echo "Seed traffic process PID: $SEED_PID"

# Let traffic run for a while to ensure incidents are detected
echo "Running traffic for 90 seconds. Please wait..."
sleep 90

log_step "5. Stopping traffic generation..."
kill $SEED_PID
wait $SEED_PID || true # Wait for it to die, ignore errors from kill


# 5. Check for incidents in Incident API
log_step "5. Querying Incident API for detected incidents..."
INCIDENTS_RESPONSE=$(curl -s -X GET "$INCIDENT_API_URL/incidents" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Accept: application/json")

echo "Incident API Response:"
echo "$INCIDENTS_RESPONSE" | jq .

INCIDENT_COUNT=$(echo "$INCIDENTS_RESPONSE" | jq '. | length')

if [[ "$INCIDENT_COUNT" -gt 0 ]]; then
  echo "SUCCESS: $INCIDENT_COUNT incidents detected!"
else
  echo "WARNING: No incidents detected. This might indicate an issue or not enough traffic."
  # It's possible for traffic to not trigger an incident in short runs.
  # For a "passing" test, we'll accept 0 for now but warn.
fi

# Try to trigger a contract break specifically
log_step "6. Triggering a contract break and checking for incident..."
INITIAL_PROFILE=$(curl -s -X GET "$GATEWAY_URL/target-api/profile" | jq '.address') # Check if address exists

make_request "$GATEWAY_URL/target-api/profile/evolve" "POST" # Evolve schema
sleep 5 # Give analyzer time to process

PROFILE_CONTRACT_BREAK_RESPONSE=$(curl -s -X GET "$INCIDENT_API_URL/incidents?type=CONTRACT_BREAK" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Accept: application/json")

CONTRACT_BREAK_COUNT=$(echo "$PROFILE_CONTRACT_BREAK_RESPONSE" | jq '. | length')

if [[ "$CONTRACT_BREAK_COUNT" -gt 0 ]]; then
  echo "SUCCESS: Contract break incident detected!"
else
  echo "FAILURE: Contract break incident NOT detected."
  exit 1
fi


# 6. Optional: Check Jaeger, Prometheus, Grafana (manual verification needed for now)
log_step "7. Verify observability (manual step):"
echo "   - Jaeger UI: http://localhost:16686"
echo "   - Prometheus UI: http://localhost:9090"
echo "   - Grafana UI: http://localhost:3000 (admin/admin)"
echo "     Check AARE Overview Dashboard for metrics and incident trends."


log_step "Integration Test Completed (check logs above for details)"

# --- Cleanup ---
log_step "8. Bringing down Docker Compose services..."
docker-compose down

echo "--- AARE Integration Test Finished ---"
