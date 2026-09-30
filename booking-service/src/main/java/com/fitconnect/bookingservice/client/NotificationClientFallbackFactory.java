package com.fitconnect.bookingservice.client;

import com.fitconnect.bookingservice.client.dto.NotificationRequest;
import com.fitconnect.bookingservice.client.dto.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback du circuit breaker pour notification-service. Les notifications ne sont pas
 * critiques pour la saga : on degrade silencieusement (log) au lieu de faire echouer
 * la reservation.
 */
@Component
@Slf4j
public class NotificationClientFallbackFactory implements FallbackFactory<NotificationClient> {

    @Override
    public NotificationClient create(Throwable cause) {
        return request -> {
            log.warn("notification-service indisponible, notification {} pour l'utilisateur {} non envoyee : {}",
                    request.getType(), request.getUserId(), cause.toString());
            return NotificationResponse.builder()
                    .userId(request.getUserId())
                    .email(request.getEmail())
                    .type(request.getType())
                    .status("FALLBACK")
                    .build();
        };
    }
}
