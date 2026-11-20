# ADR-0003: Model each church as the tenant/organization

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

The system is multi-tenant: each church must only see and modify its own data.
The domain language mixes "church" and "organization", and the authentication
token carries an `organization_id` claim.

We needed a tenant identity that is stable, easy to propagate through JWTs, and
simple enough for the first version of the product.

## Decision

Treat a **church as the tenant**. There is no separate `Organization` aggregate:

- `Church` is the aggregate root for a tenant.
- `Church.id` **is** the organization identifier used in the `organization_id`
  JWT claim.
- `Congregation` belongs to a `Church` (`church_id`).
- `User.churchId` stores the tenant the user belongs to.
- Organization-scoped checks compare the JWT's organization id to
  `Church.id` or `User.churchId`.

The organization module owns church creation. When a church is created, the
organization id is the new church id, and the identity module provisions the
first (admin) user for that tenant.

## Consequences

### Positive

- One identifier flows from the JWT to every query; no mapping table between
  organizations and churches.
- Tenant isolation is straightforward: compare the claim to the entity's
  church/org id.
- The model matches the product's first use case: one church, one tenant.

### Negative / trade-offs

- A church cannot currently represent multiple independent legal organizations.
- Multi-campus or federated church structures are not modeled.
- Renaming or merging tenants is not supported.
- The organization id is effectively immutable because it is the church id.

### Risks and follow-ups

- If the product later needs an `Organization` that owns several churches, this
  decision must be superseded with a migration plan.
- Church creation is a bootstrap operation and is not organization-scoped in
  the same way as other writes (the admin creates the tenant).

## Alternatives considered

- **Separate `Organization` entity owning churches** — deferred: more flexible,
  but adds a mapping layer and complicates the JWT claim for no current benefit.
- **Keycloak Organizations feature as the tenant source** — not adopted: the
  application database still needs a tenant key for referential integrity and
  auditing.
- **Shared schema with a tenant discriminator on every table** — effectively
  what we do at the row level, but without a separate tenant table.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/organization/internal/Church.java`
- `src/main/java/org/churchcrm/churchcrmapi/organization/internal/Congregation.java`
- `src/main/java/org/churchcrm/churchcrmapi/organization/internal/IChurchService.java`
  (`getChurchById` compares `church.getId()` to `organizationId`)
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/User.java`
  (`churchId`)
- `infra/keycloak/main.tf` — `organization-id` user attribute and
  `organization_id` claim mapper
- `src/main/resources/db/migration/organization/V1__create_churches_and_congregations_tables.sql`
