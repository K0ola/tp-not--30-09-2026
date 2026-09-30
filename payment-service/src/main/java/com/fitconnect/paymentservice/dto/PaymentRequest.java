package com.fitconnect.paymentservice.dto;

import com.fitconnect.paymentservice.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    @NotNull(message = "L'identifiant de la reservation est obligatoire")
    private Long bookingId;

    private String bookingReference;

    @NotNull(message = "L'identifiant de l'utilisateur est obligatoire")
    private Long userId;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.00", message = "Le montant doit etre positif ou nul")
    private BigDecimal amount;

    @NotNull(message = "Le moyen de paiement est obligatoire")
    private PaymentMethod paymentMethod;

    @Pattern(regexp = "\\d{4}", message = "Les 4 derniers chiffres de la carte doivent etre 4 chiffres")
    private String cardLastFour;

    private String transactionId;
}
