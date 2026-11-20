# ADR-0013: Standardize error responses with RFC 9457 ProblemDetail

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

Clients need consistent, machine-readable error responses across all endpoints.
Ad-hoc error handling (returning strings, entities, or Spring's default error
body) produces inconsistent shapes and can leak implementation details such as
stack traces, SQL errors, or resource existence.

## Decision

Use **RFC 9457 (Problem Details for HTTP APIs)** via Spring's `ProblemDetail`
and a single global handler:

- Base exception types live in `crosscutting.web`:
  - `ValidationException` → 400
  - `NotFoundException` → 404
  - `ConflictException` → 409
  - `ForbiddenException` → 403
  - `UnauthorizedException` (security package) → 401
- `GlobalExceptionHandler` (`@RestControllerAdvice`, extends
  `ResponseEntityExceptionHandler`) maps each type to a `ProblemDetail` with
  `title`, `detail`, `type` (`about:blank`), and a `timestamp` property.
- Extra, non-sensitive properties are added where useful:
  `fieldErrors`, `conflictType`, `conflictValue`, `requiredPermission`,
  `resource`.
- Spring's `MethodArgumentNotValidException` is converted to a 400 with
  `fieldErrors`.
- `AccessDeniedException` is mapped to a generic 403.
- A catch-all `Exception` handler returns a generic 500 and logs the full stack
  trace server-side.
- 404 details are sanitized to `"<ResourceType> not found"`; 401 details are
  sanitized to `"Authentication failed"`.

## Consequences

### Positive

- One error shape for every endpoint and every client.
- Field-level validation feedback is preserved for form UIs.
- Sensitive internals are not exposed; details are logged server-side.
- Consistent with the 404-not-403 policy (ADR-0005).

### Negative / trade-offs

- Sanitized 404s omit the requested id, which can slow client debugging.
- The 401 body is intentionally generic, so token problems look the same to
  clients.
- Adding a new exception type requires updating the handler and its tests.
- `timestamp` is a custom property rather than a standard Problem Details
  member.

### Risks and follow-ups

- Ensure new exception types extend one of the base types so they are handled
  consistently.
- Do not include stack traces, SQL, or secrets in `detail` or custom properties.
- Consider a stable `type` URI per error category instead of `about:blank`.

## Alternatives considered

- **Custom error envelope** — rejected: reinvents Problem Details and requires
  custom client handling.
- **Spring Boot default error response** — rejected: inconsistent shape and
  less control over sanitization.
- **Return 200 with an error payload** — rejected: breaks HTTP semantics and
  client retry logic.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/web/internal/GlobalExceptionHandler.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/web/ValidationException.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/web/NotFoundException.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/web/ConflictException.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/web/ForbiddenException.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/security/UnauthorizedException.java`
