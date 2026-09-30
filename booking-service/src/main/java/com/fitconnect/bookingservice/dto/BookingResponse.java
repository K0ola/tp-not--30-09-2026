package com.fitconnect.bookingservice.dto;

import com.fitconnect.bookingservice.model.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long id;
    private String bookingReference;
    private Long userId;
    private String userEmail;
    private String userName;
    private Long classId;
    private String className;
    private LocalDateTime classDate;
    private String instructor;
    private BigDecimal price;
    private Integer numberOfSpots;
    private BigDecimal totalAmount;
    private LocalDateTime bookingDate;
    private BookingStatus status;
    private LocalDateTime paymentDeadline;
    private LocalDateTime cancellationDeadline;
    private Long paymentId;
    private String paymentReference;
    private LocalDateTime confirmationDate;
    private LocalDateTime cancellationDate;
    private boolean reminderSent;
}
