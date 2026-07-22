# Task Manager API

[![CI](https://github.com/Reinazs11/task-manager-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Reinazs11/task-manager-api/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

REST API for task and project management with JWT authentication, built with
Java 21 and Spring Boot 3 following Simplified DDD and TDD.

> Portfolio project demonstrating a production-grade backend: real PostgreSQL
> (via Testcontainers, no H2 shortcuts), strict architecture enforcement
> (ArchUnit), mutation testing (PIT), versioned schema migrations (Flyway), and
> a 96% line-coverage gate that fails the build.

---

## Highlights

- **Java 21** (records, sealed interfaces, pattern matching) on **Spring Boot 3.3**
- **Stateless JWT auth** — access token (15 min) + refresh token (7 days, rotated),
  HS256 with `iss`/`aud` enforcement
- **Token rotation endpoint** `POST /auth/refresh` — trade a refresh for a new pair
- **Anti-enumeration posture** — login, resource lookups, and refresh all collapse
  error variants into a single 401/403 so callers cannot enumerate
- **Simplified DDD** — bounded contexts (`users`, `tasks`) with `domain` /
  `application` / `infrastructure` / `api` layers
- **Testcontainers** for integration tests against **real PostgreSQL 16** (no H2)
- **Architecture tests with ArchUnit** enforce layering and cross-context isolation
- **Mutation testing with PIT** runs continuously (scoped to the domain layer)
- **Flyway** owns schema migrations; Hibernate runs in `validate` mode everywhere
- **OpenAPI 3 / Swagger UI** documents every endpoint (disabled in prod)
- **CORS** env-driven with fail-fast in prod (no silent allow-all)
- **Actuator health probe** exposed (no auth) for Docker/K8s
- **Structured logs** with correlation ids and header redaction
- **BCrypt cost 12** (OWASP 2026), single source of truth
- **JaCoCo coverage gate** at 80% LINE

---

## Tech stack

| Layer            | Choice                          | Why                                                    |
|------------------|---------------------------------|--------------------------------------------------------|
| Language         | Java 21                         | LTS, records, sealed interfaces, pattern matching      |
| Framework        | Spring Boot 3.3                 | De-facto Java backend; auto-config + mature ecosystem  |
| Persistence      | Spring Data JPA + Hibernate     | Standard ORM; `validate`-only, schema owned by Flyway  |
| Database         | PostgreSQL 16                   | Real-world RDBMS; UUID + timestamptz + CHECK constraints |
| Migrations       | Flyway 10                       | Plain SQL, versioned, applied before Hibernate validates |
| Security         | Spring Security + jjwt 0.12     | Stateless, HS256, access + refresh tokens              |
| Docs             | springdoc-openapi 2.6           | Swagger UI for dev; disabled in prod                   |
| Build            | Maven (Java 21)                 | Surefire (unit) + Failsafe (integration)               |
| Tests            | JUnit 5 + Mockito + AssertJ     | Slices (`@DataJpaTest`) and full-stack (`@SpringBootTest`) |
| Integration DB   | Testcontainers 1.20             | Real PostgreSQL per run; no H2 dialect surprises       |
| Arch tests       | ArchUnit 1.3                    | Layering + cross-context + stereotype + naming rules   |
| Mutation tests   | PIT 1.16                        | Verifies tests catch real mutations, not just lines    |
| Coverage         | JaCoCo 0.8                      | 80% gate at `verify`                                   |
| Containerization | Docker + docker-compose         | One command brings everything up                       |

---

## Architecture

```
                          HTTP request (with X-Request-Id)
                                       │
                                       ▼
                ┌──────────────────────────────────────────┐
                │   api layer                              │
                │   AuthController · ProjectController · …  │  ← @RestController, DTOs (records)
                └──────────────────────────────────────────┘
                                       │ calls
                                       ▼
                ┌──────────────────────────────────────────┐
                │   application layer                      │
                │   RegisterUserUseCase · CreateTaskUseCase │  ← orchestration, transactions
                │   TaskMapper (MapStruct) · ports          │
                └──────────────────────────────────────────┘
                                       │ invokes
                                       ▼
                ┌──────────────────────────────────────────┐
                │   domain layer (pure Java, no Spring)    │
                │   User · Email · Password · UserId        │  ← entities, value objects, invariants
                │   Project · Task · TaskStatus · Priority  │
                └──────────────────────────────────────────┘
                                       ▲ implemented by
                                       │
                ┌──────────────────────────────────────────┐
                │   infrastructure layer                   │
                │   UserEntity · UserRepositoryImpl         │  ← JPA, BCrypt, JWT, SecurityConfig
                │   JpaRepositories · Flyway migrations    │
                └──────────────────────────────────────────┘
                                       │ JDBC
                                       ▼
                                PostgreSQL 16

  Cross-cutting (com.renan.taskmanager.common):
    api/             GlobalExceptionHandler · ErrorResponse (one shape, 6 fields)
                     OpenApiConfig
    observability/   CorrelationIdFilter · SanitizingRequestLoggingFilter
    security/        SecurityConfig · JwtService · JwtAuthenticationFilter
                     JsonAuthenticationEntryPoint
    domain/          UserId (shared kernel — referenced by tasks and users)
```

### Two bounded contexts

- **`users`** — registration, login, JWT issuance, password hashing (BCrypt)
- **`tasks`** — projects and tasks, with owner-based authorization and
  status-transition rules (`TODO → IN_PROGRESS → DONE`)

Contexts communicate only through the **`common` shared kernel** — never
directly. ArchUnit enforces this.

### Why "simplified" DDD?

Full DDD adds aggregates, events, factories, anti-corruption layers and more —
useful in large systems, but ceremony without payoff at this scale. We keep
the patterns that earn their place (**bounded contexts, value objects, pure
domain layer, ports/adapters**) and skip the rest (events, ACLs, context
mapping). The pragmatic middle between a flat `controller/service/repository`
layout and enterprise DDD.

---

## API endpoints

All routes are prefixed with `/api/v1`.

| Method | Path                              | Auth | Description                                  |
|--------|-----------------------------------|------|----------------------------------------------|
| POST   | `/auth/register`                  | —    | Register a new user                          |
| POST   | `/auth/login`                     | —    | Exchange credentials for access + refresh JWT |
| POST   | `/auth/refresh`                   | —    | Rotate tokens: refresh in → new access + refresh out |
| POST   | `/projects`                       | JWT  | Create a project                             |
| GET    | `/projects`                       | JWT  | List the caller's projects (paginated)       |
| GET    | `/projects/{id}`                  | JWT  | Get a project (owner or 403)                 |
| DELETE | `/projects/{id}`                  | JWT  | Delete a project (owner or 403)              |
| POST   | `/projects/{id}/tasks`            | JWT  | Create a task inside a project (owner or 403)|
| GET    | `/projects/{id}/tasks`            | JWT  | List tasks (filter by `status`, paginated)   |
| PATCH  | `/tasks/{id}/status`              | JWT  | Transition a task's status (owner or 403)    |
| GET    | `/actuator/health`                | —    | Liveness/readiness probe (Docker, K8s)       |

Every error returns a single JSON shape:
`{ timestamp, status, error, message, path, details }`.
Authenticated lookups collapse 404 into 403 (anti-enumeration: a caller
cannot tell "exists but not mine" from "does not exist").

---

## How to run

### Prerequisites

- Docker + Docker Compose

### Bring everything up (PostgreSQL + API)

```bash
cp .env.example .env          # optional for dev, mandatory for prod (sets JWT_SECRET)
docker compose up --build
```

> The dev profile ships a default `JWT_SECRET`, so `docker compose up --build`
> works without `.env` for a first run. Override it via `.env` for anything
> beyond local development.

The API starts at `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html` (dev profile only).

### Database only (run the app from your IDE)

```bash
docker compose up postgres
```

Then run `TaskManagerApplication` from your IDE with the `dev` profile.
Flyway applies the schema on startup; Hibernate validates it.

---

## Testing

```bash
./mvnw test                    # fast: unit tests only (no Docker)
./mvnw verify                  # full: unit + integration (Testcontainers)
```

> The project uses the Maven Wrapper — no local Maven install required.

Convention:

- `*Test.java` → unit tests, run by Surefire (no Spring context)
- `*IT.java`   → integration tests, run by Failsafe (Spring + real PostgreSQL)

Coverage report: `target/site/jacoco/index.html` (generated by `./mvnw verify`).

### Mutation testing (PIT)

Scoped runs only (whole-project is slow):

```bash
mvn -P pit test-compile org.pitest:pitest-maven:mutationCoverage \
    -DtargetClasses=com.renan.taskmanager.common.api.* \
    -DtargetTests=com.renan.taskmanager.common.api.*
```

HTML report: `target/pit-reports/index.html`.

---

## Key engineering decisions

| Decision | Rationale |
|----------|-----------|
| Simplified DDD (not full) | Portfolio-grade rigor without the overhead of aggregates-of-aggregates |
| `ddl-auto=validate` + Flyway | Schema is versioned and reviewed; Hibernate never mutates it |
| Testcontainers over H2 | Catches dialect-specific bugs (UUID, unique constraints, FK enforcement) |
| Spring-managed container as `@Bean` | Aligns container lifecycle with the ApplicationContext cache |
| Centralized error shape | One 6-field JSON contract enforced by `ErrorResponseContractIT` |
| Stateless JWT (no sessions) | Horizontally scalable; `/auth/refresh` rotates without a token store |
| Anti-enumeration collapse (404 → 403) | A non-owner (or a random id) gets 403 uniformly — no resource existence leak |
| JWT `iss`/`aud` enforced in parser | Defense-in-depth: tokens from a different issuer/audience are rejected even if the signing key leaked |
| Token-type check in `JwtService` | `parseAccessToken`/`parseRefreshToken` centralize signature + exp + iss/aud + type in one place |
| CORS fail-fast in prod | Empty `CORS_ALLOWED_ORIGINS` throws at startup instead of silently allowing any origin |
| BCrypt cost 12, single bean | OWASP 2026 baseline; `BCryptPasswordHasher` injects the bean instead of `new`-ing one |
| Actuator: only `/health` exposed | Liveness/readiness for Docker/K8s without leaking env, beans, or heap dumps |
| PIT scoped to domain in CI | Mutation testing runs continuously where invariants live, ~10s per build |
| Correlation id in every log line | One id traces a request across filters, controllers, and DB |
| OpenAPI disabled in prod | Internal docs never leak to the public internet |

See `AGENTS.md` for the full design-decision log and contribution rules.

---

## License

MIT — see [LICENSE](LICENSE).
