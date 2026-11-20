# organization Specification

## Purpose
Defines how church organizations are created through the API, including the automatic main congregation, admin user provisioning, validation, and tenant-scoped responses.

## Requirements
### Requirement: Church Creation Capability
The system SHALL allow creation of new church organizations with automatic main congregation setup.

#### Scenario: Church creation capability
- **GIVEN** the church creation feature
- **WHEN** implemented
- **THEN** churches can be created with congregations

### Requirement: Church Creation Response
The system SHALL return the created church data and Location header upon successful creation.

#### Scenario: Church creation response
- **GIVEN** a valid church creation request
- **WHEN** the church is successfully created
- **THEN** the system returns HTTP 201 with the created ChurchDto in the response body
- **AND** the Location header points to the getById route for the created church

### Requirement: Church Name Validation
Church names SHALL meet length and content requirements.

#### Scenario: Church name validation
- **GIVEN** church name input
- **WHEN** the request is validated
- **THEN** it must be validated for length and content

### Requirement: Hostname Validation
Hostnames SHALL be unique and meet length requirements.

#### Scenario: Hostname validation
- **GIVEN** hostname input
- **WHEN** the request is validated
- **THEN** it must be validated for uniqueness and length

### Requirement: User Information Validation
User name, email, and full name SHALL be valid.

#### Scenario: User information validation
- **GIVEN** user name, email, and full name input
- **WHEN** the request is validated
- **THEN** they must be validated

### Requirement: Full Name Validation Rules
Full name inputs SHALL be validated according to specified constraints.

#### Scenario: Full name validation rules
- **GIVEN** a church creation request
- **WHEN** the full name is validated
- **THEN** full name must not be empty or blank

### Requirement: Address Validation
Address fields SHALL be validated when provided.

#### Scenario: Address validation
- **GIVEN** address input when provided
- **WHEN** the request is validated
- **THEN** fields must be validated

### Requirement: Name Validation Rules
Name inputs SHALL be validated according to specified constraints.

#### Scenario: Name validation rules
- **GIVEN** a church creation request
- **WHEN** the name is validated
- **THEN** name must be 3-200 characters and not empty or blank

### Requirement: Hostname Validation Rules
Hostname inputs SHALL be validated according to specified constraints.

#### Scenario: Hostname validation rules
- **GIVEN** a church creation request
- **WHEN** the hostname is validated
- **THEN** hostname must be 3-50 characters and not empty or blank

### Requirement: Username Validation Rules
Username inputs SHALL be validated according to specified constraints.

#### Scenario: Username validation rules
- **GIVEN** a church creation request
- **WHEN** the username is validated
- **THEN** username must not be empty or blank

### Requirement: Email Validation Rules
Email inputs SHALL be validated according to specified constraints.

#### Scenario: Email validation rules
- **GIVEN** a church creation request
- **WHEN** the email is validated
- **THEN** email must be valid format and not empty or blank

### Requirement: Address Validation Rules
Address inputs SHALL be validated according to specified constraints when provided.

#### Scenario: Address validation rules
- **GIVEN** a church creation request with address
- **WHEN** the address is validated
- **THEN** address line 1 must be 3-200 characters
- **AND** city must be 3-100 characters
- **AND** address line 2 must be 3-200 characters if provided
- **AND** postal code must be 3-20 characters if provided

### Requirement: Church Creation with Admin User
The system SHALL create an admin user automatically when a church is created, assigned to the ADMIN group.

#### Scenario: Admin user creation during church setup
- **GIVEN** a church creation request with user details
- **WHEN** the church is successfully created
- **THEN** an admin user is automatically created in Keycloak with hardcoded password "password"
- **AND** assigned to the ADMIN group
- **AND** stored in the Users table with organization ID relationship
- **AND** receives a default password requiring reset on first access
- **AND** belongs to the created church's organization
