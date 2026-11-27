# Design

## Context

See `proposal.md` for motivation. The `membership` module already owns membership statuses and follows the established conventions: public records in `membership/`, implementation in `membership/internal/` (ADR-0002), a schema-per-module Flyway migration (ADR-0009), UUID ids with an embedded `Audit` (ADR-0010), immutable record DTOs with MapStruct (ADR-0011), RFC 9457 errors (ADR-0013), and the eight ArchUnit rules (ADR-0014). The existing `Address` value object lives in `organization.internal` and has different fields (`line1`, `line2`, `city`, `postalCode`) than a family address, so it is not reused.

The tenant is the church (ADR-0003), the JWT `organization_id` claim is the church id (ADR-0004), and cross-tenant access must return 404 rather than 403 (ADR-0005). This change adds no cross-module event, so ADR-0012 does not apply.

Two request contradictions were resolved with the user before drafting:

- Family names are **unique per church** (case-insensitive), overriding the "duplicate family names are allowed" acceptance criterion; a 409 case is added to the error contract.
- The create endpoint is `POST /membership/families`, matching the membership module convention and the required `Location` header.

## Goals / Non-Goals

**Goals:**
- Add an explicit, independently creatable family resource in the `membership` module.
- Support an optional structured address and an optional ISO-8601 wedding date.
- Enforce per-church case-insensitive name uniqueness in the database.
- Scope every family to the JWT church and ignore any request-supplied `churchId`.
- Follow ADR-0009 through ADR-0011, ADR-0013, and ADR-0014 and the existing membership module patterns.

**Non-Goals:**
- Person creation, person linking, or automatic family creation.
- `GET`, list, `PUT`, `PATCH`, or `DELETE` family endpoints.
- Congregation association, member counts, or family relationships beyond the fields in this change.
- Cross-module events, Keycloak changes, or role changes.
- Reusing `organization.AddressDto`; the family address has different fields and validation.
- Backfilling or migrating existing data.

## Decisions

### 1. Capability and package layout

Add the new capability at `membership/families` (public API at `membership/`, implementation at `membership/internal/`). Public records are `CreateFamilyDto`, `FamilyDto`, and `FamilyAddressDto`; internal types are `Family`, `FamilyAddress`, `FamilyRepository`, `FamilyMapper`, `FamilyService`, and `FamilyController`. No `FamilyNotFoundException` is added because this change exposes no read endpoint. The module already depends on `crosscutting` for `Audit`, `@OrganizationId`, and base exceptions; no new module dependency is introduced.

**Alternatives:** put families in `organization` (rejected: families belong to the membership domain alongside future people and groups); create a top-level `family` module (rejected: duplicates the membership domain and its schema); reuse `organization.AddressDto` (rejected: missing `state`/`country` and different validation).

### 2. Data model and migration

Add `src/main/resources/db/migration/membership/V2__create_families_table.sql` with a `membership.families` table:

- `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
- `church_id UUID NOT NULL`
- `name VARCHAR(200) NOT NULL`
- nullable address columns `line1 VARCHAR(200)`, `line2 VARCHAR(200)`, `city VARCHAR(100)`, `state VARCHAR(100)`, `country VARCHAR(60)`, `postal_code VARCHAR(20)`
- `wedding_date DATE`
- standard `created_at`, `updated_at`, `created_by`, `updated_by` audit columns

Add `idx_families_church_id` on `(church_id)` and `idx_families_unique_name_per_church` as a unique index on `(church_id, lower(name))`. There is no cross-schema foreign key to `organization.churches` (ADR-0009); `church_id` is indexed instead.

The `Family` entity uses `@Entity`, `@Table(schema = "membership", name = "families")`, `@EntityListeners(AuditingEntityListener.class)`, a UUID `@Id` with `GenerationType.UUID`, `churchId`, `name`, an `@Embedded FamilyAddress address`, `LocalDate weddingDate`, and `@Embedded Audit audit = new Audit()`. `FamilyAddress` is an `@Embeddable` value object with `line1`, `line2`, `city`, `state`, `country`, and `postalCode`. The entity extends nothing (ADR-0010).

**Alternatives:** store the address as JSONB (rejected: column-level constraints and queries are simpler with explicit columns); put `wedding_date` in a separate table (rejected: unnecessary for a single optional value); add a cross-schema FK (rejected by ADR-0009).

### 3. Request normalization and validation

`CreateFamilyDto` is a record with `name`, `@Valid FamilyAddressDto address`, and `LocalDate weddingDate`. `FamilyAddressDto` is a record with `line1`, `line2`, `city`, `state`, `country`, and `postalCode`. Both records normalize input in compact constructors before Bean Validation runs:

- `name` is trimmed; null stays null.
- Each address field is trimmed, and blank strings become null. This lets optional address fields be omitted, null, or empty while `@Size(min = 3)` still rejects genuinely short non-empty values.
- `line1` and `city` use `@NotBlank` plus `@Size`; optional fields use only `@Size` with their maxima and minima.
- `name` uses `@NotBlank` and `@Size(min = 3, max = 200)` on the normalized value.
- `weddingDate` is a `LocalDate` with an explicit `@JsonFormat(pattern = "yyyy-MM-dd")` contract.

Nested validation produces field paths such as `address.line1` and `address.city`, which `GlobalExceptionHandler` already returns as `fieldErrors` (ADR-0013). A malformed date is rejected by Jackson before Bean Validation and returns 400 through `ResponseEntityExceptionHandler`.

**Alternatives:** trim in the service (rejected: Bean Validation would run on untrimmed values and could accept a name that becomes too short after trimming); custom constraints (rejected: compact-constructor normalization is simpler and keeps the DTO immutable); `@Pattern` for optional address fields (rejected: less readable than normalizing blanks to null).

### 4. API contract

Expose only `POST /membership/families`. The controller method takes `@RequestBody @Valid CreateFamilyDto dto` and `@OrganizationId UUID churchId`, returns `ResponseEntity<FamilyDto>` (ADR-0014), and sets `Location` to `/membership/families/{id}`. No `@PreAuthorize` is used: `SecurityConfig` already requires authentication for every non-documentation request, and the requirement is any authenticated user rather than a specific role.

`FamilyDto` contains `id`, `churchId`, `name`, `address`, `weddingDate`, `createdBy`, `createdAt`, `updatedBy`, and `updatedAt`. `FamilyAddressDto` is reused for the response address. A request-supplied `churchId` is ignored because `CreateFamilyDto` has no such component and Spring Boot ignores unknown JSON properties by default, matching the existing membership status behavior.

OpenAPI annotations document the 201, 400, 401, 409, and 500 responses and the global `bearer-jwt` security requirement (ADR-0015).

**Alternatives:** require `ADMIN` (rejected: the requirement is any authenticated church user); accept `churchId` in the body (rejected: the JWT is the only tenant source, ADR-0004); return a bare DTO (rejected by ADR-0014).

### 5. Service behavior

`FamilyService` is package-private, class-level `@Transactional(readOnly = true)`, with `@Transactional` on `create`. The create flow:

1. Pre-check `existsByChurchIdAndNameIgnoreCase(churchId, dto.name())` and throw `ConflictException` with conflict type `name` when a duplicate exists.
2. Map the DTO to a `Family`, set `churchId` from the JWT, and leave the audit to `AuditingEntityListener`/`AuditorProvider`.
3. Call `repository.saveAndFlush(entity)` so a concurrent duplicate surfaces inside the method.
4. Catch `DataIntegrityViolationException`, inspect the most specific cause for `idx_families_unique_name_per_church`, and rethrow a `ConflictException`; other constraint failures also become a generic `ConflictException`, matching the membership status pattern.
5. Log only the generated id and church id. The family name is personal data and is not logged.

The pre-check uses `IgnoreCase` while the index uses `lower(name)`; both express case-insensitive equality, consistent with the existing membership status service.

**Alternatives:** rely only on the database index (rejected: the friendly pre-check handles the normal case and the flush handles the race); load and compare entities (rejected: unnecessary for creation); use `save` without a flush (rejected: a constraint violation would surface at commit time and become a 500).

### 6. Tenant scoping and 404 semantics

The tenant is always the JWT church (ADR-0003, ADR-0004). Creation never accepts or derives a church from the request, so a caller cannot create a family for another church. This change exposes no family read endpoint, so there is no cross-tenant read path. Any future family read must use a church-scoped repository query (for example `findByChurchIdAndId`) and throw `NotFoundException` for a family outside the caller's church, never `ForbiddenException` (ADR-0005). The existing membership status `getById` is the reference pattern.

**Alternatives:** return 403 for cross-tenant access (rejected by ADR-0005); expose a scoped GET now (rejected by the request's non-goal).

### 7. Audit and side effects

User-created families use the existing `AuditingEntityListener` and `AuditorProvider`, so `createdBy`/`updatedBy` come from the authenticated principal and the timestamps are server-generated. The service depends only on `FamilyRepository`; it has no person or identity dependency, so creating a family cannot create or link a person.

**Alternatives:** set audit fields manually (rejected: duplicates the existing auditing mechanism); publish a family-created event (rejected: no consumer and no requirement).

### 8. Testing

Use the four levels from ADR-0016:

- **Unit:** DTO normalization and Bean Validation, mapper field and audit mapping, and service behavior (church scoping, duplicate conflict, constraint-name mapping, no other repository interaction).
- **Integration (real PostgreSQL):** MockMvc with `JwtTestDsl` for 201 with minimal and full payloads, `Location`, audit fields, 400 name/address/date failures, 401, 201 for a role-less JWT, 409 for a duplicate in the same church, 201 for the same name in another church, ignored request `churchId`, and no row persisted after a rejected request.
- **Repository/database:** the unique `(church_id, lower(name))` index rejects a case-variant duplicate and the `church_id` column has no cross-schema foreign key.
- **OpenAPI:** only `POST /membership/families` is documented for families, and a GET request returns no family data.
- **ArchUnit and Modulith:** the new entity, controller, and `@OrganizationId` parameters satisfy all eight rules, and `ApplicationModules.verify()` remains acyclic.

Membership tests do not require Keycloak.

**Alternatives:** H2 (rejected by ADR-0016); mocking the controller layer (rejected: the integration level is the project standard for endpoints).

## Risks / Trade-offs

- **Unique names conflict with the original acceptance criterion** → The user chose uniqueness; the spec, error contract, and tests use 409 and the "duplicates allowed" criterion is intentionally dropped. Reviewers should confirm this decision.
- **Case-insensitive pre-check versus expression index** → `IgnoreCase` and `lower(name)` must stay aligned; the repository test inserts a case variant directly to prove the index, and the integration test proves the 409 path.
- **Optional address fields accepted as empty strings** → Normalizing blanks to null in the DTO keeps `@Size` meaningful; the response returns null rather than the submitted empty string, which is documented in the spec.
- **Malformed date has no field-level `fieldErrors` entry** → Jackson rejects it before Bean Validation; the spec requires only 400 for that case. A custom deserializer is deferred.
- **No cross-schema foreign key** → Orphaned families are possible if a church is deleted. Consistent with ADR-0009 and the existing membership statuses; tracked by the existing cross-schema integrity gap.
- **No person model yet** → The "no person is created" guarantee is enforced by the service's dependency set and verified by a unit test that only the family repository is used.
- **No read endpoint** → The 404-not-403 rule cannot be exercised for families until a read endpoint exists; the design records the required scoped-query pattern for that future change.

## Migration Plan

1. Add `src/main/resources/db/migration/membership/V2__create_families_table.sql`.
2. Deploy; Flyway creates the table, audit columns, and indexes. No backfill is needed.
3. Rollback: drop `membership.families` and remove the family classes. No other module depends on them.
4. No event registry or Keycloak changes are required.

## Open Questions

None.
