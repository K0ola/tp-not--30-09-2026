package com.fitconnect.bookingservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class BookingRequest {

    @NotNull(message = "L'identifiant utilisateur est obligatoire")
    private Long userId;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email est invalide")
    private String userEmail;

    @NotBlank(message = "Le nom de l'utilisateur est obligatoire")
    private String userName;

    @NotNull(message = "L'identifiant du cours est obligatoire")
    private Long classId;

    @NotNull(message = "Le nombre de places est obligatoire")
    @Min(value = 1, message = "Il faut reserver au moins 1 place")
    @Max(value = 4, message = "Maximum 4 places par reservation")
    private Integer numberOfSpots;
}
