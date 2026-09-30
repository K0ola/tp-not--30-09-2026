package com.fitconnect.classservice.dto;

import com.fitconnect.classservice.model.ClassCategory;
import com.fitconnect.classservice.model.ClassLevel;
import com.fitconnect.classservice.model.ClassStatus;
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
public class FitnessClassResponse {
    private Long id;
    private String name;
    private String description;
    private String instructor;
    private String gymLocation;
    private ClassCategory category;
    private ClassLevel level;
    private Integer durationMinutes;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private Integer availableSpots;
    private BigDecimal price;
    private LocalDateTime dateTime;
    private ClassStatus status;
    private Long version;
}
