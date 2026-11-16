# Tasks for Add User Management Capability

## Identity Management Module Setup
- [x] Create identity module package structure (`identity/` and `identity.internal/`)
- [x] Define User entity for Users table with UUID id, Audit embeddable, Keycloak user ID, and organization ID (identity schema)
- [x] Create UserDto and CreateUserDto records
- [x] Implement UserRepository with organization-scoped queries
- [x] Create UserMapper for entity-DTO conversion

## User Creation Service
- [x] Implement UserService with create method (organization-scoped)
- [x] Add Keycloak user creation logic with group assignment (USERS)
- [x] Add validation for uniqueness and organization access
- [x] Publish UserCreated event via Spring Modulith

## User Creation Controller
- [x] Create UserController with POST endpoint for user creation
- [x] Add @OrganizationId parameter for scoping
- [x] Implement proper OpenAPI documentation
- [x] Return ResponseEntity<UserDto> with HTTP 201

## Organization Admin User Creation
- [x] Modify ChurchService to create admin user in ADMIN group during organization creation
- [x] Update ChurchCreated event to include admin user details
- [x] Ensure admin user gets temporary password requiring reset

## Security and Validation
- [x] Add @PreAuthorize for user creation (ADMIN role required)
- [x] Implement organization ownership validation
- [x] Add comprehensive input validation on DTOs
- [x] Return 404 for unauthorized organization access

## Testing
- [x] Write unit tests for UserService and UserController
- [x] Add integration tests for user creation endpoint
- [x] Update ChurchCreationIntegrationTest for admin user creation
- [x] Verify ArchUnit tests pass for new code

## Documentation and Validation
- [x] Update OpenAPI specs for new endpoints
- [x] Validate all spec deltas are correctly defined
- [x] Run openspec validate to ensure compliance
- [x] Test cross-module integration with organization module