# API Reference — DTOs

Complete field-level documentation for every request and response class used in this API.

---

## Request DTOs

Request DTOs carry data from the client into the API. All validation annotations on request DTOs are triggered by `@Valid` in the controller method — they do nothing by themselves.

---

### `LoginRequest.java`

Used by: `POST /api/auth/login`

```java
@Data
public class LoginRequest {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
```

**Fields:**

| Field | Type | Validation | Required | Notes |
|---|---|---|---|---|
| `email` | `String` | `@Email`, `@NotBlank` | Yes | Must be a valid email format |
| `password` | `String` | `@NotBlank` | Yes | Raw password — compared against BCrypt hash in service |

**Example request body:**
```json
{
  "email": "darshan@example.com",
  "password": "securepass"
}
```

**Validation failures → 400 Bad Request:**
- Email is blank → `"Email is required"`
- Email is malformed → `"Invalid email format"`
- Password is blank → `"Password is required"`

---

### `RegisterRequest.java`

Used by: `POST /api/auth/register`

```java
@Data
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
```

**Fields:**

| Field | Type | Validation | Required | Notes |
|---|---|---|---|---|
| `name` | `String` | `@NotBlank` | Yes | Display name stored in users table |
| `email` | `String` | `@Email`, `@NotBlank` | Yes | Must be unique — checked in service layer |
| `password` | `String` | `@NotBlank`, `@Size(min=8)` | Yes | BCrypt-hashed before storage |

**Example request body:**
```json
{
  "name": "Darshan Siddarth",
  "email": "darshan@example.com",
  "password": "securepass"
}
```

**Validation failures → 400 Bad Request:**
- Any field blank → field-specific message
- Password under 8 characters → `"Password must be at least 8 characters"`
- Email already registered → `409 Conflict` — `"Email already registered"`

---

### `TaskRequest.java`

Used by: `POST /api/tasks` (create) and `PUT /api/tasks/{id}` (update)

```java
@Data
public class TaskRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String status;

    @Future(message = "Due date must be a future date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;
}
```

**Fields:**

| Field | Type | Validation | Required | Notes |
|---|---|---|---|---|
| `title` | `String` | `@NotBlank` | Yes | Task title |
| `description` | `String` | None | No | Optional detail text |
| `status` | `String` | None at DTO level | No | `TODO`, `IN_PROGRESS`, or `DONE`. Converted via `Status.valueOf(status.toUpperCase())` in service. Defaults to `TODO` if not provided. Invalid values throw `IllegalArgumentException` → `400`. |
| `dueDate` | `LocalDate` | `@Future`, `@JsonFormat` | No | Must be a future date. Format: `yyyy-MM-dd`. `null` is allowed (no due date). |

**Example request body (full):**
```json
{
  "title": "Finish API documentation",
  "description": "Write README and architecture docs",
  "status": "IN_PROGRESS",
  "dueDate": "2026-12-31"
}
```

**Example request body (minimal):**
```json
{
  "title": "Buy groceries"
}
```

**Validation failures → 400 Bad Request:**
- Title blank → `"Title is required"`
- `dueDate` is today or in the past → `"Due date must be a future date"`
- `status` is an invalid string (e.g. `"PENDING"`) → `"Invalid value: No enum constant ..."`

---

## Response DTOs

Response DTOs carry data from the API to the client. They never expose entity internals (passwords, raw foreign keys, etc.).

---

### `AppResponse.java`

The universal envelope wrapper. **Every API response** — success or error — uses this shape.

```java
@Getter
@AllArgsConstructor
public class AppResponse {
    private boolean success;
    private String message;
    private Object data;

    public static AppResponse success(String message, Object data) {
        return new AppResponse(true, message, data);
    }

    public static AppResponse error(String message) {
        return new AppResponse(false, message, null);
    }
}
```

**Fields:**

| Field | Type | Notes |
|---|---|---|
| `success` | `boolean` | `true` on success, `false` on error |
| `message` | `String` | Human-readable description of the result |
| `data` | `Object` | The actual payload (can be any DTO, `null` on error) |

**Success shape:**
```json
{
  "success": true,
  "message": "Task created",
  "data": { ... }
}
```

**Error shape:**
```json
{
  "success": false,
  "message": "Task not found",
  "data": null
}
```

Static factory methods `AppResponse.success()` and `AppResponse.error()` are used throughout the controllers — no `new AppResponse(...)` calls at call sites.

---

### `AuthResponse.java`

Returned by both `POST /api/auth/register` and `POST /api/auth/login`. Carries all identity info the client needs after authentication — no separate profile call needed.

```java
@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String name;
}
```

**Fields:**

| Field | Type | Notes |
|---|---|---|
| `token` | `String` | JWT — include in `Authorization: Bearer <token>` on all subsequent requests |
| `email` | `String` | Authenticated user's email |
| `name` | `String` | Authenticated user's display name |

**Example JSON (as `data` inside `AppResponse`):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "darshan@example.com",
  "name": "Darshan Siddarth"
}
```

---

### `TaskResponse.java`

Returned by all task endpoints. Uses `TaskStatus` (DTO enum) — not `Task.Status` (entity enum) — keeping the API contract decoupled from the database model.

```java
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskResponse {
    private Long id;
    private String title;
    private String description;
    private TaskStatus status;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Fields:**

| Field | Type | Nullable in JSON | Notes |
|---|---|---|---|
| `id` | `Long` | No | DB-generated task ID |
| `title` | `String` | No | Task title |
| `description` | `String` | Yes (omitted if null) | Optional task description |
| `status` | `TaskStatus` | No | One of: `TODO`, `IN_PROGRESS`, `DONE` |
| `dueDate` | `LocalDate` | Yes (omitted if null) | Format: `yyyy-MM-dd` |
| `createdAt` | `LocalDateTime` | No | Set by `@PrePersist` |
| `updatedAt` | `LocalDateTime` | No | Set by `@PrePersist` and `@PreUpdate` |

**`@JsonInclude(NON_NULL)`:** Fields that are `null` (e.g. `description`, `dueDate`) are excluded from the JSON output entirely — the field won't appear in the response at all rather than showing `"dueDate": null`.

**Example JSON (with due date):**
```json
{
  "id": 1,
  "title": "Finish API documentation",
  "description": "Write README and architecture docs",
  "status": "IN_PROGRESS",
  "dueDate": "2026-12-31",
  "createdAt": "2026-03-26T10:00:00",
  "updatedAt": "2026-03-26T10:00:00"
}
```

**Example JSON (without due date — field omitted):**
```json
{
  "id": 2,
  "title": "Buy groceries",
  "status": "TODO",
  "createdAt": "2026-03-26T11:00:00",
  "updatedAt": "2026-03-26T11:00:00"
}
```

---

### `PagedResponse.java`

Wraps paginated query results. Returned as the `data` field inside `AppResponse` for `GET /api/tasks`.

```java
@Getter
public class PagedResponse<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean last;

    public PagedResponse(Page<T> pageData) {
        this.content       = pageData.getContent();
        this.page          = pageData.getNumber();
        this.size          = pageData.getSize();
        this.totalElements = pageData.getTotalElements();
        this.totalPages    = pageData.getTotalPages();
        this.last          = pageData.isLast();
    }
}
```

**Fields:**

| Field | Type | Notes |
|---|---|---|
| `content` | `List<T>` | The items for the current page. `T` = `TaskResponse` in this API. |
| `page` | `int` | Current page index (0-based). Page 1 in UI = `page: 0` in response. |
| `size` | `int` | Number of items requested per page (max 50, enforced in controller). |
| `totalElements` | `long` | Total number of matching items across **all** pages. |
| `totalPages` | `int` | Total number of pages. |
| `last` | `boolean` | `true` if this is the last page — useful for infinite scroll / "load more" logic. |

**Example JSON (as `data` inside `AppResponse`):**
```json
{
  "content": [
    { "id": 3, "title": "Buy groceries", "status": "TODO", "createdAt": "...", "updatedAt": "..." },
    { "id": 1, "title": "Finish API documentation", "status": "IN_PROGRESS", "dueDate": "2026-12-31", "createdAt": "...", "updatedAt": "..." }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 2,
  "totalPages": 1,
  "last": true
}
```

---

### `TaskStatus.java` (DTO Enum)

```java
public enum TaskStatus {
    TODO, IN_PROGRESS, DONE
}
```

Standalone enum in the `dto` package — separate from `Task.Status` in the entity package.

**Why it exists separately from `Task.Status`:**
The entity enum (`Task.Status`) is tied to the database layer. `TaskStatus` is the API contract — what the client sees in responses. If the entity enum ever changes internally (e.g. renamed values, added states), the API contract can remain stable. The mapping happens in `TaskService.toResponse()`:

```java
TaskStatus.valueOf(task.getStatus().name())
// task.getStatus() → Task.Status.TODO
// .name()          → "TODO"
// TaskStatus.valueOf("TODO") → TaskStatus.TODO
```

**Valid values:** `TODO`, `IN_PROGRESS`, `DONE`

Used in: `TaskResponse.status` (output), mapped from `Task.Status` (internal).
Accepted as `String` in: `TaskRequest.status` (input), converted via `Status.valueOf(status.toUpperCase())` in service.
