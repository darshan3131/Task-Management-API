# Task Management API — Complete Notes
### Every file, every annotation, every concept explained

---

## PROJECT STRUCTURE

```
taskapi/
├── config/
│   ├── SecurityConfig.java       ← Spring Security rules + JWT filter wiring
│   └── SwaggerConfig.java        ← OpenAPI/Swagger UI setup
├── controller/
│   ├── AuthController.java       ← /api/auth/register, /api/auth/login
│   └── TaskController.java       ← /api/tasks CRUD endpoints
├── dto/
│   ├── TaskStatus.java           ← standalone enum: TODO, IN_PROGRESS, DONE (decoupled from entity)
│   ├── request/
│   │   ├── LoginRequest.java     ← email + password input
│   │   ├── RegisterRequest.java  ← name + email + password input
│   │   └── TaskRequest.java      ← title + description + status + dueDate input
│   └── response/
│       ├── AppResponse.java      ← universal wrapper { success, message, data }
│       ├── AuthResponse.java     ← token + email + name
│       ├── PagedResponse.java    ← paginated wrapper { content, page, totalElements... }
│       └── TaskResponse.java     ← task output shape
├── entity/
│   ├── Task.java                 ← tasks table in DB
│   └── User.java                 ← users table in DB
├── exception/
│   ├── DuplicateEmailException.java
│   ├── GlobalExceptionHandler.java ← catches all exceptions, returns clean JSON
│   ├── ResourceNotFoundException.java
│   └── UnauthorizedException.java
├── repository/
│   ├── TaskRepository.java       ← paginated DB queries for tasks
│   └── UserRepository.java       ← DB queries for users
├── security/
│   ├── JwtFilter.java            ← runs on every request, reads token
│   ├── JwtUtil.java              ← create / validate / read JWT tokens
│   └── UserDetailsServiceImpl.java ← loads user from DB for Spring Security
└── service/
    ├── AuthService.java          ← register + login business logic
    └── TaskService.java          ← CRUD + search + pagination logic
```

---

## REQUEST LIFECYCLE — What happens when a request hits the API

```
HTTP Request
    ↓
[JwtFilter]           ← reads Authorization header, validates token, sets user in SecurityContext
    ↓
[SecurityConfig]      ← checks if route is permitAll or needs authentication
    ↓
[Controller]          ← receives request, calls service
    ↓
[Service]             ← business logic, calls repository
    ↓
[Repository]          ← SQL query to MySQL database
    ↓
[Entity]              ← Java object mapped to DB row
    ↓
[Service]             ← maps entity → DTO (TaskResponse)
    ↓
[Controller]          ← wraps in AppResponse, returns ResponseEntity
    ↓
HTTP Response (JSON)
```

If anything throws an exception at any step → `GlobalExceptionHandler` catches it and returns clean JSON.

---

## PACKAGE 1 — entity/

### What is an entity?
A Java class that maps directly to a database table.
Each field = a column. Each object = a row.
Spring Data JPA + Hibernate handle the SQL automatically.

---

### User.java

```java
@Entity          // tells JPA: this class is a DB table
@Table(name = "users")  // the actual table name in MySQL
@Getter          // Lombok: generates getEmail(), getName(), etc.
@Setter          // Lombok: generates setEmail(), setName(), etc.
public class User {

    @Id                                          // this is the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // auto-increment (1, 2, 3...)
    private Long id;

    @Column(nullable = false, unique = true)     // NOT NULL + UNIQUE constraint in DB
    private String email;

    @Column(nullable = false)                    // NOT NULL
    private String password;                     // stored as BCrypt hash, never plain text

    @Column(nullable = false)
    private String name;

    private LocalDateTime createdAt = LocalDateTime.now();
}
```

**Key annotations:**
- `@Entity` — marks class as a JPA-managed table
- `@Table(name="users")` — without this, JPA uses class name as table name
- `@Id` — every entity must have a primary key
- `@GeneratedValue(strategy = GenerationType.IDENTITY)` — DB handles auto-increment
- `@Column(nullable = false)` — adds NOT NULL constraint to the column
- `@Column(unique = true)` — adds UNIQUE constraint (no two rows can have same value)

---

### Task.java

```java
@Entity
@Table(name = "tasks")
@Getter
@Setter
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;     // no @Column = nullable by default

    @Enumerated(EnumType.STRING)    // stores "TODO"/"IN_PROGRESS"/"DONE" as text in DB
    private Status status = Status.TODO;   // default value

    private LocalDate dueDate;      // date only (no time) — e.g. 2026-12-01

    @ManyToOne                      // Many tasks can belong to ONE user
    @JoinColumn(name = "user_id")   // creates user_id foreign key column in tasks table
    private User user;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist                     // JPA lifecycle hook: runs BEFORE INSERT into DB
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate                      // JPA lifecycle hook: runs BEFORE UPDATE in DB
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum Status {
        TODO, IN_PROGRESS, DONE
    }
}
```

**Key annotations:**
- `@Enumerated(EnumType.STRING)` — without this, JPA stores 0, 1, 2 (ordinal). STRING stores "TODO" — readable and safe if enum order changes
- `@ManyToOne` — relationship annotation. One User has many Tasks
- `@JoinColumn(name = "user_id")` — the foreign key column name in the tasks table
- `@PrePersist` — lifecycle callback before first save
- `@PreUpdate` — lifecycle callback before every update

**Why LocalDate for dueDate, not LocalDateTime?**
Due date is just a calendar date (2026-12-01). No need for time.
LocalDate = date only. LocalDateTime = date + time.

---

## PACKAGE 2 — dto/

### What is a DTO?
Data Transfer Object. A class used to carry data between layers.
- **Request DTOs** → shape of data coming IN (from client)
- **Response DTOs** → shape of data going OUT (to client)

Why not just return the entity directly? Because entities have sensitive fields (password), internal fields (user_id), and you'd leak DB structure to the outside world.

---

### dto/TaskStatus.java

```java
public enum TaskStatus {
    TODO, IN_PROGRESS, DONE
}
```

This is a standalone enum in the DTO package — separate from `Task.Status` in the entity.

**Why separate?** The entity enum is tied to DB storage. The DTO enum is the API contract. If you ever rename a DB value, the API contract stays stable. The service maps between them: `TaskStatus.valueOf(task.getStatus().name())`.

---

### dto/request/RegisterRequest.java

```java
@Data   // Lombok: generates getters + setters + toString + equals + hashCode
public class RegisterRequest {

    @NotBlank(message = "Name is required")     // fails if null or ""
    private String name;

    @Email(message = "Invalid email format")    // validates email pattern
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
```

**Key annotations:**
- `@Data` — Lombok shortcut. Equivalent to `@Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor`
- `@NotBlank` — rejects null AND empty strings AND strings that are only whitespace. Stricter than `@NotNull`
- `@Email` — checks format (must have @ and domain). Does NOT check if email actually exists
- `@Size(min = 8)` — string length must be >= 8

These annotations do nothing by themselves. They fire only when `@Valid` is used on the controller method parameter.

---

### dto/request/TaskRequest.java

```java
@Data
public class TaskRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String status;    // kept as String so we can validate + convert in service

    @Future(message = "Due date must be a future date")   // date must be after today
    @JsonFormat(pattern = "yyyy-MM-dd")                   // tells Jackson how to parse the date from JSON
    private LocalDate dueDate;
}
```

**Key annotations:**
- `@Future` — validation: date must be strictly in the future. Today's date fails
- `@JsonFormat(pattern = "yyyy-MM-dd")` — tells Jackson how to deserialize the date string. Without this, Jackson doesn't know how to convert `"2026-12-01"` into a `LocalDate`

---

### dto/response/AppResponse.java

```java
@Getter
@AllArgsConstructor
public class AppResponse {

    private boolean success;
    private String message;
    private Object data;

    // Static factory methods — cleaner than new AppResponse(true, "msg", data)
    public static AppResponse success(String message, Object data) {
        return new AppResponse(true, message, data);
    }

    public static AppResponse error(String message) {
        return new AppResponse(false, message, null);
    }
}
```

Every API response follows the same shape:
```json
{
  "success": true,
  "message": "Task created",
  "data": { ...actual payload... }
}
```
This is the **envelope pattern**. The client always knows where to look.

**Annotations:**
- `@Getter` — generates only getters (no setters — response objects are read-only)
- `@AllArgsConstructor` — generates `AppResponse(boolean success, String message, Object data)`

---

### dto/response/AuthResponse.java

```java
@Getter
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String email;
    private String name;
}
```

Returns all three fields after login/register so the client knows who just authenticated — without needing a separate profile API call. Most tutorials only return the token. Returning email + name is the professional move.

---

### dto/response/TaskResponse.java

```java
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)   // omits null fields from JSON output
public class TaskResponse {

    private Long id;
    private String title;
    private String description;
    private TaskStatus status;      // uses DTO enum, not entity enum
    private LocalDate dueDate;      // omitted from JSON if null (no dueDate set)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Key annotations:**
- `@JsonInclude(NON_NULL)` — if `dueDate` is null, it's excluded from the response entirely instead of showing `"dueDate": null`
- `TaskStatus` — uses the DTO enum, not `Task.Status`. Decouples the API contract from the entity

---

### dto/response/PagedResponse.java

```java
@Getter
public class PagedResponse<T> {   // <T> = generic — works with any type (TaskResponse, etc.)

    private final List<T> content;       // the actual items for this page
    private final int page;              // current page number (0-based)
    private final int size;              // items per page
    private final long totalElements;    // total items across ALL pages
    private final int totalPages;        // how many pages exist
    private final boolean last;          // true if this is the last page

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

Spring Data returns a `Page<T>` internally. This class converts it into a clean JSON-friendly shape.

---

## PACKAGE 3 — repository/

### What is a repository?
An interface that handles all database operations.
You extend `JpaRepository` and Spring generates the SQL automatically.

---

### UserRepository.java

```java
public interface UserRepository extends JpaRepository<User, Long> {
    // Built-in: save(), findById(), findAll(), delete(), count(), existsById()

    Optional<User> findByEmail(String email);
    // Spring Data reads the method name and generates:
    // SELECT * FROM users WHERE email = ?
}
```

**How Spring Data query derivation works:**
The method name IS the query. Spring parses it at startup:
- `findBy` → SELECT WHERE
- `Email` → the field name in User entity
- Returns `Optional<User>` → safe — won't throw NullPointerException if not found

---

### TaskRepository.java

```java
public interface TaskRepository extends JpaRepository<Task, Long> {

    // All queries are paginated — adds LIMIT + OFFSET + ORDER BY to the query
    Page<Task> findByUser(User user, Pageable pageable);
    // SELECT * FROM tasks WHERE user_id = ? LIMIT ? OFFSET ?

    Page<Task> findByUserAndStatus(User user, Status status, Pageable pageable);
    // SELECT * FROM tasks WHERE user_id = ? AND status = ?

    Page<Task> findByUserAndTitleContainingIgnoreCase(User user, String keyword, Pageable pageable);
    // SELECT * FROM tasks WHERE user_id = ? AND UPPER(title) LIKE UPPER('%keyword%')

    Page<Task> findByUserAndStatusAndTitleContainingIgnoreCase(User user, Status status, String keyword, Pageable pageable);
    // All three filters combined
}
```

**Keyword breakdown:**
- `Containing` → SQL LIKE '%value%'
- `IgnoreCase` → case-insensitive match
- `And` → multiple WHERE conditions
- `Pageable` parameter → adds LIMIT + OFFSET + ORDER BY to the query

Only paginated queries are defined here. Non-paginated list methods were removed — they were never called and just added noise.

---

## PACKAGE 4 — service/

### What is a service?
The business logic layer. Controllers call services. Services call repositories.
Never put business logic in the controller. Never put business logic in the repository.

---

### AuthService.java

```java
@Slf4j
@Service
public class AuthService {

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Register attempt for email: {}", request.getEmail());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("Registration failed - email already exists: {}", request.getEmail());
            throw new DuplicateEmailException("Email already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        log.info("User registered successfully: {}", request.getEmail());
        return new AuthResponse(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.getName());
    }

    @Transactional(readOnly = true)   // read-only: no writes, DB can skip dirty tracking
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        // Same error message for both cases — prevents user enumeration attacks
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        log.info("Login successful for email: {}", request.getEmail());
        return new AuthResponse(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.getName());
    }
}
```

**What is user enumeration?**
If login returns "email not found" for unknown emails and "wrong password" for known ones, an attacker can figure out which emails are registered. Same error message for both cases prevents this.

---

### TaskService.java

```java
@Slf4j
@Service
public class TaskService {

    // Maps entity Status → DTO TaskStatus — decouples API contract from entity
    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                TaskStatus.valueOf(task.getStatus().name()),
                task.getDueDate(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> getAllTasks(String email, String status, String search, int page, int size) {
        User user = getUser(email);
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasSearch = search != null && !search.isBlank();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Search input excluded from logs — user-provided data can contain PII
        log.info("Fetching tasks for user: {} | status={} | page={} | size={}", email, status, page, size);

        Page<Task> taskPage;
        if (hasStatus && hasSearch) {
            taskPage = taskRepository.findByUserAndStatusAndTitleContainingIgnoreCase(
                    user, Status.valueOf(status.toUpperCase()), search, pageable);
        } else if (hasStatus) {
            taskPage = taskRepository.findByUserAndStatus(user, Status.valueOf(status.toUpperCase()), pageable);
        } else if (hasSearch) {
            taskPage = taskRepository.findByUserAndTitleContainingIgnoreCase(user, search, pageable);
        } else {
            taskPage = taskRepository.findByUser(user, pageable);
        }

        return new PagedResponse<>(taskPage.map(this::toResponse));
        // taskPage.map() converts Page<Task> → Page<TaskResponse>
        // this::toResponse is a method reference
    }

    @Transactional
    public TaskResponse createTask(String email, TaskRequest request) {
        User user = getUser(email);
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setDueDate(request.getDueDate());
        task.setUser(user);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            task.setStatus(Status.valueOf(request.getStatus().toUpperCase()));
            // .toUpperCase() handles "done" → "DONE" so valueOf() doesn't throw
        }
        return toResponse(taskRepository.save(task));
        // save() → INSERT. Returns the saved entity with the generated ID.
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(String email, Long id) {
        User user = getUser(email);
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // Ownership check: the task exists — but does it belong to THIS user?
        if (!task.getUser().getId().equals(user.getId())) {
            log.warn("Access denied: user {} tried to access task id: {}", email, id);
            throw new UnauthorizedException("Access denied");
        }
        return toResponse(task);
    }

    @Transactional
    public TaskResponse updateTask(String email, Long id, TaskRequest request) { ... }

    @Transactional
    public void deleteTask(String email, Long id) { ... }
}
```

**@Transactional(readOnly = true) explained:**
- Tells the database this transaction will only read, not write
- DB can skip dirty tracking and write locks — minor performance gain
- Read methods (getAllTasks, getTask, login) → always readOnly
- Write methods (createTask, updateTask, deleteTask) → plain @Transactional

---

## PACKAGE 5 — security/

### JwtUtil.java

```java
@Component
public class JwtUtil {

    @Value("${jwt.secret}")      // reads from application.properties or environment variable
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
        // Output: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkYXJzaGFuQGdtYWlsLmNvbSJ9.SIGNATURE"
        //          ^^^ header (alg)       ^^^ payload (Base64, not encrypted)     ^^^ signature
    }

    public String extractEmail(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();   // "sub" field = email
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;   // expired, tampered, wrong signature → invalid
        }
    }
}
```

**What is JWT?**
JSON Web Token. Three Base64-encoded parts separated by dots:
1. **Header** — algorithm (HS256) and type (JWT)
2. **Payload** — claims: subject (email), issuedAt, expiration
3. **Signature** — HMAC of header + payload using your secret key

The signature is what makes it tamper-proof. If someone changes the payload, the signature won't match.

**@Value:** reads values from `application.properties` or env vars at startup. `${jwt.secret}` maps to `jwt.secret=...` in properties.

---

### JwtFilter.java

```java
@Component
public class JwtFilter extends OncePerRequestFilter {
    // OncePerRequestFilter = guaranteed to run exactly once per HTTP request

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        // Reads: "Bearer eyJhbGciOiJIUzI1NiJ9..."

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);  // remove "Bearer " prefix

            if (jwtUtil.isTokenValid(token)) {
                String email = jwtUtil.extractEmail(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                    );
                // "this user is authenticated"

                SecurityContextHolder.getContext().setAuthentication(authToken);
                // This is how @AuthenticationPrincipal works in controllers
            }
        }

        filterChain.doFilter(request, response);
        // MUST call this — passes the request to the next filter/controller
    }
}
```

**Flow:**
1. Request arrives → JwtFilter reads the token
2. Validates + extracts email → loads UserDetails from DB
3. Sets authentication in SecurityContextHolder
4. Controller reads it via `@AuthenticationPrincipal`

If no token or invalid token → SecurityContext stays empty → SecurityConfig blocks with 401.

---

### UserDetailsServiceImpl.java

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                new ArrayList<>()   // empty = no roles
        );
        // Returns Spring Security's User — not your entity User
    }
}
```

Bridge between your `User` entity and Spring Security. Spring Security knows `UserDetails`, not your `User.java`. This class translates one into the other.

---

## PACKAGE 6 — config/

### SecurityConfig.java

```java
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless JWT API — CSRF not needed (no browser sessions or cookies)
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
        // BCrypt is deliberately slow — prevents brute force
        // encode() generates a different hash each time (salt)
        // matches(raw, hash) rehashes and compares — never decrypts
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

**@Configuration vs @Component:**
Both register a class as a Spring bean, but `@Configuration` is for bean definition classes. Methods with `@Bean` inside `@Configuration` are proxied — Spring ensures each `@Bean` method is called only once.

---

### SwaggerConfig.java

```java
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Task Management API")
                        .version("1.0.0")
                        .contact(new Contact().name("Darshan Siddarth").email("darshansiddarth05@gmail.com")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                // Applies JWT security globally — all endpoints show the lock icon
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```

---

## PACKAGE 7 — controller/

### What is a controller?
The entry point for HTTP requests. Receives request, calls service, returns response.
No business logic here. Thin layer — validate, delegate, respond.

---

### AuthController.java

```java
@RestController      // = @Controller + @ResponseBody (return values serialized to JSON)
@RequestMapping("/api/auth")
@Tag(name = "Authentication")   // Swagger: groups endpoints under "Authentication"
public class AuthController {

    @PostMapping("/register")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ResponseEntity<AppResponse> register(@Valid @RequestBody RegisterRequest request) {
        // @Valid — triggers Bean Validation on RegisterRequest
        // @RequestBody — deserializes JSON body into RegisterRequest object
        // ResponseEntity — lets you control HTTP status code

        AuthResponse authResponse = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)   // 201
                .body(AppResponse.success("User registered successfully", authResponse));
    }

    @PostMapping("/login")
    public ResponseEntity<AppResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(AppResponse.success("Login successful", authResponse));
    }
}
```

---

### TaskController.java

```java
@RestController
@RequestMapping("/api/tasks")
@SecurityRequirement(name = "Bearer Authentication")   // Swagger: lock icon on all endpoints
public class TaskController {

    @GetMapping
    public ResponseEntity<AppResponse> getAllTasks(
            @AuthenticationPrincipal UserDetails userDetails,
            // Reads the authenticated user from SecurityContext (set by JwtFilter)
            // userDetails.getUsername() = the email from the JWT

            @RequestParam(required = false) String status,   // ?status=TODO (optional)
            @RequestParam(required = false) String search,   // ?search=keyword (optional)
            @RequestParam(defaultValue = "0") int page,      // ?page=0 (default 0)
            @RequestParam(defaultValue = "10") int size) {   // ?size=10 (default 10)

        if (size > 50) size = 50;   // cap to prevent abuse
        PagedResponse<TaskResponse> result = taskService.getAllTasks(...);
        return ResponseEntity.ok(AppResponse.success("Tasks retrieved", result));
    }

    @PostMapping
    public ResponseEntity<AppResponse> createTask(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TaskRequest request) {
        TaskResponse task = taskService.createTask(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AppResponse.success("Task created", task));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppResponse> getTask(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) { ... }   // {id} from URL path → Long id parameter

    @PutMapping("/{id}")
    public ResponseEntity<AppResponse> updateTask(...) { ... }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        taskService.deleteTask(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();   // 204 No Content — correct REST for DELETE
    }
}
```

**HTTP methods:**
- `@GetMapping` → READ (safe, no side effects)
- `@PostMapping` → CREATE (sends body, creates resource)
- `@PutMapping` → UPDATE (replaces entire resource)
- `@DeleteMapping` → DELETE

**HTTP status codes:**
- `201 Created` → POST (resource created)
- `200 OK` → GET, PUT (successful read/update)
- `204 No Content` → DELETE (success, nothing to return)

**@PathVariable vs @RequestParam:**
- `@PathVariable` → from URL path: `/api/tasks/5` — the `5` is extracted
- `@RequestParam` → from query string: `/api/tasks?status=TODO` — `status` is extracted

---

## PACKAGE 8 — exception/

### GlobalExceptionHandler.java

```java
@RestControllerAdvice   // = @ControllerAdvice + @ResponseBody
                        // Catches exceptions thrown anywhere in controllers/services
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<AppResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AppResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    // Thrown by @Valid when Bean Validation fails
    public ResponseEntity<AppResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()                           // safe — no IndexOutOfBoundsException
                .map(err -> err.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(AppResponse.error(message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    // Thrown when /api/tasks/abc is called and "abc" can't convert to Long
    public ResponseEntity<AppResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AppResponse.error("Invalid ID format: '" + ex.getValue() + "' is not a valid number"));
    }

    @ExceptionHandler(Exception.class)
    // Catches EVERYTHING else — last resort
    public ResponseEntity<AppResponse> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AppResponse.error("Something went wrong"));
        // Never expose the real exception message to the client — security risk
    }
}
```

**Without GlobalExceptionHandler:** exceptions return Spring's default HTML error page or JSON with stacktraces. With it: every error returns your clean `AppResponse` JSON.

**Why `stream().findFirst()` instead of `.get(0)`?**
`.get(0)` throws `IndexOutOfBoundsException` if the list is empty. `stream().findFirst()` is null-safe — always returns something.

---

## LOMBOK ANNOTATIONS — Complete Reference

| Annotation | What it generates |
|---|---|
| `@Getter` | `getField()` for every field |
| `@Setter` | `setField(value)` for every field |
| `@Data` | `@Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor` |
| `@AllArgsConstructor` | Constructor with ALL fields as parameters |
| `@NoArgsConstructor` | Empty constructor (no params) |
| `@RequiredArgsConstructor` | Constructor for `final` fields only |
| `@Slf4j` | `private static final Logger log = LoggerFactory.getLogger(ClassName.class)` |
| `@Builder` | Builder pattern — `Task.builder().title("x").build()` |

---

## SLF4J LOGGING — Complete Reference

SLF4J = Simple Logging Facade for Java. Abstraction layer — actual engine is Logback (Spring Boot default).

```java
@Slf4j   // on class — generates the log field
public class TaskService {

    log.info("normal event: {}", value);         // INFO — business events
    log.warn("suspicious: {}", value);           // WARN — auth failures, access denied
    log.error("something broke: {}", message);  // ERROR — caught exceptions

    // {} placeholder — never concatenate in log calls
    log.info("User: " + email);      // BAD — string built even if logging is off
    log.info("User: {}", email);     // GOOD — string built only if INFO is enabled
}
```

**What to log:** register, login success/failure, task created, deleted, access denied.
**What NOT to log:** passwords, JWT tokens, user-provided search input (PII risk).

---

## SPRING ANNOTATIONS — Quick Reference

| Annotation | Layer | Purpose |
|---|---|---|
| `@SpringBootApplication` | Main | Bootstraps everything — component scan + auto-configuration |
| `@RestController` | Controller | HTTP endpoint class, returns JSON |
| `@RequestMapping` | Controller | Base URL prefix |
| `@GetMapping / @PostMapping / @PutMapping / @DeleteMapping` | Controller | HTTP method handler |
| `@RequestBody` | Controller | Deserialize JSON request body |
| `@PathVariable` | Controller | Extract value from URL path |
| `@RequestParam` | Controller | Extract query parameter from URL |
| `@AuthenticationPrincipal` | Controller | Get current authenticated user |
| `@Valid` | Controller | Trigger Bean Validation |
| `@Service` | Service | Business logic bean |
| `@Transactional` | Service | Wrap method in DB transaction (write operations) |
| `@Transactional(readOnly=true)` | Service | Read-only transaction — no write locks, DB optimization |
| `@Entity` | Entity | JPA-managed DB table |
| `@Table` | Entity | Specify table name |
| `@Id` | Entity | Primary key |
| `@GeneratedValue` | Entity | Auto-increment strategy |
| `@Column` | Entity | Column constraints |
| `@ManyToOne / @OneToMany` | Entity | Relationship mapping |
| `@JoinColumn` | Entity | Foreign key column |
| `@Enumerated` | Entity | How to store enum in DB |
| `@PrePersist / @PreUpdate` | Entity | Lifecycle hooks |
| `@Component` | Any | Generic Spring bean |
| `@Configuration` | Config | Bean definition class |
| `@Bean` | Config | Register method return value as Spring bean |
| `@Value` | Any | Inject value from properties/env vars |

---

## BEAN VALIDATION ANNOTATIONS

| Annotation | Checks |
|---|---|
| `@NotNull` | not null |
| `@NotBlank` | not null, not empty, not whitespace-only |
| `@NotEmpty` | not null, not empty (allows whitespace) |
| `@Email` | valid email format |
| `@Size(min, max)` | string length or collection size |
| `@Min(value)` | number >= value |
| `@Max(value)` | number <= value |
| `@Future` | date must be after now |
| `@Past` | date must be before now |
| `@Pattern(regexp)` | matches regex |

These fire ONLY when `@Valid` is used on the parameter in the controller.
When validation fails → `MethodArgumentNotValidException` → caught by `GlobalExceptionHandler`.

---

## HTTP STATUS CODES USED IN THIS PROJECT

| Code | Name | Used for |
|---|---|---|
| 200 | OK | Successful GET, PUT |
| 201 | Created | Successful POST (resource created) |
| 204 | No Content | Successful DELETE (nothing to return) |
| 400 | Bad Request | Validation failure, invalid format |
| 401 | Unauthorized | Missing/invalid JWT, wrong password, not your task |
| 404 | Not Found | Task or user doesn't exist |
| 409 | Conflict | Email already registered |
| 500 | Internal Server Error | Unexpected exceptions |

---

## application.properties — Key Settings

```properties
# Database (uses env vars for Railway, falls back for local dev)
spring.datasource.url=${DATABASE_URL:jdbc:mysql://localhost:3306/taskdb}
spring.datasource.username=${DATABASE_USERNAME:root}
spring.datasource.password=${DATABASE_PASSWORD:yourpassword}

# JPA
spring.jpa.hibernate.ddl-auto=update   # creates/updates tables automatically on startup
spring.jpa.show-sql=false              # don't print SQL to console in production

# JWT
jwt.secret=${JWT_SECRET:your-secret-key}
jwt.expiration=${JWT_EXPIRATION:86400000}   # 86400000ms = 24 hours

# Port (Railway sets PORT env var)
server.port=${PORT:8080}

# Swagger
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/v3/api-docs

# Actuator — expose only health and info, never everything
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=never
```

`${VAR_NAME:default}` syntax: use environment variable if set, else use the default.
Same code works locally AND on Railway without changing files.

---

## COMPLETE FLOW SUMMARY

### Register:
```
POST /api/auth/register
  → AuthController.register()
  → @Valid checks RegisterRequest
  → AuthService.register()  [@Transactional]
  → Check duplicate email in DB
  → BCrypt hash the password
  → Save User to DB
  → Generate JWT with email as subject
  → Return AuthResponse { token, email, name }  ← client gets all three, no extra call needed
```

### Login:
```
POST /api/auth/login
  → AuthController.login()
  → AuthService.login()  [@Transactional(readOnly=true)]
  → Find user by email
  → Same error if email OR password wrong — prevents enumeration
  → BCrypt compare raw password to stored hash
  → Generate JWT
  → Return AuthResponse { token, email, name }
```

### Every authenticated request (e.g. Create Task):
```
POST /api/tasks  +  Authorization: Bearer <token>
  → JwtFilter.doFilterInternal()
  → Extract token from header
  → JwtUtil.isTokenValid() — verify signature + expiry
  → JwtUtil.extractEmail() — get email from payload
  → UserDetailsServiceImpl.loadUserByUsername() — load from DB
  → Set authentication in SecurityContextHolder
  → SecurityConfig allows request (authenticated)
  → TaskController.createTask()
  → @AuthenticationPrincipal pulls user from SecurityContext
  → @Valid checks TaskRequest
  → TaskService.createTask()  [@Transactional]
  → Save Task to DB (triggers @PrePersist → sets createdAt/updatedAt)
  → Map Task entity → TaskResponse DTO (entity Status → DTO TaskStatus)
  → Wrap in AppResponse
  → Return 201
```

### Delete Task:
```
DELETE /api/tasks/5  +  Authorization: Bearer <token>
  → [JWT validation — same as above]
  → TaskController.deleteTask()
  → TaskService.deleteTask()  [@Transactional]
  → Load task by ID → 404 if not found
  → Ownership check → 401 if not your task
  → taskRepository.delete(task)
  → Return 204 No Content (no body — correct REST semantics)
```
