package com.fitconnect.notificationservice.dto;

import com.fitconnect.notificationservice.model.NotificationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {

    @NotNull(message = "L'identifiant de l'utilisateur est obligatoire")
    private Long userId;

    @NotBlank(message = "L'email du destinataire est obligatoire")
    @Email(message = "L'email du destinataire est invalide")
    private String email;

    @NotNull(message = "Le type de notification est obligatoire")
    private NotificationType type;

    @NotBlank(message = "Le sujet est obligatoire")
    private String subject;

    @NotBlank(message = "Le contenu est obligatoire")
    private String content;
}
