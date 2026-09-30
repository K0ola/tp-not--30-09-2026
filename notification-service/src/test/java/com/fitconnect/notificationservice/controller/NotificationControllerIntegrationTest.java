package com.fitconnect.notificationservice.controller;

import com.fitconnect.notificationservice.dto.NotificationResponse;
import com.fitconnect.notificationservice.model.NotificationStatus;
import com.fitconnect.notificationservice.model.NotificationType;
import com.fitconnect.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    void sendNotificationReturnsCreated() throws Exception {
        when(notificationService.send(any())).thenReturn(NotificationResponse.builder()
                .id(1L)
                .userId(7L)
                .email("john@example.com")
                .type(NotificationType.BOOKING_CONFIRMATION)
                .subject("Reservation confirmee")
                .content("Votre reservation est confirmee.")
                .createdDate(LocalDateTime.now())
                .sentDate(LocalDateTime.now())
                .status(NotificationStatus.SENT)
                .attempts(1)
                .build());

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"email\":\"john@example.com\",\"type\":\"BOOKING_CONFIRMATION\","
                                + "\"subject\":\"Reservation confirmee\",\"content\":\"Votre reservation est confirmee.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.attempts").value(1));
    }

    @Test
    void invalidEmailReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"email\":\"not-an-email\",\"type\":\"BOOKING_CONFIRMATION\","
                                + "\"subject\":\"Reservation confirmee\",\"content\":\"Votre reservation est confirmee.\"}"))
                .andExpect(status().isBadRequest());
    }
}
