# ADR-0001: Adopt a modular monolith with Spring Modulith

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

The Church CRM API serves several distinct concerns: organization management
(churches and congregations), identity management (users and Keycloak
provisioning), and shared cross-cutting concerns (auditing, security, web
error handling, OpenAPI). These concerns have different lifecycles and data
ownership.

The team is small, the domain is still being discovered, and the system is
deployed as a single API. Introducing independently deployed services would
add network calls, distributed transactions, service discovery, and operational
overhead before the domain boundaries are proven.

At the same time, an unstructured layered monolith would let code from one
concern reach into another freely, making future extraction expensive.

## Decision

Build the API as a **modular monolith** using **Spring Modulith**:

- Each direct subpackage of `org.churchcrm.churchcrmapi` is a module
  (`organization`, `identity`, `crosscutting`).
- Module boundaries are verified in tests with
  `ApplicationModules.of(ChurchCrmApiApplication.class).verify()`.
- Cross-module communication uses Spring application events rather than direct
  calls to internal classes.
- Module documentation is generated with `Documenter` (PlantUML and module
  docs) from the same `ApplicationModules` model.
- The application remains a single Spring Boot deployable.

## Consequences

### Positive

- One deployable and one database keep local development and CI simple.
- Module boundaries are explicit and **verified**, not just conventional.
- In-process events give loose coupling without message-broker infrastructure.
- If a module later needs extraction, its public API and event contracts are
  already isolated.

### Negative / trade-offs

- All modules share one JVM, one database instance, and one release cadence.
- A module cannot scale independently.
- Event publication is in-process (with a JDBC registry for durability), not a
  true distributed messaging system.

### Risks and follow-ups

- Module verification only catches structural violations; discipline is still
  required for behavioral coupling.
- Extraction to services would require externalizing events (see ADR-0012).

## Alternatives considered

- **Layered monolith** — rejected: no enforceable boundaries; cross-concern
  coupling grows silently.
- **Microservices** — rejected: operational and consistency cost is not
  justified at the current team size and domain maturity.
- **Separate Maven modules per bounded context** — deferred: stronger
  isolation, but more build overhead; can be adopted incrementally because the
  package boundaries already exist.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/ChurchCrmApiApplication.java`
- `src/test/java/org/churchcrm/churchcrmapi/ChurchCrmApiApplicationTests.java`
  (`moduleTests`, `writeDocumentation`)
- `pom.xml` — `spring-modulith-starter-jdbc`, `spring-modulith-events-api`,
  `spring-modulith-runtime`
- `src/main/resources/application.yml` — `spring.modulith.*`
