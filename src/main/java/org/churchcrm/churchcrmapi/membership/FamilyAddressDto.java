package org.churchcrm.churchcrmapi.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Address value object for a family.
 * All fields are trimmed; blank optional fields become null.
 */
@Schema(description = "Family address information")
public record FamilyAddressDto(
        @Schema(description = "First line of the address (street address)", example = "123 Main Street", required = true)
        @NotBlank(message = "Address line 1 cannot be blank")
        @Size(min = 3, max = 200, message = "Address line 1 must be between 3 and 200 characters")
        String line1,

        @Schema(description = "Second line of the address (suite, apartment, etc.)", example = "Suite 100")
        @Size(min = 3, max = 200, message = "Address line 2 must be between 3 and 200 characters if provided")
        String line2,

        @Schema(description = "City name", example = "Springfield", required = true)
        @NotBlank(message = "City cannot be blank")
        @Size(min = 3, max = 100, message = "City must be between 3 and 100 characters")
        String city,

        @Schema(description = "State or province", example = "Illinois")
        @Size(min = 3, max = 100, message = "State must be between 3 and 100 characters if provided")
        String state,

        @Schema(description = "Country", example = "United States")
        @Size(min = 3, max = 60, message = "Country must be between 3 and 60 characters if provided")
        String country,

        @Schema(description = "Postal/ZIP code", example = "62701")
        @Size(min = 3, max = 20, message = "Postal code must be between 3 and 20 characters if provided")
        String postalCode
) {
    public FamilyAddressDto {
        line1 = normalize(line1);
        line2 = normalize(line2);
        city = normalize(city);
        state = normalize(state);
        country = normalize(country);
        postalCode = normalize(postalCode);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
