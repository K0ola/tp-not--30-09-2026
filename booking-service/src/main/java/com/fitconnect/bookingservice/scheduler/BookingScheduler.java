package com.fitconnect.bookingservice.scheduler;

import com.fitconnect.bookingservice.dto.BookingResponse;
import com.fitconnect.bookingservice.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Taches planifiees du booking-service :
 * <ul>
 *   <li>expiration des paiements en attente (toutes les 5 minutes par defaut) ;</li>
 *   <li>rappel des cours ayant lieu dans les 24 prochaines heures (toutes les heures par defaut).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingScheduler {

    private final BookingService bookingService;

    @Scheduled(fixedRateString = "${booking.scheduler.expiration-rate:300000}")
    public int expirePendingBookings() {
        List<BookingResponse> expired = bookingService.getExpired();
        if (expired.isEmpty()) {
            log.debug("Aucune reservation en attente expiree");
            return 0;
        }
        log.info("{} reservation(s) en attente de paiement expiree(s)", expired.size());
        int processed = 0;
        for (BookingResponse booking : expired) {
            try {
                bookingService.expireBooking(booking.getId());
                processed++;
            } catch (Exception ex) {
                // on isole chaque reservation : une erreur ne bloque pas les autres
                log.error("Echec de l'expiration de la reservation {} : {}", booking.getBookingReference(),
                        ex.getMessage());
            }
        }
        return processed;
    }

    @Scheduled(cron = "${booking.scheduler.reminder-cron:0 0 * * * *}")
    public int sendReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<BookingResponse> reminded = bookingService.sendReminders(now, now.plusHours(24));
        if (!reminded.isEmpty()) {
            log.info("{} rappel(s) de cours envoye(s)", reminded.size());
        }
        return reminded.size();
    }
}
