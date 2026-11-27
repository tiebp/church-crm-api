package org.churchcrm.churchcrmapi.membership.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.churchcrm.churchcrmapi.crosscutting.web.OrganizationId;
import org.churchcrm.churchcrmapi.membership.CreateFamilyDto;
import org.churchcrm.churchcrmapi.membership.FamilyDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller for Family operations.
 */
@RestController
@RequestMapping("/membership/families")
@RequiredArgsConstructor
@Tag(name = "Families", description = "Family management endpoints")
public class FamilyController {

    private final FamilyService service;

    /**
     * Create a family for the caller's church.
     * ArchUnit Rule #8: MUST return ResponseEntity
     */
    @PostMapping
    @Operation(
            summary = "Create family",
            description = """
                    Creates a family for the church from the JWT organization_id claim.
                    Any authenticated user may create a family. Any churchId in the request body is ignored.
                    """,
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Family created successfully",
                    content = @Content(schema = @Schema(implementation = FamilyDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - a family with this name already exists in this church",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content
            )
    })
    public ResponseEntity<FamilyDto> create(
            @RequestBody @Valid CreateFamilyDto dto,
            @OrganizationId UUID churchId) {

        FamilyDto created = service.create(dto, churchId);
        return ResponseEntity
                .created(URI.create("/membership/families/" + created.id()))
                .body(created);
    }
}
