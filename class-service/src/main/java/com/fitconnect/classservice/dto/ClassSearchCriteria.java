package com.fitconnect.classservice.dto;

import com.fitconnect.classservice.model.ClassCategory;
import com.fitconnect.classservice.model.ClassLevel;
import com.fitconnect.classservice.model.ClassStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Criteres de filtrage optionnels pour la liste et la recherche des cours.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassSearchCriteria {
    private ClassCategory category;
    private ClassLevel level;
    private ClassStatus status;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String location;
    private String instructor;
}
