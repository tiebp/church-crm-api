# ADR-0009: Use PostgreSQL schemas and Flyway as the schema source of truth

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

The application needs a relational store with strong consistency for churches,
congregations, users, and business constraints (unique hostnames, per-church
unique usernames/emails, one main congregation per church). Modules should own
their data without sharing tables.

Schema changes must be reviewable, repeatable, and applied consistently across
local, CI, and deployed environments.

## Decision

Use **PostgreSQL** with **Flyway** migrations and **one schema per module**:

- `organization` schema: `churches`, `congregations`.
- `identity` schema: `users`.
- Hibernate `ddl-auto: none`; Flyway is the only source of schema truth.
- Migrations live under `src/main/resources/db/migration/{module}/`.
- Business rules are enforced in the database where practical:
  - `churches.host_name` unique.
  - Unique `(username, church_id)` and `(email, church_id)` in `identity.users`.
  - Partial unique index ensuring at most one main congregation per church:
    `CREATE UNIQUE INDEX ... ON organization.congregations(church_id) WHERE is_main = true;`
- `spring.flyway.baseline-on-migrate: true` for existing databases.
- Spring Modulith's event publication registry uses its own JDBC schema,
  initialized via `spring.modulith.events.jdbc.schema-initialization.enabled`.

## Consequences

### Positive

- Schema changes are versioned, reviewable, and reproducible.
- Database constraints protect invariants even if application checks are
  bypassed or racy.
- Schema-per-module reinforces module ownership and simplifies future
  extraction.
- `ddl-auto: none` prevents accidental schema drift from entities.

### Negative / trade-offs

- Every entity change requires a migration; development is slightly slower.
- Migrations across modules must be coordinated for a release.
- No foreign key from `identity.users.church_id` to `organization.churches.id`
  (deliberate cross-schema/module boundary), so orphaned users are possible.
- Integration tests need a real PostgreSQL instance; H2 is not used.

### Risks and follow-ups

- Add a cross-schema integrity strategy (FK, event-driven check, or periodic
  reconciliation) for `users.church_id`.
- Review index duplication in the identity migration (several indexes are
  declared twice).
- Consider per-module Flyway history tables if modules are ever split.

## Alternatives considered

- **Hibernate `ddl-auto: update`** — rejected: unsafe and non-deterministic in
  production.
- **Single shared schema** — rejected: weakens module ownership and complicates
  extraction.
- **Liquibase** — viable, but Flyway is simpler and already integrated with
  Spring Boot.
- **Database per module** — deferred: stronger isolation but adds operational
  and transactional complexity.

## Implementation references

- `src/main/resources/db/migration/organization/V1__create_churches_and_congregations_tables.sql`
- `src/main/resources/db/migration/identity/V1__create_users_table.sql`
- `src/main/resources/application.yml` — `spring.jpa.hibernate.ddl-auto`,
  `spring.flyway.*`, `spring.modulith.events.jdbc.*`
- `pom.xml` — `spring-boot-starter-flyway`,
  `flyway-database-postgresql`
- `docker-compose.yml` — PostgreSQL service and multi-database init script
- `infra/pg/create-multiple-postgresql-databases.sh`
