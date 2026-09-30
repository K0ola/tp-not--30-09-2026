package com.fitconnect.notificationservice.service;

import com.fitconnect.notificationservice.exception.NotificationDeliveryException;
import com.fitconnect.notificationservice.model.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Simulation de l'envoi d'un email.
 * Aucun email reel n'est envoye : l'envoi est simplement trace dans les logs.
 */
@Slf4j
@Component
public class EmailSender {

    private static final String FAILING_DOMAIN = "@fail.test";

    public void send(Notification notification) {
        // SIMULATION : pour pouvoir demontrer le flux de reprise (retry),
        // l'envoi echoue volontairement lorsque l'adresse du destinataire
        // se termine par "@fail.test" (ex : bob@fail.test). Toute autre adresse
        // est consideree comme livree avec succes.
        if (notification.getEmail() != null && notification.getEmail().endsWith(FAILING_DOMAIN)) {
            throw new NotificationDeliveryException(
                    "Echec de l'envoi de l'email au destinataire : " + notification.getEmail());
        }

        log.info("[EMAIL] to={} type={} subject={}",
                notification.getEmail(), notification.getType(), notification.getSubject());
    }
}
