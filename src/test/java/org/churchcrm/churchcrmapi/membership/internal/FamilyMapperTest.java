package org.churchcrm.churchcrmapi.membership.internal;

import org.churchcrm.churchcrmapi.crosscutting.auditing.Audit;
import org.churchcrm.churchcrmapi.membership.CreateFamilyDto;
import org.churchcrm.churchcrmapi.membership.FamilyAddressDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyMapperTest {

    private final FamilyMapper mapper = new FamilyMapperImpl();

    @Test
    void toDtoMapsEntityFieldsNestedAddressAndAudit() {
        var entity = new Family();
        entity.setId(UUID.randomUUID());
        entity.setChurchId(UUID.randomUUID());
        entity.setName("Doe");
        entity.setAddress(new FamilyAddress("123 Main Street", "Suite 100", "Springfield", "Illinois", "United States", "62701"));
        entity.setWeddingDate(LocalDate.of(2005, 6, 18));

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
        assertThat(dto.name()).isEqualTo("Doe");
        assertThat(dto.address()).isEqualTo(new FamilyAddressDto(
                "123 Main Street", "Suite 100", "Springfield", "Illinois", "United States", "62701"));
        assertThat(dto.weddingDate()).isEqualTo(LocalDate.of(2005, 6, 18));
        assertThat(dto.createdBy()).isEqualTo("admin");
        assertThat(dto.createdAt()).isEqualTo(createdAt);
        assertThat(dto.updatedBy()).isEqualTo("admin");
        assertThat(dto.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void toDtoReturnsNullAddressWhenEntityAddressIsNull() {
        var entity = new Family();
        entity.setName("Doe");

        var dto = mapper.toDto(entity);

        assertThat(dto.address()).isNull();
    }

    @Test
    void toDtoReturnsNullAddressWhenAllAddressFieldsAreNull() {
        var entity = new Family();
        entity.setName("Doe");
        entity.setAddress(new FamilyAddress());

        var dto = mapper.toDto(entity);

        assertThat(dto.address()).isNull();
    }

    @Test
    void toEntityMapsCreateFieldsAndIgnoresServerControlledFields() {
        var address = new FamilyAddressDto("123 Main Street", null, "Springfield", null, null, null);
        var dto = new CreateFamilyDto("Doe", address, LocalDate.of(2005, 6, 18));

        var entity = mapper.toEntity(dto);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getChurchId()).isNull();
        assertThat(entity.getName()).isEqualTo("Doe");
        assertThat(entity.getAddress()).isEqualTo(new FamilyAddress("123 Main Street", null, "Springfield", null, null, null));
        assertThat(entity.getWeddingDate()).isEqualTo(LocalDate.of(2005, 6, 18));
        assertThat(entity.getAudit()).isNotNull();
        assertThat(entity.getAudit().getCreatedAt()).isNull();
        assertThat(entity.getAudit().getCreatedBy()).isNull();
    }

    @Test
    void toEntityMapsNullAddressToNull() {
        var dto = new CreateFamilyDto("Doe", null, null);

        var entity = mapper.toEntity(dto);

        assertThat(entity.getAddress()).isNull();
    }
}
