package com.fitconnect.bookingservice.controller;

import com.fitconnect.bookingservice.dto.BookingRequest;
import com.fitconnect.bookingservice.dto.BookingResponse;
import com.fitconnect.bookingservice.dto.ConfirmBookingRequest;
import com.fitconnect.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @GetMapping
    public ResponseEntity<List<BookingResponse>> getAll() {
        return ResponseEntity.ok(bookingService.getAll());
    }

    /** Reservations en attente dont le delai de paiement est depasse (utilise par le scheduler). */
    @GetMapping("/expired")
    public ResponseEntity<List<BookingResponse>> getExpired() {
        return ResponseEntity.ok(bookingService.getExpired());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookingResponse>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(bookingService.getByUser(userId));
    }

    /** Cas 1/2 : reservation -> 201 PENDING_PAYMENT ou 409 si plus de places. */
    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request));
    }

    /** Cas 3 : paiement -> 200 CONFIRMED, 402 si refuse, 409 si expire. */
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<BookingResponse> confirm(@PathVariable Long id,
                                                   @Valid @RequestBody ConfirmBookingRequest request) {
        return ResponseEntity.ok(bookingService.confirmBooking(id, request));
    }

    /** Cas 4 : annulation -> 200 CANCELLED (rembourse si paye), 409 hors delai. */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<BookingResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.completeBooking(id));
    }
}
