# Architecture

## Overview

Task Management API is a stateless REST API built on a standard layered architecture. Each layer has a single responsibility and communicates only with the layer directly below it.

```
┌─────────────────────────────────────┐
│           HTTP Client               │
└──────────────────┬──────────────────┘
                   │
┌──────────────────▼──────────────────┐
│         Security Filter Chain       │  JwtFilter → SecurityConfig
└──────────────────┬──────────────────┘
                   │
┌──────────────────▼──────────────────┐
│            Controller Layer         │  AuthController, TaskController
└──────────────────┬──────────────────┘
                   │
┌──────────────────▼──────────────────┐
│             Service Layer           │  AuthService, TaskService
└──────────────────┬──────────────────┘
                   │
┌──────────────────▼──────────────────┐
│           Repository Layer          │  UserRepository, TaskRepository
└──────────────────┬──────────────────┘
                   │
┌──────────────────▼──────────────────┐
│           MySQL Database            │  users, tasks tables
└─────────────────────────────────────┘
```

---

## Layer Responsibilities

### Controller Layer
- Entry point for all HTTP requests
- Validates input via `@Valid`
- Delegates all business logic to the service layer
- Wraps responses in `AppResponse` and returns `ResponseEntity`
- Contains no business logic

**Files:** `AuthController.java`, `TaskController.java`

### Service Layer
- All business logic lives here
- Manages transaction boundaries (`@Transactional`, `@Transactional(readOnly=true)`)
- Calls repositories for data access
- Maps entity objects to DTOs before returning to the controller
- Throws custom exceptions for error cases

**Files:** `AuthService.java`, `TaskService.java`

### Repository Layer
- Interfaces extending `JpaRepository<Entity, Long>`
- Spring Data JPA generates SQL from method names at startup
- All task queries are paginated — no non-paginated list methods

**Files:** `UserRepository.java`, `TaskRepository.java`

### DTO Layer
- Separates the API contract from the database model
- Request DTOs carry input from the client
- Response DTOs carry output to the client
- `TaskStatus` enum is defined separately from `Task.Status` — decouples API contract from entity

**Files:** `dto/request/`, `dto/response/`, `dto/TaskStatus.java`

### Entity Layer
- JPA-managed classes mapped to MySQL tables
- Lifecycle hooks (`@PrePersist`, `@PreUpdate`) automatically set `createdAt` and `updatedAt`
- `Task.Status` enum stored as `EnumType.STRING` in the database

**Files:** `User.java`, `Task.java`

### Security Layer
- `JwtFilter` intercepts every request and validates the token before it reaches the controller
- `UserDetailsServiceImpl` bridges the `User` entity and Spring Security's `UserDetails`
- `JwtUtil` handles token generation, parsing, and validation

**Files:** `JwtFilter.java`, `JwtUtil.java`, `UserDetailsServiceImpl.java`

---

## Request Lifecycle

### Unauthenticated request (Register / Login)

```
POST /api/auth/register
  → JwtFilter: no Authorization header → skips token validation
  → SecurityConfig: /api/auth/** is permitAll → allows through
  → AuthController.register()
  → @Valid validates RegisterRequest
  → AuthService.register() [@Transactional]
      → check duplicate email
      → BCrypt hash password
      → save User to DB
      → generate JWT
  → return 201 + AppResponse { token, email, name }
```

### Authenticated request (Create Task)

```
POST /api/tasks
Authorization: Bearer <token>
  → JwtFilter: reads Authorization header
      → extracts token
      → JwtUtil.isTokenValid() → verifies signature + expiry
      → JwtUtil.extractEmail() → reads email from payload
      → UserDetailsServiceImpl.loadUserByUsername() → loads user from DB
      → sets authentication in SecurityContextHolder
  → SecurityConfig: /api/tasks requires authentication → authenticated → allows through
  → TaskController.createTask()
      → @AuthenticationPrincipal reads user from SecurityContext
      → @Valid validates TaskRequest
  → TaskService.createTask() [@Transactional]
      → load User from DB
      → create and save Task
      → @PrePersist sets createdAt + updatedAt
      → map Task entity → TaskResponse DTO (entity Status → DTO TaskStatus)
  → return 201 + AppResponse { task data }
```

### Exception path

```
Any layer throws exception
  → GlobalExceptionHandler catches it (@RestControllerAdvice)
  → maps exception type to HTTP status
  → returns AppResponse { success: false, message: "...", data: null }
```

---

## Key Design Decisions

### Envelope response pattern
Every response — success or error — is wrapped in `AppResponse { success, message, data }`. Clients always know where to look regardless of endpoint.

### DTO / Entity separation
Entities are never returned directly from controllers. Response DTOs exclude sensitive fields (password, internal IDs in relationships) and use a separate `TaskStatus` enum so the API contract is independent of the DB model.

### Ownership-based access control
Every task operation verifies that the authenticated user owns the task. Accessing or modifying another user's task returns `401 Unauthorized`, not `403 Forbidden` — consistent with the rest of the auth error handling.

### Paginated-only repository
`TaskRepository` only defines paginated query methods. Non-paginated list methods were deliberately excluded — there is no use case in this API for loading all tasks of a user into memory.

### `@Transactional(readOnly=true)` on reads
All read operations (`getAllTasks`, `getTask`, `login`) are marked `readOnly`. This signals to the database that no writes will occur, allowing it to skip dirty tracking and write locks.

### User-provided search input excluded from logs
The `search` query parameter is excluded from log statements. User-provided free text can contain PII or sensitive data and should not appear in application logs.
