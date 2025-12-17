package com.aare.targetapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/checkout")
public class CheckoutController {

    @PostMapping
    public ResponseEntity<Map<String, String>> checkout(@RequestBody Map<String, Object> payload) {
        // Simulate a normal, successful checkout process
        return ResponseEntity.ok(Map.of(
                "orderId", UUID.randomUUID().toString(),
                "status", "success",
                "message", "Order placed successfully"
        ));
    }
}
