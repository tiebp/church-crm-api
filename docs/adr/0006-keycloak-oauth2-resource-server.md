# ADR-0006: Delegate authentication to Keycloak as OAuth2 resource server

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

The API needs authentication, role-based authorization, user lifecycle
management, password policies, and eventually SSO. Building and operating an
identity provider is a large security surface that is not the product's core
value.

The product already has a browser UI (`church-cms-ui`) and an API client
(`church-cms-api`). Users are organized into groups (`ADMINS`, `USERS`) and
carry an `organization-id` attribute.

## Decision

Use **Keycloak** as the identity provider and configure Spring Security as an
**OAuth2 resource server**:

- The API validates JWTs against Keycloak's issuer/JWK set
  (`spring.security.oauth2.resourceserver.jwt.*`).
- `JwtConverter` maps roles from the `resource_access` claim for both
  `church-cms-api` and `church-cms-ui` clients into `ROLE_*` authorities.
- Method security (`@EnableMethodSecurity`) drives `@PreAuthorize` checks
  (`hasRole('ADMIN')`, etc.).
- Sessions are stateless; CSRF is disabled for the API.
- Swagger/OpenAPI endpoints are permitted without authentication.
- A `spring.security.enabled` property can disable security for local
  development and some integration tests.
- Keycloak realms, clients, roles, groups, the organization claim mapper, and a
  default admin user are provisioned with **Terraform**.

## Consequences

### Positive

- No password storage or credential handling in the application database.
- Industry-standard OIDC/OAuth2 flows, token rotation, and account management.
- Roles and groups are managed centrally in Keycloak and provisioned as code.
- The API remains stateless and horizontally scalable.

### Negative / trade-offs

- Keycloak is a hard runtime dependency for authenticated flows.
- Role names and client ids are coupled to Keycloak configuration
  (`church-cms-api`, `church-cms-ui`, `ADMIN`, `USER`).
- The `spring.security.enabled=false` escape hatch must never be enabled in
  production.
- There is currently no CORS configuration for the UI and no Actuator/health
  endpoint.

### Risks and follow-ups

- Client secrets and the default user password are committed as defaults (see
  ADR-0017); move them to a secret manager.
- Consider validating audience and adding token revocation/back-channel logout.
- The UI client's redirect URIs are hardcoded to `localhost:3000` in Terraform.

## Alternatives considered

- **Build authentication in-house** — rejected: high security and maintenance
  burden.
- **Auth0/Okta** — rejected for now: Keycloak is self-hostable and already
  provisioned as code.
- **Session-based authentication** — rejected: the API is stateless and
  consumed by a SPA.
- **Opaque tokens with introspection** — rejected: JWT validation is simpler
  and avoids a per-request introspection call.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/security/SecurityConfig.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/security/JwtConverter.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/security/SecurityProperties.java`
- `src/main/resources/application.yml` — `spring.security.*`
- `infra/keycloak/main.tf` — realm, clients, roles, groups, mappers
- `.github/workflows/ci.yml` — Keycloak started for integration tests
