package com.fitconnect.bookingservice.dto;

import com.fitconnect.bookingservice.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmBookingRequest {

    @NotNull(message = "Le moyen de paiement est obligatoire")
    private PaymentMethod paymentMethod;

    @Pattern(regexp = "\\d{4}", message = "cardLastFour doit contenir exactement 4 chiffres")
    private String cardLastFour;

    private String transactionId;
}
