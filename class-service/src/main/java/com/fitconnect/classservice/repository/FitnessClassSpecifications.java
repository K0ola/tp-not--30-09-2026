package com.fitconnect.classservice.repository;

import com.fitconnect.classservice.dto.ClassSearchCriteria;
import com.fitconnect.classservice.model.FitnessClass;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Construit dynamiquement la requete JPA a partir des filtres fournis
 * (tous optionnels, combines en AND).
 */
public final class FitnessClassSpecifications {

    private FitnessClassSpecifications() {
    }

    public static Specification<FitnessClass> from(ClassSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.getCategory() != null) {
                predicates.add(cb.equal(root.get("category"), criteria.getCategory()));
            }
            if (criteria.getLevel() != null) {
                predicates.add(cb.equal(root.get("level"), criteria.getLevel()));
            }
            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }
            if (criteria.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dateTime"),
                        criteria.getDateFrom().atStartOfDay()));
            }
            if (criteria.getDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dateTime"),
                        criteria.getDateTo().atTime(LocalTime.MAX)));
            }
            if (hasText(criteria.getLocation())) {
                predicates.add(cb.like(cb.lower(root.get("gymLocation")),
                        "%" + criteria.getLocation().toLowerCase() + "%"));
            }
            if (hasText(criteria.getInstructor())) {
                predicates.add(cb.like(cb.lower(root.get("instructor")),
                        "%" + criteria.getInstructor().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
