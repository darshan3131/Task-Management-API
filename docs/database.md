# Database

## Overview

The application uses **MySQL 8** as the primary database. The ORM layer is **Hibernate** managed via **Spring Data JPA**. Tables are created and updated automatically at startup using `ddl-auto=update`.

---

## Entities

### `users` table — `User.java`

| Column | Java Type | DB Constraint | Notes |
|---|---|---|---|
| `id` | `Long` | PRIMARY KEY, AUTO_INCREMENT | Auto-generated |
| `email` | `String` | NOT NULL, UNIQUE | Used as JWT subject and login identifier |
| `password` | `String` | NOT NULL | BCrypt-hashed, never plain text |
| `name` | `String` | NOT NULL | Display name |
| `created_at` | `LocalDateTime` | — | Set at object creation time |

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    private LocalDateTime createdAt = LocalDateTime.now();
}
```

---

### `tasks` table — `Task.java`

| Column | Java Type | DB Constraint | Notes |
|---|---|---|---|
| `id` | `Long` | PRIMARY KEY, AUTO_INCREMENT | Auto-generated |
| `title` | `String` | NOT NULL | Required field |
| `description` | `String` | NULLABLE | Optional |
| `status` | `String` (enum) | — | Stored as string: `TODO`, `IN_PROGRESS`, `DONE` |
| `due_date` | `LocalDate` | NULLABLE | Optional future date |
| `user_id` | `Long` | FOREIGN KEY → users.id | Ownership reference |
| `created_at` | `LocalDateTime` | — | Set by `@PrePersist` |
| `updated_at` | `LocalDateTime` | — | Set by `@PrePersist` and `@PreUpdate` |

```java
@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    private Status status = Status.TODO;

    private LocalDate dueDate;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum Status { TODO, IN_PROGRESS, DONE }
}
```

---

## Relationships

```
users (1) ──────────── (many) tasks
```

- One `User` can have many `Task` records
- Each `Task` has exactly one `User` (owner)
- Relationship is `@ManyToOne` on the `Task` side
- The `user_id` foreign key column is on the `tasks` table

There is no `@OneToMany` on the `User` entity — tasks are always accessed from the task side filtered by user (via repository queries).

---

## Status Enum Storage

`Task.Status` is stored using `@Enumerated(EnumType.STRING)`:

```java
@Enumerated(EnumType.STRING)
private Status status = Status.TODO;
```

This stores the literal string `"TODO"`, `"IN_PROGRESS"`, or `"DONE"` in the database column. If `EnumType.ORDINAL` were used instead, it would store `0`, `1`, `2` — which breaks silently if the enum order ever changes.

Default value is `TODO` when a task is created without a `status` field.

---

## Lifecycle Hooks

`@PrePersist` and `@PreUpdate` automatically manage timestamps:

```java
@PrePersist
protected void onCreate() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
}

@PreUpdate
protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
}
```

- `@PrePersist` runs before the first `INSERT` — sets both `createdAt` and `updatedAt`
- `@PreUpdate` runs before every `UPDATE` — updates only `updatedAt`
- These run in the JPA layer, not the application layer, so they fire regardless of how the entity is saved

---

## Repository Queries

### `UserRepository`

```java
Optional<User> findByEmail(String email);
// SELECT * FROM users WHERE email = ?
```

### `TaskRepository`

All queries filter by the owning `User` object and are paginated:

```java
Page<Task> findByUser(User user, Pageable pageable);
Page<Task> findByUserAndStatus(User user, Status status, Pageable pageable);
Page<Task> findByUserAndTitleContainingIgnoreCase(User user, String keyword, Pageable pageable);
Page<Task> findByUserAndStatusAndTitleContainingIgnoreCase(User user, Status status, String keyword, Pageable pageable);
```

Generated SQL example for the last method:
```sql
SELECT * FROM tasks
WHERE user_id = ?
  AND status = ?
  AND UPPER(title) LIKE UPPER('%keyword%')
ORDER BY created_at DESC
LIMIT ? OFFSET ?
```

---

## DDL Auto Behavior

```properties
spring.jpa.hibernate.ddl-auto=update
```

On every application startup, Hibernate compares the entity definitions against the actual database schema and applies any additions (new columns, new tables). It does **not** drop or rename existing columns — that must be done manually.

This is acceptable for development and small production apps. For large production systems, use Flyway or Liquibase for versioned migrations instead.

---

## Assumptions and Constraints

- Email uniqueness is enforced at the database level (`UNIQUE` constraint on `users.email`) and validated in the application layer (`DuplicateEmailException`)
- There is no soft delete — `DELETE /api/tasks/{id}` permanently removes the row
- There is no cascade delete — if a user record is removed, their task rows would become orphaned (user deletion is not an exposed endpoint in this API)
- `dueDate` is a `LocalDate` (date only, no time). If provided, it must be a future date — validated by `@Future` in `TaskRequest`
