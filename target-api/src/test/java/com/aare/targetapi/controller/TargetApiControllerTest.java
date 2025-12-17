package com.aare.targetapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest({
    CheckoutController.class,
    PaymentController.class,
    OrderController.class,
    ProfileController.class,
    InventoryController.class
})
public class TargetApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void checkout_shouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Collections.singletonMap("amount", 100))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.orderId").exists());
    }

    @Test
    void payment_shouldReturnSuccessAndInternalServerError() throws Exception {
        // First 4 requests should be successful
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/payment")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Collections.singletonMap("amount", 100))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        // 5th request should be 5xx
        mockMvc.perform(post("/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Collections.singletonMap("amount", 100))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"));

        // Reset the counter in the controller by re-initializing it
        // This is a hack for unit test, normally stateful components are not tested like this
        // For a true integration test, you'd let the app run or use a separate test instance
    }

    @Test
    void order_shouldReturnOrderDetailsWithVariableLatency() throws Exception {
        mockMvc.perform(get("/orders/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("123"))
                .andExpect(jsonPath("$.latency_simulated_ms").isNumber());
    }

    @Test
    void profile_shouldReturnProfileV1ByDefault() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").exists()); // V1 specific field
    }

    @Test
    void profile_shouldReturnProfileV2WhenRequested() throws Exception {
        mockMvc.perform(get("/profile").param("schemaVersion", "v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists()) // V2 specific field
                .andExpect(jsonPath("$.emailAddress").exists()); // V2 specific field
    }

    @Test
    void inventory_shouldReturnInventoryAndRateLimit() throws Exception {
        // First few requests should be successful (RATE_LIMIT is 5)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/inventory/productA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId").value("productA"));
        }

        // 6th request should be rate-limited
        mockMvc.perform(get("/inventory/productA"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void inventory_shouldReturnServiceUnavailableForOutOfStock() throws Exception {
        mockMvc.perform(get("/inventory/out-of-stock-item"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("error"));
    }
}
