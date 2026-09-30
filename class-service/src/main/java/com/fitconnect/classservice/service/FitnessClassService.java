package com.fitconnect.classservice.service;

import com.fitconnect.classservice.dto.ClassSearchCriteria;
import com.fitconnect.classservice.dto.FitnessClassRequest;
import com.fitconnect.classservice.dto.FitnessClassResponse;
import com.fitconnect.classservice.exception.ConflictException;
import com.fitconnect.classservice.exception.ResourceNotFoundException;
import com.fitconnect.classservice.model.ClassStatus;
import com.fitconnect.classservice.model.FitnessClass;
import com.fitconnect.classservice.repository.FitnessClassRepository;
import com.fitconnect.classservice.repository.FitnessClassSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FitnessClassService {

    private final FitnessClassRepository repository;

    @Transactional(readOnly = true)
    public Page<FitnessClassResponse> getAll(ClassSearchCriteria criteria, Pageable pageable) {
        return repository.findAll(FitnessClassSpecifications.from(criteria), pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<FitnessClassResponse> search(ClassSearchCriteria criteria) {
        return repository.findAll(FitnessClassSpecifications.from(criteria), Sort.by("dateTime").ascending())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FitnessClassResponse getById(Long id) {
        return mapToResponse(findById(id));
    }

    @Transactional
    public FitnessClassResponse create(FitnessClassRequest request) {
        FitnessClass fitnessClass = FitnessClass.builder()
                .name(request.getName())
                .description(request.getDescription())
                .instructor(request.getInstructor())
                .gymLocation(request.getGymLocation())
                .category(request.getCategory())
                .level(request.getLevel())
                .durationMinutes(request.getDurationMinutes())
                .maxParticipants(request.getMaxParticipants())
                .currentParticipants(0)
                .price(request.getPrice())
                .dateTime(request.getDateTime())
                .status(ClassStatus.SCHEDULED)
                .build();
        return mapToResponse(repository.save(fitnessClass));
    }

    @Transactional
    public FitnessClassResponse update(Long id, FitnessClassRequest request) {
        FitnessClass fitnessClass = findById(id);
        if (request.getMaxParticipants() < fitnessClass.getCurrentParticipants()) {
            throw new ConflictException("Le nombre maximum de participants ne peut pas etre inferieur aux "
                    + fitnessClass.getCurrentParticipants() + " participants deja inscrits");
        }
        fitnessClass.setName(request.getName());
        fitnessClass.setDescription(request.getDescription());
        fitnessClass.setInstructor(request.getInstructor());
        fitnessClass.setGymLocation(request.getGymLocation());
        fitnessClass.setCategory(request.getCategory());
        fitnessClass.setLevel(request.getLevel());
        fitnessClass.setDurationMinutes(request.getDurationMinutes());
        fitnessClass.setMaxParticipants(request.getMaxParticipants());
        fitnessClass.setPrice(request.getPrice());
        fitnessClass.setDateTime(request.getDateTime());
        return mapToResponse(repository.save(fitnessClass));
    }

    /**
     * Annulation logique : le cours passe en CANCELLED (les reservations existantes
     * gardent une reference valide vers le cours). Un cours sans participant est
     * supprime physiquement.
     */
    @Transactional
    public void delete(Long id) {
        FitnessClass fitnessClass = findById(id);
        if (fitnessClass.getCurrentParticipants() == 0) {
            repository.delete(fitnessClass);
            return;
        }
        fitnessClass.setStatus(ClassStatus.CANCELLED);
        repository.save(fitnessClass);
    }

    /**
     * Reserve des places. La verification de capacite est faite dans l'entite et la
     * concurrence est protegee par le champ @Version : si deux appels concurrents
     * lisent la meme version, un seul commit reussit, l'autre recoit 409.
     */
    @Transactional
    public FitnessClassResponse incrementParticipants(Long id, int spots) {
        FitnessClass fitnessClass = findById(id);
        fitnessClass.incrementParticipants(spots);
        // flush immediat : la version est incrementee (et un eventuel conflit detecte) avant la reponse
        return mapToResponse(repository.saveAndFlush(fitnessClass));
    }

    @Transactional
    public FitnessClassResponse decrementParticipants(Long id, int spots) {
        FitnessClass fitnessClass = findById(id);
        fitnessClass.decrementParticipants(spots);
        return mapToResponse(repository.saveAndFlush(fitnessClass));
    }

    private FitnessClass findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cours introuvable avec l'id : " + id));
    }

    private FitnessClassResponse mapToResponse(FitnessClass fitnessClass) {
        return FitnessClassResponse.builder()
                .id(fitnessClass.getId())
                .name(fitnessClass.getName())
                .description(fitnessClass.getDescription())
                .instructor(fitnessClass.getInstructor())
                .gymLocation(fitnessClass.getGymLocation())
                .category(fitnessClass.getCategory())
                .level(fitnessClass.getLevel())
                .durationMinutes(fitnessClass.getDurationMinutes())
                .maxParticipants(fitnessClass.getMaxParticipants())
                .currentParticipants(fitnessClass.getCurrentParticipants())
                .availableSpots(fitnessClass.getAvailableSpots())
                .price(fitnessClass.getPrice())
                .dateTime(fitnessClass.getDateTime())
                .status(fitnessClass.getStatus())
                .version(fitnessClass.getVersion())
                .build();
    }
}
