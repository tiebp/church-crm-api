# ADR-0016: Test at four levels: unit, integration, module, architecture

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

The system combines HTTP endpoints, JWT-based security, PostgreSQL constraints,
Keycloak provisioning, and cross-module events. A single test style cannot
cover all of these: pure unit tests miss wiring and SQL, while full end-to-end
tests are slow and flaky.

## Decision

Use four complementary levels of testing:

1. **Unit tests** (`*Test.java`, package-private, Mockito):
   - entities/factories (`ChurchTest`), services (`UserServiceTest`),
     controllers with mocked services (`UserControllerTest`,
     `OrganizationControllerTest`).
2. **Integration tests** (`*IntegrationTest.java`, `@SpringBootTest` +
   `@AutoConfigureMockMvc`):
   - full HTTP stack against a real PostgreSQL and Keycloak;
   - security enabled/disabled per test via
     `@TestPropertySource(properties = "spring.security.enabled=...")`;
   - JWTs built with `JwtTestDsl` (organization claim + roles);
   - `@Transactional` rollback for most tests, with
     `Propagation.NOT_SUPPORTED` where async events must commit;
   - polling helpers (`awaitUser`) for eventual consistency.
3. **Module tests** (`@ApplicationModuleTest` + `Scenario`):
   - verify event publication/consumption within and across module slices
     (`ChurchCreatedEventTest`, `IdentityEventIntegrationTest`);
   - `TestAuditingConfig` is imported because module slices do not load the
     production auditing configuration.
4. **Architecture tests** (ArchUnit + `ApplicationModules.verify()`):
   - the eight design rules (ADR-0014) and Spring Modulith module boundaries.

Supporting tools: **Datafaker** for unique test data, `JwtTestDsl` for tokens,
`TestAuditingConfig` for slice auditing.

CI runs the integration suite with dockerized PostgreSQL and Keycloak
(`make up` then `docker compose up integration-tests`).

## Consequences

### Positive

- Fast feedback from unit tests; realistic confidence from integration tests.
- Security and organization scoping are exercised with real JWTs.
- Database constraints are validated against real PostgreSQL.
- Cross-module events are tested with Modulith's scenario API.
- Architecture rules prevent structural regressions.

### Negative / trade-offs

- Integration and module tests require live PostgreSQL and Keycloak; local and
  CI runs are heavier and slower.
- Testcontainers is **not** used; environment setup depends on Docker Compose.
- Event-driven provisioning requires polling and
  `Propagation.NOT_SUPPORTED`, which is easy to get wrong.
- Some module tests are duplicated (`IdentityEventIntegrationTest` vs
  `OnChurchCreatedIntegrationTest`).

### Risks and follow-ups

- Introduce Testcontainers to remove the external `make up` prerequisite.
- Ensure test data cleanup/isolation as the suite grows.
- Add contract tests for the UI/API boundary if the UI becomes a separate
  repository.
- Remove or consolidate duplicated event tests.

## Alternatives considered

- **H2 for integration tests** — rejected: does not exercise PostgreSQL-specific
  features (schemas, partial indexes).
- **Only unit tests** — rejected: misses wiring, security, and SQL.
- **Only end-to-end tests** — rejected: slow, flaky, and poor failure locality.
- **Testcontainers from the start** — deferred: Compose-based CI was already in
  place; Testcontainers is a likely improvement.

## Implementation references

- `src/test/java/org/churchcrm/churchcrmapi/organization/internal/ChurchTest.java`
- `src/test/java/org/churchcrm/churchcrmapi/identity/internal/UserServiceTest.java`
- `src/test/java/org/churchcrm/churchcrmapi/organization/internal/integration/ChurchCreationIntegrationTest.java`
- `src/test/java/org/churchcrm/churchcrmapi/identity/internal/integration/UserCreationIntegrationTest.java`
- `src/test/java/org/churchcrm/churchcrmapi/organization/internal/integration/ChurchCreatedEventTest.java`
- `src/test/java/org/churchcrm/churchcrmapi/identity/internal/integration/IdentityEventIntegrationTest.java`
- `src/test/java/org/churchcrm/churchcrmapi/utils/JwtTestDsl.java`
- `src/test/java/org/churchcrm/churchcrmapi/utils/TestAuditingConfig.java`
- `.github/workflows/ci.yml`
- `docker-compose.yml` — `integration-tests` service
