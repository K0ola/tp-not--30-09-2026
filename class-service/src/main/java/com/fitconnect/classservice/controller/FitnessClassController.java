package com.fitconnect.classservice.controller;

import com.fitconnect.classservice.dto.ClassSearchCriteria;
import com.fitconnect.classservice.dto.FitnessClassRequest;
import com.fitconnect.classservice.dto.FitnessClassResponse;
import com.fitconnect.classservice.model.ClassCategory;
import com.fitconnect.classservice.model.ClassLevel;
import com.fitconnect.classservice.model.ClassStatus;
import com.fitconnect.classservice.service.FitnessClassService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
@Validated
public class FitnessClassController {

    private final FitnessClassService service;

    /**
     * Liste paginee avec filtres optionnels.
     * Exemples : ?category=YOGA&level=BEGINNER  ?dateFrom=2026-09-10&dateTo=2026-09-20
     *            ?location=Paris&instructor=Marie  ?page=0&size=10&sort=dateTime,asc
     */
    @GetMapping
    public ResponseEntity<Page<FitnessClassResponse>> getAll(
            @RequestParam(required = false) ClassCategory category,
            @RequestParam(required = false) ClassLevel level,
            @RequestParam(required = false) ClassStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String instructor,
            @PageableDefault(size = 10, sort = "dateTime", direction = Sort.Direction.ASC) Pageable pageable) {
        ClassSearchCriteria criteria = ClassSearchCriteria.builder()
                .category(category).level(level).status(status)
                .dateFrom(dateFrom).dateTo(dateTo)
                .location(location).instructor(instructor)
                .build();
        return ResponseEntity.ok(service.getAll(criteria, pageable));
    }

    /**
     * Recherche par date (jour precis ou intervalle), categorie, niveau et localisation.
     */
    @GetMapping("/search")
    public ResponseEntity<List<FitnessClassResponse>> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) ClassCategory category,
            @RequestParam(required = false) ClassLevel level,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String instructor) {
        ClassSearchCriteria criteria = ClassSearchCriteria.builder()
                .category(category).level(level)
                .dateFrom(date != null ? date : dateFrom)
                .dateTo(date != null ? date : dateTo)
                .location(location).instructor(instructor)
                .build();
        return ResponseEntity.ok(service.search(criteria));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FitnessClassResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<FitnessClassResponse> create(@Valid @RequestBody FitnessClassRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FitnessClassResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody FitnessClassRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Appele par booking-service lors d'une reservation. */
    @PatchMapping("/{id}/increment")
    public ResponseEntity<FitnessClassResponse> increment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "spots doit etre >= 1") int spots) {
        return ResponseEntity.ok(service.incrementParticipants(id, spots));
    }

    /** Appele par booking-service lors d'une annulation ou d'une expiration. */
    @PatchMapping("/{id}/decrement")
    public ResponseEntity<FitnessClassResponse> decrement(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "spots doit etre >= 1") int spots) {
        return ResponseEntity.ok(service.decrementParticipants(id, spots));
    }
}
