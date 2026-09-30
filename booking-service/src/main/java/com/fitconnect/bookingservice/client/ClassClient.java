package com.fitconnect.bookingservice.client;

import com.fitconnect.bookingservice.client.dto.ClassResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Client Feign vers class-service (resolu via Eureka, protege par un circuit breaker).
 */
@FeignClient(name = "class-service", fallbackFactory = ClassClientFallbackFactory.class)
public interface ClassClient {

    @GetMapping("/api/classes/{id}")
    ClassResponse getClassById(@PathVariable("id") Long id);

    @PatchMapping("/api/classes/{id}/increment")
    ClassResponse incrementParticipants(@PathVariable("id") Long id, @RequestParam("spots") int spots);

    @PatchMapping("/api/classes/{id}/decrement")
    ClassResponse decrementParticipants(@PathVariable("id") Long id, @RequestParam("spots") int spots);
}
