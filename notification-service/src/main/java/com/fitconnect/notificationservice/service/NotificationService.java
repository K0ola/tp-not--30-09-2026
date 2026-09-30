package com.fitconnect.notificationservice.service;

import com.fitconnect.notificationservice.dto.NotificationRequest;
import com.fitconnect.notificationservice.dto.NotificationResponse;
import com.fitconnect.notificationservice.exception.ConflictException;
import com.fitconnect.notificationservice.exception.ResourceNotFoundException;
import com.fitconnect.notificationservice.model.Notification;
import com.fitconnect.notificationservice.model.NotificationStatus;
import com.fitconnect.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getAll() {
        return notificationRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public NotificationResponse getById(Long id) {
        return mapToResponse(findNotificationById(id));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getByUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedDateDesc(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getPending() {
        return notificationRepository
                .findByStatusInOrderByCreatedDateAsc(List.of(NotificationStatus.PENDING, NotificationStatus.FAILED))
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse send(NotificationRequest request) {
        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .email(request.getEmail())
                .type(request.getType())
                .subject(request.getSubject())
                .content(request.getContent())
                .status(NotificationStatus.PENDING)
                .attempts(0)
                .createdDate(LocalDateTime.now())
                .build();

        attemptDelivery(notification);
        return mapToResponse(notificationRepository.save(notification));
    }

    @Transactional
    public NotificationResponse retry(Long id) {
        Notification notification = findNotificationById(id);
        if (notification.getStatus() == NotificationStatus.SENT) {
            throw new ConflictException("Cette notification a deja ete envoyee");
        }

        attemptDelivery(notification);
        return mapToResponse(notificationRepository.save(notification));
    }

    private void attemptDelivery(Notification notification) {
        int attempts = notification.getAttempts() == null ? 0 : notification.getAttempts();
        notification.setAttempts(attempts + 1);

        try {
            emailSender.send(notification);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentDate(LocalDateTime.now());
            notification.setErrorMessage(null);
        } catch (RuntimeException ex) {
            log.warn("Echec de l'envoi de la notification (tentative {}) vers {} : {}",
                    notification.getAttempts(), notification.getEmail(), ex.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(ex.getMessage());
        }
    }

    private Notification findNotificationById(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification introuvable avec l'id : " + id));
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .email(notification.getEmail())
                .type(notification.getType())
                .subject(notification.getSubject())
                .content(notification.getContent())
                .createdDate(notification.getCreatedDate())
                .sentDate(notification.getSentDate())
                .status(notification.getStatus())
                .attempts(notification.getAttempts())
                .errorMessage(notification.getErrorMessage())
                .build();
    }
}
