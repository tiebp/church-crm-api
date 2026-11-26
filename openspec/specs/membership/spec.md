# membership Specification

## Purpose

Defines how churches configure membership statuses, including creation, retrieval, default seeding on church creation, per-church uniqueness, and tenant isolation.

## Requirements

### Requirement: Create membership status
The system SHALL allow an authenticated church administrator to create a custom membership status for the church identified by the JWT `organization_id` claim via `POST /membership/statuses`.

#### Scenario: Successful creation
- **GIVEN** an `ADMIN` JWT whose `organization_id` is church X and a valid creation request
- **WHEN** the client calls `POST /membership/statuses`
- **THEN** the system creates the status for church X and returns HTTP 201

### Requirement: Create status response
The system SHALL return the created membership status with its id, code, label, description, sortOrder, isDefault, active, and audit fields. When omitted, `sortOrder` SHALL default to 0, `isDefault` to false, and `active` to true.

#### Scenario: Defaults applied
- **GIVEN** a valid creation request that omits `sortOrder` and `isDefault`
- **WHEN** the status is created
- **THEN** the response contains HTTP 201, `sortOrder=0`, `isDefault=false`, `active=true`, and non-null `createdAt` and `updatedAt`

#### Scenario: Explicit default
- **GIVEN** a valid creation request with `isDefault=true` and no existing default status in the church
- **WHEN** the status is created
- **THEN** the response contains HTTP 201 and `isDefault=true`

### Requirement: Create status authorization
The system SHALL require the `ADMIN` role to create a membership status and SHALL return HTTP 401 for unauthenticated requests and HTTP 403 for authenticated users without `ADMIN`.

#### Scenario: Unauthenticated request
- **GIVEN** a creation request without a JWT
- **WHEN** the client calls `POST /membership/statuses`
- **THEN** the system returns HTTP 401

#### Scenario: Authenticated without ADMIN
- **GIVEN** a valid `USER` JWT
- **WHEN** the client calls `POST /membership/statuses`
- **THEN** the system returns HTTP 403

### Requirement: Create status validation
The system SHALL validate creation input. `code` MUST be 1-50 characters matching `^[a-z][a-z0-9_]*$`; `label` MUST be 1-100 characters and not blank; `description` MUST be at most 500 characters; `sortOrder` MUST be zero or greater.

#### Scenario: Invalid code
- **GIVEN** a creation request with an uppercase or malformed `code`
- **WHEN** the request is validated
- **THEN** the system returns HTTP 400 with a field error for `code`

#### Scenario: Invalid label
- **GIVEN** a creation request with a blank `label`
- **WHEN** the request is validated
- **THEN** the system returns HTTP 400 with a field error for `label`

#### Scenario: Invalid description
- **GIVEN** a creation request with a `description` longer than 500 characters
- **WHEN** the request is validated
- **THEN** the system returns HTTP 400 with a field error for `description`

#### Scenario: Invalid sort order
- **GIVEN** a creation request with a negative `sortOrder`
- **WHEN** the request is validated
- **THEN** the system returns HTTP 400 with a field error for `sortOrder`

### Requirement: Code uniqueness
The system SHALL enforce case-insensitive uniqueness of `code` within a church and SHALL return HTTP 409 when a duplicate exists.

#### Scenario: Duplicate code in the same church
- **GIVEN** church X already has a status with code `member`
- **WHEN** an administrator creates a status with code `Member` or `member` for church X
- **THEN** the system returns HTTP 409 with a conflict indicating `code`

#### Scenario: Same code in another church
- **GIVEN** church X has a status with code `member` and church Y does not
- **WHEN** an administrator for church Y creates a status with code `member`
- **THEN** the system returns HTTP 201

### Requirement: Default status constraint
The system SHALL allow at most one default status per church and SHALL return HTTP 409 when an administrator tries to create a second default.

#### Scenario: Second default rejected
- **GIVEN** church X already has a default status
- **WHEN** an administrator creates a status with `isDefault=true` for church X
- **THEN** the system returns HTTP 409 with a conflict indicating `isDefault`

### Requirement: Organization-scoped creation
The system SHALL assign every created status to the church in the JWT `organization_id` claim and SHALL ignore any `churchId` supplied in the request body.

#### Scenario: Request churchId is ignored
- **GIVEN** an `ADMIN` JWT for church X and a request body containing `churchId` for church Y
- **WHEN** the status is created
- **THEN** the status belongs to church X and is not visible to church Y

### Requirement: List membership statuses
The system SHALL return all membership statuses for the caller's church, including inactive statuses, ordered by `sortOrder` ascending. Any authenticated user in the church SHALL be able to list statuses.

#### Scenario: Ordered list
- **GIVEN** church X has statuses with `sortOrder` 30, 10, and 20
- **WHEN** an authenticated user for church X calls `GET /membership/statuses`
- **THEN** the system returns HTTP 200 with the statuses ordered 10, 20, 30

#### Scenario: Inactive statuses included
- **GIVEN** church X has an inactive status
- **WHEN** an authenticated user for church X lists statuses
- **THEN** the inactive status is included in the response

### Requirement: Get membership status by id
The system SHALL return a single membership status by id when it belongs to the caller's church and SHALL return HTTP 404 when it does not exist or belongs to another church. Any authenticated user in the church SHALL be able to read a status.

#### Scenario: Status found
- **GIVEN** a status in church X and an authenticated user for church X
- **WHEN** the user calls `GET /membership/statuses/{id}`
- **THEN** the system returns HTTP 200 with the status

#### Scenario: Status in another church
- **GIVEN** a status in church Y and an authenticated user for church X
- **WHEN** the user calls `GET /membership/statuses/{id}`
- **THEN** the system returns HTTP 404

### Requirement: Default status seeding
When a church is created, the system SHALL asynchronously seed six default statuses: `member` (sortOrder 10), `regular_attender` (20), `guest` (30, default), `non_attender` (40), `excluded` (50), and `disciplined` (60).

#### Scenario: Defaults created after church creation
- **GIVEN** a successful `ChurchCreated` event
- **WHEN** the event is processed
- **THEN** the church has six statuses with the specified codes and sort orders, and `guest` is the only default

### Requirement: Seeding idempotency
The default-status seeding SHALL be idempotent so that duplicate event deliveries or retries do not create duplicates or fail.

#### Scenario: Duplicate delivery
- **GIVEN** the defaults for a church have already been seeded
- **WHEN** the church creation event is delivered again
- **THEN** the church still has exactly six default statuses and no error is raised

### Requirement: Status audit
The system SHALL record `createdBy`, `createdAt`, `updatedBy`, and `updatedAt` for every membership status. User-created statuses SHALL use the authenticated administrator as the actor; seeded defaults SHALL use `system`.

#### Scenario: Seeded status audit
- **GIVEN** a seeded default status
- **WHEN** it is read
- **THEN** `createdBy` and `updatedBy` are `system`, and `createdAt` and `updatedAt` are set

#### Scenario: User-created status audit
- **GIVEN** a status created by an authenticated administrator
- **WHEN** it is read
- **THEN** `createdBy` is the administrator and `createdAt` and `updatedAt` are set

### Requirement: Membership schema migration
The system SHALL create a `membership` schema and `membership_statuses` table in Flyway migration `membership/V1__create_membership_statuses_table.sql`, including audit columns, a unique index on `(church_id, lower(code))`, and a partial unique index on `(church_id) WHERE is_default = true`.

#### Scenario: Migration creates schema and constraints
- **GIVEN** a database without the membership schema
- **WHEN** Flyway runs the membership migration
- **THEN** the schema, table, audit columns, and both unique indexes exist

#### Scenario: Database rejects duplicate code
- **GIVEN** the membership table exists
- **WHEN** a row with a duplicate `(church_id, lower(code))` is inserted directly
- **THEN** the database rejects the insert

#### Scenario: Database rejects a second default
- **GIVEN** a church already has a default status
- **WHEN** a second default row for the same church is inserted directly
- **THEN** the database rejects the insert

### Requirement: Cross-schema integrity
The `membership_statuses.church_id` column SHALL be indexed but SHALL NOT have a foreign key to `organization.churches`, consistent with ADR-0009.

#### Scenario: No cross-schema foreign key
- **GIVEN** the membership migration has run
- **WHEN** the schema is inspected
- **THEN** `church_id` is indexed and has no cross-schema foreign key
