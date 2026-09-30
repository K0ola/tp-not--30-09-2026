package com.fitconnect.classservice.exception;

/**
 * Levee lorsqu'une reservation depasserait la capacite maximale du cours (409 Conflict).
 */
public class NoSpotsAvailableException extends RuntimeException {
    public NoSpotsAvailableException(String message) {
        super(message);
    }
}
