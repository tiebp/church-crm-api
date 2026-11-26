package org.churchcrm.churchcrmapi.membership.internal;

import org.churchcrm.churchcrmapi.crosscutting.auditing.Audit;
import org.churchcrm.churchcrmapi.membership.CreateMembershipStatusDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipStatusMapperTest {

    private final MembershipStatusMapper mapper = new MembershipStatusMapperImpl();

    @Test
    void toDtoMapsEntityFieldsAndAudit() {
        var entity = new MembershipStatus();
        entity.setId(UUID.randomUUID());
        entity.setChurchId(UUID.randomUUID());
        entity.setCode("member");
        entity.setLabel("Member");
        entity.setDescription("Confirmed church member");
        entity.setSortOrder(10);
        entity.setIsDefault(true);
        entity.setActive(true);

        var audit = new Audit();
        var createdAt = Instant.parse("2026-01-01T10:00:00Z");
        var updatedAt = Instant.parse("2026-01-02T10:00:00Z");
        audit.setCreatedAt(createdAt);
        audit.setCreatedBy("admin");
        audit.setUpdatedAt(updatedAt);
        audit.setUpdatedBy("admin");
        entity.setAudit(audit);

        var dto = mapper.toDto(entity);

        assertThat(dto.id()).isEqualTo(entity.getId());
        assertThat(dto.churchId()).isEqualTo(entity.getChurchId());
        assertThat(dto.code()).isEqualTo("member");
        assertThat(dto.label()).isEqualTo("Member");
        assertThat(dto.description()).isEqualTo("Confirmed church member");
        assertThat(dto.sortOrder()).isEqualTo(10);
        assertThat(dto.isDefault()).isTrue();
        assertThat(dto.active()).isTrue();
        assertThat(dto.createdBy()).isEqualTo("admin");
        assertThat(dto.createdAt()).isEqualTo(createdAt);
        assertThat(dto.updatedBy()).isEqualTo("admin");
        assertThat(dto.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void toEntityMapsCreateFieldsAndIgnoresServerControlledFields() {
        var dto = new CreateMembershipStatusDto("regular_attender", "Regular Attender", "Attends regularly", 20, true);

        var entity = mapper.toEntity(dto);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getChurchId()).isNull();
        assertThat(entity.getCode()).isEqualTo("regular_attender");
        assertThat(entity.getLabel()).isEqualTo("Regular Attender");
        assertThat(entity.getDescription()).isEqualTo("Attends regularly");
        assertThat(entity.getSortOrder()).isEqualTo(20);
        assertThat(entity.getIsDefault()).isTrue();
        assertThat(entity.getActive()).isTrue();
        assertThat(entity.getAudit()).isNotNull();
    }
}
