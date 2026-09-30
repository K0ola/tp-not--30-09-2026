package com.fitconnect.classservice.model;

import com.fitconnect.classservice.exception.ConflictException;
import com.fitconnect.classservice.exception.NoSpotsAvailableException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FitnessClassTest {

    private FitnessClass classWith(int max, int current) {
        return FitnessClass.builder()
                .id(1L).name("Yoga").description("desc").instructor("Marie").gymLocation("Paris")
                .category(ClassCategory.YOGA).level(ClassLevel.BEGINNER).durationMinutes(60)
                .maxParticipants(max).currentParticipants(current).price(new BigDecimal("25.00"))
                .dateTime(LocalDateTime.now().plusDays(3)).status(ClassStatus.SCHEDULED)
                .build();
    }

    @Test
    void shouldIncrementParticipants_whenSpotsAvailable() {
        // Given: class with 10 spots, 5 current participants
        FitnessClass fitnessClass = classWith(10, 5);

        // When: booking 2 spots
        fitnessClass.incrementParticipants(2);

        // Then: spots = 7
        assertThat(fitnessClass.getCurrentParticipants()).isEqualTo(7);
        assertThat(fitnessClass.getAvailableSpots()).isEqualTo(3);
    }

    @Test
    void shouldThrowException_whenNoSpotsAvailable() {
        // Given: class with 10 spots, 9 current participants
        FitnessClass fitnessClass = classWith(10, 9);

        // When / Then: booking 2 spots -> NoSpotsAvailableException, state unchanged
        assertThatThrownBy(() -> fitnessClass.incrementParticipants(2))
                .isInstanceOf(NoSpotsAvailableException.class)
                .hasMessageContaining("Plus de places disponibles");
        assertThat(fitnessClass.getCurrentParticipants()).isEqualTo(9);
    }

    @Test
    void shouldAllowFillingExactlyToCapacity() {
        FitnessClass fitnessClass = classWith(10, 8);
        fitnessClass.incrementParticipants(2);
        assertThat(fitnessClass.getCurrentParticipants()).isEqualTo(10);
        assertThat(fitnessClass.getAvailableSpots()).isZero();
    }

    @Test
    void shouldRejectIncrement_whenClassIsCancelled() {
        FitnessClass fitnessClass = classWith(10, 0);
        fitnessClass.setStatus(ClassStatus.CANCELLED);

        assertThatThrownBy(() -> fitnessClass.incrementParticipants(1))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldDecrementParticipants() {
        FitnessClass fitnessClass = classWith(10, 5);
        fitnessClass.decrementParticipants(2);
        assertThat(fitnessClass.getCurrentParticipants()).isEqualTo(3);
    }

    @Test
    void shouldRejectDecrement_belowZero() {
        FitnessClass fitnessClass = classWith(10, 1);
        assertThatThrownBy(() -> fitnessClass.decrementParticipants(2))
                .isInstanceOf(ConflictException.class);
        assertThat(fitnessClass.getCurrentParticipants()).isEqualTo(1);
    }
}
