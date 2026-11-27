# Proposal

## Why

Families are the household unit that later person and member features must attach to, but the system has no way to create one explicitly. If family creation were left implicit, person creation would either create families as a side effect or model people without a household. Introducing an explicit family resource now keeps those later workflows simple and consistent.

This change adds a church-scoped family capability to the `membership` module. It follows ADR-0002 through ADR-0005 for module layout, tenant identity, JWT organization scoping, and 404-not-403 semantics; ADR-0009 through ADR-0011 for Flyway migrations, entity conventions, and immutable DTOs with MapStruct; and ADR-0013 and ADR-0014 for error responses and ArchUnit rules.

## What Changes

- Add a `Family` entity and an embedded family address value object, persisted in the `membership` schema.
- Add `POST /membership/families`, available to any authenticated user, returning HTTP 201 with the created family and `Location: /membership/families/{id}`.
- Derive `churchId` exclusively from the JWT `organization_id` claim; ignore any `churchId` in the request body.
- Accept a required `name` (3-200 characters after trimming), an optional `address`, and an optional ISO-8601 `weddingDate`.
- When an address is supplied, require `line1` and `city`; allow `line2`, `state`, `country`, and `postalCode` to be omitted, `null`, or empty.
- Enforce case-insensitive family name uniqueness per church in the database and return HTTP 409 for duplicates.
- Keep person creation and family linking out of this change; a family may exist with no people.
- **Non-goals:**
  - No person creation, person linking, or automatic family creation.
  - No `GET`, list, `PUT`, `PATCH`, or `DELETE` family endpoints.
  - No congregation association, member counts, or family relationships beyond the fields in this change.
  - No cross-module events published or consumed.
  - No Keycloak, Terraform, or role changes.
  - No UI work.

## Capabilities

### New Capabilities

- `membership/families`: Church-scoped creation of family records, including optional address and wedding date, validation, per-church name uniqueness, tenant scoping, and audit fields.

### Modified Capabilities

- None. The existing `membership` capability covers membership statuses only and its requirements are unchanged.

## Impact

- **Module:** new public DTOs in `membership/` and new entity, embeddable address, repository, mapper, service, and controller in `membership/internal/`.
- **Database:** new `membership.families` table via Flyway migration `membership/V2__create_families_table.sql` (V1 already creates the membership statuses table), with audit columns and a unique index on `(church_id, lower(name))`; no cross-schema foreign key to `organization.churches` per ADR-0009.
- **API:** new `POST /membership/families`; no other family endpoints.
- **Security:** any authenticated user may create a family; creation is always scoped to the JWT church; a request-supplied `churchId` is ignored; future read paths must use church-scoped queries and return 404 for cross-tenant access per ADR-0005.
- **Keycloak:** no changes.
- **Events:** none.
- **Tests:** new unit, integration, OpenAPI, and ArchUnit coverage; existing tests remain unchanged.
