package com.fitconnect.notificationservice.service;

import com.fitconnect.notificationservice.dto.NotificationRequest;
import com.fitconnect.notificationservice.dto.NotificationResponse;
import com.fitconnect.notificationservice.exception.ConflictException;
import com.fitconnect.notificationservice.exception.NotificationDeliveryException;
import com.fitconnect.notificationservice.model.Notification;
import com.fitconnect.notificationservice.model.NotificationStatus;
import com.fitconnect.notificationservice.model.NotificationType;
import com.fitconnect.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private NotificationService notificationService;

    private NotificationRequest request(String email) {
        return NotificationRequest.builder()
                .userId(1L)
                .email(email)
                .type(NotificationType.BOOKING_CONFIRMATION)
                .subject("Reservation confirmee")
                .content("Votre reservation est confirmee.")
                .build();
    }

    private void saveReturnsArgumentWithId(Long id) {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getId() == null) {
                notification.setId(id);
            }
            return notification;
        });
    }

    @Test
    void shouldMarkAsSent_whenDeliverySucceeds() {
        saveReturnsArgumentWithId(10L);

        NotificationResponse response = notificationService.send(request("john@example.com"));

        verify(emailSender).send(any(Notification.class));
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.getAttempts()).isEqualTo(1);
        assertThat(response.getSentDate()).isNotNull();
        assertThat(response.getCreatedDate()).isNotNull();
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    void shouldMarkAsFailed_whenDeliveryThrows() {
        saveReturnsArgumentWithId(11L);
        doThrow(new NotificationDeliveryException("Echec de l'envoi de l'email au destinataire : bob@fail.test"))
                .when(emailSender).send(any(Notification.class));

        NotificationResponse response = notificationService.send(request("bob@fail.test"));

        assertThat(response.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(response.getAttempts()).isEqualTo(1);
        assertThat(response.getSentDate()).isNull();
        assertThat(response.getErrorMessage())
                .isEqualTo("Echec de l'envoi de l'email au destinataire : bob@fail.test");
    }

    @Test
    void shouldRetryFailedNotification() {
        Notification failed = Notification.builder()
                .id(12L)
                .userId(1L)
                .email("bob@fail.test")
                .type(NotificationType.PAYMENT_CONFIRMATION)
                .subject("Paiement confirme")
                .content("Votre paiement a ete accepte.")
                .status(NotificationStatus.FAILED)
                .attempts(1)
                .errorMessage("Echec precedent")
                .createdDate(LocalDateTime.now().minusMinutes(5))
                .build();
        when(notificationRepository.findById(12L)).thenReturn(Optional.of(failed));
        saveReturnsArgumentWithId(12L);

        NotificationResponse response = notificationService.retry(12L);

        verify(emailSender).send(failed);
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.getAttempts()).isEqualTo(2);
        assertThat(response.getSentDate()).isNotNull();
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    void shouldThrowConflict_whenRetryingSentNotification() {
        Notification sent = Notification.builder()
                .id(13L)
                .userId(1L)
                .email("john@example.com")
                .type(NotificationType.BOOKING_REMINDER)
                .subject("Rappel")
                .content("Votre cours commence demain.")
                .status(NotificationStatus.SENT)
                .attempts(1)
                .sentDate(LocalDateTime.now())
                .createdDate(LocalDateTime.now())
                .build();
        when(notificationRepository.findById(13L)).thenReturn(Optional.of(sent));

        assertThatThrownBy(() -> notificationService.retry(13L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cette notification a deja ete envoyee");

        verify(emailSender, never()).send(any(Notification.class));
        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
