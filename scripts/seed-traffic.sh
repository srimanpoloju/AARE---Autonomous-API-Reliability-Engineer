#!/bin/bash

echo "Starting traffic generation..."
echo "Press [CTRL+C] to stop."

# Base URL of the Gateway
GATEWAY_URL="http://localhost:8080/api"

# Function to make a request and log
make_request() {
  local endpoint=$1
  local method=${2:-GET}
  local body=${3:-}
  local headers=${4:-}
  local delay=${5:-0.1} # default 100ms delay

  response=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$GATEWAY_URL/$endpoint" \
    $(if [[ -n "$headers" ]]; then echo -H "$headers"; fi) \
    $(if [[ -n "$body" ]]; then echo -H "Content-Type: application/json" -d "$body"; fi) \
  )
  echo "[$(date +'%H:%M:%S')] Request to $GATEWAY_URL/$endpoint ($method) -> HTTP $response"
  sleep "$delay"
}

# --- Traffic Simulation Loop ---
while true; do
  # 1. Normal Checkout (normal)
  make_request "target-api/checkout" "POST" '{"items": ["prod1", "prod2"], "amount": 100.0}'

  # 2. Payment (can produce 5xx under certain load/conditions)
  make_request "target-api/payment" "POST" '{"orderId": "order-123", "amount": 100.0}'

  # 3. Get Orders (variable latency)
  make_request "target-api/orders"

  # 4. Get Profile (schema that can evolve over time)
  make_request "target-api/profile"

  # 5. Inventory (rate-limited; can produce traffic drops)
  make_request "target-api/inventory/item1"

  # Simulate different loads
  if (( RANDOM % 10 == 0 )); then # Every ~10th iteration, hit harder or trigger a change
    echo "--- High Load or Special Event ---"
    for i in $(seq 1 5); do
      make_request "target-api/payment" "POST" '{"orderId": "order-highload", "amount": 200.0}' 0.05 # Faster
      make_request "target-api/orders" 0.05
    done
  fi

  if (( RANDOM % 20 == 0 )); then # Every ~20th iteration, evolve profile schema
    echo "--- Evolving Profile Schema ---"
    make_request "target-api/profile/evolve" "POST"
  fi

done
