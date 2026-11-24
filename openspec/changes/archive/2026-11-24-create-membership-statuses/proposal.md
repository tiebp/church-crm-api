# Proposal

## Why

Church admins need a consistent way to classify people by membership status, but the system currently has no status model. Without it, future member and family features would either hard-code status values or invent divergent lists per church.

This change introduces a church-scoped membership status capability with seeded defaults, following the module and tenant rules in ADR-0002 through ADR-0005, the persistence conventions in ADR-0009 through ADR-0012, and the ArchUnit conventions in ADR-0014. It consumes the existing `ChurchCreated` event (ADR-0012) so every new church starts with the same baseline.

## What Changes

- Add a new `membership` module with a `MembershipStatus` entity and a `membership` schema migration.
- Add `POST /membership/statuses` to create a custom status, requiring the `ADMIN` role and scoping the status to the caller's church from the JWT.
- Add `GET /membership/statuses` and `GET /membership/statuses/{id}` for authenticated, organization-scoped reads.
- Seed six default statuses per church when `ChurchCreated` is published: `member`, `regular_attender`, `guest` (default), `non_attender`, `excluded`, `disciplined`.
- Enforce per-church case-insensitive code uniqueness and at most one default status in the database.
- Mark seeded defaults with a `system` audit actor.
- Keep the existing `ADMIN` role for this change; the planned `SYSADMIN`/`CHURCH_ADMIN` split is deferred.
- **Non-goals:**
  - No PATCH or DELETE status endpoints.
  - No backfill for existing churches (the system is not in production).
  - No POST readiness check that the six defaults exist; accepted as tech debt.
  - No `SYSADMIN`/`CHURCH_ADMIN` role split or Keycloak/Terraform changes.
  - No UI work.
  - No new cross-module event.

## Capabilities

### New Capabilities

- `membership`: Church-scoped membership status configuration, including creation, retrieval, validation, and default-status seeding on church creation.

### Modified Capabilities

- None.

## Impact

- **New module:** `membership` with public DTOs and internal entity, repository, service, controller, mapper, seeder, and exception.
- **Database:** new `membership` schema and `membership_statuses` table via Flyway; no cross-schema foreign key to `organization.churches` per ADR-0009.
- **Events:** consumes the existing `organization.ChurchCreated` event; no new event is published.
- **Security:** `POST` requires `ADMIN`; all endpoints are organization-scoped via the JWT `organization_id` claim; cross-church reads return 404 per ADR-0005.
- **Keycloak:** no changes.
- **Tests:** new unit, integration, Modulith, and ArchUnit coverage; existing tests remain unchanged.
