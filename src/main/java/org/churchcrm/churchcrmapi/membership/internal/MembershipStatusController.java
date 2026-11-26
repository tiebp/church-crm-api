package org.churchcrm.churchcrmapi.membership.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.churchcrm.churchcrmapi.crosscutting.web.OrganizationId;
import org.churchcrm.churchcrmapi.membership.CreateMembershipStatusDto;
import org.churchcrm.churchcrmapi.membership.MembershipStatusDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for MembershipStatus operations.
 */
@RestController
@RequestMapping("/membership/statuses")
@RequiredArgsConstructor
@Tag(name = "Membership Statuses", description = "Membership status configuration endpoints")
public class MembershipStatusController {

    private final MembershipStatusService service;

    /**
     * Create a custom membership status for the caller's church.
     * ArchUnit Rule #8: MUST return ResponseEntity
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Create membership status",
            description = """
                    Creates a custom membership status for the church from the JWT organization_id claim.
                    Requires the ADMIN role. Any churchId in the request body is ignored.
                    """,
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Membership status created successfully",
                    content = @Content(schema = @Schema(implementation = MembershipStatusDto.class))
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
                    responseCode = "403",
                    description = "Forbidden - requires ADMIN role",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - duplicate code or an existing default status",
                    content = @Content
            )
    })
    public ResponseEntity<MembershipStatusDto> create(
            @RequestBody @Valid CreateMembershipStatusDto dto,
            @OrganizationId UUID organizationId) {

        MembershipStatusDto created = service.create(dto, organizationId);
        return ResponseEntity
                .created(URI.create("/membership/statuses/" + created.id()))
                .body(created);
    }

    /**
     * List all membership statuses for the caller's church.
     * ArchUnit Rule #8: MUST return ResponseEntity
     */
    @GetMapping
    @Operation(
            summary = "List membership statuses",
            description = "Lists all membership statuses for the church from the JWT organization_id claim, " +
                    "including inactive statuses, ordered by sortOrder ascending.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Statuses returned successfully",
                    content = @Content(schema = @Schema(implementation = MembershipStatusDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT",
                    content = @Content
            )
    })
    public ResponseEntity<List<MembershipStatusDto>> list(
            @OrganizationId UUID organizationId) {

        return ResponseEntity.ok(service.list(organizationId));
    }

    /**
     * Get a membership status by ID.
     * ArchUnit Rule #8: MUST return ResponseEntity
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Get membership status by ID",
            description = "Retrieves a membership status that belongs to the caller's church. " +
                    "Returns 404 when the status does not exist or belongs to another church.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Membership status found",
                    content = @Content(schema = @Schema(implementation = MembershipStatusDto.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Membership status not found or access denied",
                    content = @Content
            )
    })
    public ResponseEntity<MembershipStatusDto> get(
            @Parameter(description = "Membership status UUID", required = true,
                    example = "9987eb71-8050-4e44-8f4e-179564bfccca")
            @PathVariable UUID id,
            @OrganizationId UUID organizationId) {

        return ResponseEntity.ok(service.getById(id, organizationId));
    }
}
