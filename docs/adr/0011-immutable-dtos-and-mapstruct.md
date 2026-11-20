# ADR-0011: Use immutable record DTOs and MapStruct mapping

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

API contracts must be stable and independent of JPA entities. Returning
entities directly couples the wire format to the database schema and risks
lazy-loading and accidental field exposure. Mutable DTO classes invite
inconsistent state.

Mapping between entities, DTOs, and events is repetitive and error-prone when
written by hand.

## Decision

Use **immutable Java records** for API contracts and **MapStruct** for mapping:

- Response DTOs are records (`ChurchDto`, `UserDto`, `AddressDto`).
- Create/request DTOs are records with Jakarta Bean Validation annotations
  (`@NotBlank`, `@Size`, `@Email`, `@Valid`) and OpenAPI `@Schema`
  annotations.
- Audit fields are flattened into response DTOs (`createdBy`, `createdAt`,
  `updatedBy`, `updatedAt`) from the embedded `Audit`.
- Create DTOs never contain `id` or audit fields; mappers ignore them.
- MapStruct mappers use `componentModel = "spring"` and explicit `@Mapping`
  rules for audit flattening and event construction.
- Domain events are also mapped with MapStruct where practical.
- Address is optional in creation DTOs; `null` means no address.

## Consequences

### Positive

- DTOs are immutable, thread-safe, and easy to reason about.
- The API contract is decoupled from the persistence model.
- MapStruct generates mapping code at compile time (fast, no reflection).
- Validation lives on the contract and is applied by `@Valid` in controllers.
- OpenAPI documentation is generated from the same records.

### Negative / trade-offs

- Records cannot be partially mutated; updates require constructing new
  instances.
- MapStruct mappings must be kept in sync with DTO/entity changes; unmapped
  fields fail at compile time (good) but require explicit `@Mapping`s.
- Flattening `Audit` requires repetitive `@Mapping` annotations on each mapper.
- Lombok and MapStruct annotation processors must both be configured (order
  matters).

### Risks and follow-ups

- `ChurchCreated` is a Lombok `@Value` class while `UserCreated` is a record;
  standardize on records for new events.
- Consider a shared `AuditDto` or mapping config to reduce repeated audit
  mappings.
- Validate that no entity is ever returned from a controller (currently
  enforced by convention and `ResponseEntity` rule, not by a type rule).

## Alternatives considered

- **Mutable DTO classes with Lombok** — rejected: less safe and less clear
  contracts.
- **Return JPA entities directly** — rejected: leaks schema and lazy-loading
  behavior.
- **Manual mapping** — rejected: repetitive and easy to miss fields.
- **ModelMapper / reflection-based mappers** — rejected: runtime cost and
  weaker compile-time safety.
- **JSON views / Jackson annotations on entities** — rejected: couples wire
  format to persistence.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/organization/ChurchDto.java`
- `src/main/java/org/churchcrm/churchcrmapi/organization/CreateChurchDto.java`
- `src/main/java/org/churchcrm/churchcrmapi/organization/AddressDto.java`
- `src/main/java/org/churchcrm/churchcrmapi/organization/internal/ChurchMapper.java`
- `src/main/java/org/churchcrm/churchcrmapi/identity/UserDto.java`
- `src/main/java/org/churchcrm/churchcrmapi/identity/CreateUserDto.java`
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/UserMapper.java`
- `pom.xml` — MapStruct and annotation processor configuration
