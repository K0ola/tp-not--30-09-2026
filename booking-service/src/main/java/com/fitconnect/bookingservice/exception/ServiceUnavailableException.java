package com.fitconnect.bookingservice.exception;

/** Un service partenaire est injoignable ou son circuit breaker est ouvert (503). */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
