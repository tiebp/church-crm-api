# ADR-0014: Enforce architecture with ArchUnit rules

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

Conventions documented only in prose drift over time, especially when new
contributors (human or AI) add entities, controllers, or endpoints. We wanted
architectural rules to fail the build rather than be discovered in review.

The rules must cover entity design, organization-scope type safety, and
controller response contracts, while allowing legitimate exceptions for
infrastructure and framework classes.

## Decision

Use **ArchUnit** (JUnit 5) to enforce eight rules in `ArchitectureTest`, with
custom `ArchCondition` implementations where the DSL is insufficient:

1. Every `@Entity` (outside `crosscutting`, excluding Modulith's
   `EventPublication`) has an `@Embedded Audit` field (`AuditRule`).
2. Every `@Id` field on an entity is `UUID`.
3. The `Audit` class is `@Embeddable`.
4. Entities do not use inheritance (`InheritanceRule`).
5. The embedded `Audit` field is named exactly `audit`.
6. Every entity has `@EntityListeners(AuditingEntityListener.class)`
   (`AuditingEntityListenerRule`).
7. Every `@OrganizationId` parameter is `UUID` (`OrganizationIdRule`).
8. Every REST endpoint method returns `ResponseEntity`
   (`ControllerResponseRule`).

The rules run in the normal test suite and therefore in CI. The
`OrganizationIdRule` also has dedicated positive/negative unit tests.

## Consequences

### Positive

- Violations fail fast, locally and in CI.
- Rules are executable documentation of the entity/controller conventions.
- New contributors get immediate feedback instead of review comments.
- The rules are small and easy to extend.

### Negative / trade-offs

- ArchUnit checks structure, not behavior or module dependency direction.
- Rules can produce false positives and require exception lists (for example,
  `crosscutting`, `EventPublication`).
- The rules add test-suite time and a dependency.
- Some conventions (DTO immutability, no entity leakage, package-private
  services) are not enforced yet.

### Risks and follow-ups

- Add a rule forbidding imports of another module's `.internal` package.
- Add a rule that controllers do not return entities.
- Keep `ApplicationModules.verify()` (module boundaries) and ArchUnit (design
  rules) complementary; neither replaces the other.
- When adding a rule, add a focused test that proves it catches a violation
  (as done for `OrganizationIdRule`).

## Alternatives considered

- **Code review only** — rejected: inconsistent and slow.
- **Checkstyle/PMD** — useful for style, but poor at semantic/type rules like
  "embedded field named audit".
- **Spring Modulith verification only** — covers module dependencies but not
  entity/controller conventions.
- **No enforcement** — rejected: conventions would drift.

## Implementation references

- `src/test/java/org/churchcrm/churchcrmapi/arch/ArchitectureTest.java`
- `src/test/java/org/churchcrm/churchcrmapi/arch/rules/AuditRule.java`
- `src/test/java/org/churchcrm/churchcrmapi/arch/rules/AuditingEntityListenerRule.java`
- `src/test/java/org/churchcrm/churchcrmapi/arch/rules/ControllerResponseRule.java`
- `src/test/java/org/churchcrm/churchcrmapi/arch/rules/InheritanceRule.java`
- `src/test/java/org/churchcrm/churchcrmapi/arch/rules/OrganizationIdRule.java`
- `src/test/java/org/churchcrm/churchcrmapi/arch/rules/OrganizationIdRuleTest.java`
- `pom.xml` — `archunit-junit5`
