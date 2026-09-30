package com.fitconnect.paymentservice.service;

import com.fitconnect.paymentservice.dto.PaymentRequest;
import com.fitconnect.paymentservice.dto.PaymentResponse;
import com.fitconnect.paymentservice.exception.ConflictException;
import com.fitconnect.paymentservice.exception.ResourceNotFoundException;
import com.fitconnect.paymentservice.model.Payment;
import com.fitconnect.paymentservice.model.PaymentStatus;
import com.fitconnect.paymentservice.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class PaymentService {

    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String TXN_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentRepository repository;
    private final BigDecimal rejectionThreshold;

    public PaymentService(PaymentRepository repository,
                          @Value("${payment.rejection-threshold:100.00}") BigDecimal rejectionThreshold) {
        this.repository = repository;
        this.rejectionThreshold = rejectionThreshold;
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getAll() {
        return repository.findAll().stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getById(Long id) {
        return mapToResponse(findById(id));
    }

    /**
     * Le paiement "de reference" d'une reservation : celui accepte (ou rembourse) s'il
     * existe, sinon la tentative la plus recente.
     */
    @Transactional(readOnly = true)
    public PaymentResponse getByBookingId(Long bookingId) {
        return repository.findFirstByBookingIdAndStatusInOrderByPaymentDateDesc(bookingId,
                        Set.of(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED))
                .or(() -> repository.findByBookingIdOrderByPaymentDateDesc(bookingId).stream().findFirst())
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun paiement trouve pour la reservation : " + bookingId));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getByUserId(Long userId) {
        return repository.findByUserIdOrderByPaymentDateDesc(userId).stream().map(this::mapToResponse).toList();
    }

    /**
     * Simulation de paiement : accepte si amount < seuil (100 EUR par defaut), refuse sinon.
     * Un refus est un resultat metier (status FAILED), pas une erreur HTTP.
     */
    @Transactional
    public PaymentResponse process(PaymentRequest request) {
        repository.findFirstByBookingIdAndStatusInOrderByPaymentDateDesc(request.getBookingId(),
                        Set.of(PaymentStatus.SUCCESS))
                .ifPresent(existing -> {
                    throw new ConflictException("Un paiement a deja ete accepte pour cette reservation");
                });

        Payment payment = Payment.builder()
                .paymentReference(generateReference())
                .bookingId(request.getBookingId())
                .bookingReference(request.getBookingReference())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .cardLastFour(request.getPaymentMethod().isCard() ? request.getCardLastFour() : null)
                .transactionId(request.getTransactionId() != null && !request.getTransactionId().isBlank()
                        ? request.getTransactionId() : generateTransactionId())
                .paymentDate(LocalDateTime.now())
                .status(PaymentStatus.PENDING)
                .build();

        if (request.getAmount().compareTo(rejectionThreshold) < 0) {
            payment.setStatus(PaymentStatus.SUCCESS);
            log.info("Paiement {} accepte : {} EUR pour la reservation {}", payment.getPaymentReference(),
                    payment.getAmount(), payment.getBookingId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Paiement refuse par la banque (montant >= " + rejectionThreshold + " EUR)");
            log.warn("Paiement {} refuse : {} EUR pour la reservation {}", payment.getPaymentReference(),
                    payment.getAmount(), payment.getBookingId());
        }

        return mapToResponse(repository.save(payment));
    }

    @Transactional
    public PaymentResponse refund(Long id) {
        Payment payment = findById(id);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ConflictException("Seul un paiement accepte peut etre rembourse");
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundDate(LocalDateTime.now());
        log.info("Paiement {} rembourse ({} EUR)", payment.getPaymentReference(), payment.getAmount());
        return mapToResponse(repository.save(payment));
    }

    private Payment findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable avec l'id : " + id));
    }

    private String generateReference() {
        return "PAY-" + randomString(REFERENCE_ALPHABET, 5);
    }

    private String generateTransactionId() {
        return "txn_" + randomString(TXN_ALPHABET, 12);
    }

    private static String randomString(String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .paymentReference(payment.getPaymentReference())
                .bookingId(payment.getBookingId())
                .bookingReference(payment.getBookingReference())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .cardLastFour(payment.getCardLastFour())
                .transactionId(payment.getTransactionId())
                .paymentDate(payment.getPaymentDate())
                .status(payment.getStatus())
                .failureReason(payment.getFailureReason())
                .refundDate(payment.getRefundDate())
                .build();
    }
}
