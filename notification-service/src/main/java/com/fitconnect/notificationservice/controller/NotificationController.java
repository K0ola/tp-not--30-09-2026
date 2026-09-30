package com.fitconnect.notificationservice.controller;

import com.fitconnect.notificationservice.dto.NotificationRequest;
import com.fitconnect.notificationservice.dto.NotificationResponse;
import com.fitconnect.notificationservice.service.NotificationService;
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
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getAllNotifications() {
        return ResponseEntity.ok(notificationService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getNotificationById(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<NotificationResponse>> getNotificationsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getByUser(userId));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<NotificationResponse>> getPendingNotifications() {
        return ResponseEntity.ok(notificationService.getPending());
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> sendNotification(@Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.send(request));
    }

    @PatchMapping("/{id}/retry")
    public ResponseEntity<NotificationResponse> retryNotification(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.retry(id));
    }
}
