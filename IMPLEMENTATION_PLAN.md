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

## Step 3 — Users Infrastructure + Authentication ⏳ IN PROGRESS

**Goal:** Persist users and authenticate them via JWT. The most complex step —
security has many moving parts and silent failures become vulnerabilities.

**Divided into two sessions for focus:**

### Session 3A — Persistence + Password Hashing ✅ DONE
- `UserEntity` (JPA `@Entity`, uses Lombok for getters/setters)
- `UserJpaRepository` (Spring Data JPA interface)
- `UserRepositoryImpl` — implements domain `UserRepository` port
- `UserMapper` (MapStruct) — `User` ↔ `UserEntity` ↔ DTOs
- `PasswordHasher` interface (domain port) + `BCryptPasswordHasher` (infra)
- `users` domain: `UserRepository` port interface, `UserAlreadyExistsException`
- `Password.fromHash()` factory for reconstituting from stored hash

### Session 3B — Security + JWT + Endpoints ⏳ NEXT
- `JwtService` — generate, sign, validate access (15min) + refresh (7d) tokens
- `SecurityConfig` — stateless Spring Security, CSRF disabled, session policy
- `JwtAuthenticationFilter` — extracts JWT from `Authorization: Bearer`, validates, sets auth context
- `application/RegisterUserUseCase` — orchestrates registration (hash password, check duplicates, persist)
- `application/LoginUseCase` — validates credentials, issues tokens
- `api/AuthController` — `POST /api/v1/auth/register` and `POST /api/v1/auth/login`
- Request DTOs: `RegisterRequest`, `LoginRequest` (with Bean Validation)
- Response DTOs: `TokenResponse`, `UserResponse`
- Integration tests with Testcontainers (real PostgreSQL via Docker)

**Acceptance criteria:**
- [x] Persistence layer works against real PostgreSQL (Testcontainers)
- [x] Password hashing with BCrypt (cost 10)
- [ ] Register a user → persisted with BCrypt-hashed password
- [ ] Login with valid credentials → returns access + refresh JWT
- [ ] Login with wrong password → 401
- [ ] Register with duplicate email → 409 Conflict
- [ ] Protected endpoint rejects requests without token
- [ ] Protected endpoint accepts requests with valid token
- [ ] Integration tests run against real PostgreSQL (Testcontainers)
- [ ] All new code has unit + integration test coverage

---

## Step 4 — Tasks Domain

**Goal:** Pure domain layer for the tasks context (projects + tasks).

**Implements:**
- `TaskStatus` enum (TODO, IN_PROGRESS, DONE) — possibly sealed interface
- `Priority` enum (LOW, MEDIUM, HIGH)
- `ProjectId`, `TaskId` value objects
- `Project` entity (aggregate root) — belongs to a User
- `Task` entity (belongs to a Project) — title, description, status, priority
- Domain rules:
  - Task cannot be created without a project
  - Status transitions enforced (e.g., DONE cannot go back to TODO without explicit action)
  - Project owner validation (tasks only visible to project owner)
- Domain exceptions: `ProjectNotFoundException`, `TaskNotFoundException`,
  `InvalidStatusTransitionException`

**Acceptance criteria:**
- [ ] Pure domain, no framework dependencies
- [ ] TDD: status transition rules fully tested
- [ ] Entities enforce invariants on construction and mutation
- [ ] All value objects immutable and validated

---

## Step 5 — Tasks Infrastructure + API CRUD

**Goal:** Persist projects and tasks, expose CRUD endpoints with pagination and filtering.

**Implements:**
- `ProjectEntity`, `TaskEntity` (JPA entities with relationships)
- `ProjectJpaRepository`, `TaskJpaRepository`
- Mappers (MapStruct) for Project and Task
- Application services: `CreateProjectUseCase`, `CreateTaskUseCase`,
  `ListTasksUseCase`, `UpdateTaskStatusUseCase`, etc.
- Controllers:
  - `POST /projects`, `GET /projects` (paginated), `GET/PUT/DELETE /projects/{id}`
  - `POST /projects/{id}/tasks`, `GET /projects/{id}/tasks` (with filters:
    `?status=DONE&page=0&size=10`)
  - `GET/PUT/DELETE /tasks/{id}`
- Pagination via Spring Data `Pageable`
- Bean Validation on all request DTOs

**Acceptance criteria:**
- [ ] Full CRUD for projects and tasks
- [ ] Pagination working on list endpoints
- [ ] Filtering by status works
- [ ] Users can only access their own projects/tasks (authorization)
- [ ] Integration tests cover happy path + authorization failures

---

## Step 6 — Cross-cutting Concerns

**Goal:** Production-readiness concerns that span the whole application.

**Implements:**
- `GlobalExceptionHandler` (`@RestControllerAdvice`) — standardized error JSON:
  `{ timestamp, status, error, message, path, details }`
- Custom exception → HTTP status mapping (404, 409, 400, 401, 403, 500)
- OpenAPI documentation complete:
  - `@Tag`, `@Operation`, `@ApiResponse` on all controllers
  - API docs at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`
  - Security scheme documented (Bearer JWT)
- Logging strategy: SLF4J structured logs at appropriate levels
- Request/response logging for debugging (sanitized — no passwords/tokens)
- API versioning decision: `/api/v1/...` prefix applied to all endpoints

**Acceptance criteria:**
- [ ] All errors return the standardized JSON shape
- [ ] Swagger UI fully documents every endpoint
- [ ] Logs are useful for debugging without leaking secrets
- [ ] API versioning consistent across all routes

---

## Step 7 — Polish and Release

**Goal:** Final review pass and GitHub publication.

**Implements:**
- Full JaCoCo coverage review — fill gaps, remove dead code
- Architecture review: verify DDD layering (no domain → infra dependencies)
- README final polish:
  - Architecture diagram (ASCII or image)
  - Full endpoint reference table
  - How-to-run for both Docker and IDE
  - Testing instructions
  - Tech stack rationale
- `LICENSE` file (MIT or similar)
- Clean git history (rebase if needed, meaningful commits)
- GitHub repository created and pushed
- GitHub repo description + topics (java, spring-boot, rest-api, jwt, postgresql,
  testcontainers, ddd, tdd)
- Optional: GitHub Actions CI workflow (run tests on push)

**Acceptance criteria:**
- [ ] Coverage ≥ 80% with meaningful tests (no filler)
- [ ] No compiler warnings
- [ ] README explains everything a reviewer needs
- [ ] Project published on GitHub
- [ ] Repository is presentable as a portfolio piece

---

## Progress Tracking

| Step | Status | Commit |
|------|--------|--------|
| 1. Bootstrap | ✅ DONE | `94954b9`, `6f2cf51`, `d09b795` |
| 2. Users Domain | ✅ DONE | `c0d907c`, `7a647e9` |
| 3. Users Infra + Auth | ⏳ IN PROGRESS (3A done) | `6811fe1` |
| 4. Tasks Domain | ⬜ Pending | — |
| 5. Tasks Infra + API | ⬜ Pending | — |
| 6. Cross-cutting | ⬜ Pending | — |
| 7. Polish and Release | ⬜ Pending | — |

**Legend:** ✅ DONE · ⏳ NEXT/IN PROGRESS · ⬜ PENDING
