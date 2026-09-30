package com.fitconnect.bookingservice.service;

import com.fitconnect.bookingservice.client.ClassClient;
import com.fitconnect.bookingservice.client.PaymentClient;
import com.fitconnect.bookingservice.client.dto.ClassResponse;
import com.fitconnect.bookingservice.client.dto.PaymentRequest;
import com.fitconnect.bookingservice.client.dto.PaymentResponse;
import com.fitconnect.bookingservice.config.BookingProperties;
import com.fitconnect.bookingservice.dto.BookingRequest;
import com.fitconnect.bookingservice.dto.BookingResponse;
import com.fitconnect.bookingservice.dto.ConfirmBookingRequest;
import com.fitconnect.bookingservice.exception.ConflictException;
import com.fitconnect.bookingservice.exception.PaymentFailedException;
import com.fitconnect.bookingservice.exception.ResourceNotFoundException;
import com.fitconnect.bookingservice.model.Booking;
import com.fitconnect.bookingservice.model.BookingStatus;
import com.fitconnect.bookingservice.model.PaymentMethod;
import com.fitconnect.bookingservice.repository.BookingRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final long CLASS_ID = 101L;
    private static final BigDecimal PRICE = new BigDecimal("25.00");

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ClassClient classClient;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private NotificationPublisher notifications;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, classClient, paymentClient, notifications, new BookingProperties());
    }

    // ------------------------------------------------------------------ fixtures

    private ClassResponse classWith(int max, int current) {
        return ClassResponse.builder()
                .id(CLASS_ID).name("Yoga Vinyasa").instructor("Marie Dupont").gymLocation("Paris")
                .category("YOGA").level("BEGINNER").durationMinutes(60)
                .maxParticipants(max).currentParticipants(current).availableSpots(max - current)
                .price(PRICE).dateTime(LocalDateTime.now().plusDays(3).withNano(0)).status("SCHEDULED")
                .build();
    }

    private BookingRequest request(int spots) {
        return BookingRequest.builder()
                .userId(1L).userEmail("john@example.com").userName("John Doe")
                .classId(CLASS_ID).numberOfSpots(spots)
                .build();
    }

    private Booking booking(BookingStatus status, LocalDateTime paymentDeadline, LocalDateTime cancellationDeadline) {
        return Booking.builder()
                .id(10L).bookingReference("BK-ABC12").userId(1L).userEmail("john@example.com").userName("John Doe")
                .classId(CLASS_ID).className("Yoga Vinyasa").classDate(cancellationDeadline.plusHours(24))
                .instructor("Marie Dupont").price(PRICE).numberOfSpots(2).totalAmount(new BigDecimal("50.00"))
                .bookingDate(LocalDateTime.now().minusMinutes(5)).status(status)
                .paymentDeadline(paymentDeadline).cancellationDeadline(cancellationDeadline)
                .build();
    }

    private static FeignException.Conflict feignConflict() {
        Request request = Request.create(Request.HttpMethod.PATCH, "/api/classes/101/increment", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return new FeignException.Conflict("no spots", request, null, Map.of());
    }

    private static FeignException.NotFound feignNotFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/api/classes/999", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return new FeignException.NotFound("not found", request, null, Map.of());
    }

    private void saveReturnsArgument() {
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(10L);
            return b;
        });
    }

    // ------------------------------------------------------------------ tests obligatoires

    @Test
    void shouldCreateBooking_whenSpotsAvailable() {
        // Given: class with 10 spots, 5 current participants
        when(classClient.getClassById(CLASS_ID)).thenReturn(classWith(10, 5));
        when(classClient.incrementParticipants(CLASS_ID, 2)).thenReturn(classWith(10, 7));
        saveReturnsArgument();

        // When: booking 2 spots
        BookingResponse response = service.createBooking(request(2));

        // Then: status = PENDING_PAYMENT, spots = 7
        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(response.getBookingReference()).startsWith("BK-").hasSize(8);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(response.getClassName()).isEqualTo("Yoga Vinyasa");
        assertThat(response.getPaymentDeadline()).isAfter(response.getBookingDate());
        assertThat(response.getPaymentDeadline()).isEqualTo(response.getBookingDate().plusHours(1));
        assertThat(response.getCancellationDeadline()).isEqualTo(response.getClassDate().minusHours(24));
        verify(classClient).incrementParticipants(CLASS_ID, 2);
        verify(notifications).bookingCreated(any(Booking.class));
    }

    @Test
    void shouldThrowException_whenNoSpotsAvailable() {
        // Given: class with 10 spots, 9 current participants
        when(classClient.getClassById(CLASS_ID)).thenReturn(classWith(10, 9));

        // When / Then: booking 2 spots -> 409 (ConflictException), rien n'est reserve ni persiste
        assertThatThrownBy(() -> service.createBooking(request(2)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Plus de places disponibles pour ce cours");
        verify(classClient, never()).incrementParticipants(anyLong(), anyInt());
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldCancelBookingAndRefund_whenWithinDeadline() {
        // Given: confirmed booking, cancellationDeadline in future
        Booking confirmed = booking(BookingStatus.CONFIRMED, LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now().plusDays(2));
        confirmed.setPaymentId(55L);
        confirmed.setPaymentReference("PAY-XYZ99");
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(confirmed));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // When: cancel booking
        BookingResponse response = service.cancelBooking(10L);

        // Then: status = CANCELLED, payment refunded, spots decreased
        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.getCancellationDate()).isNotNull();
        verify(paymentClient).refund(55L);
        verify(classClient).decrementParticipants(CLASS_ID, 2);
        verify(notifications).bookingCancelled(any(Booking.class), org.mockito.ArgumentMatchers.eq(true));
    }

    // ------------------------------------------------------------------ tests complementaires

    @Test
    void shouldThrowConflict_whenIncrementRacesWithAnotherBooking() {
        // la lecture voyait encore 1 place, mais class-service refuse a l'increment (409)
        ClassResponse stale = classWith(10, 9);
        stale.setAvailableSpots(1);
        when(classClient.getClassById(CLASS_ID)).thenReturn(stale);
        when(classClient.incrementParticipants(CLASS_ID, 1)).thenThrow(feignConflict());

        assertThatThrownBy(() -> service.createBooking(request(1)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Plus de places disponibles pour ce cours");
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldReturnNotFound_whenClassDoesNotExist() {
        when(classClient.getClassById(CLASS_ID)).thenThrow(feignNotFound());

        assertThatThrownBy(() -> service.createBooking(request(1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldReleaseSpots_whenPersistenceFails() {
        when(classClient.getClassById(CLASS_ID)).thenReturn(classWith(10, 5));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.createBooking(request(2))).isInstanceOf(IllegalStateException.class);

        // compensation : les places prises sont rendues
        verify(classClient).incrementParticipants(CLASS_ID, 2);
        verify(classClient).decrementParticipants(CLASS_ID, 2);
    }

    @Test
    void shouldConfirmBooking_whenPaymentSucceeds() {
        Booking pending = booking(BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now().plusDays(2));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentClient.processPayment(any(PaymentRequest.class))).thenReturn(PaymentResponse.builder()
                .id(55L).paymentReference("PAY-XYZ99").bookingId(10L).amount(new BigDecimal("50.00"))
                .status("SUCCESS").build());

        BookingResponse response = service.confirmBooking(10L, ConfirmBookingRequest.builder()
                .paymentMethod(PaymentMethod.CREDIT_CARD).cardLastFour("1234").transactionId("txn_123456").build());

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.getPaymentId()).isEqualTo(55L);
        assertThat(response.getPaymentReference()).isEqualTo("PAY-XYZ99");
        verify(paymentClient).processPayment(argThat(p -> p.getBookingId().equals(10L)
                && p.getAmount().compareTo(new BigDecimal("50.00")) == 0
                && p.getPaymentMethod() == PaymentMethod.CREDIT_CARD
                && "1234".equals(p.getCardLastFour())));
        verify(notifications).paymentConfirmed(any(Booking.class));
    }

    @Test
    void shouldKeepBookingPending_whenPaymentIsRefused() {
        Booking pending = booking(BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now().plusDays(2));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(paymentClient.processPayment(any(PaymentRequest.class))).thenReturn(PaymentResponse.builder()
                .id(56L).status("FAILED").failureReason("Paiement refuse par la banque").build());

        assertThatThrownBy(() -> service.confirmBooking(10L, ConfirmBookingRequest.builder()
                .paymentMethod(PaymentMethod.CREDIT_CARD).cardLastFour("1234").build()))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("refuse");
        assertThat(pending.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldRejectConfirmation_whenPaymentDeadlinePassed() {
        Booking expired = booking(BookingStatus.PENDING_PAYMENT, LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusDays(2));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.confirmBooking(10L, ConfirmBookingRequest.builder()
                .paymentMethod(PaymentMethod.PAYPAL).build()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("delai de paiement");
        verify(paymentClient, never()).processPayment(any());
    }

    @Test
    void shouldRejectConfirmation_whenBookingNotPending() {
        Booking cancelled = booking(BookingStatus.CANCELLED, LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now().plusDays(2));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.confirmBooking(10L, ConfirmBookingRequest.builder()
                .paymentMethod(PaymentMethod.PAYPAL).build()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldRejectCancellation_whenDeadlinePassed() {
        Booking lateBooking = booking(BookingStatus.CONFIRMED, LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusHours(1));
        lateBooking.setPaymentId(55L);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(lateBooking));

        assertThatThrownBy(() -> service.cancelBooking(10L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Annulation non autorisee");
        verify(paymentClient, never()).refund(anyLong());
        verify(classClient, never()).decrementParticipants(anyLong(), anyInt());
        assertThat(lateBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void shouldCancelPendingBookingWithoutRefund() {
        Booking pending = booking(BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now().plusDays(2));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = service.cancelBooking(10L);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(paymentClient, never()).refund(anyLong());
        verify(classClient).decrementParticipants(CLASS_ID, 2);
    }

    @Test
    void shouldRejectCancellation_whenAlreadyCancelled() {
        Booking cancelled = booking(BookingStatus.CANCELLED, LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now().plusDays(2));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.cancelBooking(10L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldExpirePendingBooking() {
        Booking pending = booking(BookingStatus.PENDING_PAYMENT, LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusDays(2));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = service.expireBooking(10L);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(classClient).decrementParticipants(CLASS_ID, 2);
        verify(notifications).bookingExpired(any(Booking.class));
    }

    @Test
    void shouldCompleteOnlyConfirmedBookings() {
        Booking confirmed = booking(BookingStatus.CONFIRMED, LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now().plusDays(2));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(confirmed));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.completeBooking(10L).getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThatThrownBy(() -> service.completeBooking(10L)).isInstanceOf(ConflictException.class);
    }
}
