package com.aare.targetapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class OrdersController {

    private final Random random = new Random();
    private final ConcurrentHashMap<String, Integer> inventory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lastInventoryRequest = new ConcurrentHashMap<>();

    public OrdersController() {
        inventory.put("item1", 100);
        inventory.put("item2", 50);
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> checkout(@RequestBody Map<String, Object> payload) {
        // Normal behavior
        return ResponseEntity.ok(Map.of("message", "Checkout successful", "orderId", UUID.randomUUID().toString()));
    }

    @PostMapping("/payment")
    public ResponseEntity<Map<String, String>> payment(@RequestBody Map<String, Object> payload) throws InterruptedException {
        // Can produce 5xx under certain load/conditions
        if (random.nextInt(100) < 10) { // 10% chance of 500 error
            TimeUnit.MILLISECONDS.sleep(random.nextInt(200) + 50); // Simulate some processing before failure
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Payment processing failed unexpectedly"));
        }
        TimeUnit.MILLISECONDS.sleep(random.nextInt(100) + 20); // Simulate processing time
        return ResponseEntity.ok(Map.of("message", "Payment successful", "transactionId", UUID.randomUUID().toString()));
    }

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> getOrders() throws InterruptedException {
        // Variable latency
        int latency = random.nextInt(500) + 50; // 50ms to 550ms
        TimeUnit.MILLISECONDS.sleep(latency);

        return ResponseEntity.ok(Map.of(
                "orderId", UUID.randomUUID().toString(),
                "status", "completed",
                "items", new String[]{"item1", "item2"},
                "total", random.nextDouble() * 100
        ));
    }

    private int profileVersion = 1; // Used to simulate schema evolution
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile() {
        // Returns JSON with schema that can evolve over time
        if (profileVersion == 1) {
            return ResponseEntity.ok(Map.of(
                    "userId", "user-" + UUID.randomUUID().toString().substring(0, 8),
                    "username", "testuser",
                    "email", "test@example.com"
            ));
        } else {
            // Simulate schema evolution: add 'address', 'phone'
            return ResponseEntity.ok(Map.of(
                    "userId", "user-" + UUID.randomUUID().toString().substring(0, 8),
                    "username", "testuser",
                    "email", "test@example.com",
                    "address", Map.of("street", "123 Main St", "city", "Anytown"),
                    "phone", "555-1234"
            ));
        }
    }

    @PostMapping("/profile/evolve")
    public ResponseEntity<Map<String, String>> evolveProfileSchema() {
        profileVersion = (profileVersion == 1) ? 2 : 1;
        return ResponseEntity.ok(Map.of("message", "Profile schema evolved to version " + profileVersion));
    }


    @GetMapping("/inventory/{itemId}")
    public ResponseEntity<Map<String, Object>> getInventory(@PathVariable String itemId) throws InterruptedException {
        // Rate-limited; can produce traffic drops
        LocalDateTime now = LocalDateTime.now();
        lastInventoryRequest.putIfAbsent(itemId, now);

        if (now.minusSeconds(2).isBefore(lastInventoryRequest.get(itemId))) { // 2-second rate limit
            TimeUnit.MILLISECONDS.sleep(random.nextInt(50) + 10);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded for " + itemId));
        }
        lastInventoryRequest.put(itemId, now);

        Integer count = inventory.getOrDefault(itemId, 0);
        return ResponseEntity.ok(Map.of("itemId", itemId, "stock", count));
    }
}
