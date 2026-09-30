package com.fitconnect.bookingservice.client;

import com.fitconnect.bookingservice.client.dto.NotificationRequest;
import com.fitconnect.bookingservice.client.dto.NotificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", fallbackFactory = NotificationClientFallbackFactory.class)
public interface NotificationClient {

    @PostMapping("/api/notifications")
    NotificationResponse send(@RequestBody NotificationRequest request);
}
