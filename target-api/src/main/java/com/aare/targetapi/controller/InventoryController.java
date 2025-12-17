package com.aare.targetapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private volatile LocalDateTime lastResetTime = LocalDateTime.now();
    private final int RATE_LIMIT = 5; // Allow 5 requests per 10 seconds
    private final int TIME_WINDOW_SECONDS = 10;

    @GetMapping("/{productId}")
    public ResponseEntity<Map<String, String>> getInventory(@PathVariable String productId) {
        LocalDateTime now = LocalDateTime.now();

        // Reset counter if time window has passed
        if (now.minusSeconds(TIME_WINDOW_SECONDS).isAfter(lastResetTime)) {
            requestCounter.set(0);
            lastResetTime = now;
        }

        int currentRequests = requestCounter.incrementAndGet();

        if (currentRequests > RATE_LIMIT) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(TIME_WINDOW_SECONDS))
                    .body(Map.of("status", "error", "message", "Rate limit exceeded. Try again in " + TIME_WINDOW_SECONDS + " seconds."));
        }

        // Simulate a traffic drop if product is "out-of-stock"
        if ("out-of-stock-item".equalsIgnoreCase(productId)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "error", "message", "Simulated traffic drop for out of stock item."));
        }


        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "stock", String.valueOf(100 - currentRequests),
                "message", "Inventory details for " + productId
        ));
    }
}
