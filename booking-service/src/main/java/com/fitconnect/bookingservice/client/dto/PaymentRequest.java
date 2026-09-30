package com.fitconnect.bookingservice.client.dto;

import com.fitconnect.bookingservice.model.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Corps envoye a POST /api/payments. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {
    private Long bookingId;
    private String bookingReference;
    private Long userId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String cardLastFour;
    private String transactionId;
}
