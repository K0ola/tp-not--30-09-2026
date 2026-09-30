package com.fitconnect.bookingservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String bookingReference;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private Long classId;

    // --- Snapshot du cours au moment de la reservation ---
    private String className;
    private LocalDateTime classDate;
    private String instructor;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer numberOfSpots;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private LocalDateTime bookingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private LocalDateTime paymentDeadline;

    @Column(nullable = false)
    private LocalDateTime cancellationDeadline;

    // --- Suivi du paiement (renseigne a la confirmation) ---
    private Long paymentId;
    private String paymentReference;
    private LocalDateTime confirmationDate;

    private LocalDateTime cancellationDate;

    /** Evite d'envoyer plusieurs fois le rappel 24h. */
    @Builder.Default
    @Column(nullable = false)
    private boolean reminderSent = false;

    public boolean isPaymentDeadlinePassed(LocalDateTime now) {
        return now.isAfter(paymentDeadline);
    }

    public boolean isCancellationDeadlinePassed(LocalDateTime now) {
        return now.isAfter(cancellationDeadline);
    }
}
