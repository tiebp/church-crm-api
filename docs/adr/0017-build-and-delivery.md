# ADR-0017: Deliver with Maven, Docker, GitHub Actions, and Terraform

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Church CRM team
- **Supersedes:** —
- **Superseded by:** —

## Context

Developers and CI need a reproducible environment with the API, PostgreSQL, and
Keycloak. Keycloak configuration (realm, clients, roles, groups, claim mappers)
is non-trivial and must not be clicked together by hand. The build must produce
a runnable artifact and run integration tests.

## Decision

Standardize build, infrastructure, and delivery:

- **Build**: Maven with the Maven Wrapper (`./mvnw`), Java 21, Spring Boot
  parent, annotation processors for Lombok and MapStruct.
- **Container image**: multi-stage `Dockerfile`:
  - `build` — `mvn install -Dmaven.test.skip=true`
  - `test` — runs `mvn clean verify`
  - `publish` — builds the deployable jar
  - `final` — `eclipse-temurin:21` runtime image running `service.jar`
- **Local environment**: `docker-compose.yml` with PostgreSQL, Keycloak, a
  Terraform service, and an `integration-tests` service; `Makefile` targets
  `up`, `down`, `configure-keycloak`, and `test`.
- **CI**: GitHub Actions (`.github/workflows/ci.yml`) starts dependencies with
  `make up`, runs integration tests in Compose, prints logs on failure, and
  cleans up.
- **Keycloak as code**: Terraform (`mrparkers/keycloak` provider) provisions the
  realm, user profile, API/UI clients, audience mapper, organization claim
  mapper, service-account `manage-users` role, default admin user, roles
  (`ADMIN`, `USER`), and groups (`ADMINS`, `USERS`).

## Consequences

### Positive

- One command (`make up`) brings up a working local/CI environment.
- Keycloak configuration is versioned and reproducible.
- The Docker image is self-contained and uses the same build as CI.
- Integration tests run against the same dependency versions as local
  development.

### Negative / trade-offs

- No continuous deployment or environment promotion is defined yet; CI only
  verifies.
- Secrets and default credentials are committed in `.env`, `application.yml`,
  `terraform.tfvars`, and `variables.tf`.
- Terraform state management is not addressed (local state).
- Keycloak is built from `latest`, so builds are not fully reproducible.
- The `integration-tests` Compose service depends on external healthchecks and
  can be slow.

### Risks and follow-ups

- Introduce a secret manager and remove default secrets from the repository.
- Pin the Keycloak image tag/digest.
- Configure remote Terraform state and separate per-environment variables.
- Add a deployment pipeline and image tagging/publishing (currently
  `publish` stage exists but no registry push is configured).
- Consider Testcontainers to simplify CI dependency orchestration.

## Alternatives considered

- **Gradle** — rejected: the project already uses Maven and the wrapper.
- **Jib / Buildpacks** — viable alternatives for image builds; the multi-stage
  Dockerfile is explicit and already works.
- **Manual Keycloak setup** — rejected: not reproducible and error-prone.
- **Ansible/Helm for Keycloak** — deferred: Terraform fits the
  provision-once-per-environment model.
- **Kubernetes manifests** — deferred: no orchestration requirement yet.

## Implementation references

- `pom.xml`, `mvnw`, `mvnw.cmd`
- `Dockerfile`
- `docker-compose.yml`
- `Makefile`
- `.github/workflows/ci.yml`
- `infra/keycloak/main.tf`, `variables.tf`, `outputs.tf`, `terraform.tfvars`
- `infra/keycloak/Dockerfile`, `Terraform.Dockerfile`, `script.sh`
- `infra/pg/create-multiple-postgresql-databases.sh`
- `.env`
