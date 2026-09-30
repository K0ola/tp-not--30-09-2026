package com.fitconnect.bookingservice.repository;

import com.fitconnect.bookingservice.model.Booking;
import com.fitconnect.bookingservice.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdOrderByBookingDateDesc(Long userId);

    List<Booking> findByStatus(BookingStatus status);

    /** Reservations en attente dont le delai de paiement est depasse. */
    List<Booking> findByStatusAndPaymentDeadlineBefore(BookingStatus status, LocalDateTime deadline);

    /** Reservations confirmees dont le cours a lieu dans la fenetre donnee et pas encore rappelees. */
    List<Booking> findByStatusAndReminderSentFalseAndClassDateBetween(BookingStatus status,
                                                                      LocalDateTime from,
                                                                      LocalDateTime to);
}
