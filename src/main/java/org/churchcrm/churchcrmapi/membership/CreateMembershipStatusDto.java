package org.churchcrm.churchcrmapi.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating a MembershipStatus.
 * No id, church, active, or audit fields (server-generated).
 */
@Schema(description = "Request to create a membership status")
public record CreateMembershipStatusDto(
        @Schema(description = "Machine-readable status code", example = "member", required = true)
        @NotBlank(message = "Code cannot be blank")
        @Size(min = 1, max = 50, message = "Code must be between 1 and 50 characters")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$",
                message = "Code must start with a lowercase letter and contain only lowercase letters, digits, and underscores")
        String code,

        @Schema(description = "Human-readable status label", example = "Member", required = true)
        @NotBlank(message = "Label cannot be blank")
        @Size(min = 1, max = 100, message = "Label must be between 1 and 100 characters")
        String label,

        @Schema(description = "Optional status description", example = "Confirmed church member")
        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @Schema(description = "Ordering position within the church; defaults to 0", example = "10")
        @PositiveOrZero(message = "Sort order must be zero or greater")
        Integer sortOrder,

        @Schema(description = "Whether this is the church's default status; defaults to false", example = "false")
        Boolean isDefault
) {
}
