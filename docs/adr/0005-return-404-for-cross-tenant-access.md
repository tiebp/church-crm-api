# ADR-0005: Return 404 (not 403) for cross-tenant access

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

When an authenticated user requests a resource that belongs to another tenant,
the server can respond in several ways. A `403 Forbidden` confirms that the
resource exists but is not accessible; a `404 Not Found` hides its existence.

For a multi-tenant CRM, leaking the existence of churches, users, or future
records (members, donations) is itself an information disclosure. Attackers can
enumerate valid ids by comparing 403 and 404 responses.

## Decision

For **organization-scope mismatches**, return **404 Not Found** using
`NotFoundException` (or a subclass such as `ChurchNotFoundException`), never
`ForbiddenException`:

```java
if (!church.getId().equals(organizationId)) {
    throw new ChurchNotFoundException(churchId); // 404, not 403
}
```

`403 Forbidden` is reserved for **role/permission** failures (for example, a
non-admin calling an admin endpoint), which are handled by Spring Security and
`@PreAuthorize`.

The global exception handler also sanitizes 404 details to `"<ResourceType> not
found"` so the response does not echo the requested id.

## Consequences

### Positive

- Resource existence is not leaked across tenants.
- Consistent behavior across modules (`ChurchService`, `UserService`).
- Clients cannot distinguish "does not exist" from "belongs to someone else".

### Negative / trade-offs

- Legitimate users who mistype an id cannot tell whether the resource exists.
- Debugging tenant-access problems requires server logs, not the HTTP response.
- The API contract uses 404 for both missing and unauthorized resources, which
  can surprise integrators.

### Risks and follow-ups

- This is a security-through-obscurity control, not a substitute for correct
  authorization checks. Every scoped query must still verify ownership.
- Future list/search endpoints must not leak counts or metadata across tenants.

## Alternatives considered

- **403 Forbidden on mismatch** — rejected: leaks existence.
- **401 Unauthorized** — rejected: the caller is authenticated; the problem is
  authorization/ownership.
- **Empty 200 with null body** — rejected: ambiguous and harder to consume.
- **404 only for truly missing, 403 for mismatch** — rejected: the distinction
  is exactly what we want to hide.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/web/NotFoundException.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/web/ForbiddenException.java`
- `src/main/java/org/churchcrm/churchcrmapi/crosscutting/web/internal/GlobalExceptionHandler.java`
- `src/main/java/org/churchcrm/churchcrmapi/organization/internal/IChurchService.java`
- `src/main/java/org/churchcrm/churchcrmapi/organization/internal/ChurchNotFoundException.java`
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/IUserService.java`
- `src/test/java/org/churchcrm/churchcrmapi/identity/internal/UserServiceTest.java`
