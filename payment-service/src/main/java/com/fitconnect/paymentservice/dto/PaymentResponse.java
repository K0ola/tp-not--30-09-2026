package com.fitconnect.paymentservice.dto;

import com.fitconnect.paymentservice.model.PaymentMethod;
import com.fitconnect.paymentservice.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private String paymentReference;
    private Long bookingId;
    private String bookingReference;
    private Long userId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private String cardLastFour;
    private String transactionId;
    private LocalDateTime paymentDate;
    private PaymentStatus status;
    private String failureReason;
    private LocalDateTime refundDate;
}
