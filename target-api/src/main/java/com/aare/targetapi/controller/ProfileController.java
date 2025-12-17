package com.aare.targetapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private Map<String, Object> getProfileV1() {
        return Map.of(
                "userId", "user-123",
                "username", "john.doe",
                "email", "john.doe@example.com",
                "firstName", "John",
                "lastName", "Doe",
                "registrationDate", LocalDate.of(2023, 1, 1).toString(),
                "preferences", Map.of(
                        "newsletter", true,
                        "theme", "dark"
                )
        );
    }

    private Map<String, Object> getProfileV2() {
        return Map.of(
                "id", "user-123", // Changed field name
                "username", "john.doe",
                "emailAddress", "john.doe@example.com", // Changed field name
                "name", Map.of("first", "John", "last", "Doe"), // Nested name
                "registrationDate", LocalDate.of(2023, 1, 1).toString(),
                "contact", Map.of(
                        "phone", "123-456-7890", // New field
                        "address", "123 Main St"
                ),
                "preferences", Map.of(
                        "newsletter", true,
                        "theme", "dark",
                        "notifications", "email" // New field
                )
        );
    }

    private volatile boolean useV2Schema = false;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(@RequestParam(required = false) String schemaVersion) {
        if ("v2".equalsIgnoreCase(schemaVersion)) {
            useV2Schema = true;
        } else if ("v1".equalsIgnoreCase(schemaVersion)) {
            useV2Schema = false;
        }

        if (useV2Schema) {
            return ResponseEntity.ok(getProfileV2());
        } else {
            return ResponseEntity.ok(getProfileV1());
        }
    }
}
