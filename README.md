# Task Management API

A RESTful backend API for managing personal tasks — built with Spring Boot, secured with JWT, documented with Swagger, and deployable to Railway.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.0.4 |
| Security | Spring Security + JWT (jjwt 0.11.5) |
| Database | MySQL via Spring Data JPA / Hibernate |
| Documentation | Swagger UI (springdoc-openapi 2.8.9) |
| Observability | Spring Boot Actuator |
| Utilities | Lombok, Spring Validation |
| Build | Maven |

---

## Features

- User registration and login with JWT authentication
- Full CRUD for personal tasks (create, read, update, delete)
- Filter tasks by status: `TODO`, `IN_PROGRESS`, `DONE`
- Search tasks by title keyword (case-insensitive)
- Paginated responses sorted newest first
- Global exception handling — all errors return consistent JSON
- Swagger UI for interactive API exploration
- Health check via Spring Boot Actuator

---

## Project Structure

```
src/main/java/com/darshan/taskapi/
├── config/          # SecurityConfig, SwaggerConfig
├── controller/      # AuthController, TaskController
├── dto/             # Request/Response DTOs, TaskStatus enum
├── entity/          # User, Task (JPA entities)
├── exception/       # Custom exceptions, GlobalExceptionHandler
├── repository/      # UserRepository, TaskRepository
├── security/        # JwtUtil, JwtFilter, UserDetailsServiceImpl
└── service/         # AuthService, TaskService
```

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.0+ running locally

### 1. Clone the repository

```bash
git clone https://github.com/your-username/taskapi.git
cd taskapi
```

### 2. Create the database

```sql
CREATE DATABASE taskdb;
```

> Tables are created automatically on first run via `ddl-auto=update`.

### 3. Configure environment variables

Export these before running, or set them in your IDE run configuration:

```bash
export DATABASE_URL=jdbc:mysql://localhost:3306/taskdb?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
export DATABASE_USERNAME=root
export DATABASE_PASSWORD=yourpassword
export JWT_SECRET=your-256-bit-secret-key-here
export JWT_EXPIRATION=86400000
```

> If not set, the app falls back to the defaults in `application.properties`. **Never commit real secrets.**

### 4. Run the application

```bash
./mvnw spring-boot:run
```

API starts at: `http://localhost:8080`

---

## API Base URL

```
http://localhost:8080/api
```

---

## Swagger UI

```
http://localhost:8080/swagger-ui.html
```

To authenticate in Swagger:
1. Call `POST /api/auth/login` and copy the `token` from the response
2. Click the **Authorize** button (top right)
3. Paste the token → click **Authorize**
4. All subsequent requests include the JWT automatically

---

## API Endpoints

### Authentication

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | No | Create a new account |
| POST | `/api/auth/login` | No | Login and receive a JWT token |

### Tasks

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/tasks` | Yes | Get all tasks (paginated, filterable) |
| POST | `/api/tasks` | Yes | Create a new task |
| GET | `/api/tasks/{id}` | Yes | Get a single task by ID |
| PUT | `/api/tasks/{id}` | Yes | Update a task |
| DELETE | `/api/tasks/{id}` | Yes | Delete a task (returns 204) |

### Monitoring

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/actuator/health` | No | Application health status |
| GET | `/actuator/info` | No | Application info |

---

## Example: Register

**Request**
```http
POST /api/auth/register
Content-Type: application/json

{
  "name": "Darshan Siddarth",
  "email": "darshan@example.com",
  "password": "securepass"
}
```

**Response — 201 Created**
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "email": "darshan@example.com",
    "name": "Darshan Siddarth"
  }
}
```

---

## Example: Create Task

**Request**
```http
POST /api/tasks
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{
  "title": "Finish API documentation",
  "description": "Write README and architecture docs",
  "status": "IN_PROGRESS",
  "dueDate": "2026-12-31"
}
```

**Response — 201 Created**
```json
{
  "success": true,
  "message": "Task created",
  "data": {
    "id": 1,
    "title": "Finish API documentation",
    "description": "Write README and architecture docs",
    "status": "IN_PROGRESS",
    "dueDate": "2026-12-31",
    "createdAt": "2026-03-26T10:00:00",
    "updatedAt": "2026-03-26T10:00:00"
  }
}
```

> Note: `dueDate` is omitted from the response if not set (`@JsonInclude(NON_NULL)`).

---

## Example: Get All Tasks (with filters)

```http
GET /api/tasks?status=IN_PROGRESS&search=API&page=0&size=10
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Response — 200 OK**
```json
{
  "success": true,
  "message": "Tasks retrieved",
  "data": {
    "content": [ { "id": 1, "title": "Finish API documentation", "..." : "..." } ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
}
```

**Query Parameters**

| Param | Type | Default | Description |
|---|---|---|---|
| `status` | String | — | Filter by `TODO`, `IN_PROGRESS`, or `DONE` |
| `search` | String | — | Search by title keyword (case-insensitive) |
| `page` | int | 0 | Page number (0-based) |
| `size` | int | 10 | Page size (max 50) |

---

## Error Response Format

All errors return the same shape:

```json
{
  "success": false,
  "message": "Task not found",
  "data": null
}
```

| Status | Scenario |
|---|---|
| 400 | Validation failed, invalid ID format |
| 401 | Missing/invalid JWT, wrong credentials, not your task |
| 404 | Task or user not found |
| 409 | Email already registered |
| 500 | Unexpected server error |

---

## Deployment (Railway)

1. Push the project to a GitHub repository
2. Create a new project on [Railway](https://railway.app)
3. Add a **MySQL** plugin to your Railway project
4. Set the following environment variables in Railway settings:

| Variable | Value |
|---|---|
| `DATABASE_URL` | From Railway MySQL plugin |
| `DATABASE_USERNAME` | From Railway MySQL plugin |
| `DATABASE_PASSWORD` | From Railway MySQL plugin |
| `JWT_SECRET` | Generate a strong random secret |
| `JWT_EXPIRATION` | `86400000` (24 hours) |

5. Railway auto-detects the Maven project and deploys on every push to main.

---

## Author

**Darshan Siddarth**
darshansiddarth05@gmail.com
