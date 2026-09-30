package com.fitconnect.bookingservice.exception;

/** Le paiement a ete refuse par payment-service (402 Payment Required). */
public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String message) {
        super(message);
    }
}
