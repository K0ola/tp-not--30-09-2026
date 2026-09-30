package com.fitconnect.bookingservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Regles metier configurables (config-repo/booking-service.yml).
 * Les durees acceptent la syntaxe Spring : 1h, 30m, 15s...
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "booking")
public class BookingProperties {

    /** Delai de paiement apres la reservation (defaut : 1 heure). */
    private Duration paymentDeadline = Duration.ofHours(1);

    /** Annulation gratuite jusqu'a cette duree avant le cours (defaut : 24 heures). */
    private Duration cancellationDeadline = Duration.ofHours(24);

    private Scheduler scheduler = new Scheduler();

    @Getter
    @Setter
    public static class Scheduler {
        private boolean enabled = true;
        private long expirationRate = 300_000;
        private String reminderCron = "0 0 * * * *";
    }
}
