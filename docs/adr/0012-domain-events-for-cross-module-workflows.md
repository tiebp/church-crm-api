# ADR-0012: Use domain events for cross-module workflows

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

Creating a church requires work in two modules: the organization module
persists the church and its main congregation, and the identity module
provisions the first admin user in Keycloak and mirrors it locally.

Calling the identity service directly from the organization service would
couple the modules, make church creation fail when Keycloak is unavailable, and
violate the module boundaries established in ADR-0001/0002.

## Decision

Use **Spring application events** for cross-module workflows, with Spring
Modulith's event infrastructure:

- The organization module publishes `ChurchCreated` via
  `ApplicationEventPublisher` after the church is saved.
- The identity module listens with
  `@ApplicationModuleListener` (`IdentityService.on(ChurchCreated)`).
- `ChurchCreated` and `UserCreated` live in the public module packages and are
  the cross-module contracts.
- The **JDBC event publication registry** (`spring-modulith-starter-jdbc`)
  persists publications so events are not lost if the listener fails; the
  registry schema is initialized automatically.
- Listeners are transactional and run after commit (`@ApplicationModuleListener`
  semantics), giving eventual consistency.

## Consequences

### Positive

- The organization module does not depend on identity internals.
- Church creation succeeds even if Keycloak is temporarily unavailable; user
  provisioning can be retried from the event registry.
- New consumers can subscribe to `ChurchCreated`/`UserCreated` without changing
  the publisher.
- The event registry provides durability and at-least-once delivery semantics.

### Negative / trade-offs

- **Eventual consistency**: immediately after church creation, the admin user
  may not exist yet. Tests must poll/wait (`awaitUser`).
- Listeners must be idempotent because delivery is at-least-once.
- The current listener catches exceptions and rethrows `RuntimeException`,
  which marks the publication for retry but has no explicit backoff or
  dead-letter policy.
- Event payloads become public contracts and are harder to change than internal
  method signatures.

### Risks and follow-ups

- Define an explicit retry/backoff and dead-letter/incomplete-event handling
  strategy.
- Add idempotency keys or existence checks in `IdentityService` to avoid
  duplicate users on redelivery.
- Consider externalizing events if modules are ever split into services.
- Remove the unused `IdentityCreated` placeholder or implement it.
- Consolidate the duplicated module-event tests
  (`IdentityEventIntegrationTest`, `OnChurchCreatedIntegrationTest`).

## Alternatives considered

- **Direct service call** — rejected: tight coupling and failure propagation.
- **Synchronous Spring events** — rejected: loses the durability and retry
  benefits of the Modulith registry.
- **Kafka/RabbitMQ** — deferred: operational overhead is not justified for a
  single deployable.
- **Transactional outbox implemented manually** — rejected: Spring Modulith's
  registry already provides this.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/organization/ChurchCreated.java`
- `src/main/java/org/churchcrm/churchcrmapi/organization/internal/IChurchService.java`
  (`eventPublisher.publishEvent(event)`)
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/IdentityService.java`
- `src/main/java/org/churchcrm/churchcrmapi/identity/UserCreated.java`
- `src/main/resources/application.yml` — `spring.modulith.events.jdbc.*`
- `pom.xml` — `spring-modulith-starter-jdbc`, `spring-modulith-events-api`
- `src/test/java/org/churchcrm/churchcrmapi/organization/internal/integration/ChurchCreatedEventTest.java`
- `src/test/java/org/churchcrm/churchcrmapi/identity/internal/integration/IdentityEventIntegrationTest.java`
