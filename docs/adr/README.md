# Architecture Decision Records

This directory records the significant architectural decisions behind the
Church CRM API. Each record explains **why** a decision was made, the context
that constrained it, and the consequences developers must live with.

The records describe the system **as implemented**. Where the current code
carries known debt, that debt is recorded under *Consequences* rather than
hidden.

## Format

We use a lightweight [Nygard-style](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
template:

- **Status** — Accepted, Superseded, Deprecated, or Proposed
- **Context** — the forces at play
- **Decision** — what we chose
- **Consequences** — positive outcomes, trade-offs, and risks
- **Alternatives considered** — what we rejected and why
- **Implementation references** — the code that embodies the decision

## Status legend

| Status | Meaning |
|--------|---------|
| Accepted | The decision is in force. |
| Accepted (provisional) | In force, but expected to be revisited. |
| Proposed | Under discussion; not yet implemented. |
| Superseded | Replaced by a later ADR. |
| Deprecated | No longer relevant. |

## Index

| ADR | Title | Status | Area |
|-----|-------|--------|------|
| [0001](0001-modular-monolith-with-spring-modulith.md) | Adopt a modular monolith with Spring Modulith | Accepted | Architecture |
| [0002](0002-public-api-and-internal-packages.md) | Separate public module API from `.internal` implementation | Accepted | Architecture |
| [0003](0003-church-as-organization-tenant.md) | Model each church as the tenant/organization | Accepted | Domain |
| [0004](0004-organization-scope-from-jwt.md) | Resolve tenant scope from the JWT via `@OrganizationId` | Accepted | Security |
| [0005](0005-return-404-for-cross-tenant-access.md) | Return 404 (not 403) for cross-tenant access | Accepted | Security |
| [0006](0006-keycloak-oauth2-resource-server.md) | Delegate authentication to Keycloak as OAuth2 resource server | Accepted | Security |
| [0007](0007-keycloak-user-provisioning-and-mirroring.md) | Provision Keycloak users and mirror them in `identity` | Accepted | Identity |
| [0008](0008-temporary-default-password.md) | Use a temporary default password for provisioned users | Accepted (provisional) | Identity |
| [0009](0009-postgres-flyway-schema-per-module.md) | Use PostgreSQL schemas and Flyway as the schema source of truth | Accepted | Persistence |
| [0010](0010-entity-conventions-and-auditing.md) | Standardize entities: UUID PKs, embedded Audit, composition | Accepted | Persistence |
| [0011](0011-immutable-dtos-and-mapstruct.md) | Use immutable record DTOs and MapStruct mapping | Accepted | API |
| [0012](0012-domain-events-for-cross-module-workflows.md) | Use domain events for cross-module workflows | Accepted | Architecture |
| [0013](0013-rfc9457-problem-details.md) | Standardize error responses with RFC 9457 ProblemDetail | Accepted | API |
| [0014](0014-archunit-architecture-rules.md) | Enforce architecture with ArchUnit rules | Accepted | Quality |
| [0015](0015-openapi-documentation.md) | Document the API with SpringDoc OpenAPI and bearer JWT | Accepted | API |
| [0016](0016-testing-strategy.md) | Test at four levels: unit, integration, module, architecture | Accepted | Quality |
| [0017](0017-build-and-delivery.md) | Deliver with Maven, Docker, GitHub Actions, and Terraform | Accepted | Delivery |

## How to add a decision

1. Copy the structure of an existing record.
2. Use the next free number and a kebab-case title.
3. Record the decision **as made**, including rejected alternatives.
4. Add the record to the index above.
5. If a decision changes, do not rewrite history: mark the old ADR
   `Superseded by ADR-XXXX` and create the new record.

## Known gaps and candidate future ADRs

The following concerns are not yet decided or implemented. They are listed here
so they are not lost; promote one to an ADR when it is addressed.

- **CORS** for the `church-cms-ui` client (currently no CORS configuration).
- **Health and observability**: no Spring Boot Actuator or metrics endpoint.
- **Pagination and list endpoints**: only create/get-by-id exist today.
- **Event reliability**: no explicit retry policy, dead-letter handling, or
  externalization for `ChurchCreated`/`UserCreated`.
- **Secret management**: default secrets are committed in `.env`,
  `application.yml`, and `terraform.tfvars`.
- **Cross-schema integrity**: `identity.users.church_id` has no foreign key to
  `organization.churches.id`.
- **API versioning and idempotency** for write endpoints.
- **Rate limiting and abuse protection** at the edge.
- **Soft delete / archiving** strategy for churches and users.
- **Keycloak image pinning**: `infra/keycloak` builds from `latest`.
