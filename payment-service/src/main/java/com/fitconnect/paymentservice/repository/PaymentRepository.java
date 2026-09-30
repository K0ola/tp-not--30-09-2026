package com.fitconnect.paymentservice.repository;

import com.fitconnect.paymentservice.model.Payment;
import com.fitconnect.paymentservice.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBookingIdOrderByPaymentDateDesc(Long bookingId);

    Optional<Payment> findFirstByBookingIdAndStatusInOrderByPaymentDateDesc(Long bookingId,
                                                                            Collection<PaymentStatus> statuses);

    List<Payment> findByUserIdOrderByPaymentDateDesc(Long userId);
}
