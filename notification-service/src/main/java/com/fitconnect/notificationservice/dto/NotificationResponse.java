package com.fitconnect.notificationservice.dto;

import com.fitconnect.notificationservice.model.NotificationStatus;
import com.fitconnect.notificationservice.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private Long id;
    private Long userId;
    private String email;
    private NotificationType type;
    private String subject;
    private String content;
    private LocalDateTime createdDate;
    private LocalDateTime sentDate;
    private NotificationStatus status;
    private Integer attempts;
    private String errorMessage;
}
