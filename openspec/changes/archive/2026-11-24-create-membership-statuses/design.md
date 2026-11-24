# Design

## Context

See `proposal.md` for motivation. The existing `organization` and `identity` modules establish the conventions this change follows: public DTOs at `{module}/`, implementation in `{module}.internal/` (ADR-0002), schema-per-module Flyway migrations (ADR-0009), UUID ids with an embedded `Audit` (ADR-0010), immutable record DTOs with MapStruct (ADR-0011), and cross-module events through the Modulith JDBC registry (ADR-0012). `ChurchCreated` is already published by `ChurchService` and consumed by `identity`, so `membership` can add a second asynchronous consumer without changing `organization`.

The tenant is the church (ADR-0003); the JWT `organization_id` claim is the church id (ADR-0004); cross-tenant reads return 404 (ADR-0005). This change keeps the existing `ADMIN` role; the planned `SYSADMIN`/`CHURCH_ADMIN` split is out of scope. No address fields are introduced.

## Goals / Non-Goals

**Goals:**
- Add a `membership` module with a `membership_statuses` table and organization-scoped create/read endpoints.
- Seed six defaults per church from `ChurchCreated`, idempotently.
- Enforce per-church case-insensitive code uniqueness and at most one default in the database.
- Follow ADR-0009/0010/0011/0012/0014 and the existing module and test patterns.

**Non-Goals:**
- PATCH/DELETE status endpoints.
- Backfill for existing churches.
- POST readiness validation that defaults exist.
- `SYSADMIN`/`CHURCH_ADMIN` role split or Keycloak/Terraform changes.
- UI work.
- A new cross-module event.
- Address fields (not applicable to this entity).

## Decisions

### 1. Module and package layout

Create `membership/` for public DTOs (`MembershipStatusDto`, `CreateMembershipStatusDto`) and `membership/internal/` for the entity, repository, mapper, service, controller, seeder, and `MembershipStatusNotFoundException`. The module depends on `organization` for the public `ChurchCreated` event and on `crosscutting` for `Audit`, `@OrganizationId`, and the base exceptions. `organization` does not depend on `membership`, so `ApplicationModules.verify()` stays acyclic.

**Alternatives:** put statuses in `organization` (rejected: statuses belong to the future membership domain, alongside members and families); put them in `identity` (rejected: identity owns users, not membership configuration).

### 2. Data model and migration

Use a `membership_statuses` table with `id UUID` PK, `church_id UUID NOT NULL`, `code VARCHAR(50)`, `label VARCHAR(100)`, `description VARCHAR(500)`, `sort_order INTEGER`, `is_default BOOLEAN`, `active BOOLEAN`, and the standard `created_at`/`updated_at`/`created_by`/`updated_by` audit columns. Add `(church_id, sort_order)` for listing, a unique index on `(church_id, lower(code))` for case-insensitive uniqueness, and a partial unique index on `(church_id) WHERE is_default = true` for the single-default rule. There is no cross-schema foreign key to `organization.churches` (ADR-0009); `church_id` is indexed instead.

The entity uses `Boolean isDefault` rather than a primitive, matching the `Congregation.isMain` precedent and avoiding Lombok/MapStruct property-name mismatches. It uses `@GeneratedValue(strategy = GenerationType.UUID)`, `@Embedded Audit audit = new Audit()`, and no inheritance (ADR-0010).

**Alternatives:** a fixed status enum (rejected: churches must be able to define custom statuses); a separate defaults table (rejected: unnecessary complexity); a cross-schema FK (rejected by ADR-0009; the orphaned-row follow-up already applies to `identity.users.church_id`).

### 3. API contract

Expose `POST /membership/statuses`, `GET /membership/statuses`, and `GET /membership/statuses/{id}`. `POST` requires `ADMIN`; the two `GET`s require authentication but no specific role. Every controller method takes `@OrganizationId UUID churchId` and returns `ResponseEntity<T>` (ADR-0014). The create request is a `CreateMembershipStatusDto` record with `code`, `label`, `description`, `sortOrder`, and `isDefault`; the response is a `MembershipStatusDto` record with the entity fields plus audit fields. `POST` returns 201; validation failures return 400; unauthenticated returns 401; non-admin returns 403; conflicts return 409; missing or cross-church reads return 404 (ADR-0005).

**Alternatives:** nested `/churches/{id}/statuses` (rejected: the tenant comes from the JWT and the existing API uses flat organization-scoped paths such as `/users`); accepting `churchId` in the body (rejected: the JWT is the only tenant source and prevents cross-tenant writes).

### 4. Service behavior

`create` pre-checks `existsByChurchIdAndCodeIgnoreCase` and, when `isDefault` is true, `existsByChurchIdAndIsDefaultTrue` to return friendly 409s. It maps the DTO, sets `churchId`, sets `active=true`, applies `sortOrder=0`/`isDefault=false` when omitted, then calls `saveAndFlush`. `saveAndFlush` is required because `GlobalExceptionHandler` has no `DataIntegrityViolationException` handler; catching the constraint violation inside the method maps the race to 409 instead of a commit-time 500. The catch inspects the constraint name to distinguish the code index from the default index and throws the matching `ConflictException`.

`getById` uses `findByChurchIdAndId(churchId, id)` and throws `MembershipStatusNotFoundException` when absent, so cross-church access is a 404 without a separate comparison. `list` uses `findByChurchIdOrderBySortOrderAsc` and includes inactive statuses. The service is package-private, class-level `@Transactional(readOnly = true)`, with `@Transactional` on `create`.

**Alternatives:** pre-checks only (rejected: racy); a global `DataIntegrityViolationException` handler (rejected: conflict semantics are module-specific and the existing pattern uses `ConflictException`); loading the entity and comparing `churchId` manually (rejected: the scoped query is simpler and still satisfies ADR-0005).

### 5. Default seeding

Add a package-private `MembershipStatusSeeder` with `@ApplicationModuleListener` in `membership.internal`. It calls a repository native bulk insert for the six defaults: `member` (10), `regular_attender` (20), `guest` (30, default), `non_attender` (40), `excluded` (50), `disciplined` (60). The insert uses `gen_random_uuid()`, `CURRENT_TIMESTAMP`, and `created_by`/`updated_by = 'system'`, followed by `ON CONFLICT DO NOTHING`. The listener logs the inserted count and rethrows failures so the Modulith registry retries (ADR-0012).

`ON CONFLICT DO NOTHING` without a conflict target handles both the code index and the partial default index, so a custom default created before seeding does not fail the listener. The trade-off is that the guest default may be skipped in that race, leaving the custom default in place.

**Alternatives:** `saveAll` with an existence check (rejected: racy and more code); one insert per row (rejected: unnecessary round trips); JPA entities with manually set audit fields (rejected: the native upsert is simpler and explicitly sets the system actor).

### 6. Audit

User-created statuses use the existing `AuditingEntityListener` and `AuditorProvider`; the entity embeds `Audit`. Seeded defaults set `'system'` explicitly in the native insert because `@ApplicationModuleListener` runs without a security context. A future ADR should define system-generated audit attribution and actor propagation in events.

**Alternatives:** make `AuditorProvider` return `'system'` when unauthenticated (rejected: changes global behavior and needs the ADR first); set the audit fields on JPA entities and `saveAll` (rejected: the native upsert is simpler).

### 7. Testing

Use the four levels from ADR-0016: service unit tests for defaults and conflict mapping; controller integration tests with MockMvc and the existing JWT DSL; repository/database tests for both unique indexes; a `@ApplicationModuleTest` for the seeder; and ArchUnit plus `ApplicationModules.verify()`. Membership tests do not need Keycloak. Use the real PostgreSQL test setup.

**Alternatives:** H2 (rejected by ADR-0016); Keycloak for membership tests (unnecessary).

## Risks / Trade-offs

- **Race between seeding and the first admin request** → The admin cannot authenticate until Keycloak provisioning completes, and seeding is six local inserts. Accepted as tech debt; a future provisioning marker should replace the implicit assumption.
- **Custom default created before seeding** → `ON CONFLICT DO NOTHING` skips the guest default, leaving the custom default. Accepted; the POST does not validate defaults.
- **No cross-schema FK** → Orphaned statuses are possible if a church is deleted. Consistent with ADR-0009; the existing cross-schema integrity follow-up applies.
- **`ADMIN` conflates sysadmin and church admin** → A church admin can still call `POST /churches` until the role split. Out of scope; tracked as tech debt.
- **Expression index alignment** → The unique index uses `lower(code)` and the repository check uses `IgnoreCase`; they must stay aligned.
- **Native SQL** → Less type-safe than JPA; confined to the seeder and covered by integration tests.
- **Extra flush on create** → `saveAndFlush` adds one flush per create; negligible.

## Migration Plan

1. Add `src/main/resources/db/migration/membership/V1__create_membership_statuses_table.sql`.
2. Deploy; Spring Modulith runs the module migration and creates the schema, table, audit columns, and indexes.
3. Churches created after deployment receive defaults through `ChurchCreated`. Existing churches are not backfilled (non-goal).
4. Rollback: drop the `membership` schema/table and remove the module. No other module depends on it.

## Open Questions

None.
