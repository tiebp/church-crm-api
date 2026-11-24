# Tasks

## 1. Schema and contracts

- [x] 1.1 Create `src/main/resources/db/migration/membership/V1__create_membership_statuses_table.sql` with the `membership` schema, `membership_statuses` table, audit columns, `(church_id, sort_order)` index, unique `(church_id, lower(code))` index, and partial unique `(church_id) WHERE is_default = true` index. Verify the migration applies against the local PostgreSQL test database and the indexes exist.
- [x] 1.2 Add `MembershipStatus` in `membership.internal` with `@Entity`, `@Table(schema = "membership", name = "membership_statuses")`, `@EntityListeners(AuditingEntityListener.class)`, UUID `@Id` with `GenerationType.UUID`, `churchId`, `code`, `label`, `description`, `sortOrder`, `Boolean isDefault`, `Boolean active`, and `@Embedded Audit audit = new Audit()`. Verify `./mvnw clean compile` and `./mvnw test -Dtest=ArchitectureTest`.
- [x] 1.3 Add the public records `MembershipStatusDto` and `CreateMembershipStatusDto` in `membership` with the fields and Bean Validation annotations from the spec. Verify `./mvnw clean compile` and a unit test that rejects invalid code, blank label, long description, and negative sortOrder.
- [x] 1.4 Add `MembershipStatusMapper` with `toDto` and `toEntity`, ignoring `id`, `audit`, `churchId`, and `active` on create. Verify MapStruct generates the implementation during `./mvnw clean compile` and a mapper unit test covers audit field mapping.
- [x] 1.5 Add `MembershipStatusRepository` with `findByChurchIdOrderBySortOrderAsc`, `findByChurchIdAndId`, `existsByChurchIdAndCodeIgnoreCase`, `existsByChurchIdAndIsDefaultTrue`, and the native `seedDefaults(UUID)` bulk insert. Verify the repository compiles and an integration test proves the unique indexes reject a duplicate code and a second default.

## 2. Create and read API

- [x] 2.1 Add `MembershipStatusNotFoundException` extending `NotFoundException` with the membership status resource type. Verify the controller integration tests return 404 for a missing status.
- [x] 2.2 Implement `MembershipStatusService.list` and `getById` with church-scoped queries and 404 behavior. Verify unit tests cover ordering, inactive inclusion, and cross-church 404.
- [x] 2.3 Implement `MembershipStatusService.create` with code/default pre-checks, `sortOrder`/`isDefault` defaults, `active=true`, `saveAndFlush`, and constraint-name mapping to `ConflictException`. Verify unit tests cover 409 code conflict, 409 default conflict, and the constraint race.
- [x] 2.4 Add `MembershipStatusController` with `POST /membership/statuses` (`ADMIN`), `GET /membership/statuses`, and `GET /membership/statuses/{id}`, using `@OrganizationId UUID`, `ResponseEntity<T>`, `@Valid`, and OpenAPI annotations. Verify `./mvnw clean compile` and `./mvnw test -Dtest=ArchitectureTest`.
- [x] 2.5 Add controller integration tests with MockMvc and the JWT DSL for 201, 400 validation, 409 code/default, 401, 403, 404 cross-church, list ordering, and inactive inclusion. Verify the tests pass against PostgreSQL.
- [x] 2.6 Verify the OpenAPI document contains the three membership status paths and the create schema. Verify by loading `/api-docs` in the test context or asserting the generated JSON.

## 3. Default seeding

- [x] 3.1 Add `MembershipStatusSeeder` in `membership.internal` with `@ApplicationModuleListener` on `ChurchCreated`, calling `seedDefaults`, logging the inserted count, and rethrowing failures. Verify `./mvnw clean compile`.
- [x] 3.2 Add a `@ApplicationModuleTest` in `membership.internal` that publishes `ChurchCreated` and verifies six defaults, the `guest` default, and `createdBy`/`updatedBy = 'system'`. Verify the test passes against PostgreSQL.
- [x] 3.3 Extend the seeder test to deliver `ChurchCreated` twice and verify exactly six statuses and no error. Verify the idempotency test passes.
- [x] 3.4 Add a test for a custom default created before seeding and verify the upsert does not fail and leaves one default. Verify the test passes.

## 4. Verification

- [x] 4.1 Run `./mvnw clean compile` and verify it succeeds.
- [x] 4.2 Run `./mvnw test -Dtest=ArchitectureTest` and verify all 8 rules pass.
- [x] 4.3 Run the membership test suite (`./mvnw test -Dtest='Membership*Test'`) and verify it passes.
- [x] 4.4 Run the full `./mvnw test` suite with PostgreSQL and Keycloak available and verify it passes.
- [x] 4.5 Review the change against the spec and confirm every `#### Scenario:` has a corresponding test or manual verification.

## Workflow follow-up

- Archive the change after review and verification.
- Confirm the archived membership spec is present and the main specs validate.
