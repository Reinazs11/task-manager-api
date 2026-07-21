# Task Manager API

REST API for task and project management with JWT authentication, built with
Java 21 and Spring Boot 3 following Simplified DDD and TDD.

> Portfolio project demonstrating a production-grade backend: real PostgreSQL
> (via Testcontainers, no H2 shortcuts), strict architecture enforcement
> (ArchUnit), mutation testing (PIT), versioned schema migrations (Flyway), and
> a 96% line-coverage gate that fails the build.

---

## Highlights

- **Java 21** (records, sealed interfaces, pattern matching) on **Spring Boot 3.3**
- **Stateless JWT auth** — access token (15 min) + refresh token (7 days), HS256
- **Simplified DDD** — bounded contexts (`users`, `tasks`) with `domain` /
  `application` / `infrastructure` / `api` layers
- **Testcontainers** for integration tests against **real PostgreSQL 16** (no H2)
- **Architecture tests with ArchUnit** enforce layering and cross-context isolation
- **Mutation testing with PIT** proves the tests actually catch bugs
- **Flyway** owns schema migrations; Hibernate runs in `validate` mode everywhere
- **OpenAPI 3 / Swagger UI** documents every endpoint (disabled in prod)
- **Structured logs** with correlation ids and header redaction
- **JaCoCo coverage gate** at 80% (current: 96.7%)

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
| POST   | `/projects`                       | JWT  | Create a project                             |
| GET    | `/projects`                       | JWT  | List the caller's projects (paginated)       |
| GET    | `/projects/{id}`                  | JWT  | Get a project (owner only)                   |
| DELETE | `/projects/{id}`                  | JWT  | Delete a project (owner only)                |
| POST   | `/projects/{id}/tasks`            | JWT  | Create a task inside a project               |
| GET    | `/projects/{id}/tasks`            | JWT  | List tasks (filter by `status`, paginated)   |
| PATCH  | `/tasks/{id}/status`              | JWT  | Transition a task's status (owner only)      |

Every error returns a single JSON shape:
`{ timestamp, status, error, message, path, details }`.

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
mvn test                       # fast: unit tests only (no Docker)
mvn verify                     # full: unit + integration (Testcontainers)
```

Convention:

- `*Test.java` → unit tests, run by Surefire (no Spring context)
- `*IT.java`   → integration tests, run by Failsafe (Spring + real PostgreSQL)

Coverage report: `target/site/jacoco/index.html` (generated by `mvn verify`).

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
| Testcontainers over H2 | Catches dialect-specific bugs (UUID, unique constraints, case sensitivity) |
| Spring-managed container as `@Bean` | Aligns container lifecycle with the ApplicationContext cache |
| Centralized error shape | One 6-field JSON contract enforced by `ErrorResponseContractIT` |
| Stateless JWT (no sessions) | Horizontally scalable; refresh rotation handled by clients |
| Correlation id in every log line | One id traces a request across filters, controllers, and DB |
| OpenAPI disabled in prod | Internal docs never leak to the public internet |

See `AGENTS.md` for the full design-decision log and contribution rules.

---

## License

MIT — see [LICENSE](LICENSE).
