package org.churchcrm.churchcrmapi.membership.internal;

import org.churchcrm.churchcrmapi.membership.CreateMembershipStatusDto;
import org.churchcrm.churchcrmapi.membership.MembershipStatusDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for MembershipStatus.
 */
@Mapper(componentModel = "spring")
interface MembershipStatusMapper {

    /**
     * Converts entity to DTO, flattening the embedded audit fields.
     */
    @Mapping(target = "createdAt", source = "audit.createdAt")
    @Mapping(target = "createdBy", source = "audit.createdBy")
    @Mapping(target = "updatedAt", source = "audit.updatedAt")
    @Mapping(target = "updatedBy", source = "audit.updatedBy")
    MembershipStatusDto toDto(MembershipStatus entity);

    /**
     * Converts create DTO to entity.
     * Ignores server-controlled fields (id, audit, churchId, active).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "audit", ignore = true)
    @Mapping(target = "churchId", ignore = true)
    @Mapping(target = "active", ignore = true)
    MembershipStatus toEntity(CreateMembershipStatusDto dto);
}
