# identity Specification

## Purpose
Defines how organization-scoped users are created and managed through the API, including Keycloak provisioning, USERS group assignment, validation, uniqueness, audit, and 404-based tenant isolation.

## Requirements
### Requirement: User Creation Capability
The system SHALL allow creation of new users within an organization via HTTP endpoint.

#### Scenario: User creation via HTTP endpoint
- **GIVEN** a valid user creation request with username, fullname, email
- **WHEN** the endpoint is called by an authorized admin user
- **THEN** a new user is created in Keycloak with hardcoded password "password"
- **AND** assigned to the USERS group
- **AND** stored in the Users table with organization ID relationship
- **AND** receives a default password requiring reset on first access

### Requirement: User Creation Response
The system SHALL return the created user data upon successful creation.

#### Scenario: User creation response
- **GIVEN** a successful user creation request
- **WHEN** the user is created
- **THEN** the system returns HTTP 201 with UserDto in the response body
- **AND** the Location header points to the user details endpoint

### Requirement: User Validation
User inputs SHALL meet validation requirements for username, fullname, and email.

#### Scenario: User input validation
- **GIVEN** user creation input
- **WHEN** the request is validated
- **THEN** username must be 3-50 characters and not blank
- **AND** fullname must be 3-200 characters and not blank
- **AND** email must be valid format, max 254 characters, and not blank

### Requirement: User Organization Scoping
Users SHALL belong to the organization of the creator and be inaccessible outside that organization.

#### Scenario: Organization-scoped user access
- **GIVEN** a user creation request
- **WHEN** created by a user from organization X
- **THEN** the new user belongs to organization X
- **AND** can only be accessed by users from organization X

### Requirement: User Group Assignment
Users created via HTTP endpoint SHALL be assigned to the USERS group.

#### Scenario: User group assignment
- **GIVEN** a user created via HTTP endpoint
- **WHEN** the user is provisioned in Keycloak
- **THEN** the user is assigned to the USERS group
- **AND** has appropriate permissions for regular users

### Requirement: User Password Management
Newly created users SHALL receive a default password requiring reset on first access.

#### Scenario: Default password handling
- **GIVEN** a newly created user
- **WHEN** the account is provisioned
- **THEN** the user receives a temporary password
- **AND** must reset password on first login
- **AND** password is managed by Keycloak

### Requirement: User Uniqueness
Usernames and emails SHALL be unique within the organization.

#### Scenario: User uniqueness validation
- **GIVEN** a user creation request
- **WHEN** username or email already exists in the organization
- **THEN** the system returns HTTP 409 Conflict
- **AND** provides clear error message

### Requirement: User Authorization
Only users with ADMIN role SHALL be able to create new users.

#### Scenario: User creation authorization
- **GIVEN** a user creation request
- **WHEN** the creator does not have ADMIN role
- **THEN** the system returns HTTP 403 Forbidden

### Requirement: Username Validation Rules
Username inputs SHALL be validated according to specified constraints.

#### Scenario: Username validation rules
- **GIVEN** a user creation request
- **WHEN** the username is validated
- **THEN** username must be 3-50 characters and not empty or blank

### Requirement: Fullname Validation Rules
Fullname inputs SHALL be validated according to specified constraints.

#### Scenario: Fullname validation rules
- **GIVEN** a user creation request
- **WHEN** the fullname is validated
- **THEN** fullname must be 3-200 characters and not empty or blank

### Requirement: Email Validation Rules
Email inputs SHALL be validated according to specified constraints.

#### Scenario: Email validation rules
- **GIVEN** a user creation request
- **WHEN** the email is validated
- **THEN** email must be valid format, max 254 characters, and not empty or blank

### Requirement: User Audit Trail
All user creation SHALL be audited with creation metadata.

#### Scenario: User creation audit
- **GIVEN** a user creation event
- **WHEN** the user is persisted
- **THEN** the user entity includes createdBy, createdAt, updatedBy, updatedAt
- **AND** audit information is properly maintained
