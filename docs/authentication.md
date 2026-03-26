# Authentication

## Overview

This API uses **stateless JWT (JSON Web Token) authentication**. There are no server-side sessions. Every request carries its own identity in the `Authorization` header. The server validates the token on each request without consulting any session store.

---

## JWT Structure

A JWT has three Base64-encoded parts separated by dots:

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkYXJzaGFuQGdtYWlsLmNvbSIsImlhdCI6...}.SIGNATURE
      ▲                              ▲                                        ▲
   Header                         Payload                                Signature
 (algorithm)               (claims: sub, iat, exp)               (HMAC of header+payload)
```

**Header:** `{ "alg": "HS256" }`
**Payload claims used:**
- `sub` — email (subject / who the token is for)
- `iat` — issued at (timestamp)
- `exp` — expiration time (timestamp)

**Algorithm:** HS256 (HMAC-SHA256) using the `JWT_SECRET` environment variable as the signing key.

The payload is Base64-encoded, **not encrypted**. Do not put sensitive data in it.

---

## Token Generation — `JwtUtil.java`

```java
public String generateToken(String email) {
    return Jwts.builder()
            .setSubject(email)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
}
```

- `expiration` is read from `jwt.expiration` in `application.properties` (default: 86400000ms = 24 hours)
- `getSigningKey()` converts the secret string to a `javax.crypto.Key` using HMAC-SHA

---

## Token Validation — `JwtUtil.java`

```java
public boolean isTokenValid(String token) {
    try {
        Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
        return true;
    } catch (JwtException e) {
        return false;
    }
}
```

A token is invalid if:
- The signature doesn't match (tampered payload)
- The token has expired (`exp` is in the past)
- The token is malformed

---

## Filter Chain — `JwtFilter.java`

`JwtFilter` extends `OncePerRequestFilter`, which guarantees it runs exactly once per HTTP request.

```
Incoming request
    ↓
Read Authorization header
    ↓
Header present + starts with "Bearer "?
    ├── No  → skip (no auth set, SecurityConfig will reject if endpoint needs auth)
    └── Yes → extract token (substring after "Bearer ")
                ↓
           isTokenValid(token)?
                ├── No  → skip (SecurityContext stays empty)
                └── Yes → extractEmail(token)
                            ↓
                       loadUserByUsername(email) → hit DB
                            ↓
                       create UsernamePasswordAuthenticationToken
                            ↓
                       SecurityContextHolder.getContext().setAuthentication(...)
                            ↓
                       continue filter chain → reaches controller
```

After the filter runs, the controller reads the authenticated user via `@AuthenticationPrincipal UserDetails userDetails`. `userDetails.getUsername()` returns the email from the JWT.

---

## Security Configuration — `SecurityConfig.java`

```java
http
    .csrf(csrf -> csrf.disable())
    // Stateless JWT API — no browser sessions or cookies → CSRF not applicable

    .sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    // No sessions created or used — every request is independent

    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/**").permitAll()
        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
        .anyRequest().authenticated())

    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    // JwtFilter runs before Spring's default username/password filter
```

**Public endpoints (no token required):**
- `/api/auth/register`
- `/api/auth/login`
- `/swagger-ui/**`, `/v3/api-docs/**`
- `/actuator/health`, `/actuator/info`

**All other endpoints require a valid JWT.**

---

## UserDetailsServiceImpl — Bridge to Spring Security

Spring Security's filter chain requires a `UserDetailsService`. This implementation loads the user from the database by email and wraps it in Spring Security's `UserDetails` type.

```java
@Override
public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

    return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPassword(),
            new ArrayList<>()   // no roles — this API does not use role-based access control
    );
}
```

---

## Password Handling

Passwords are hashed using **BCrypt** before storage. BCrypt is a deliberately slow algorithm that resists brute-force attacks.

```java
// On register:
user.setPassword(passwordEncoder.encode(request.getPassword()));

// On login:
passwordEncoder.matches(request.getPassword(), user.getPassword())
// rehashes the raw input and compares — the original password is never stored or decrypted
```

---

## User Enumeration Prevention

The login endpoint returns the same error message whether the email doesn't exist or the password is wrong:

```
"Invalid email or password"
```

This prevents an attacker from using the API to discover which email addresses are registered.

---

## Authentication Flow Summary

```
Register:
  Client → POST /api/auth/register { name, email, password }
         ← 201 { token, email, name }

Login:
  Client → POST /api/auth/login { email, password }
         ← 200 { token, email, name }

Authenticated request:
  Client → GET /api/tasks
           Authorization: Bearer <token>
         ← 200 { tasks data }

Invalid/missing token:
  Client → GET /api/tasks (no header)
         ← 401 { success: false, message: "..." }
```
