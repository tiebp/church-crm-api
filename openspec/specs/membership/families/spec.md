# Membership Families Specification

## Purpose

Defines how authenticated church users create family records in their own church, including optional address and wedding date, validation, per-church name uniqueness, tenant scoping, and audit fields.

## Requirements

### Requirement: Create family
The system SHALL allow any authenticated user to create a family for the church identified by the JWT `organization_id` claim via `POST /membership/families`.

#### Scenario: Successful creation with required fields only
- **GIVEN** a valid JWT whose `organization_id` is church X and a request body containing only a valid `name`
- **WHEN** the client calls `POST /membership/families`
- **THEN** the system creates the family for church X and returns HTTP 201

#### Scenario: Successful creation with full address and wedding date
- **GIVEN** a valid JWT for church X and a request with a valid `name`, a complete `address`, and an ISO-8601 `weddingDate`
- **WHEN** the client calls `POST /membership/families`
- **THEN** the system returns HTTP 201 with the address and wedding date stored and returned unchanged

### Requirement: Create family response
The system SHALL return HTTP 201 with the created family body and a `Location` header of `/membership/families/{id}`.

#### Scenario: Response body and location
- **GIVEN** a successful family creation
- **WHEN** the response is returned
- **THEN** the body contains the server-generated `id`, the JWT `churchId`, the submitted fields, and non-null audit fields, and the `Location` header is `/membership/families/{id}`

### Requirement: Create family authorization
The system SHALL require a valid JWT but no specific role. Requests without a valid JWT SHALL return HTTP 401 and SHALL persist nothing.

#### Scenario: Unauthenticated request
- **GIVEN** a creation request without a JWT
- **WHEN** the client calls `POST /membership/families`
- **THEN** the system returns HTTP 401 and no family is persisted

#### Scenario: Authenticated user without a church role
- **GIVEN** a valid JWT for church X that carries no church roles
- **WHEN** the client calls `POST /membership/families` with a valid request
- **THEN** the system returns HTTP 201

### Requirement: Organization-scoped creation
The system SHALL derive `churchId` only from the JWT `organization_id` claim, SHALL ignore any `churchId` supplied in the request body, and SHALL NOT create a family for another church.

#### Scenario: Request churchId is ignored
- **GIVEN** a JWT for church X and a request body containing `churchId` for church Y
- **WHEN** the family is created
- **THEN** the response `churchId` is church X and the family belongs to church X

### Requirement: Family name validation
The system SHALL require a non-blank `name` of 3-200 characters after trimming leading and trailing whitespace, and SHALL return HTTP 400 with a field-level error for `name` otherwise.

#### Scenario: Name too short after trimming
- **GIVEN** a creation request whose `name` is shorter than 3 characters after trimming
- **WHEN** the request is validated
- **THEN** the system returns HTTP 400 with a field error for `name`

#### Scenario: Name too long
- **GIVEN** a creation request whose `name` is longer than 200 characters
- **WHEN** the request is validated
- **THEN** the system returns HTTP 400 with a field error for `name`

#### Scenario: Blank name
- **GIVEN** a creation request whose `name` is null, empty, or whitespace only
- **WHEN** the request is validated
- **THEN** the system returns HTTP 400 with a field error for `name`

#### Scenario: Name is trimmed
- **GIVEN** a creation request whose `name` has leading or trailing whitespace
- **WHEN** the family is created
- **THEN** the response `name` contains the trimmed value

### Requirement: Family name uniqueness
The system SHALL enforce case-insensitive uniqueness of `name` within a church and SHALL return HTTP 409 when a duplicate exists. The same name SHALL be allowed in another church.

#### Scenario: Duplicate name in the same church
- **GIVEN** church X already has a family named `Doe`
- **WHEN** a user for church X creates a family named `doe`
- **THEN** the system returns HTTP 409 with a conflict indicating `name`

#### Scenario: Same name in another church
- **GIVEN** church X has a family named `Doe` and church Y does not
- **WHEN** a user for church Y creates a family named `Doe`
- **THEN** the system returns HTTP 201

#### Scenario: Database rejects a duplicate name
- **GIVEN** the `membership.families` table exists and church X has a family named `Doe`
- **WHEN** a row with the same `church_id` and a case variant of `Doe` is inserted directly
- **THEN** the database rejects the insert

### Requirement: Address validation
The system SHALL accept `address` as optional. When present, `line1` (3-200) and `city` (3-100) SHALL be required; `line2` (3-200), `state` (3-100), `country` (3-60), and `postalCode` (3-20) SHALL be optional and MAY be null or empty.

#### Scenario: Address omitted
- **GIVEN** a creation request with no `address`
- **WHEN** the family is created
- **THEN** the system returns HTTP 201 and the response `address` is null

#### Scenario: Complete valid address
- **GIVEN** a creation request with `line1`, `line2`, `city`, `state`, `country`, and `postalCode` all within their limits
- **WHEN** the family is created
- **THEN** the system returns HTTP 201 and the response contains every supplied address field

#### Scenario: Missing line1
- **GIVEN** a creation request with an `address` that has no `line1`
- **WHEN** the request is validated
- **THEN** the system returns HTTP 400 with a field error for `address.line1`

#### Scenario: Missing city
- **GIVEN** a creation request with an `address` that has no `city`
- **WHEN** the request is validated
- **THEN** the system returns HTTP 400 with a field error for `address.city`

#### Scenario: Optional address field too short
- **GIVEN** a creation request with a supplied optional address field shorter than its minimum length
- **WHEN** the request is validated
- **THEN** the system returns HTTP 400 with a field error for that address field

#### Scenario: Empty optional address fields
- **GIVEN** a creation request whose optional address fields are empty strings
- **WHEN** the family is created
- **THEN** the system returns HTTP 201 and stores the empty optional fields as null

### Requirement: Wedding date validation
The system SHALL accept `weddingDate` as an optional ISO-8601 `YYYY-MM-DD` date, SHALL store null when it is omitted, and SHALL return HTTP 400 for malformed values.

#### Scenario: Wedding date omitted
- **GIVEN** a creation request with no `weddingDate`
- **WHEN** the family is created
- **THEN** the system returns HTTP 201 and the response `weddingDate` is null

#### Scenario: Valid wedding date
- **GIVEN** a creation request with `weddingDate` set to a valid `YYYY-MM-DD` date
- **WHEN** the family is created
- **THEN** the system returns HTTP 201 and the response contains that date

#### Scenario: Malformed wedding date
- **GIVEN** a creation request with a `weddingDate` that is not a valid ISO-8601 date
- **WHEN** the request is processed
- **THEN** the system returns HTTP 400

### Requirement: Family audit
The system SHALL record `createdBy`, `createdAt`, `updatedBy`, and `updatedAt` for every family. `createdBy` and `updatedBy` SHALL be the authenticated principal, and the timestamps SHALL be server-generated.

#### Scenario: Audit fields populated
- **GIVEN** a family created by an authenticated user
- **WHEN** the creation response is returned
- **THEN** `createdBy` and `updatedBy` identify that user and `createdAt` and `updatedAt` are set

### Requirement: Atomic creation and no side effects
The system SHALL persist only the family row atomically. Creating a family SHALL NOT create, link, or modify any person, and a family MAY exist with no people.

#### Scenario: No person is created
- **GIVEN** a valid family creation request
- **WHEN** the family is created
- **THEN** only the family is persisted and no person is created or linked

#### Scenario: Validation failure persists nothing
- **GIVEN** a family creation request that fails validation or name uniqueness
- **WHEN** the request is rejected
- **THEN** no family row is persisted

### Requirement: Family schema migration
The system SHALL create the `membership.families` table in Flyway migration `membership/V2__create_families_table.sql` with audit columns, a `church_id` index, a unique index on `(church_id, lower(name))`, and no cross-schema foreign key per ADR-0009.

#### Scenario: Migration creates table and indexes
- **GIVEN** a database without the `membership.families` table
- **WHEN** Flyway runs the family migration
- **THEN** the table, address and wedding-date columns, audit columns, `church_id` index, and unique name index exist

#### Scenario: No cross-schema foreign key
- **GIVEN** the family migration has run
- **WHEN** the schema is inspected
- **THEN** `church_id` is indexed and has no cross-schema foreign key to `organization.churches`

### Requirement: Error contract
The system SHALL return RFC 9457 ProblemDetail errors per ADR-0013: HTTP 400 with `fieldErrors` for validation, HTTP 401 for a missing or invalid JWT, HTTP 409 for duplicate names, and HTTP 500 with a generic message for unexpected failures.

#### Scenario: Validation error shape
- **GIVEN** a creation request that fails field validation
- **WHEN** the system rejects it
- **THEN** the response is HTTP 400 with a ProblemDetail containing `fieldErrors`

#### Scenario: Duplicate name error shape
- **GIVEN** a creation request that duplicates an existing family name in the church
- **WHEN** the system rejects it
- **THEN** the response is HTTP 409 with a ProblemDetail identifying `name`

#### Scenario: Unexpected failure
- **GIVEN** an unexpected server failure during creation
- **WHEN** the system responds
- **THEN** the response is HTTP 500 with a generic ProblemDetail that exposes no internal details

### Requirement: Only family creation endpoint
The system SHALL expose only `POST /membership/families` for families in this change and SHALL NOT add list, get, update, or delete family endpoints. Any future family read endpoint MUST be church-scoped and MUST return HTTP 404, never 403, for families outside the caller's church (ADR-0005).

#### Scenario: OpenAPI exposes only family creation
- **GIVEN** the generated OpenAPI document
- **WHEN** family paths are inspected
- **THEN** only `POST /membership/families` exists and no list, get, update, or delete family operation is documented

#### Scenario: No family read route
- **GIVEN** a family in church Y and an authenticated user for church X
- **WHEN** the user calls `GET /membership/families/{id}`
- **THEN** the system returns no family data and does not return HTTP 403
