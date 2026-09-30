package com.fitconnect.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Vue du cours renvoyee par class-service. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassResponse {
    private Long id;
    private String name;
    private String instructor;
    private String gymLocation;
    private String category;
    private String level;
    private Integer durationMinutes;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private Integer availableSpots;
    private BigDecimal price;
    private LocalDateTime dateTime;
    private String status;
}
