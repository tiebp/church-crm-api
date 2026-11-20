# ADR-0015: Document the API with SpringDoc OpenAPI and bearer JWT

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

The API is consumed by a separate UI (`church-cms-ui`) and by integrators. The
team needs an always-current description of endpoints, request/response
schemas, validation rules, and authentication requirements. Maintaining a
separate hand-written specification would drift from the code.

## Decision

Generate OpenAPI documentation from the code with **SpringDoc**:

- `springdoc-openapi-starter-webmvc-ui` is on the classpath.
- `OpenApiConfig` defines API info and a global `bearer-jwt` HTTP security
  scheme.
- DTOs and records carry `@Schema` descriptions and examples.
- Controllers carry `@Operation`, `@ApiResponses`, `@Parameter`, and
  `@SecurityRequirement` annotations.
- Swagger UI is available at `/swagger-ui.html`; the raw spec at `/api-docs`.
- Spring Security permits the documentation endpoints without authentication.
- Swagger UI is configured with the `church-cms-ui` OAuth client (PKCE) so
  developers can authenticate interactively.

## Consequences

### Positive

- Documentation is generated from the same code that serves requests.
- Validation constraints and examples are visible to consumers.
- The bearer-JWT requirement is explicit in the spec and the UI.
- No separate spec artifact to keep in sync.

### Negative / trade-offs

- Annotations add noise to DTOs and controllers.
- Documentation can still drift if annotations are wrong or missing.
- The spec is not contract-first; generated clients are not part of the build.
- A global security requirement is applied even to endpoints that permit
  anonymous access (currently only docs are anonymous).

### Risks and follow-ups

- Consider generating a committed OpenAPI artifact in CI to detect breaking
  changes.
- Ensure new endpoints include `@Operation`/`@ApiResponses`; there is no
  ArchUnit rule for this yet.
- The Swagger UI OAuth client secret is configured in `application.yml`; move
  it to a secret for non-local environments.

## Alternatives considered

- **Contract-first OpenAPI** — deferred: would enable generated clients but
  adds a spec-authoring step and tooling.
- **Hand-written Markdown/API docs** — rejected: drifts quickly.
- **No documentation** — rejected: unacceptable for external consumers.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/openapi/OpenApiConfig.java`
- `src/main/resources/application.yml` — `springdoc.*`
- `src/main/java/org/churchcrm/churchcrmapi/organization/internal/OrganizationController.java`
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/UserController.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/security/SecurityConfig.java`
  (permitted docs paths)
- `pom.xml` — `springdoc-openapi-starter-webmvc-ui`
