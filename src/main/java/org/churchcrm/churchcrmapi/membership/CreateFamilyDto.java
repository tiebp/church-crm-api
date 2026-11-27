package org.churchcrm.churchcrmapi.membership;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * DTO for creating a Family.
 * No id, churchId, or audit fields (server-generated).
 */
@Schema(description = "Request to create a family")
public record CreateFamilyDto(
        @Schema(description = "Family name", example = "Doe", required = true)
        @NotBlank(message = "Name cannot be blank")
        @Size(min = 3, max = 200, message = "Name must be between 3 and 200 characters")
        String name,

        @Schema(description = "Optional family address", required = false)
        @Valid
        FamilyAddressDto address,

        @Schema(description = "Optional wedding date in ISO-8601 format", example = "2005-06-18")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate weddingDate
) {
    public CreateFamilyDto {
        name = name == null ? null : name.trim();
    }
}
