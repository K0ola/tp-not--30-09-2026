package com.fitconnect.classservice.repository;

import com.fitconnect.classservice.model.ClassCategory;
import com.fitconnect.classservice.model.ClassLevel;
import com.fitconnect.classservice.model.ClassStatus;
import com.fitconnect.classservice.model.FitnessClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifie que le champ @Version empeche deux mises a jour concurrentes basees
 * sur la meme lecture : la seconde ecriture est rejetee par JPA.
 */
@SpringBootTest
class OptimisticLockingTest {

    @Autowired
    private FitnessClassRepository repository;

    @Test
    void secondStaleWriteIsRejected() {
        FitnessClass saved = repository.save(FitnessClass.builder()
                .name("Yoga").description("desc").instructor("Marie").gymLocation("Paris")
                .category(ClassCategory.YOGA).level(ClassLevel.BEGINNER).durationMinutes(60)
                .maxParticipants(10).currentParticipants(9).price(new BigDecimal("25.00"))
                .dateTime(LocalDateTime.now().plusDays(3)).status(ClassStatus.SCHEDULED)
                .build());
        assertThat(saved.getVersion()).isZero();

        // Deux "transactions" lisent la meme version (0)
        FitnessClass first = repository.findById(saved.getId()).orElseThrow();
        FitnessClass second = repository.findById(saved.getId()).orElseThrow();

        // La premiere reserve la derniere place -> version 1
        first.incrementParticipants(1);
        FitnessClass afterFirst = repository.saveAndFlush(first);
        assertThat(afterFirst.getVersion()).isEqualTo(1L);
        assertThat(afterFirst.getCurrentParticipants()).isEqualTo(10);

        // La seconde croit encore qu'il reste une place (9/10) : l'ecriture est rejetee
        second.incrementParticipants(1);
        assertThatThrownBy(() -> repository.saveAndFlush(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        FitnessClass reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCurrentParticipants()).isEqualTo(10);
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }
}
