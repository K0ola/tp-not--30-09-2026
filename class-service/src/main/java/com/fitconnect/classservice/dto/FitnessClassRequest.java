package com.fitconnect.classservice.dto;

import com.fitconnect.classservice.model.ClassCategory;
import com.fitconnect.classservice.model.ClassLevel;
import com.fitconnect.classservice.validation.ValidDuration;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FitnessClassRequest {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 3, message = "Le nom doit contenir au moins 3 caracteres")
    private String name;

    @NotBlank(message = "La description est obligatoire")
    private String description;

    @NotBlank(message = "L'instructeur est obligatoire")
    private String instructor;

    @NotBlank(message = "La localisation est obligatoire")
    private String gymLocation;

    @NotNull(message = "La categorie est obligatoire")
    private ClassCategory category;

    @NotNull(message = "Le niveau est obligatoire")
    private ClassLevel level;

    @NotNull(message = "La duree est obligatoire")
    @ValidDuration
    private Integer durationMinutes;

    @NotNull(message = "Le nombre maximum de participants est obligatoire")
    @Min(value = 5, message = "Un cours accueille au moins 5 participants")
    @Max(value = 30, message = "Un cours accueille au plus 30 participants")
    private Integer maxParticipants;

    @NotNull(message = "Le prix est obligatoire")
    @DecimalMin(value = "5.00", message = "Le prix minimum est de 5.00")
    private BigDecimal price;

    @NotNull(message = "La date du cours est obligatoire")
    @FutureOrPresent(message = "La date du cours doit etre a partir de maintenant")
    private LocalDateTime dateTime;
}
