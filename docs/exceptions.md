# Exception Handling

## Overview

All exceptions in this project extend `RuntimeException` — they are unchecked, so they do not need to be declared with `throws` or wrapped in try-catch at every call site. When thrown from anywhere in the service or controller layer, they bubble up and are caught centrally by `GlobalExceptionHandler`.

---

## Entry Point — `TaskapiApplication.java`

```java
@SpringBootApplication
public class TaskapiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskapiApplication.class, args);
    }
}
```

`@SpringBootApplication` is a convenience annotation that combines three annotations:

| Annotation | What it does |
|---|---|
| `@SpringBootConfiguration` | Marks this as a Spring configuration class |
| `@EnableAutoConfiguration` | Tells Spring Boot to auto-configure beans based on classpath (e.g. sees MySQL driver → configures DataSource) |
| `@ComponentScan` | Scans the current package and all sub-packages for Spring beans (`@Service`, `@Component`, `@Repository`, `@Controller`, etc.) |

`SpringApplication.run()` bootstraps the entire application — starts the embedded Tomcat server, initialises the Spring context, connects to the database, and registers all beans.

---

## Custom Exception Classes

### `ResourceNotFoundException.java`

```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

**Thrown when:** A requested resource does not exist in the database.

**Thrown by:**
- `TaskService.getTask()` — task ID not found: `"Task not found"`
- `TaskService.updateTask()` — task ID not found: `"Task not found"`
- `TaskService.deleteTask()` — task ID not found: `"Task not found"`
- `TaskService.getUser()` — user email not found: `"User not found"`

**Handled by `GlobalExceptionHandler`:**
```java
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<AppResponse> handleNotFound(ResourceNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(AppResponse.error(ex.getMessage()));
}
```

**HTTP response:** `404 Not Found`
```json
{ "success": false, "message": "Task not found", "data": null }
```

---

### `UnauthorizedException.java`

```java
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
```

**Thrown when:** Authentication fails or the authenticated user does not own the requested resource.

**Thrown by:**
- `AuthService.login()` — wrong email or password: `"Invalid email or password"`
- `TaskService.getTask()` — task belongs to a different user: `"Access denied"`
- `TaskService.updateTask()` — task belongs to a different user: `"Access denied"`
- `TaskService.deleteTask()` — task belongs to a different user: `"Access denied"`

**Handled by `GlobalExceptionHandler`:**
```java
@ExceptionHandler(UnauthorizedException.class)
public ResponseEntity<AppResponse> handleUnauthorized(UnauthorizedException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(AppResponse.error(ex.getMessage()));
}
```

**HTTP response:** `401 Unauthorized`
```json
{ "success": false, "message": "Access denied", "data": null }
```

**Design note:** The same `401` status code is used for both authentication failures (wrong password) and authorization failures (not your task). This is intentional — it keeps the error surface consistent and avoids leaking whether a task exists to a user who doesn't own it.

---

### `DuplicateEmailException.java`

```java
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
```

**Thrown when:** A registration attempt uses an email address that already exists in the `users` table.

**Thrown by:**
- `AuthService.register()` — `"Email already registered"`

**Handled by `GlobalExceptionHandler`:**
```java
@ExceptionHandler(DuplicateEmailException.class)
public ResponseEntity<AppResponse> handleDuplicateEmail(DuplicateEmailException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(AppResponse.error(ex.getMessage()));
}
```

**HTTP response:** `409 Conflict`
```json
{ "success": false, "message": "Email already registered", "data": null }
```

---

## `GlobalExceptionHandler.java`

The central exception handler for the entire application. Annotated with `@RestControllerAdvice` — it intercepts exceptions thrown from any controller or service and returns a clean `AppResponse` JSON instead of Spring's default error page.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Custom domain exceptions
    @ExceptionHandler(ResourceNotFoundException.class)
    → 404 Not Found

    @ExceptionHandler(UnauthorizedException.class)
    → 401 Unauthorized

    @ExceptionHandler(DuplicateEmailException.class)
    → 409 Conflict

    // Framework / validation exceptions
    @ExceptionHandler(IllegalArgumentException.class)
    → 400 Bad Request   (thrown when invalid status string passed to Status.valueOf())

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    → 400 Bad Request   (thrown when /api/tasks/abc passed — "abc" can't become Long)

    @ExceptionHandler(MethodArgumentNotValidException.class)
    → 400 Bad Request   (thrown by @Valid when Bean Validation fails)

    // Catch-all
    @ExceptionHandler(Exception.class)
    → 500 Internal Server Error
}
```

### Validation error handling (safe `findFirst`)

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<AppResponse> handleValidation(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(err -> err.getDefaultMessage())
            .orElse("Validation failed");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(AppResponse.error(message));
}
```

`stream().findFirst()` is used instead of `.get(0)` because `getFieldErrors()` could theoretically return an empty list (e.g. if the violation is at the object level rather than field level). `findFirst()` is null-safe and provides a fallback message.

### Catch-all handler

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<AppResponse> handleGeneral(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(AppResponse.error("Something went wrong"));
}
```

The real exception message is deliberately not returned to the client. Exposing internal error messages (stack traces, SQL errors, class names) is a security risk. The message `"Something went wrong"` is intentionally generic.

---

## Exception → HTTP Status Map

| Exception Class | HTTP Status | Scenario |
|---|---|---|
| `ResourceNotFoundException` | 404 Not Found | Task or user doesn't exist in DB |
| `UnauthorizedException` | 401 Unauthorized | Wrong credentials or not your task |
| `DuplicateEmailException` | 409 Conflict | Email already registered |
| `IllegalArgumentException` | 400 Bad Request | Invalid status string in request |
| `MethodArgumentTypeMismatchException` | 400 Bad Request | Non-numeric ID in URL path |
| `MethodArgumentNotValidException` | 400 Bad Request | Bean Validation failure (`@Valid`) |
| Any other `Exception` | 500 Internal Server Error | Unexpected / unhandled error |

---

## Exception Flow Diagram

```
Service throws ResourceNotFoundException("Task not found")
    ↓
Bubbles up through TaskController (no try-catch)
    ↓
GlobalExceptionHandler.handleNotFound() intercepts
    ↓
Returns: 404 + { "success": false, "message": "Task not found", "data": null }
```

```
Controller receives POST /api/tasks with blank title
    ↓
@Valid triggers → MethodArgumentNotValidException thrown
    ↓
GlobalExceptionHandler.handleValidation() intercepts
    ↓
Returns: 400 + { "success": false, "message": "Title is required", "data": null }
```
