# Task Manager API — Project Guidelines

## Context
Java/Spring REST API portfolio project. **Goal: demonstrate mastery of
professional REST APIs.** Code must impress technical clients in 15 seconds
of GitHub reading.

## Stack (fixed, do not change without discussion)
- Java 21 (records, sealed, pattern matching when it adds value)
- Spring Boot 3.x
- Spring Web, Spring Security, Spring Data JPA
- PostgreSQL 16 (production and tests)
- JWT via jjwt (access 15min + refresh 7days)
- Lombok, MapStruct
- Maven (wrapper `./mvnw`)
- Docker + docker-compose
- Testcontainers for integration tests

## Architectural Patterns

### Simplified DDD (not full DDD — this is a portfolio, not enterprise)
Package structure organized by bounded context, not by technical layer:
```
com.renan.taskmanager.<context>/
  domain/        — entities, value objects, domain rules (no Spring)
  application/   — application services, use cases, DTOs
  infrastructure/— JPA repositories, configs, security
  api/           — controllers, advice, request/response models
```
Contexts: `users` (authentication) and `tasks` (projects and tasks).

### Clean Code — non-negotiable rules
- Methods short (<20 lines). If longer, refactor.
- Expressive names: `findActiveTasksByProjectId` > `getTasks`.
- One method = one responsibility.
- No obvious comments (comment the "why", never the "what").
- **Everything in English**: code, comments, Javadoc, error messages,
  test DisplayNames. Standard industry practice, including for Brazilian
  companies.
- No magic numbers: extract a constant or use an enum.

### Error handling
- **Never** throw a generic `RuntimeException`.
- Domain exceptions: `<Context>Exception` (e.g. `UserAlreadyExistsException`).
- All handled by a global `@RestControllerAdvice`.
- Standardized error response (single JSON shape):
  `{ timestamp, status, error, message, path, details }`.

## Tests — absolute rules

### TDD (Red-Green-Refactor) for domain logic and application services
1. **Red**: write the failing test.
2. **Green**: minimal implementation to pass.
3. **Refactor**: improve while staying green.

### Minimum coverage: 80% (enforced by JaCoCo, build fails below)
- **Unit tests**: domain + application services with Mockito (no Spring context).
- **Integration tests**: repositories and controllers with Testcontainers (real PostgreSQL).
- **Do NOT use @SpringBootTest for everything** — it's heavy. Prefer `@WebMvcTest` + MockMvc
  for controllers, `@DataJpaTest` for repositories.

### Forbidden (cheating on tests)
- ❌ `@Disabled` on a broken test (fix it, or delete it with justification).
- ❌ `assertThat(true).isTrue()` to inflate coverage.
- ❌ Testing only the happy path. Cover: boundary, error, edge case.
- ❌ Mocking so much that you end up testing the mock.
- ❌ Skipping exception tests — that's where bugs hide.

## Git — Conventional Commits
```
feat(tasks): add create task endpoint
fix(auth): handle expired refresh token
test(users): cover duplicate email registration
refactor(domain): extract TaskStatus enum
docs: update README with new endpoints
```
- Small, atomic commits.
- Main branch: `main`.
- Feature branches: `feat/<scope>`, `fix/<scope>`, `test/<scope>`.

## Workflow with the agent
1. **Complex step = plan first.** Don't start dumping code without aligning
   on the step's scope.
2. Break implementation into steps (e.g. "Step 1: bootstrap + config",
   "Step 2: users domain"...).
3. Show what will be done, execute, verify (build + tests), report.
4. Before destructive operations (delete file, overwrite, force push):
   explain and wait for confirmation.
5. Filter outputs: never return full `mvn`/`docker` logs. Use `| tail -n` or equivalent.
6. **When completing a step:** update `IMPLEMENTATION_PLAN.md` marking the step as
   `✅ DONE`, fill the acceptance criteria checkboxes with `[x]`, and add the commit
   hash in the progress table. This keeps the roadmap synchronized with the real
   project state.

## Pending design decisions (document here when decided)
- [ ] Logging strategy (default SLF4J? Logback? Structured logging?)
- [x] **API versioning: `/api/v1/...` prefix on all endpoints** (decided: path-based)
- [ ] Schema migration strategy (Flyway? Liquibase? ddl-auto=validate?)

## Modern stack — quick reference

### Records (Java 14+)
Immutable data carrier. Replaces verbose DTOs.
```java
// Old way: 40 lines of getter/setter/equals/hashCode
public record TaskResponseDTO(UUID id, String title, TaskStatus status) {}
```

### Sealed classes (Java 17+)
Restricts who can extend a class. Useful for modeling finite states.
```java
public sealed interface TaskStatus permits Todo, InProgress, Done {}
```

### Pattern matching (Java 21)
Modern `switch` that extracts types automatically. More readable.

### Testcontainers
Spins up real Docker containers (PostgreSQL) during tests and tears them down after.
**Why it matters:** testing with H2 (in-memory DB) hides bugs that only surface
in real PostgreSQL. Clients see this and know you tested for real.

### MapStruct
Generates Entity ↔ DTO mappers at compile time. Faster than ModelMapper,
type-safe, no reflection.
