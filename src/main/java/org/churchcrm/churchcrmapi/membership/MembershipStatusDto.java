package org.churchcrm.churchcrmapi.membership;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for MembershipStatus.
 * Immutable record type for API responses.
 */
@Schema(description = "Membership status information")
public record MembershipStatusDto(
        @Schema(description = "Membership status ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Church ID (organization)", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID churchId,

        @Schema(description = "Machine-readable status code", example = "member")
        String code,

        @Schema(description = "Human-readable status label", example = "Member")
        String label,

        @Schema(description = "Optional status description", example = "Confirmed church member")
        String description,

        @Schema(description = "Ordering position within the church", example = "10")
        Integer sortOrder,

        @Schema(description = "Whether this is the church's default status", example = "false")
        Boolean isDefault,

        @Schema(description = "Whether the status is active", example = "true")
        Boolean active,

        @Schema(description = "User who created this status")
        String createdBy,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "User who last modified this status")
        String updatedBy,

        @Schema(description = "Last modification timestamp")
        Instant updatedAt
) {
}
