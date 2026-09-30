package com.fitconnect.bookingservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitconnect.bookingservice.client.ClassClient;
import com.fitconnect.bookingservice.client.NotificationClient;
import com.fitconnect.bookingservice.client.PaymentClient;
import com.fitconnect.bookingservice.client.dto.ClassResponse;
import com.fitconnect.bookingservice.client.dto.NotificationRequest;
import com.fitconnect.bookingservice.client.dto.NotificationResponse;
import com.fitconnect.bookingservice.client.dto.NotificationType;
import com.fitconnect.bookingservice.client.dto.PaymentRequest;
import com.fitconnect.bookingservice.client.dto.PaymentResponse;
import com.fitconnect.bookingservice.model.Booking;
import com.fitconnect.bookingservice.model.BookingStatus;
import com.fitconnect.bookingservice.repository.BookingRepository;
import com.fitconnect.bookingservice.scheduler.BookingScheduler;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration de la saga : le booking-service tourne avec sa base H2 et son
 * controller REST ; les services partenaires (class, payment, notification) sont simules.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingSagaIntegrationTest {

    private static final long CLASS_ID = 101L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingScheduler scheduler;

    @MockitoBean
    private ClassClient classClient;
    @MockitoBean
    private PaymentClient paymentClient;
    @MockitoBean
    private NotificationClient notificationClient;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        when(notificationClient.send(any(NotificationRequest.class)))
                .thenReturn(NotificationResponse.builder().id(1L).status("SENT").build());
    }

    // ------------------------------------------------------------------ fixtures

    private ClassResponse classWith(int max, int current) {
        return ClassResponse.builder()
                .id(CLASS_ID).name("Yoga Vinyasa").instructor("Marie Dupont").gymLocation("Paris")
                .category("YOGA").level("BEGINNER").durationMinutes(60)
                .maxParticipants(max).currentParticipants(current).availableSpots(max - current)
                .price(new BigDecimal("25.00")).dateTime(LocalDateTime.now().plusDays(3).withNano(0))
                .status("SCHEDULED")
                .build();
    }

    private static final String BOOKING_JSON = """
            {"userId": 1, "userEmail": "john@example.com", "userName": "John Doe", "classId": 101, "numberOfSpots": 2}
            """;

    private static final String CONFIRM_JSON = """
            {"paymentMethod": "CREDIT_CARD", "cardLastFour": "1234", "transactionId": "txn_123456"}
            """;

    private Booking persistedBooking(BookingStatus status, LocalDateTime paymentDeadline, LocalDateTime classDate) {
        return bookingRepository.save(Booking.builder()
                .bookingReference("BK-TEST1").userId(1L).userEmail("john@example.com").userName("John Doe")
                .classId(CLASS_ID).className("Yoga Vinyasa").classDate(classDate).instructor("Marie Dupont")
                .price(new BigDecimal("25.00")).numberOfSpots(2).totalAmount(new BigDecimal("50.00"))
                .bookingDate(LocalDateTime.now().minusHours(2)).status(status)
                .paymentDeadline(paymentDeadline).cancellationDeadline(classDate.minusHours(24))
                .paymentId(status == BookingStatus.CONFIRMED ? 55L : null)
                .build());
    }

    private static FeignException.Conflict feignConflict() {
        Request request = Request.create(Request.HttpMethod.PATCH, "/api/classes/101/increment", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return new FeignException.Conflict("no spots", request, null, Map.of());
    }

    private long createBooking() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(BOOKING_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.bookingReference", startsWith("BK-")))
                .andExpect(jsonPath("$.totalAmount").value(50.00))
                .andExpect(jsonPath("$.className").value("Yoga Vinyasa"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    // ------------------------------------------------------------------ tests obligatoires

    @Test
    void shouldCompleteFullBookingFlow() throws Exception {
        // 1. Create class (cote class-service, simule) : 10 places, 5 inscrits
        when(classClient.getClassById(CLASS_ID)).thenReturn(classWith(10, 5));
        when(classClient.incrementParticipants(CLASS_ID, 2)).thenReturn(classWith(10, 7));
        when(paymentClient.processPayment(any(PaymentRequest.class))).thenReturn(PaymentResponse.builder()
                .id(55L).paymentReference("PAY-XYZ99").bookingId(1L).amount(new BigDecimal("50.00"))
                .status("SUCCESS").build());

        // 2. Create booking
        long bookingId = createBooking();

        // 3. Confirm payment
        mockMvc.perform(patch("/api/bookings/{id}/confirm", bookingId).contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentId").value(55))
                .andExpect(jsonPath("$.paymentReference").value("PAY-XYZ99"));

        // 4. Verify booking status = CONFIRMED
        mockMvc.perform(get("/api/bookings/{id}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // 5. Verify spots decreased (2 places reservees sur class-service)
        verify(classClient).incrementParticipants(CLASS_ID, 2);
        verify(paymentClient).processPayment(any(PaymentRequest.class));

        // 6. Verify notifications sent : BOOKING_CONFIRMATION puis PAYMENT_CONFIRMATION
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient, times(2)).send(captor.capture());
        List<NotificationType> types = captor.getAllValues().stream().map(NotificationRequest::getType).toList();
        assertThat(types).containsExactly(NotificationType.BOOKING_CONFIRMATION, NotificationType.PAYMENT_CONFIRMATION);
        assertThat(captor.getAllValues().get(0).getEmail()).isEqualTo("john@example.com");
        assertThat(captor.getAllValues().get(0).getContent()).contains("en attente de paiement");
    }

    @Test
    void shouldCancelExpiredBookings() {
        // 1. Create booking with paymentDeadline in past
        Booking expired = persistedBooking(BookingStatus.PENDING_PAYMENT, LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusDays(3));

        // 2. Run scheduler
        int processed = scheduler.expirePendingBookings();

        // 3. Verify status = CANCELLED
        assertThat(processed).isEqualTo(1);
        Booking reloaded = bookingRepository.findById(expired.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(reloaded.getCancellationDate()).isNotNull();

        // 4. Verify spots restored
        verify(classClient).decrementParticipants(CLASS_ID, 2);
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient).send(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.BOOKING_CANCELLED);

        // un second passage ne fait rien
        assertThat(scheduler.expirePendingBookings()).isZero();
    }

    // ------------------------------------------------------------------ scenarios d'erreur (collection Postman)

    @Test
    void shouldReturnConflict_whenNoSpotsAvailable() throws Exception {
        when(classClient.getClassById(CLASS_ID)).thenReturn(classWith(10, 9));

        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(BOOKING_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Plus de places disponibles pour ce cours"));

        verify(classClient, never()).incrementParticipants(anyLong(), anyInt());
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void shouldReturnConflict_whenClassServiceRefusesIncrement() throws Exception {
        when(classClient.getClassById(CLASS_ID)).thenReturn(classWith(10, 5));
        when(classClient.incrementParticipants(CLASS_ID, 2)).thenThrow(feignConflict());

        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(BOOKING_JSON))
                .andExpect(status().isConflict());
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void shouldReturnConflict_whenPaymentDeadlineExpired() throws Exception {
        Booking expired = persistedBooking(BookingStatus.PENDING_PAYMENT, LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusDays(3));

        mockMvc.perform(patch("/api/bookings/{id}/confirm", expired.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Le delai de paiement est depasse")));
        verify(paymentClient, never()).processPayment(any());
    }

    @Test
    void shouldReturnPaymentRequired_whenPaymentRefused() throws Exception {
        Booking pending = persistedBooking(BookingStatus.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now().plusDays(3));
        when(paymentClient.processPayment(any(PaymentRequest.class))).thenReturn(PaymentResponse.builder()
                .id(56L).status("FAILED").failureReason("Paiement refuse par la banque (montant >= 100 EUR)").build());

        mockMvc.perform(patch("/api/bookings/{id}/confirm", pending.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRM_JSON))
                .andExpect(status().isPaymentRequired());

        assertThat(bookingRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldCancelAndRefund_whenWithinDeadline() throws Exception {
        Booking confirmed = persistedBooking(BookingStatus.CONFIRMED, LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now().plusDays(3));
        when(paymentClient.refund(55L)).thenReturn(PaymentResponse.builder().id(55L).status("REFUNDED").build());

        mockMvc.perform(patch("/api/bookings/{id}/cancel", confirmed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(paymentClient).refund(55L);
        verify(classClient).decrementParticipants(CLASS_ID, 2);
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient).send(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.BOOKING_CANCELLED);
        assertThat(captor.getValue().getContent()).contains("rembourse");
    }

    @Test
    void shouldReturnConflict_whenCancellingTooLate() throws Exception {
        // cours dans 10h -> deadline d'annulation (classDate - 24h) deja depassee
        Booking lateBooking = persistedBooking(BookingStatus.CONFIRMED, LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(10));

        mockMvc.perform(patch("/api/bookings/{id}/cancel", lateBooking.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Annulation non autorisee")));

        verify(paymentClient, never()).refund(anyLong());
        verify(classClient, never()).decrementParticipants(anyLong(), anyInt());
        assertThat(bookingRepository.findById(lateBooking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void shouldValidateBookingRequest() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 1, \"userEmail\": \"john@example.com\", \"userName\": \"John\", \"classId\": 101, \"numberOfSpots\": 5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("numberOfSpots")));

        mockMvc.perform(get("/api/bookings/{id}", 9999)).andExpect(status().isNotFound());
    }

    @Test
    void shouldListExpiredAndUserBookings() throws Exception {
        persistedBooking(BookingStatus.PENDING_PAYMENT, LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusDays(3));

        mockMvc.perform(get("/api/bookings/expired"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/bookings/user/{userId}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/bookings/user/{userId}", 2))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldSendReminderOnlyOnce() {
        persistedBooking(BookingStatus.CONFIRMED, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(10));

        assertThat(scheduler.sendReminders()).isEqualTo(1);
        assertThat(scheduler.sendReminders()).isZero();

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient).send(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.BOOKING_REMINDER);
    }
}
