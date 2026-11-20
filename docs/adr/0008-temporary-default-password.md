# ADR-0008: Use a temporary default password for provisioned users

- **Status:** Accepted (provisional)
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

Provisioned users (the first admin of a church and users created by an admin)
need an initial credential. There is no email delivery, invitation flow, or
self-service registration yet. Requiring an admin to set a unique password for
every user would complicate the provisioning flow and expose passwords to the
admin.

## Decision

Create every provisioned user in Keycloak with a **temporary password**
(`"password"`) that must be changed on first login:

- `CredentialRepresentation` type `PASSWORD`.
- Value is the literal `"password"`.
- `temporary = true` so Keycloak forces a reset.
- Users are placed in `ADMINS` or `USERS` according to the flow.
- Passwords are never stored in the application database.

This is recorded as **provisional**: it is acceptable for the current
development/demo stage but not for production.

## Consequences

### Positive

- Unblocks church and user provisioning without an email/invite system.
- Keycloak enforces the password change on first login.
- No password data enters the application database or logs.

### Negative / trade-offs

- **Security debt**: every new account initially shares a well-known password.
- Anyone who knows a username can attempt the default password before the
  first login.
- The temporary password is documented in the OpenAPI descriptions, making it
  public knowledge.
- Users are created with `emailVerified = true` even though no verification
  email was sent.

### Risks and follow-ups

- Replace with an invite email or a Keycloak "execute actions" email flow.
- Generate a random per-user temporary password and deliver it out of band.
- Do not enable `emailVerified = true` without actual verification.
- This ADR should be superseded before any production deployment.

## Alternatives considered

- **Invite email with a set-password link** — preferred long-term, but needs
  mail infrastructure and a token flow.
- **Admin sets the initial password** — rejected: exposes the password to the
  admin and increases support burden.
- **Self-registration** — rejected for now: users must be created within a
  tenant by an authorized admin.
- **Random generated password returned once in the API response** — better than
  a fixed value, but still requires secure delivery; a reasonable next step.

## Implementation references

- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/IUserService.java`
  (`setCredentials`)
- `src/main/java/org/churchcrm/churchcrmapi/identity/internal/UserController.java`
  (OpenAPI description mentions the temporary password)
- `src/main/java/org/churchcrm/churchcrmapi/organization/internal/OrganizationController.java`
  (OpenAPI description mentions the temporary password)
- `openspec/specs/identity/spec.md` — default password requirement
- `openspec/specs/organization/spec.md` — admin user creation requirement
