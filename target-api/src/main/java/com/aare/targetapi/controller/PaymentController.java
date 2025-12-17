package com.aare.targetapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final AtomicInteger requestCount = new AtomicInteger(0);

    @PostMapping
    public ResponseEntity<Map<String, String>> processPayment(@RequestBody Map<String, Object> payload) {
        int count = requestCount.incrementAndGet();

        // Simulate 5xx error every 5 requests
        if (count % 5 == 0) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Payment service unavailable (simulated)"));
        }

        return ResponseEntity.ok(Map.of("status", "success", "message", "Payment processed successfully"));
    }
}
