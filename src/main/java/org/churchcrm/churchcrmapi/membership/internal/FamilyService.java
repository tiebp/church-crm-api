package org.churchcrm.churchcrmapi.membership.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.churchcrm.churchcrmapi.crosscutting.web.ConflictException;
import org.churchcrm.churchcrmapi.membership.CreateFamilyDto;
import org.churchcrm.churchcrmapi.membership.FamilyDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for Family operations.
 * Internal to the module.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
class FamilyService {

    static final String UNIQUE_NAME_INDEX = "idx_families_unique_name_per_church";

    private final FamilyRepository repository;
    private final FamilyMapper mapper;

    /**
     * Creates a family for the caller's church.
     * Pre-checks provide friendly conflicts; the flush maps unique-index races to 409.
     */
    @Transactional
    public FamilyDto create(CreateFamilyDto dto, UUID churchId) {
        if (repository.existsByChurchIdAndNameIgnoreCase(churchId, dto.name())) {
            throw new ConflictException(
                    "Family with this name already exists in this church",
                    "name",
                    dto.name()
            );
        }

        Family entity = mapper.toEntity(dto);
        entity.setChurchId(churchId);

        try {
            entity = repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw mapConstraintViolation(ex, dto);
        }

        log.info("Created Family: id={}, church={}", entity.getId(), churchId);

        return mapper.toDto(entity);
    }

    private ConflictException mapConstraintViolation(DataIntegrityViolationException ex, CreateFamilyDto dto) {
        String message = ex.getMostSpecificCause().getMessage();

        if (message != null && message.contains(UNIQUE_NAME_INDEX)) {
            return new ConflictException(
                    "Family with this name already exists in this church",
                    "name",
                    dto.name()
            );
        }

        return new ConflictException("Family conflicts with an existing record", ex);
    }
}
