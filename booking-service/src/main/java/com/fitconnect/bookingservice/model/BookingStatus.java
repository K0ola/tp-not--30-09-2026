package com.fitconnect.bookingservice.model;

public enum BookingStatus {
    PENDING_PAYMENT, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW;

    /** Une reservation terminee ne peut plus evoluer. */
    public boolean isFinal() {
        return this == CANCELLED || this == COMPLETED || this == NO_SHOW;
    }
}
