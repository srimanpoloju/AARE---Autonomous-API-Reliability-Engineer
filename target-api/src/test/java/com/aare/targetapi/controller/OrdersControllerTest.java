package com.aare.targetapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(OrdersController.class)
public class OrdersControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void checkout_shouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": [\"prod1\"], \"amount\": 50.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Checkout successful")));
    }

    @Test
    void getOrders_shouldReturnOrdersWithVariableLatency() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.status", is("completed")));
    }

    @Test
    void getProfile_shouldReturnProfileV1Initially() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", notNullValue()))
                .andExpect(jsonPath("$.email", is("test@example.com")))
                .andExpect(jsonPath("$.address").doesNotExist());
    }
}
