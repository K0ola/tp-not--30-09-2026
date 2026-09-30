package com.fitconnect.bookingservice.client;

import com.fitconnect.bookingservice.client.dto.PaymentRequest;
import com.fitconnect.bookingservice.client.dto.PaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback du circuit breaker pour payment-service : un paiement ne doit jamais etre
 * "devine", on renvoie 503 et la reservation reste PENDING_PAYMENT (le client peut retenter).
 */
@Component
@Slf4j
public class PaymentClientFallbackFactory implements FallbackFactory<PaymentClient> {

    @Override
    public PaymentClient create(Throwable cause) {
        log.warn("Fallback payment-service declenche : {}", cause.toString());
        return new PaymentClient() {
            @Override
            public PaymentResponse processPayment(PaymentRequest request) {
                throw FallbackSupport.translate(cause, "payment-service");
            }

            @Override
            public PaymentResponse getPaymentByBooking(Long bookingId) {
                throw FallbackSupport.translate(cause, "payment-service");
            }

            @Override
            public PaymentResponse refund(Long id) {
                throw FallbackSupport.translate(cause, "payment-service");
            }
        };
    }
}
