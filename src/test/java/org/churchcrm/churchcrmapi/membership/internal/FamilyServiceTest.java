package org.churchcrm.churchcrmapi.membership.internal;

import org.churchcrm.churchcrmapi.crosscutting.auditing.Audit;
import org.churchcrm.churchcrmapi.crosscutting.web.ConflictException;
import org.churchcrm.churchcrmapi.membership.CreateFamilyDto;
import org.churchcrm.churchcrmapi.membership.FamilyAddressDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    @Mock
    private FamilyRepository repository;

    @Spy
    private FamilyMapper mapper = new FamilyMapperImpl();

    @InjectMocks
    private FamilyService service;

    @Test
    void createScopesFamilyToChurchAndReturnsCreatedDto() {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateFamilyDto(
                "  Doe  ",
                new FamilyAddressDto("123 Main Street", "Suite 100", "Springfield", "Illinois", "United States", "62701"),
                LocalDate.of(2005, 6, 18));
        when(repository.saveAndFlush(any(Family.class))).thenAnswer(invocation -> {
            Family saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            var audit = new Audit();
            audit.setCreatedBy("user");
            audit.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));
            audit.setUpdatedBy("user");
            audit.setUpdatedAt(Instant.parse("2026-01-01T10:00:00Z"));
            saved.setAudit(audit);
            return saved;
        });

        var result = service.create(dto, churchId);

        assertThat(result.churchId()).isEqualTo(churchId);
        assertThat(result.name()).isEqualTo("Doe");
        assertThat(result.address()).isEqualTo(new FamilyAddressDto(
                "123 Main Street", "Suite 100", "Springfield", "Illinois", "United States", "62701"));
        assertThat(result.weddingDate()).isEqualTo(LocalDate.of(2005, 6, 18));
        assertThat(result.createdBy()).isEqualTo("user");
        assertThat(result.createdAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(result.updatedBy()).isEqualTo("user");
        assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void createRejectsDuplicateNameInSameChurch() {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateFamilyDto("Doe", null, null);
        when(repository.existsByChurchIdAndNameIgnoreCase(churchId, "Doe")).thenReturn(true);

        assertThatThrownBy(() -> service.create(dto, churchId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getConflictType())
                .isEqualTo("name");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createMapsUniqueIndexRaceToConflict() {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateFamilyDto("Doe", null, null);
        when(repository.saveAndFlush(any(Family.class)))
                .thenThrow(constraintViolation(FamilyService.UNIQUE_NAME_INDEX));

        assertThatThrownBy(() -> service.create(dto, churchId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getConflictType())
                .isEqualTo("name");
    }

    @Test
    void createLeavesAuditFieldsToTheListener() {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateFamilyDto("Doe", null, null);
        AtomicReference<Family> persisted = new AtomicReference<>();
        when(repository.saveAndFlush(any(Family.class))).thenAnswer(invocation -> {
            Family saved = invocation.getArgument(0);
            persisted.set(saved);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.create(dto, churchId);

        Audit audit = persisted.get().getAudit();
        assertThat(audit).isNotNull();
        assertThat(audit.getCreatedBy()).isNull();
        assertThat(audit.getCreatedAt()).isNull();
        assertThat(audit.getUpdatedBy()).isNull();
        assertThat(audit.getUpdatedAt()).isNull();
    }

    @Test
    void createUsesOnlyFamilyRepository() {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateFamilyDto("Doe", null, null);
        when(repository.saveAndFlush(any(Family.class))).thenAnswer(invocation -> {
            Family saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.create(dto, churchId);

        verify(repository).existsByChurchIdAndNameIgnoreCase(churchId, "Doe");
        verify(repository).saveAndFlush(any(Family.class));
        verifyNoMoreInteractions(repository);
    }

    private DataIntegrityViolationException constraintViolation(String indexName) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("duplicate key value violates unique constraint \"" + indexName + "\"")
        );
    }
}
