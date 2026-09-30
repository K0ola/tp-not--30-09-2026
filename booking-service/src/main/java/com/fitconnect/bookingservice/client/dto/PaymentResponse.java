package com.fitconnect.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Vue du paiement renvoyee par payment-service. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentResponse {
    private Long id;
    private String paymentReference;
    private Long bookingId;
    private Long userId;
    private BigDecimal amount;
    private String paymentMethod;
    private String transactionId;
    private LocalDateTime paymentDate;
    private String status;
    private String failureReason;

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
