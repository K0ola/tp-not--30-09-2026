package com.fitconnect.bookingservice.service;

import java.security.SecureRandom;

/** Genere des references de la forme BK-XXXXX (sans caracteres ambigus 0/O, 1/I). */
public final class BookingReferenceGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingReferenceGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder("BK-");
        for (int i = 0; i < 5; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
