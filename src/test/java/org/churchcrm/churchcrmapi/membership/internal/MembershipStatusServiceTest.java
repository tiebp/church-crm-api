package org.churchcrm.churchcrmapi.membership.internal;

import org.churchcrm.churchcrmapi.crosscutting.web.ConflictException;
import org.churchcrm.churchcrmapi.membership.CreateMembershipStatusDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipStatusServiceTest {

    @Mock
    private MembershipStatusRepository repository;

    @Spy
    private MembershipStatusMapper mapper = new MembershipStatusMapperImpl();

    @InjectMocks
    private MembershipStatusService service;

    @Test
    void listReturnsStatusesOrderedAndIncludesInactive() {
        UUID churchId = UUID.randomUUID();
        var first = status(churchId, "member", 10, true);
        var second = status(churchId, "guest", 20, false);
        var third = status(churchId, "excluded", 30, true);
        when(repository.findByChurchIdOrderBySortOrderAsc(churchId)).thenReturn(List.of(first, second, third));

        var result = service.list(churchId);

        assertThat(result).extracting(dto -> dto.code())
                .containsExactly("member", "guest", "excluded");
        assertThat(result).extracting(dto -> dto.active())
                .containsExactly(true, false, true);
    }

    @Test
    void getByIdReturnsStatusScopedToChurch() {
        UUID churchId = UUID.randomUUID();
        var entity = status(churchId, "member", 10, true);
        when(repository.findByChurchIdAndId(churchId, entity.getId())).thenReturn(Optional.of(entity));

        var result = service.getById(entity.getId(), churchId);

        assertThat(result.code()).isEqualTo("member");
        assertThat(result.churchId()).isEqualTo(churchId);
    }

    @Test
    void getByIdThrowsNotFoundForMissingOrCrossChurchStatus() {
        UUID id = UUID.randomUUID();
        UUID churchId = UUID.randomUUID();
        when(repository.findByChurchIdAndId(churchId, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id, churchId))
                .isInstanceOf(MembershipStatusNotFoundException.class);
    }

    @Test
    void createAppliesDefaults() {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateMembershipStatusDto("member", "Member", null, null, null);
        when(repository.saveAndFlush(any(MembershipStatus.class))).thenAnswer(invocation -> {
            MembershipStatus saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var result = service.create(dto, churchId);

        assertThat(result.churchId()).isEqualTo(churchId);
        assertThat(result.code()).isEqualTo("member");
        assertThat(result.sortOrder()).isZero();
        assertThat(result.isDefault()).isFalse();
        assertThat(result.active()).isTrue();
    }

    @Test
    void createPreservesExplicitSortOrderAndDefault() {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateMembershipStatusDto("guest", "Guest", "Visitors", 30, true);
        when(repository.saveAndFlush(any(MembershipStatus.class))).thenAnswer(invocation -> {
            MembershipStatus saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var result = service.create(dto, churchId);

        assertThat(result.sortOrder()).isEqualTo(30);
        assertThat(result.isDefault()).isTrue();
        assertThat(result.description()).isEqualTo("Visitors");
        assertThat(result.active()).isTrue();
    }

    @Test
    void createRejectsDuplicateCode() {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateMembershipStatusDto("member", "Member", null, 0, false);
        when(repository.existsByChurchIdAndCodeIgnoreCase(churchId, "member")).thenReturn(true);

        assertThatThrownBy(() -> service.create(dto, churchId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getConflictType())
                .isEqualTo("code");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsSecondDefault() {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateMembershipStatusDto("custom_default", "Custom Default", null, 0, true);
        when(repository.existsByChurchIdAndIsDefaultTrue(churchId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(dto, churchId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getConflictType())
                .isEqualTo("isDefault");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createMapsCodeIndexRaceToConflict() {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateMembershipStatusDto("member", "Member", null, 0, false);
        when(repository.saveAndFlush(any(MembershipStatus.class)))
                .thenThrow(constraintViolation(MembershipStatusService.UNIQUE_CODE_INDEX));

        assertThatThrownBy(() -> service.create(dto, churchId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getConflictType())
                .isEqualTo("code");
    }

    @Test
    void createMapsDefaultIndexRaceToConflict() {
        UUID churchId = UUID.randomUUID();
        var dto = new CreateMembershipStatusDto("guest", "Guest", null, 30, true);
        when(repository.saveAndFlush(any(MembershipStatus.class)))
                .thenThrow(constraintViolation(MembershipStatusService.ONE_DEFAULT_INDEX));

        assertThatThrownBy(() -> service.create(dto, churchId))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getConflictType())
                .isEqualTo("isDefault");
    }

    private DataIntegrityViolationException constraintViolation(String indexName) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("duplicate key value violates unique constraint \"" + indexName + "\"")
        );
    }

    private MembershipStatus status(UUID churchId, String code, int sortOrder, boolean active) {
        var status = new MembershipStatus();
        status.setId(UUID.randomUUID());
        status.setChurchId(churchId);
        status.setCode(code);
        status.setLabel(code);
        status.setSortOrder(sortOrder);
        status.setIsDefault(false);
        status.setActive(active);
        return status;
    }
}
