package com.fitconnect.notificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private Long userId;

    @NotBlank
    @Email
    private String email;

    @NotNull
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @NotBlank
    private String subject;

    @NotBlank
    @Column(length = 2000)
    private String content;

    private LocalDateTime sentDate;

    private LocalDateTime createdDate;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    @Builder.Default
    private Integer attempts = 0;

    private String errorMessage;
}
