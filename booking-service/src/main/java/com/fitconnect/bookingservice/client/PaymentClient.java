package com.fitconnect.bookingservice.client;

import com.fitconnect.bookingservice.client.dto.PaymentRequest;
import com.fitconnect.bookingservice.client.dto.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", fallbackFactory = PaymentClientFallbackFactory.class)
public interface PaymentClient {

    @PostMapping("/api/payments")
    PaymentResponse processPayment(@RequestBody PaymentRequest request);

    @GetMapping("/api/payments/booking/{bookingId}")
    PaymentResponse getPaymentByBooking(@PathVariable("bookingId") Long bookingId);

    @PostMapping("/api/payments/{id}/refund")
    PaymentResponse refund(@PathVariable("id") Long id);
}
