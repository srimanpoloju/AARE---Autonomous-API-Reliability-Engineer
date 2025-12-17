.PHONY: up down logs test seed-traffic

# Default to loading .env file if it exists
ifneq (,$(wildcard ./.env))
    include .env
    export
endif

up:
	@echo "Starting all AARE services..."
	docker-compose up --build -d

down:
	@echo "Stopping all AARE services..."
	docker-compose down

logs:
	@echo "Tailing logs for all services..."
	docker-compose logs -f

# This is a placeholder. A proper implementation would run tests in each service.
test:
	@echo "Running tests for all services..."
	@echo "Building services to ensure dependencies are fresh..."
	docker-compose build
	@echo "Running tests in collector..."
	docker-compose run --rm collector mvn test
	@echo "Running tests in analyzer..."
	docker-compose run --rm analyzer mvn test
	@echo "Running tests in incident-api..."
	docker-compose run --rm incident-api mvn test
	@echo "Running tests in gateway..."
	docker-compose run --rm gateway mvn test
	@echo "Running tests in target-api..."
	docker-compose run --rm target-api mvn test
	# Add UI tests here if any:
	# docker-compose run --rm ui npm test

# Generates sample traffic to the gateway to test the system.
seed-traffic:
	@echo "Generating seed traffic..."
	@while true; do \
		curl -s -o /dev/null -w "Checkout: %{http_code}\n" http://localhost:8080/checkout; \
		curl -s -o /dev/null -w "Payment (success): %{http_code}\n" -X POST -H "Content-Type: application/json" -d '{"amount": 100}' http://localhost:8080/payment; \
		curl -s -o /dev/null -w "Payment (failure): %{http_code}\n" -X POST -H "Content-Type: application/json" -d '{"amount": -10}' http://localhost:8080/payment; \
		curl -s -o /dev/null -w "Orders: %{http_code}\n" http://localhost:8080/orders?id=123; \
		curl -s -o /dev/null -w "Profile (v1): %{http_code}\n" http://localhost:8080/profile; \
		curl -s -o /dev/null -w "Profile (v2 - breaking change): %{http_code}\n" http://localhost:8080/profile?version=2; \
		curl -s -o /dev/null -w "Inventory (limited): %{http_code}\n" http://localhost:8080/inventory; \
		echo "-----------------"; \
		sleep 2; \
	done