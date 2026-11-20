# ADR-0010: Standardize entities: UUID PKs, embedded Audit, composition

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

Entity design had to be consistent across modules and support auditing,
distributed-friendly identifiers, and future extraction. Inheritance-based base
entities are a common source of coupling and are difficult to evolve across
module boundaries.

Audit data (who/when created/updated) is required for mutable entities and
should be populated automatically, not by every service.

## Decision

Adopt a uniform entity convention, enforced by ArchUnit:

- **UUID primary keys** (`@Id` of type `UUID`), generated with
  `GenerationType.UUID` for application-created entities. Exception: `User`
  uses the Keycloak-assigned UUID (no generation) and implements `Persistable`.
- **Embedded audit** field named exactly `audit`:
  `@Embedded private Audit audit = new Audit();`
- **`Audit` is `@Embeddable`** with `createdAt`, `updatedAt`, `createdBy`,
  `updatedBy`.
- **`@EntityListeners(AuditingEntityListener.class)`** on every entity (and on
  `Audit` itself, which is an experimental convenience).
- **No entity inheritance** — composition over inheritance; entities extend
  `Object`.
- JPA auditing is enabled by `JpaAuditingConfig`
  (`@EnableJpaAuditing(auditorAwareRef = "auditorProvider")`).
- `AuditorProvider` reads the current principal from the `SecurityContext` and
  returns empty when unauthenticated.

## Consequences

### Positive

- Consistent, predictable entity shape across modules.
- Audit fields are populated automatically on persist/update.
- UUIDs avoid collisions and are safe to expose in URLs and events.
- Composition keeps entities independent and easy to move between modules.
- The rules are executable: violations fail the build.

### Negative / trade-offs

- UUID primary keys are larger than `bigint` and can have index locality
  costs.
- `@EntityListeners` on `Audit` is unusual; entities still need their own
  annotation to be safe.
- `@ApplicationModuleTest` slices do not load `JpaAuditingConfig`; tests must
  import `TestAuditingConfig` or auditing silently does nothing.
- The `User` entity's externally assigned id and `isNew()` override are a
  special case that must be understood.

### Risks and follow-ups

- Keep the `Audit` field initialized (`= new Audit()`); otherwise embedding
  nulls can break auditing.
- When adding entities, always add a Flyway migration with matching audit
  columns.
- Consider Hibernate Envers if full change history (not just last-modified) is
  required later.

## Alternatives considered

- **`Long`/sequence ids** — rejected: less suitable for distributed systems and
  external exposure.
- **Abstract `BaseEntity` with audit fields** — rejected: violates composition
  over inheritance and couples modules.
- **Manual audit population in services** — rejected: repetitive and easy to
  forget.
- **Hibernate Envers** — deferred: heavier; current requirement is last
  modified metadata only.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/auditing/Audit.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/auditing/internal/JpaAuditingConfig.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/auditing/internal/AuditorProvider.java`
- `src/main/java/org/churchcrm/churchcrmapi/organization/internal/Church.java`
- `src/main/java/org/churchcrm/churchcrmapi/organization/internal/Congregation.java`
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/User.java`
- `src/test/java/org/churchcrm/churchcrmapi/arch/ArchitectureTest.java`
  (rules 1–6)
- `src/test/java/org/churchcrm/churchcrmapi/utils/TestAuditingConfig.java`
