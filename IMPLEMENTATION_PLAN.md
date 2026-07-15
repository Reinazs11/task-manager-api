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
| 3. Users Infra + Auth | ✅ DONE | `6811fe1`, `d0c08f1`, `32525ba` |
| 4. Tasks Domain | ✅ DONE | `955ca31`, `a4e6b6c` |
| 5. Tasks Infra + API | ✅ DONE | `2be7414` |
| 6. Cross-cutting | ⬜ NEXT | — |
| 7. Polish and Release | ⬜ Pending | — |

**Legend:** ✅ DONE · ⏳ NEXT/IN PROGRESS · ⬜ PENDING
