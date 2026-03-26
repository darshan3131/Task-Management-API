# Configuration

## Overview

All configuration is in `src/main/resources/application.properties`. Sensitive values are read from environment variables with safe local development fallbacks using the `${VAR_NAME:default}` syntax.

**Rule:** Environment variables override the defaults. In production, always set real values via environment variables — never rely on defaults.

---

## Full `application.properties`

```properties
spring.application.name=taskapi

# ─── Server ───────────────────────────────────────────────────────────────────
server.port=${PORT:8080}

# ─── Database ─────────────────────────────────────────────────────────────────
spring.datasource.url=${DATABASE_URL:jdbc:mysql://localhost:3306/taskdb?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC}
spring.datasource.username=${DATABASE_USERNAME:root}
spring.datasource.password=${DATABASE_PASSWORD:my-secret-pw}

# ─── JPA ──────────────────────────────────────────────────────────────────────
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# ─── JWT ──────────────────────────────────────────────────────────────────────
jwt.secret=${JWT_SECRET:5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437}
jwt.expiration=${JWT_EXPIRATION:86400000}

# ─── Swagger / OpenAPI ────────────────────────────────────────────────────────
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/v3/api-docs

# ─── Actuator ─────────────────────────────────────────────────────────────────
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=never
```

---

## Property Reference

### Server

| Property | Env Var | Default | Description |
|---|---|---|---|
| `server.port` | `PORT` | `8080` | Port the application listens on. Railway injects `PORT` automatically. |

---

### Database

| Property | Env Var | Default | Description |
|---|---|---|---|
| `spring.datasource.url` | `DATABASE_URL` | Local MySQL URL | Full JDBC connection string including database name and options |
| `spring.datasource.username` | `DATABASE_USERNAME` | `root` | Database user |
| `spring.datasource.password` | `DATABASE_PASSWORD` | `my-secret-pw` | Database password — **must be overridden in production** |

**Local development URL breakdown:**
```
jdbc:mysql://localhost:3306/taskdb
  ?createDatabaseIfNotExist=true   ← creates the DB if it doesn't exist
  &useSSL=false                    ← disables SSL for local connections
  &serverTimezone=UTC              ← prevents timezone mismatch errors
```

---

### JPA / Hibernate

| Property | Value | Description |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `update` | Hibernate creates/updates tables on startup to match entity definitions. Does not drop existing columns. |
| `spring.jpa.show-sql` | `false` | SQL statements are not printed to console. Set to `true` for local debugging only. |
| `spring.jpa.properties.hibernate.dialect` | `MySQLDialect` | Tells Hibernate to generate MySQL-compatible SQL. Required for MySQL 8. |

---

### JWT

| Property | Env Var | Default | Description |
|---|---|---|---|
| `jwt.secret` | `JWT_SECRET` | Hardcoded hex string | The signing key for HS256. **Must be overridden in production.** The default is exposed in source code. |
| `jwt.expiration` | `JWT_EXPIRATION` | `86400000` | Token lifetime in milliseconds. Default = 24 hours. |

**How the secret is used:**
```java
@Value("${jwt.secret}")
private String secret;

private Key getSigningKey() {
    return Keys.hmacShaKeyFor(secret.getBytes());
}
```

**JWT_SECRET requirements:**
- Must be at least 256 bits (32 characters) for HS256
- Should be a random, high-entropy string
- Never commit the real value to source control

---

### Swagger / OpenAPI

| Property | Value | Description |
|---|---|---|
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | URL for Swagger UI |
| `springdoc.api-docs.path` | `/v3/api-docs` | URL for raw OpenAPI JSON spec |

Both paths are permitted without authentication in `SecurityConfig`.

---

### Actuator

| Property | Value | Description |
|---|---|---|
| `management.endpoints.web.exposure.include` | `health,info` | Only exposes `/actuator/health` and `/actuator/info`. All other actuator endpoints are hidden. |
| `management.endpoint.health.show-details` | `never` | Health endpoint returns `{"status":"UP"}` only — no internal component details exposed publicly. |

Both endpoints are permitted without authentication in `SecurityConfig`:
```java
.requestMatchers("/actuator/health", "/actuator/info").permitAll()
```

---

## Environment Variables Summary

| Variable | Required in Production | Description |
|---|---|---|
| `DATABASE_URL` | Yes | Full JDBC URL to the MySQL database |
| `DATABASE_USERNAME` | Yes | Database username |
| `DATABASE_PASSWORD` | Yes | Database password |
| `JWT_SECRET` | Yes | HS256 signing key (min 32 chars, high entropy) |
| `JWT_EXPIRATION` | Optional | Token lifetime in ms (default 86400000 = 24h) |
| `PORT` | No (Railway sets it) | Server port (default 8080) |

---

## Dev vs Production Differences

| Setting | Local Development | Production (Railway) |
|---|---|---|
| `DATABASE_URL` | `localhost:3306/taskdb` | Railway MySQL plugin URL |
| `DATABASE_PASSWORD` | Any local password | Set as Railway env var |
| `JWT_SECRET` | Falls back to default in code | Set as Railway env var |
| `server.port` | 8080 | Set by Railway (`PORT`) |
| `ddl-auto` | `update` (auto-manage schema) | `update` (same — acceptable for this scale) |
| `show-sql` | Can set to `true` for debugging | `false` (never log SQL in production) |
