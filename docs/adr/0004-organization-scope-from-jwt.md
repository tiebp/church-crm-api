# ADR-0004: Resolve tenant scope from the JWT via `@OrganizationId`

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

Every organization-scoped endpoint needs the caller's tenant id. Reading it
manually from the `SecurityContext` or `Jwt` in every controller method is
repetitive and easy to get wrong. Accepting it from a request parameter or
header would let a client claim a different tenant.

The tenant id must come from a trusted source: the signed JWT.

## Decision

Expose a custom parameter annotation, `@OrganizationId`, and resolve it from the
JWT with a Spring MVC `HandlerMethodArgumentResolver`:

- The resolver requires a `JwtAuthenticationToken`.
- It reads the `organization_id` claim.
- It rejects missing/blank claims and malformed UUIDs with
  `UnauthorizedException` (HTTP 401).
- The resolved parameter type is always `UUID`.
- The resolver is registered in `WebConfig`.
- An ArchUnit rule enforces that every `@OrganizationId` parameter is a `UUID`.

Controllers declare the tenant explicitly and pass it to services:

```java
public ResponseEntity<ChurchDto> getChurch(
        @PathVariable UUID id,
        @OrganizationId UUID organizationId) { ... }
```

## Consequences

### Positive

- Tenant extraction is centralized and consistent.
- Controllers stay declarative; no JWT plumbing in business code.
- Type safety: the claim is parsed to `UUID` once.
- Missing or malformed claims fail closed with 401.
- Tests can build tokens with `JwtTestDsl`.

### Negative / trade-offs

- Endpoints annotated with `@OrganizationId` only work for JWT-authenticated
  requests; they cannot be used for anonymous or machine endpoints that lack
  the claim.
- The annotation name is coupled to the claim name `organization_id`.
- The resolver throws a security-package exception (`UnauthorizedException`)
  from a web concern; the exception is still handled centrally.

### Risks and follow-ups

- If the claim is renamed or moved, the resolver and Terraform mapper must
  change together.
- Consider a `@CurrentUser`/principal resolver for user id if needed later.

## Alternatives considered

- **Read the `Jwt` in each controller** — rejected: repetitive and error-prone.
- **Accept `organizationId` as a query/path parameter** — rejected: insecure;
  the client could request another tenant.
- **Read from a request header** — rejected: not signed by the identity
  provider.
- **A Spring Security `@AuthenticationPrincipal` wrapper** — viable, but a
  dedicated annotation reads better at the call site.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/web/OrganizationId.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/security/OrganizationIdArgumentResolver.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/security/WebConfig.java`
- `src/test/java/org/churchcrm/churchcrmapi/arch/ArchitectureTest.java`
  (`organizationIdParametersShouldBeUuid`)
- `src/test/java/org/churchcrm/churchcrmapi/utils/JwtTestDsl.java`
- `infra/keycloak/main.tf` — `organization_id` claim mapper
