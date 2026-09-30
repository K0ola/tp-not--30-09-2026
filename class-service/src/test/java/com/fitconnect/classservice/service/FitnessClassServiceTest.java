package com.fitconnect.classservice.service;

import com.fitconnect.classservice.dto.FitnessClassRequest;
import com.fitconnect.classservice.dto.FitnessClassResponse;
import com.fitconnect.classservice.exception.ConflictException;
import com.fitconnect.classservice.exception.NoSpotsAvailableException;
import com.fitconnect.classservice.exception.ResourceNotFoundException;
import com.fitconnect.classservice.model.ClassCategory;
import com.fitconnect.classservice.model.ClassLevel;
import com.fitconnect.classservice.model.ClassStatus;
import com.fitconnect.classservice.model.FitnessClass;
import com.fitconnect.classservice.repository.FitnessClassRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FitnessClassServiceTest {

    @Mock
    private FitnessClassRepository repository;

    @InjectMocks
    private FitnessClassService service;

    private FitnessClass classWith(int max, int current) {
        return FitnessClass.builder()
                .id(1L).version(0L).name("Yoga").description("desc").instructor("Marie").gymLocation("Paris")
                .category(ClassCategory.YOGA).level(ClassLevel.BEGINNER).durationMinutes(60)
                .maxParticipants(max).currentParticipants(current).price(new BigDecimal("25.00"))
                .dateTime(LocalDateTime.now().plusDays(3)).status(ClassStatus.SCHEDULED)
                .build();
    }

    @Test
    void createInitialisesCountersAndStatus() {
        FitnessClassRequest request = FitnessClassRequest.builder()
                .name("Yoga").description("desc").instructor("Marie").gymLocation("Paris")
                .category(ClassCategory.YOGA).level(ClassLevel.BEGINNER).durationMinutes(60)
                .maxParticipants(15).price(new BigDecimal("25.00"))
                .dateTime(LocalDateTime.now().plusDays(3))
                .build();
        when(repository.save(any(FitnessClass.class))).thenAnswer(inv -> {
            FitnessClass c = inv.getArgument(0);
            c.setId(42L);
            return c;
        });

        FitnessClassResponse response = service.create(request);

        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getCurrentParticipants()).isZero();
        assertThat(response.getAvailableSpots()).isEqualTo(15);
        assertThat(response.getStatus()).isEqualTo(ClassStatus.SCHEDULED);
    }

    @Test
    void incrementParticipantsSavesUpdatedCount() {
        FitnessClass fitnessClass = classWith(10, 5);
        when(repository.findById(1L)).thenReturn(Optional.of(fitnessClass));
        when(repository.saveAndFlush(any(FitnessClass.class))).thenAnswer(inv -> inv.getArgument(0));

        FitnessClassResponse response = service.incrementParticipants(1L, 2);

        assertThat(response.getCurrentParticipants()).isEqualTo(7);
        verify(repository).saveAndFlush(fitnessClass);
    }

    @Test
    void incrementParticipantsThrowsWhenCapacityExceeded() {
        when(repository.findById(1L)).thenReturn(Optional.of(classWith(10, 9)));

        assertThatThrownBy(() -> service.incrementParticipants(1L, 2))
                .isInstanceOf(NoSpotsAvailableException.class);
        verify(repository, never()).saveAndFlush(any(FitnessClass.class));
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateRefusesCapacityBelowCurrentParticipants() {
        when(repository.findById(1L)).thenReturn(Optional.of(classWith(10, 8)));
        FitnessClassRequest request = FitnessClassRequest.builder()
                .name("Yoga").description("desc").instructor("Marie").gymLocation("Paris")
                .category(ClassCategory.YOGA).level(ClassLevel.BEGINNER).durationMinutes(60)
                .maxParticipants(5).price(new BigDecimal("25.00"))
                .dateTime(LocalDateTime.now().plusDays(3))
                .build();

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteCancelsClassWithParticipantsInsteadOfRemovingIt() {
        FitnessClass fitnessClass = classWith(10, 3);
        when(repository.findById(1L)).thenReturn(Optional.of(fitnessClass));

        service.delete(1L);

        assertThat(fitnessClass.getStatus()).isEqualTo(ClassStatus.CANCELLED);
        verify(repository, never()).delete(any(FitnessClass.class));
        verify(repository).save(fitnessClass);
    }
}
