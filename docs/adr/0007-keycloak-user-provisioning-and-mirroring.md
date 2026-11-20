# ADR-0007: Provision Keycloak users and mirror them in `identity`

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

Keycloak owns credentials and authentication, but the application needs user
data for organization relationships, auditing, and queries (for example, "list
users in this church"). Storing only a Keycloak reference would require an
external call for every read; storing credentials locally would duplicate the
identity provider and increase risk.

Users are created in two flows:

1. When a church is created, its first admin must be provisioned.
2. An existing admin can create additional users for their tenant.

## Decision

Use the **Keycloak Admin Client** to create users and **mirror** them into the
`identity.users` table:

- The application calls Keycloak's Admin REST API with a service-account client
  (`church-cms-api`, client-credentials grant).
- The Keycloak user id returned in the `Location` header becomes the primary
  key of the mirrored `User` row.
- Group assignment is `ADMINS` for the first user of a church and `USERS` for
  users created through `POST /users`.
- The user's `organization-id` attribute is set in Keycloak so the
  `organization_id` claim can be emitted in JWTs.
- Because the id is assigned **before** the row is saved, `User` implements
  `Persistable<UUID>` and derives `isNew()` from `audit.createdAt == null`
  (instead of the default id-null check).
- Cross-module provisioning is event-driven (see ADR-0012): the organization
  module publishes `ChurchCreated`, and the identity module creates the admin
  user asynchronously.

## Consequences

### Positive

- Authentication stays in Keycloak; user relationships and audits stay in the
  application database.
- Reads (get by id/email) are local and fast.
- The Keycloak id is stable and usable as the local primary key.
- The `Persistable` override makes JPA auditing and `persist()` work correctly
  with externally assigned ids.

### Negative / trade-offs

- **Dual write**: Keycloak and PostgreSQL must both succeed. There is no
  distributed transaction; the current flow can leave an orphaned Keycloak user
  if the database save fails.
- Provisioning is eventually consistent; a created church may briefly have no
  mirrored admin user.
- `identity.users.church_id` has no foreign key to `organization.churches.id`
  (cross-schema/module boundary).
- The `isNew()` override is subtle and must be preserved when the entity
  changes.

### Risks and follow-ups

- Add compensation/cleanup for failed database saves after Keycloak creation.
- Consider an outbox or saga for reliable user provisioning.
- Add a reconciliation job for users that exist in Keycloak but not in the
  database (or vice versa).
- The hardcoded temporary password is tracked separately in ADR-0008.

## Alternatives considered

- **Keycloak-only, no local mirror** — rejected: poor query support and no
  local referential/audit data.
- **Application-managed credentials** — rejected: duplicates the identity
  provider and increases security risk.
- **Synchronous user creation inside church creation** — rejected: couples the
  modules and makes church creation fail when Keycloak is unavailable; the
  event-driven flow isolates the failure.
- **Store Keycloak id in a separate column with a generated PK** — rejected:
  two ids for one user; the Keycloak id is already unique and stable.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/User.java`
  (`Persistable`, `isNew`)
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/IUserService.java`
  (`createUser`, `createKeycloakUser`, group assignment)
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/UserRepository.java`
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/config/KeycloakConfig.java`
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/config/KeycloakProperties.java`
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/IdentityService.java`
- `src/main/resources/db/migration/identity/V1__create_users_table.sql`
- `infra/keycloak/main.tf` — service account and `manage-users` role
