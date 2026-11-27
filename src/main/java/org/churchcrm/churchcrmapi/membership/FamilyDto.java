package org.churchcrm.churchcrmapi.membership;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for Family.
 * Immutable record type for API responses.
 */
@Schema(description = "Family information")
public record FamilyDto(
        @Schema(description = "Family ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Church ID (organization)", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID churchId,

        @Schema(description = "Family name", example = "Doe")
        String name,

        @Schema(description = "Family address")
        FamilyAddressDto address,

        @Schema(description = "Wedding date", example = "2005-06-18")
        LocalDate weddingDate,

        @Schema(description = "User who created this family")
        String createdBy,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "User who last modified this family")
        String updatedBy,

        @Schema(description = "Last modification timestamp")
        Instant updatedAt
) {
}
