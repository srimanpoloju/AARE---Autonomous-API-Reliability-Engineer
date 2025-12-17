package com.aare.targetapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final Random random = new Random();

    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, String>> getOrderDetails(@PathVariable String orderId) throws InterruptedException {
        // Simulate variable latency between 50ms and 500ms
        long latency = 50 + random.nextInt(450); // 50 to 499 ms
        TimeUnit.MILLISECONDS.sleep(latency);

        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "status", "completed",
                "item", "Product X",
                "quantity", "1",
                "latency_simulated_ms", String.valueOf(latency)
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listOrders() throws InterruptedException {
        // Simulate variable latency between 100ms and 1000ms
        long latency = 100 + random.nextInt(900); // 100 to 999 ms
        TimeUnit.MILLISECONDS.sleep(latency);

        return ResponseEntity.ok(Map.of(
                "orders", new Object[]{
                        Map.of("orderId", UUID.randomUUID().toString(), "item", "Product A"),
                        Map.of("orderId", UUID.randomUUID().toString(), "item", "Product B")
                },
                "totalCount", 2,
                "latency_simulated_ms", String.valueOf(latency)
        ));
    }
}
