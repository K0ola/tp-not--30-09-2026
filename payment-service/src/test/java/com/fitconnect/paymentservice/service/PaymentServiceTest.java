package com.fitconnect.paymentservice.service;

import com.fitconnect.paymentservice.dto.PaymentRequest;
import com.fitconnect.paymentservice.dto.PaymentResponse;
import com.fitconnect.paymentservice.exception.ConflictException;
import com.fitconnect.paymentservice.exception.ResourceNotFoundException;
import com.fitconnect.paymentservice.model.Payment;
import com.fitconnect.paymentservice.model.PaymentMethod;
import com.fitconnect.paymentservice.model.PaymentStatus;
import com.fitconnect.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository repository;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(repository, new BigDecimal("100.00"));
    }

    private PaymentRequest request(String amount) {
        return PaymentRequest.builder()
                .bookingId(10L).bookingReference("BK-ABC12").userId(1L)
                .amount(new BigDecimal(amount)).paymentMethod(PaymentMethod.CREDIT_CARD)
                .cardLastFour("1234")
                .build();
    }

    private void saveReturnsArgument() {
        when(repository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(7L);
            return p;
        });
    }

    @Test
    void shouldAcceptPayment_whenAmountBelowThreshold() {
        when(repository.findFirstByBookingIdAndStatusInOrderByPaymentDateDesc(anyLong(), anyCollection()))
                .thenReturn(Optional.empty());
        saveReturnsArgument();

        PaymentResponse response = service.process(request("50.00"));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getPaymentReference()).startsWith("PAY-").hasSize(9);
        assertThat(response.getTransactionId()).startsWith("txn_");
        assertThat(response.getCardLastFour()).isEqualTo("1234");
        assertThat(response.getPaymentDate()).isNotNull();
        assertThat(response.getFailureReason()).isNull();
    }

    @Test
    void shouldRejectPayment_whenAmountAtOrAboveThreshold() {
        when(repository.findFirstByBookingIdAndStatusInOrderByPaymentDateDesc(anyLong(), anyCollection()))
                .thenReturn(Optional.empty());
        saveReturnsArgument();

        PaymentResponse response = service.process(request("100.00"));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getFailureReason()).contains("refuse");
    }

    @Test
    void shouldKeepProvidedTransactionIdAndDropCardDigitsForPaypal() {
        when(repository.findFirstByBookingIdAndStatusInOrderByPaymentDateDesc(anyLong(), anyCollection()))
                .thenReturn(Optional.empty());
        saveReturnsArgument();
        PaymentRequest paypal = request("20.00");
        paypal.setPaymentMethod(PaymentMethod.PAYPAL);
        paypal.setTransactionId("txn_123456");

        PaymentResponse response = service.process(paypal);

        assertThat(response.getTransactionId()).isEqualTo("txn_123456");
        assertThat(response.getCardLastFour()).isNull();
    }

    @Test
    void shouldRefuseSecondSuccessfulPaymentForSameBooking() {
        when(repository.findFirstByBookingIdAndStatusInOrderByPaymentDateDesc(anyLong(), anyCollection()))
                .thenReturn(Optional.of(Payment.builder().id(1L).status(PaymentStatus.SUCCESS).build()));

        assertThatThrownBy(() -> service.process(request("50.00")))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRefund_whenPaymentSucceeded() {
        Payment payment = Payment.builder().id(7L).paymentReference("PAY-AAAAA").bookingId(10L).userId(1L)
                .amount(new BigDecimal("50.00")).paymentMethod(PaymentMethod.CREDIT_CARD)
                .paymentDate(LocalDateTime.now()).status(PaymentStatus.SUCCESS).build();
        when(repository.findById(7L)).thenReturn(Optional.of(payment));
        when(repository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = service.refund(7L);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(response.getRefundDate()).isNotNull();
    }

    @Test
    void shouldThrowConflict_whenRefundingFailedPayment() {
        Payment payment = Payment.builder().id(7L).status(PaymentStatus.FAILED).build();
        when(repository.findById(7L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refund(7L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Seul un paiement accepte peut etre rembourse");
    }

    @Test
    void getByBookingPrefersSuccessfulPaymentThenLatestAttempt() {
        Payment failed = Payment.builder().id(1L).bookingId(10L).status(PaymentStatus.FAILED).build();
        when(repository.findFirstByBookingIdAndStatusInOrderByPaymentDateDesc(anyLong(), anyCollection()))
                .thenReturn(Optional.empty());
        when(repository.findByBookingIdOrderByPaymentDateDesc(10L)).thenReturn(List.of(failed));

        assertThat(service.getByBookingId(10L).getStatus()).isEqualTo(PaymentStatus.FAILED);

        when(repository.findByBookingIdOrderByPaymentDateDesc(11L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.getByBookingId(11L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
