package com.fitconnect.bookingservice.service;

import com.fitconnect.bookingservice.client.NotificationClient;
import com.fitconnect.bookingservice.client.dto.NotificationRequest;
import com.fitconnect.bookingservice.client.dto.NotificationType;
import com.fitconnect.bookingservice.model.Booking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Construit et envoie les notifications liees a une reservation. L'envoi est "best effort" :
 * une panne du notification-service ne doit jamais faire echouer la saga.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisher {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");

    private final NotificationClient notificationClient;

    public void bookingCreated(Booking booking) {
        send(booking, NotificationType.BOOKING_CONFIRMATION,
                "Reservation " + booking.getBookingReference() + " en attente de paiement",
                "Bonjour " + booking.getUserName() + ", votre reservation de " + booking.getNumberOfSpots()
                        + " place(s) pour le cours \"" + booking.getClassName() + "\" du "
                        + format(booking) + " est en attente de paiement. Payez avant le "
                        + booking.getPaymentDeadline().format(DATE_FORMAT) + " (montant : "
                        + booking.getTotalAmount() + " EUR).");
    }

    public void paymentConfirmed(Booking booking) {
        send(booking, NotificationType.PAYMENT_CONFIRMATION,
                "Paiement confirme pour la reservation " + booking.getBookingReference(),
                "Bonjour " + booking.getUserName() + ", votre paiement de " + booking.getTotalAmount()
                        + " EUR (ref. " + booking.getPaymentReference() + ") a ete accepte. Votre place pour \""
                        + booking.getClassName() + "\" le " + format(booking) + " est confirmee.");
    }

    public void bookingCancelled(Booking booking, boolean refunded) {
        String refundText = refunded ? " Le montant de " + booking.getTotalAmount() + " EUR vous sera rembourse."
                : "";
        send(booking, NotificationType.BOOKING_CANCELLED,
                "Reservation " + booking.getBookingReference() + " annulee",
                "Bonjour " + booking.getUserName() + ", votre reservation pour \"" + booking.getClassName()
                        + "\" le " + format(booking) + " a ete annulee." + refundText);
    }

    public void bookingExpired(Booking booking) {
        send(booking, NotificationType.BOOKING_CANCELLED,
                "Reservation " + booking.getBookingReference() + " expiree",
                "Bonjour " + booking.getUserName() + ", votre reservation pour \"" + booking.getClassName()
                        + "\" le " + format(booking) + " a ete annulee car le paiement n'a pas ete effectue avant le "
                        + booking.getPaymentDeadline().format(DATE_FORMAT) + ".");
    }

    public void bookingReminder(Booking booking) {
        send(booking, NotificationType.BOOKING_REMINDER,
                "Rappel : votre cours \"" + booking.getClassName() + "\" est demain",
                "Bonjour " + booking.getUserName() + ", rappel de votre cours \"" + booking.getClassName()
                        + "\" avec " + booking.getInstructor() + " le " + format(booking) + ". A bientot !");
    }

    private void send(Booking booking, NotificationType type, String subject, String content) {
        try {
            notificationClient.send(NotificationRequest.builder()
                    .userId(booking.getUserId())
                    .email(booking.getUserEmail())
                    .type(type)
                    .subject(subject)
                    .content(content)
                    .build());
        } catch (Exception ex) {
            log.warn("Notification {} non envoyee pour la reservation {} : {}", type,
                    booking.getBookingReference(), ex.getMessage());
        }
    }

    private static String format(Booking booking) {
        return booking.getClassDate() == null ? "date inconnue" : booking.getClassDate().format(DATE_FORMAT);
    }
}
