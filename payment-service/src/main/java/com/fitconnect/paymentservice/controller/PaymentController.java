package com.fitconnect.paymentservice.controller;

import com.fitconnect.paymentservice.dto.PaymentRequest;
import com.fitconnect.paymentservice.dto.PaymentResponse;
import com.fitconnect.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    /** Appele par booking-service lors de la confirmation d'une reservation. */
    @PostMapping
    public ResponseEntity<PaymentResponse> process(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.process(request));
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<PaymentResponse> getByBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(service.getByBookingId(bookingId));
    }

    /** Appele par booking-service lors d'une annulation dans les delais. */
    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refund(@PathVariable Long id) {
        return ResponseEntity.ok(service.refund(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PaymentResponse>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(service.getByUserId(userId));
    }
}
