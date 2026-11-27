package org.churchcrm.churchcrmapi.membership.internal;

import org.churchcrm.churchcrmapi.membership.CreateFamilyDto;
import org.churchcrm.churchcrmapi.membership.FamilyAddressDto;
import org.churchcrm.churchcrmapi.membership.FamilyDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Family.
 */
@Mapper(componentModel = "spring")
interface FamilyMapper {

    /**
     * Converts entity to DTO, flattening the embedded audit fields.
     */
    @Mapping(target = "createdAt", source = "audit.createdAt")
    @Mapping(target = "createdBy", source = "audit.createdBy")
    @Mapping(target = "updatedAt", source = "audit.updatedAt")
    @Mapping(target = "updatedBy", source = "audit.updatedBy")
    FamilyDto toDto(Family entity);

    /**
     * Converts create DTO to entity.
     * Ignores server-controlled fields (id, audit, churchId).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "audit", ignore = true)
    @Mapping(target = "churchId", ignore = true)
    Family toEntity(CreateFamilyDto dto);

    FamilyAddress toAddress(FamilyAddressDto dto);

    default FamilyAddressDto toAddressDto(FamilyAddress address) {
        if (address == null || isEmpty(address)) {
            return null;
        }
        return new FamilyAddressDto(
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getCountry(),
                address.getPostalCode()
        );
    }

    private boolean isEmpty(FamilyAddress address) {
        return address.getLine1() == null
                && address.getLine2() == null
                && address.getCity() == null
                && address.getState() == null
                && address.getCountry() == null
                && address.getPostalCode() == null;
    }
}
