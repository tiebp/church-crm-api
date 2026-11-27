# Tasks

## 1. Schema and domain foundation

- [x] 1.1 Create `src/main/resources/db/migration/membership/V2__create_families_table.sql` with the `membership.families` table, nullable address columns (`line1`, `line2`, `city`, `state`, `country`, `postal_code`), `wedding_date`, audit columns, `idx_families_church_id`, and the unique `idx_families_unique_name_per_church` index on `(church_id, lower(name))`; do not add a cross-schema foreign key. Verify the migration applies against the local PostgreSQL test database and the table, columns, and indexes exist.
- [x] 1.2 Add `FamilyAddress` (`@Embeddable` with `line1`, `line2`, `city`, `state`, `country`, `postalCode`) and `Family` (`@Entity`, `@Table(schema = "membership", name = "families")`, `@EntityListeners(AuditingEntityListener.class)`, UUID `@Id` with `GenerationType.UUID`, `churchId`, `name`, `@Embedded FamilyAddress address`, `LocalDate weddingDate`, `@Embedded Audit audit = new Audit()`, no inheritance) in `membership.internal`. Verify `./mvnw clean compile` and `./mvnw test -Dtest=ArchitectureTest`.
- [x] 1.3 Add the public records `CreateFamilyDto`, `FamilyAddressDto`, and `FamilyDto` in `membership`. Normalize in compact constructors: trim `name`; trim each address field and convert blanks to null; annotate `name` with `@NotBlank` and `@Size(min = 3, max = 200)`, `line1`/`city` with `@NotBlank` and their `@Size` limits, optional address fields with their `@Size` limits, `address` with `@Valid`, and `weddingDate` with `@JsonFormat(pattern = "yyyy-MM-dd")`. Verify validation unit tests cover trimmed name length, too-long name, blank name, omitted/null/empty optional address fields, missing `line1`/`city`, too-short optional address fields, and valid/malformed dates.
- [x] 1.4 Add `FamilyMapper` with `toEntity(CreateFamilyDto)` (ignoring `id`, `audit`, `churchId`, and `weddingDate` only if handled explicitly), `toDto(Family)` flattening `audit.*` into `createdBy`/`createdAt`/`updatedBy`/`updatedAt`, and address mapping between `FamilyAddressDto` and `FamilyAddress`. Verify MapStruct generates the implementation during `./mvnw clean compile` and a mapper unit test covers entity fields, nested address, and audit mapping.
- [x] 1.5 Add package-private `FamilyRepository extends JpaRepository<Family, UUID>` with `boolean existsByChurchIdAndNameIgnoreCase(UUID churchId, String name)`. Verify a repository test proves the unique index rejects a case-variant duplicate in the same church, allows the same name in another church, and confirms `church_id` has no cross-schema foreign key.

## 2. Family creation API

- [x] 2.1 Implement package-private `FamilyService` with class-level `@Transactional(readOnly = true)` and `@Transactional` on `create`: pre-check `existsByChurchIdAndNameIgnoreCase` and throw `ConflictException` with conflict type `name`; map the DTO, set `churchId` from the JWT, and `saveAndFlush`; catch `DataIntegrityViolationException` and map `idx_families_unique_name_per_church` to `ConflictException`; log only the generated id and church id. Verify unit tests cover church scoping, duplicate pre-check, constraint-race mapping, audit left to the listener, and that only `FamilyRepository` is used (no person side effect).
- [x] 2.2 Add `FamilyController` with `POST /membership/families`, no `@PreAuthorize`, `@RequestBody @Valid CreateFamilyDto`, `@OrganizationId UUID churchId`, `ResponseEntity<FamilyDto>`, `Location: /membership/families/{id}`, and OpenAPI annotations for 201/400/401/409/500 and `bearer-jwt`. Verify `./mvnw clean compile` and `./mvnw test -Dtest=ArchitectureTest`.
- [x] 2.3 Add MockMvc integration tests with `JwtTestDsl` covering: 201 with only a name; 201 with a full address and wedding date; 201 with a role-less JWT; 201 and `Location` header; response `churchId` and audit fields; 400 for short/long/blank name, missing `address.line1`/`address.city`, too-short optional address fields, and malformed `weddingDate`; 401 without a JWT; 409 for a case-variant duplicate in the same church; 201 for the same name in another church; request `churchId` ignored; and no row persisted after a rejected request. Verify the integration tests pass against PostgreSQL.
- [x] 2.4 Add an OpenAPI test asserting only `POST /membership/families` exists for families and no list/get/update/delete operation is documented, plus a MockMvc test asserting `GET /membership/families/{id}` returns no family data and is not 403. Verify the tests pass.

## 3. Verification

- [x] 3.1 Run `./mvnw clean compile` and verify it succeeds.
- [x] 3.2 Run `./mvnw test -Dtest=ArchitectureTest` and verify all 8 rules pass.
- [x] 3.3 Run the membership test suite (`./mvnw test -Dtest='*Family*Test,Membership*Test'`) and verify it passes.
- [x] 3.4 Run the full `./mvnw test` suite with PostgreSQL and Keycloak available and verify it passes.
- [x] 3.5 Review the change against `specs/membership/families/spec.md` and confirm every scenario has a corresponding test, including the user-approved unique-name decision and the `POST /membership/families` path.

## Workflow follow-up

- Archive the change after review and verification.
- Confirm the archived `membership/families` spec is present and the main specs validate.
