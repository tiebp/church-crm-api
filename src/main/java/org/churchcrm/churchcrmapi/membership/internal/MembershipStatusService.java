package org.churchcrm.churchcrmapi.membership.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.churchcrm.churchcrmapi.crosscutting.web.ConflictException;
import org.churchcrm.churchcrmapi.membership.CreateMembershipStatusDto;
import org.churchcrm.churchcrmapi.membership.MembershipStatusDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for MembershipStatus operations.
 * Internal to the module.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
class MembershipStatusService {

    static final String UNIQUE_CODE_INDEX = "idx_membership_statuses_unique_code_per_church";
    static final String ONE_DEFAULT_INDEX = "idx_membership_statuses_one_default_per_church";

    private final MembershipStatusRepository repository;
    private final MembershipStatusMapper mapper;

    /**
     * Lists all statuses for a church, including inactive ones, ordered by sortOrder.
     */
    public List<MembershipStatusDto> list(UUID churchId) {
        return repository
                .findByChurchIdOrderBySortOrderAsc(churchId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    /**
     * Gets a status by id scoped to the caller's church.
     * Returns 404 when the status does not exist or belongs to another church.
     */
    public MembershipStatusDto getById(UUID id, UUID churchId) {
        return repository.findByChurchIdAndId(churchId, id)
                .map(mapper::toDto)
                .orElseThrow(() -> new MembershipStatusNotFoundException(id));
    }

    /**
     * Creates a custom status for the caller's church.
     * Pre-checks provide friendly conflicts; the flush maps unique-index races to 409.
     */
    @Transactional
    public MembershipStatusDto create(CreateMembershipStatusDto dto, UUID churchId) {
        if (repository.existsByChurchIdAndCodeIgnoreCase(churchId, dto.code())) {
            throw new ConflictException(
                    "Membership status with this code already exists in this church",
                    "code",
                    dto.code()
            );
        }

        boolean isDefault = Boolean.TRUE.equals(dto.isDefault());
        if (isDefault && repository.existsByChurchIdAndIsDefaultTrue(churchId)) {
            throw new ConflictException(
                    "A default membership status already exists in this church",
                    "isDefault",
                    true
            );
        }

        MembershipStatus entity = mapper.toEntity(dto);
        entity.setChurchId(churchId);
        entity.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : 0);
        entity.setIsDefault(isDefault);
        entity.setActive(true);

        try {
            entity = repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw mapConstraintViolation(ex, dto);
        }

        log.info("Created MembershipStatus: id={}, code={}, church={}", entity.getId(), entity.getCode(), churchId);

        return mapper.toDto(entity);
    }

    private ConflictException mapConstraintViolation(DataIntegrityViolationException ex, CreateMembershipStatusDto dto) {
        String message = ex.getMostSpecificCause().getMessage();

        if (message != null && message.contains(ONE_DEFAULT_INDEX)) {
            return new ConflictException(
                    "A default membership status already exists in this church",
                    "isDefault",
                    true
            );
        }

        if (message != null && message.contains(UNIQUE_CODE_INDEX)) {
            return new ConflictException(
                    "Membership status with this code already exists in this church",
                    "code",
                    dto.code()
            );
        }

        return new ConflictException("Membership status conflicts with an existing record", ex);
    }
}
