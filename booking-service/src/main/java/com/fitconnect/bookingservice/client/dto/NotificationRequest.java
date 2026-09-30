package com.fitconnect.bookingservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Corps envoye a POST /api/notifications. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {
    private Long userId;
    private String email;
    private NotificationType type;
    private String subject;
    private String content;
}
