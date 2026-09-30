package com.fitconnect.classservice.model;

import com.fitconnect.classservice.exception.ConflictException;
import com.fitconnect.classservice.exception.NoSpotsAvailableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fitness_classes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FitnessClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Verrouillage optimiste : JPA incremente ce champ a chaque mise a jour et
     * rejette (ObjectOptimisticLockingFailureException) toute ecriture basee sur
     * une version perimee. Deux reservations simultanees ne peuvent donc pas
     * depasser maxParticipants.
     */
    @Version
    private Long version;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false)
    private String instructor;

    @Column(nullable = false)
    private String gymLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassLevel level;

    @Column(nullable = false)
    private Integer durationMinutes;

    @Column(nullable = false)
    private Integer maxParticipants;

    @Column(nullable = false)
    private Integer currentParticipants;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private LocalDateTime dateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassStatus status;

    public int getAvailableSpots() {
        return maxParticipants - currentParticipants;
    }

    /**
     * Reserve {@code spots} places. Leve NoSpotsAvailableException si la capacite
     * serait depassee, ConflictException si le cours n'est plus ouvert.
     */
    public void incrementParticipants(int spots) {
        if (spots <= 0) {
            throw new ConflictException("Le nombre de places doit etre strictement positif");
        }
        if (status != ClassStatus.SCHEDULED) {
            throw new ConflictException("Le cours n'est pas ouvert a la reservation (statut " + status + ")");
        }
        if (this.currentParticipants + spots > this.maxParticipants) {
            throw new NoSpotsAvailableException("Plus de places disponibles pour ce cours");
        }
        this.currentParticipants += spots;
    }

    /**
     * Libere {@code spots} places (annulation ou expiration d'une reservation).
     */
    public void decrementParticipants(int spots) {
        if (spots <= 0) {
            throw new ConflictException("Le nombre de places doit etre strictement positif");
        }
        if (this.currentParticipants - spots < 0) {
            throw new ConflictException("Impossible de liberer plus de places que de participants inscrits");
        }
        this.currentParticipants -= spots;
    }
}
