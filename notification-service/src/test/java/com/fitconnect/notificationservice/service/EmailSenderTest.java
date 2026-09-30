package com.fitconnect.notificationservice.service;

import com.fitconnect.notificationservice.exception.NotificationDeliveryException;
import com.fitconnect.notificationservice.model.Notification;
import com.fitconnect.notificationservice.model.NotificationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailSenderTest {

    private final EmailSender emailSender = new EmailSender();

    private Notification notification(String email) {
        return Notification.builder()
                .userId(1L)
                .email(email)
                .type(NotificationType.BOOKING_CONFIRMATION)
                .subject("Reservation confirmee")
                .content("Votre reservation est confirmee.")
                .build();
    }

    @Test
    void shouldSucceed_forRegularEmail() {
        assertThatCode(() -> emailSender.send(notification("john@example.com")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldThrow_forFailTestDomain() {
        assertThatThrownBy(() -> emailSender.send(notification("bob@fail.test")))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessageContaining("bob@fail.test");
    }
}
