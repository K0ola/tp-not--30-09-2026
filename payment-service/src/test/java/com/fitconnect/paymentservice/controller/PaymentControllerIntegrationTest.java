package com.fitconnect.paymentservice.controller;

import com.fitconnect.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private String body(long bookingId, String amount) {
        return """
                {"bookingId": %d, "bookingReference": "BK-ABC12", "userId": 1, "amount": %s,
                 "paymentMethod": "CREDIT_CARD", "cardLastFour": "1234"}
                """.formatted(bookingId, amount);
    }

    @Test
    void shouldProcessThenRefundPayment() throws Exception {
        String location = mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(body(10, "50.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentReference", startsWith("PAY-")))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(location.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/payments/booking/{bookingId}", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        // un second paiement sur la meme reservation est refuse
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(body(10, "50.00")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/payments/{id}/refund", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(post("/api/payments/{id}/refund", id))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/payments/user/{userId}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void shouldReturnFailedStatusForLargeAmount() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(body(11, "150.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").exists());
    }

    @Test
    void shouldValidateRequest() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": 1, \"amount\": 10, \"paymentMethod\": \"CREDIT_CARD\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/payments/booking/{bookingId}", 999))
                .andExpect(status().isNotFound());
    }
}
