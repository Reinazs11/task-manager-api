# Implementation Plan — Task Manager API

> Living document. Updated after each completed step.
> See `AGENTS.md` for coding standards and workflow rules.

## Overview

Build a production-grade REST API for task management with JWT authentication,
following DDD (simplified), Clean Code, and TDD. Target: portfolio piece that
demonstrates professional backend skills for Upwork clients.

**Estimated total:** ~9 working sessions at 4-6h/day (~2 weeks).

---

## Step 1 — Project Bootstrap ✅ DONE

**Goal:** Stand up the project skeleton that compiles, tests, and runs.

**Implements:**
- `pom.xml` with Spring Boot 3.3, Spring Security, Data JPA, Validation,
  PostgreSQL driver, JWT (jjwt), Lombok, MapStruct, springdoc-openapi
- Test dependencies: JUnit 5, Mockito, AssertJ, Testcontainers, spring-security-test
- JaCoCo plugin configured (80% minimum coverage, build fails below)
- `TaskManagerApplication` entry point
- `application.yml` with datasource, JWT, and profile configuration via env vars
- `Dockerfile` (multi-stage build) and `docker-compose.yml` (PostgreSQL + app)
- `.gitignore`, `.env.example`, `README.md`, `AGENTS.md`
- Maven folder structure + smoke test

**Acceptance criteria:**
- [x] `./mvnw clean test` runs with BUILD SUCCESS
- [x] `docker compose up postgres` starts PostgreSQL on port 5432
- [x] JaCoCo report generated
- [x] Git initialized with first commit

**Commit:** `94954b9`

---

## Step 2 — Users Domain ✅ DONE

**Goal:** Build the pure domain layer for the users context (no Spring, no JPA).
The heart of DDD — business rules that don't depend on any framework.

**Implements:**
- `Email` — value object with regex validation + lowercase normalization
- `Password` — value object with strength rules (8+ chars, upper, lower, digit)
- `UserId` — value object wrapping UUID (type safety against ID confusion)
- `User` — aggregate root entity with factory methods (`create` / `reconstitute`)
- `UserAlreadyExistsException` (added in Step 3 with the application layer)

**Acceptance criteria:**
- [x] All domain classes are framework-agnostic (no Spring/JPA annotations)
- [x] Value objects are immutable and validated on construction
- [x] Entity equality based on identity (UserId), not fields
- [x] TDD: tests written first, 31 tests passing
- [x] Code review performed (found and fixed `reconstitute` timestamp bug)
- [x] All code, comments, and messages in English

**Commits:** `c0d907c`, `7a647e9`

---

## Step 3 — Users Infrastructure + Authentication ✅ DONE

**Goal:** Persist users and authenticate them via JWT. The most complex step —
security has many moving parts and silent failures become vulnerabilities.

### Session 3A — Persistence + Password Hashing ✅ DONE
- `UserEntity` (JPA `@Entity`, uses Lombok for getters/setters)
- `UserJpaRepository` (Spring Data JPA interface)
- `UserRepositoryImpl` — implements domain `UserRepository` port
- `UserMapper` (MapStruct) — `User` ↔ `UserEntity` ↔ DTOs
- `PasswordHasher` interface (domain port) + `BCryptPasswordHasher` (infra)
- `users` domain: `UserRepository` port interface, `UserAlreadyExistsException`
- `Password.fromHash()` factory for reconstituting from stored hash

### Session 3B — Security + JWT + Endpoints ✅ DONE
- `JwtService` — HS256 access (15min) + refresh (7d) tokens with jti claim
- `SecurityConfig` — stateless Spring Security, CSRF disabled, session policy
- `JwtAuthenticationFilter` — extracts JWT from `Authorization: Bearer`, validates, sets auth context
- `application/RegisterUserUseCase` — orchestrates registration (hash password, check duplicates, persist)
- `application/LoginUseCase` — validates credentials, issues tokens
- `api/AuthController` — `POST /api/v1/auth/register` and `POST /api/v1/auth/login`
- Request DTOs (records): `RegisterRequest`, `LoginRequest` (with Bean Validation)
- Response DTOs (records): `TokenResponse`, `UserResponse`
- `GlobalExceptionHandler` (basic version — refined in Step 6) for 400/401/409
- Integration tests with Testcontainers (real PostgreSQL via Docker)

**Acceptance criteria:**
- [x] Persistence layer works against real PostgreSQL (Testcontainers)
- [x] Password hashing with BCrypt (cost 10)
- [x] Register a user → persisted with BCrypt-hashed password
- [x] Login with valid credentials → returns access + refresh JWT
- [x] Login with wrong password → 401
- [x] Register with duplicate email → 409 Conflict
- [x] Integration tests run against real PostgreSQL (Testcontainers)
- [x] All new code has unit + integration test coverage

---

## Step 4 — Tasks Domain ✅ DONE

**Goal:** Pure domain layer for the tasks context (projects + tasks).

**Implements:**
- `TaskStatus` enum (TODO, IN_PROGRESS, DONE) with restricted transition graph
- `Priority` enum (LOW, MEDIUM, HIGH) with weight for sorting, MEDIUM default
- `ProjectId`, `TaskId` value objects (UUID-based, type-safe)
- `TaskTitle` value object (non-blank, max 200 chars, trimmed)
- `Project` aggregate root — owns tasks via addTask(), tasks() returns immutable view
- `Task` entity — belongs to Project, enforces status transitions
- Domain rules:
  - Task cannot be created without a project (enforced by aggregate root)
  - Status transitions enforced (TODO -> IN_PROGRESS -> DONE; DONE -> TODO forbidden)
  - Added task inherits the project's id and owner
- Domain exception: `InvalidStatusTransitionException`

**Acceptance criteria:**
- [x] Pure domain, no framework dependencies
- [x] TDD: status transition rules fully tested
- [x] Entities enforce invariants on construction and mutation
- [x] All value objects immutable and validated
- [x] tasks() returns immutable list
- [x] 48 new tests (81 -> 129 total), all passing

---

## Step 5 — Tasks Infrastructure + API CRUD ✅ DONE

**Goal:** Persist projects and tasks, expose CRUD endpoints with pagination,
filtering, and owner-based authorization.

**Implements:**
- `ProjectEntity`, `TaskEntity` (JPA with Lombok, owner_id denormalized on Task)
- `ProjectJpaRepository`, `TaskJpaRepository` (Spring Data)
- `ProjectRepositoryImpl`, `TaskRepositoryImpl` (adapters for domain ports + query ports)
- `ProjectQueryPort`, `TaskQueryPort` (application ports with pagination types —
  moved out of domain after ArchUnit caught the Spring Data leak)
- 7 use cases: CreateProject, ListProjects, GetProject, DeleteProject,
  CreateTask, ListTasks, UpdateTaskStatus (all enforce owner authorization)
- `AuthenticatedUser` helper (extracts UserId from SecurityContext)
- Controllers: ProjectController + TaskController (8 endpoints under /api/v1)
- DTOs as records: CreateProjectRequest, CreateTaskRequest,
  UpdateTaskStatusRequest, ProjectResponse, TaskResponse
- GlobalExceptionHandler updated: 403, 404, 409 for new exceptions

**Acceptance criteria:**
- [x] Full CRUD for projects and tasks
- [x] Pagination working on list endpoints
- [x] Filtering by status works
- [x] Users can only access their own projects/tasks (authorization)
- [x] Status transitions enforced (409 for invalid transitions)
- [x] Integration tests cover happy path + authorization failures
- [x] ArchUnit caught and we fixed a real architecture violation (Spring Data
      types in domain — moved to application query ports)
- [x] 26 new tests (163 -> 189 total), all passing

---

## Step 6 — Cross-cutting Concerns

**Goal:** Production-readiness concerns that span the whole application.

**Implemented in three sub-steps:**

### Step 6a — Standardized Error Handling (`059c2c8`)
- `ErrorResponse` record types the six-field contract
  `{ timestamp, status, error, message, path, details }`
- `GlobalExceptionHandler` (`@RestControllerAdvice`) maps every exception to the
  standard shape: domain exceptions (404/409/401/403), validation (400 with ALL
  field errors in `details[]`), malformed body, type mismatch, missing param,
  no-resource-found (404), 405, and a catch-all 500 that never leaks internals
- `JsonAuthenticationEntryPoint` replaces the divergent inline Security lambda
  so 401 from Spring Security and 401 from domain code produce identical bytes
- `ErrorResponseContractTest` locks all six fields per status code (anti-cheat:
  multi-error `details[]`, 500 anti-leak of stack/class names)

**Acceptance criteria:**
- [x] All errors return the standardized JSON shape
- [x] 500 never leaks the stack / exception class / internal messages
- [x] 401 from Security == 401 from domain (same six fields)

### Step 6b — OpenAPI Documentation (`564376e`)
- `OpenApiConfig` declares the `bearerAuth` HTTP/JWT security scheme
- All controllers annotated with `@Tag`, `@Operation`, `@ApiResponses`;
  protected routes carry `@SecurityRequirement("bearerAuth")`; request records
  get `@Schema` examples
- `application-prod.yml` disables `springdoc` entirely (docs do not leak in prod)
  and sets `ddl-auto=validate`
- `OpenApiDocumentationTest` locks the rendered `/v3/api-docs` contract
  (metadata, security scheme, endpoint coverage); `OpenApiProdProfileTest`
  verifies docs are offline under the `prod` profile

**Acceptance criteria:**
- [x] Swagger UI fully documents every endpoint
- [x] Security scheme (Bearer JWT) is documented
- [x] `prod` profile disables Swagger UI and api-docs

### Step 6c — Observability & Logging (`d7fd17f`)
- `CorrelationIdFilter` honors or mints `X-Request-Id`, stores it in the MDC
  (`%X{requestId}`), echoes it in the response, and clears the MDC in `finally`
  (thread-pool reuse protection)
- `SanitizingRequestLoggingFilter` emits one structured INFO line per request
  (method, URI, status, latency) and redacts `Authorization` / `Cookie` /
  `Set-Cookie` at every log level; body logging gated behind TRACE + a
  sensitive-key heuristic
- `logback-spring.xml` wires the correlation id into the pattern and sets
  levels per profile (dev / test / prod)
- Tests use Logback `ListAppender` to capture real log output and assert
  secrets are absent; integration test proves the id reaches both the response
  header and the log event MDC through the full security chain

**Acceptance criteria:**
- [x] Logs are useful for debugging without leaking secrets
- [x] Every request is traceable via a correlation id

### API versioning
- [x] `/api/v1/...` prefix applied to every route (already in place since Step 3)

---

## Step 7 — Polish and Release ✅ DONE

**Goal:** Final review pass and GitHub publication.

**Implemented in four sub-steps:**

### Step 7a — Quality Gates & Refactor de Testes
- `AbstractIntegrationTest` centralizes `@SpringBootTest`, `@AutoConfigureMockMvc`,
  MockMvc, and ObjectMapper for every full-stack integration test.
- `TestContainersConfig` exposes the PostgreSQL container as a `@Bean` with
  `@ServiceConnection`, aligning its lifecycle with Spring's ApplicationContext
  cache (fixes the `CannotCreateTransactionException` race that 12 tests hit
  when each class owned a static `@Container`).
- **Surefire/Failsafe split:** `*Test.java` (unit, fast, no Docker) vs
  `*IT.java` (integration, real PostgreSQL). Renamed 8 classes.
- Fixed a flaky JWT tamper test: tamper with the payload, not the signature
  (Base64URL padding bits made the old assertion non-deterministic).
- JaCoCo: 96.7% line coverage (gate at 80%).

### Step 7b — Schema Migration (Flyway) (`542f4da`, `bf3b16b`)
- `flyway-core` + `flyway-database-postgresql` (Flyway 10 splits per-DB support).
- `db/migration/V1__init_schema.sql` captures the schema matching the JPA
  entities with FKs, CASCADE on owner/project delete, CHECK constraints
  mirroring the domain enums, and supporting indexes.
- `ddl-auto=validate` in ALL profiles now (dev and prod). Flyway owns the schema.
- Decision documented in `AGENTS.md`.

### Step 7c — Architecture & Documentation Polish
- **ArchUnit expanded** (`35c3cd2`):
  - Cross-context isolation via `common` shared kernel (caught and fixed a real
    violation: `TaskRepositoryImpl` taking `users.domain.UserId`).
  - `@RestController`/`@Controller` only in `..api..`.
  - Spring stereotypes forbidden in `..domain..`.
  - Naming: domain classes must not end in `Controller`/`Service`/`Config`
    (`Repository` stays allowed — canonical DDD persistence port).
- **UserId moved to `common.domain`** (`1d88620`) — the shared kernel pattern
  that lets the two bounded contexts collaborate without direct dependencies.
- **JaCoCo merge** (`a1b881f`): unit + integration exec files are merged before
  the coverage gate runs, so the check reflects both suites.
- **README rewritten** (`2740fd2`): portfolio-grade with highlights, tech-stack
  table, ASCII architecture diagram, endpoint reference table, how-to-run,
  testing section, engineering-decisions table.
- **MIT `LICENSE`** and **`.dockerignore`** added.

### Step 7d — CI & Release GitHub
- **GitHub Actions workflow** (`.github/workflows/ci.yml`): builds, runs all
  tests (unit + integration with real PostgreSQL via Testcontainers), enforces
  the JaCoCo 80% gate. Uploads the coverage report as an artifact. Runs on
  push to `main`/`feat/**`/`fix/**` and on every PR to `main`.
- Published to GitHub with description and topics.

**Acceptance criteria:**
- [x] Coverage ≥ 80% with meaningful tests (no filler) — currently 96.7%
- [x] No compiler warnings
- [x] README explains everything a reviewer needs
- [x] Project published on GitHub
- [x] Repository is presentable as a portfolio piece

---

## Progress Tracking

| Step | Status | Commit |
|------|--------|--------|
| 1. Bootstrap | ✅ DONE | `94954b9`, `6f2cf51`, `d09b795` |
| 2. Users Domain | ✅ DONE | `c0d907c`, `7a647e9` |
| 3. Users Infra + Auth | ✅ DONE | `6811fe1`, `d0c08f1`, `32525ba` |
| 4. Tasks Domain | ✅ DONE | `955ca31`, `a4e6b6c` |
| 5. Tasks Infra + API | ✅ DONE | `2be7414` |
| 6a. Standardized Error Handling | ✅ DONE | `059c2c8` |
| 6b. OpenAPI Documentation | ✅ DONE | `564376e` |
| 6c. Observability & Logging | ✅ DONE | `d7fd17f` |
| 7. Polish and Release | ✅ DONE | `93e18d6`, `1165fb4`, `f616934`, `542f4da`, `bf3b16b`, `1d88620`, `35c3cd2`, `a1b881f`, `2740fd2` |

**Legend:** ✅ DONE · ⏳ NEXT/IN PROGRESS · ⬜ PENDING
