package com.fitconnect.bookingservice.service;

import com.fitconnect.bookingservice.client.ClassClient;
import com.fitconnect.bookingservice.client.PaymentClient;
import com.fitconnect.bookingservice.client.dto.ClassResponse;
import com.fitconnect.bookingservice.client.dto.PaymentRequest;
import com.fitconnect.bookingservice.client.dto.PaymentResponse;
import com.fitconnect.bookingservice.config.BookingProperties;
import com.fitconnect.bookingservice.dto.BookingRequest;
import com.fitconnect.bookingservice.dto.BookingResponse;
import com.fitconnect.bookingservice.dto.ConfirmBookingRequest;
import com.fitconnect.bookingservice.exception.ConflictException;
import com.fitconnect.bookingservice.exception.PaymentFailedException;
import com.fitconnect.bookingservice.exception.ResourceNotFoundException;
import com.fitconnect.bookingservice.model.Booking;
import com.fitconnect.bookingservice.model.BookingStatus;
import com.fitconnect.bookingservice.repository.BookingRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestrateur de la saga de reservation :
 * <pre>
 *   POST /api/bookings           : class-service (GET + increment) -> Booking PENDING_PAYMENT -> notification
 *   PATCH /{id}/confirm          : payment-service (POST) -> Booking CONFIRMED -> notification
 *   PATCH /{id}/cancel           : payment-service (refund) -> class-service (decrement) -> CANCELLED -> notification
 *   scheduler (expiration)       : class-service (decrement) -> CANCELLED -> notification
 * </pre>
 * Les compensations sont explicites (ex. liberation des places si la persistance echoue).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ClassClient classClient;
    private final PaymentClient paymentClient;
    private final NotificationPublisher notifications;
    private final BookingProperties properties;

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public List<BookingResponse> getAll() {
        return bookingRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(Long id) {
        return mapToResponse(findBooking(id));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getByUser(Long userId) {
        return bookingRepository.findByUserIdOrderByBookingDateDesc(userId).stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getExpired() {
        return bookingRepository.findByStatusAndPaymentDeadlineBefore(BookingStatus.PENDING_PAYMENT, LocalDateTime.now())
                .stream().map(this::mapToResponse).toList();
    }

    // ------------------------------------------------------------------ Cas 1 & 2 : reservation

    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        LocalDateTime now = LocalDateTime.now();

        // Etape 1 : verification du cours + snapshot
        ClassResponse fitnessClass = fetchClass(request.getClassId());
        if (!"SCHEDULED".equals(fitnessClass.getStatus())) {
            throw new ConflictException("Le cours n'est pas ouvert a la reservation (statut "
                    + fitnessClass.getStatus() + ")");
        }
        if (fitnessClass.getDateTime() != null && fitnessClass.getDateTime().isBefore(now)) {
            throw new ConflictException("Le cours a deja eu lieu");
        }
        int available = fitnessClass.getAvailableSpots() != null ? fitnessClass.getAvailableSpots()
                : fitnessClass.getMaxParticipants() - fitnessClass.getCurrentParticipants();
        if (available < request.getNumberOfSpots()) {
            throw new ConflictException("Plus de places disponibles pour ce cours");
        }
        BigDecimal totalAmount = fitnessClass.getPrice().multiply(BigDecimal.valueOf(request.getNumberOfSpots()));

        // Etape 2 : reservation des places (verrouillage optimiste cote class-service)
        try {
            classClient.incrementParticipants(request.getClassId(), request.getNumberOfSpots());
        } catch (FeignException.Conflict ex) {
            // Cas 2 : quelqu'un a reserve entre-temps. Aucune compensation : rien n'a ete cree.
            throw new ConflictException("Plus de places disponibles pour ce cours");
        }

        // Etape 3 : creation de la reservation
        Booking booking = Booking.builder()
                .bookingReference(BookingReferenceGenerator.generate())
                .userId(request.getUserId())
                .userEmail(request.getUserEmail())
                .userName(request.getUserName())
                .classId(fitnessClass.getId())
                .className(fitnessClass.getName())
                .classDate(fitnessClass.getDateTime())
                .instructor(fitnessClass.getInstructor())
                .price(fitnessClass.getPrice())
                .numberOfSpots(request.getNumberOfSpots())
                .totalAmount(totalAmount)
                .bookingDate(now)
                .status(BookingStatus.PENDING_PAYMENT)
                .paymentDeadline(now.plus(properties.getPaymentDeadline()))
                .cancellationDeadline(fitnessClass.getDateTime().minus(properties.getCancellationDeadline()))
                .build();

        Booking saved;
        try {
            saved = bookingRepository.saveAndFlush(booking);
        } catch (RuntimeException ex) {
            // Compensation : les places ont ete prises mais la reservation n'existe pas -> on les libere
            log.error("Echec de persistance de la reservation, liberation de {} place(s) du cours {}",
                    request.getNumberOfSpots(), request.getClassId());
            releaseSpots(request.getClassId(), request.getNumberOfSpots());
            throw ex;
        }

        // Etape 4 : notification (best effort)
        notifications.bookingCreated(saved);
        log.info("Reservation {} creee (PENDING_PAYMENT) pour l'utilisateur {} : {} place(s) sur le cours {}",
                saved.getBookingReference(), saved.getUserId(), saved.getNumberOfSpots(), saved.getClassId());
        return mapToResponse(saved);
    }

    // ------------------------------------------------------------------ Cas 3 : paiement

    @Transactional
    public BookingResponse confirmBooking(Long id, ConfirmBookingRequest request) {
        Booking booking = findBooking(id);
        LocalDateTime now = LocalDateTime.now();

        // Etape 1 : verification
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("La reservation n'est pas en attente de paiement (statut "
                    + booking.getStatus() + ")");
        }
        if (booking.isPaymentDeadlinePassed(now)) {
            throw new ConflictException("Le delai de paiement est depasse (limite : "
                    + booking.getPaymentDeadline() + ")");
        }

        // Etape 2 : traitement du paiement
        PaymentResponse payment;
        try {
            payment = paymentClient.processPayment(PaymentRequest.builder()
                    .bookingId(booking.getId())
                    .bookingReference(booking.getBookingReference())
                    .userId(booking.getUserId())
                    .amount(booking.getTotalAmount())
                    .paymentMethod(request.getPaymentMethod())
                    .cardLastFour(request.getCardLastFour())
                    .transactionId(request.getTransactionId())
                    .build());
        } catch (FeignException.Conflict ex) {
            throw new ConflictException("Un paiement a deja ete accepte pour cette reservation");
        }

        // Etape 3 : mise a jour de la reservation
        if (!payment.isSuccess()) {
            // La reservation reste PENDING_PAYMENT : le client peut retenter avant la deadline
            log.warn("Paiement refuse pour la reservation {} : {}", booking.getBookingReference(),
                    payment.getFailureReason());
            throw new PaymentFailedException("Paiement refuse : "
                    + (payment.getFailureReason() != null ? payment.getFailureReason() : "motif inconnu")
                    + ". La reservation reste en attente de paiement jusqu'au " + booking.getPaymentDeadline());
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentId(payment.getId());
        booking.setPaymentReference(payment.getPaymentReference());
        booking.setConfirmationDate(now);
        Booking saved = bookingRepository.save(booking);

        // Etape 4 : notification
        notifications.paymentConfirmed(saved);
        log.info("Reservation {} confirmee (paiement {})", saved.getBookingReference(), saved.getPaymentReference());
        return mapToResponse(saved);
    }

    // ------------------------------------------------------------------ Cas 4 : annulation

    @Transactional
    public BookingResponse cancelBooking(Long id) {
        Booking booking = findBooking(id);
        LocalDateTime now = LocalDateTime.now();

        // Etape 1 : verification
        if (booking.getStatus().isFinal()) {
            throw new ConflictException("La reservation est deja " + booking.getStatus());
        }
        if (booking.isCancellationDeadlinePassed(now)) {
            throw new ConflictException("Annulation non autorisee : le delai d'annulation gratuite ("
                    + properties.getCancellationDeadline().toHours() + "h avant le cours) est depasse");
        }

        // Etape 2 : remboursement si paye, puis liberation des places
        boolean refunded = false;
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            refunded = refundPayment(booking);
        }
        releaseSpots(booking.getClassId(), booking.getNumberOfSpots());

        // Etape 3 : annulation
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationDate(now);
        Booking saved = bookingRepository.save(booking);

        // Etape 4 : notification
        notifications.bookingCancelled(saved, refunded);
        log.info("Reservation {} annulee (rembourse : {})", saved.getBookingReference(), refunded);
        return mapToResponse(saved);
    }

    @Transactional
    public BookingResponse completeBooking(Long id) {
        Booking booking = findBooking(id);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Seule une reservation confirmee peut etre marquee terminee (statut "
                    + booking.getStatus() + ")");
        }
        booking.setStatus(BookingStatus.COMPLETED);
        return mapToResponse(bookingRepository.save(booking));
    }

    // ------------------------------------------------------------------ scheduler

    /**
     * Expire une reservation dont le delai de paiement est depasse : liberation des places,
     * passage en CANCELLED et notification. Appele par le scheduler pour chaque reservation.
     */
    @Transactional
    public BookingResponse expireBooking(Long id) {
        Booking booking = findBooking(id);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return mapToResponse(booking);
        }
        releaseSpots(booking.getClassId(), booking.getNumberOfSpots());
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationDate(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);
        notifications.bookingExpired(saved);
        log.info("Reservation {} expiree (paiement non recu avant {})", saved.getBookingReference(),
                saved.getPaymentDeadline());
        return mapToResponse(saved);
    }

    @Transactional
    public List<BookingResponse> sendReminders(LocalDateTime from, LocalDateTime to) {
        List<Booking> toRemind = bookingRepository.findByStatusAndReminderSentFalseAndClassDateBetween(
                BookingStatus.CONFIRMED, from, to);
        for (Booking booking : toRemind) {
            notifications.bookingReminder(booking);
            booking.setReminderSent(true);
        }
        return bookingRepository.saveAll(toRemind).stream().map(this::mapToResponse).toList();
    }

    // ------------------------------------------------------------------ helpers

    private ClassResponse fetchClass(Long classId) {
        try {
            return classClient.getClassById(classId);
        } catch (FeignException.NotFound ex) {
            throw new ResourceNotFoundException("Cours introuvable avec l'id : " + classId);
        }
    }

    /** Rembourse le paiement associe. Retourne true si un remboursement a ete effectue. */
    private boolean refundPayment(Booking booking) {
        Long paymentId = booking.getPaymentId();
        try {
            if (paymentId == null) {
                paymentId = paymentClient.getPaymentByBooking(booking.getId()).getId();
            }
            paymentClient.refund(paymentId);
            return true;
        } catch (FeignException.NotFound ex) {
            log.warn("Aucun paiement a rembourser pour la reservation {}", booking.getBookingReference());
            return false;
        } catch (FeignException.Conflict ex) {
            log.warn("Paiement de la reservation {} deja rembourse ou non remboursable", booking.getBookingReference());
            return false;
        }
    }

    /** Libere des places ; tolere un cours disparu ou deja vide (log) pour ne pas bloquer l'annulation. */
    private void releaseSpots(Long classId, int spots) {
        try {
            classClient.decrementParticipants(classId, spots);
        } catch (FeignException.NotFound | FeignException.Conflict ex) {
            log.warn("Impossible de liberer {} place(s) sur le cours {} : {}", spots, classId, ex.getMessage());
        }
    }

    private Booking findBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation introuvable avec l'id : " + id));
    }

    private BookingResponse mapToResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .userId(booking.getUserId())
                .userEmail(booking.getUserEmail())
                .userName(booking.getUserName())
                .classId(booking.getClassId())
                .className(booking.getClassName())
                .classDate(booking.getClassDate())
                .instructor(booking.getInstructor())
                .price(booking.getPrice())
                .numberOfSpots(booking.getNumberOfSpots())
                .totalAmount(booking.getTotalAmount())
                .bookingDate(booking.getBookingDate())
                .status(booking.getStatus())
                .paymentDeadline(booking.getPaymentDeadline())
                .cancellationDeadline(booking.getCancellationDeadline())
                .paymentId(booking.getPaymentId())
                .paymentReference(booking.getPaymentReference())
                .confirmationDate(booking.getConfirmationDate())
                .cancellationDate(booking.getCancellationDate())
                .reminderSent(booking.isReminderSent())
                .build();
    }
}
