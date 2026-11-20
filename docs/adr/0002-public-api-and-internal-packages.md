# ADR-0002: Separate public module API from `.internal` implementation

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

In a modular monolith, other modules must be able to use a module's contracts
without depending on its entities, repositories, or service implementations.
Without a clear convention, code from one module leaks into another, and the
module boundary becomes cosmetic.

Java package-private visibility is the only language-level tool available
inside a single Maven module, so the layout must make the intended public
surface obvious.

## Decision

Use a consistent two-level package layout per module:

- **`{module}/`** (root package) — the public API:
  - request/response DTOs (`ChurchDto`, `CreateChurchDto`, `UserDto`, ...)
  - integration events (`ChurchCreated`, `UserCreated`)
  - named interfaces for shared contracts (`@NamedInterface`)
- **`{module}.internal/`** — the implementation:
  - JPA entities (`Church`, `Congregation`, `User`, `Address`)
  - repositories, services, mappers, controllers, exceptions
  - package-private where possible

Cross-cutting packages expose named interfaces so other modules can use only
the intended surface:

- `org.churchcrm.churchcrmapi.crosscutting.web` → `@NamedInterface("web")`
- `org.churchcrm.churchcrmapi.crosscutting.auditing` → `@NamedInterface("auditing")`

Implementation types are declared package-private unless a framework or test
requires otherwise (for example, controllers are public for Spring MVC and
`UserService` is package-private behind `IUserService`).

## Consequences

### Positive

- The public surface of each module is easy to find and review.
- Implementation can be refactored without breaking other modules.
- Spring Modulith can verify that modules only depend on allowed packages.
- DTOs and events are stable contracts; entities are not.

### Negative / trade-offs

- More packages and more indirection (interfaces plus implementations).
- Package-private types require tests to live in the same package.
- Controllers living in `.internal` means they are implementation details, not
  reusable module API.

### Risks and follow-ups

- The convention is partly enforced by `modules.verify()` and partly by review.
  There is no ArchUnit rule that forbids a module from importing another
  module's `.internal` package directly; consider adding one.

## Alternatives considered

- **Everything public** — rejected: no boundary, encourages entity reuse.
- **One Maven module per bounded context** — deferred: strongest isolation but
  heavier build; the current layout allows this migration later.
- **Hexagonal ports and adapters per module** — partially adopted in spirit
  (interfaces such as `IChurchService`, `IUserService`), but not formalized as
  a port/adapter structure.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/organization/` — public DTOs/events
- `src/main/java/org/churchcrm/churchcrmapi/organization/internal/` — entities,
  `ChurchService`, `OrganizationController`, `ChurchMapper`
- `src/main/java/org/churchcrm/churchcrmapi/identity/` — public DTOs/events
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/` — `User`,
  `UserService`, `UserController`, `UserMapper`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/web/package-info.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/auditing/package-info.java`
