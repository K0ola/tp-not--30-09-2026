package com.fitconnect.notificationservice.scheduler;

import com.fitconnect.notificationservice.dto.NotificationResponse;
import com.fitconnect.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationService notificationService;

    @Scheduled(fixedRateString = "${notification.scheduler.retry-rate:300000}")
    public void retryPendingNotifications() {
        List<NotificationResponse> pending = notificationService.getPending();
        if (pending.isEmpty()) {
            return;
        }

        log.info("Reprise de {} notification(s) en attente", pending.size());
        for (NotificationResponse notification : pending) {
            try {
                NotificationResponse result = notificationService.retry(notification.getId());
                log.info("Notification {} retentee : statut={} tentatives={}",
                        result.getId(), result.getStatus(), result.getAttempts());
            } catch (Exception ex) {
                log.error("Impossible de retenter la notification {} : {}", notification.getId(), ex.getMessage());
            }
        }
    }
}
