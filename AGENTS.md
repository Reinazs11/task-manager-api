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
- JWT via jjwt (access 15min + refresh 7days, both rotated via `/auth/refresh`;
  `iss`/`aud` enforced in the parser; type check centralized in
  `JwtService.parseAccessToken`/`parseRefreshToken`)
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

### Test strategy by layer (decided after the Step 7 review)
- **Domain + application services**: unit tests with Mockito, no Spring context.
- **Authenticated controllers**: `@SpringBootTest` + Testcontainers. The
  `JwtAuthenticationFilter` and `SecurityConfig` are part of the contract
  that matters — mocking the security context with `@WebMvcTest` would hide
  bugs that only surface with the real filter chain (tampered tokens, missing
  `Bearer` prefix, refresh-vs-access confusion, etc.). `JwtAuthorizationIT`
  covers 6 token variants and `ErrorResponseContractIT` asserts the error
  envelope field-by-field; neither is reproducible with a controller slice.
- **Public controllers** (no JWT): `@WebMvcTest` + MockMvc is fine — nothing
  security-related to lose by slicing.
- **Repositories**: `@DataJpaTest` + Testcontainers.

> **Rationale:** the old rule "Prefer @WebMvcTest for controllers" was
> aspirational and didn't match what the project actually needed. Documenting
> the real decision is more honest than a rule the codebase quietly violates.

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
1. **MANDATORY planning before every Step.** Before writing any code for a new
   Step, produce a written plan covering: files to create/edit, design decisions,
   test plan (what to test, which layer: unit vs integration), and edge cases to
   cover. Get explicit approval before executing. This prevents coverage gaps
   like the one found in Step 3 (missing authorization tests).
2. Break implementation into steps (e.g. "Step 1: bootstrap + config",
   "Step 2: users domain"...).
3. Show what will be done, execute, verify (build + tests), report.
4. Before destructive operations (delete file, overwrite, force push):
   explain and wait for confirmation.
5. Filter outputs: never return full `mvn`/`docker` logs. Use `| tail -n` or equivalent.

## AI-assisted development — guardrails (learned the hard way)

These rules exist because every defect found in the `fix/review-findings` branch
traced back to one of four AI failure modes. They are non-negotiable when an
agent is writing code in this repo.

### 1. Doc-vs-code drift is the #1 failure mode — verify before committing
Long sessions lose the context of what was said earlier. The most common
defect is a comment, Javadoc, README claim, or `ci.yml` comment that describes
behavior that was changed (or never existed). Before finishing any task:
- `grep` the codebase for names/terms introduced by the change. If a comment
  says "X" and the code now does "Y", fix one of them.
- Treat every comment as a **load-bearing claim** until proven otherwise.
  Examples caught in review: "ddl-auto=update in dev" (dev was `validate`),
  "delegates internally" (didn't), "future work" (shipped it), "PIT proves"
  (CI never ran PIT).
- When you delete or rename a feature, search for mentions in README, AGENTS.md,
  `*.yml` comments, Javadoc, and OpenAPI `@Operation` descriptions.

### 2. Ship the whole feature, or document why the half is shippable
The refresh-token bug: the token was minted but no endpoint consumed it, and a
Javadoc tagged the gap as "future work". That's worse than not advertising the
feature — it advertises what doesn't work.
- An endpoint that issues a token/credential MUST have a documented consumer
  endpoint in the same PR, OR be explicitly disabled (don't mint it).
- If you must ship a partial capability, prefix the user-facing description
  with "Not yet implemented:" so reviewers see it instantly. Never bury the
  gap in a Javadoc.

### 3. Apply a decision everywhere, or the inconsistency will out you
`DeleteProjectUseCase` collapsed 404→403 for anti-enumeration, but `Get` and
`Create` distinguished them. A senior reviewer spots this in seconds and reads
it as rushed review.
- When you make a security/contract decision, grep for analogous code paths
  and apply it to ALL of them in the same commit.
- Anti-enumeration, error-shape, status-code semantics, and authorization
  checks belong on every endpoint of the same kind — no exceptions per file.

### 4. Re-verify every review finding before acting on it
3 of ~10 findings in the first automated review were false positives (a Javadoc
"lying" that wasn't lying, a "dead" method with a real caller, "hardcoded
timing" that was just a format assertion). Implementing a fix for a non-bug
either deletes working code or masks the real bug.
- For every "X is wrong" claim, run the grep/read that proves it before writing
  the fix. Cite `file:line` in the plan.
- If the grep contradicts the claim, say so explicitly — don't silently adapt.
  Document the false positive; the next reviewer benefits.

## Pending design decisions (document here when decided)
- [x] **Logging strategy: SLF4J + Logback native, no extra dependencies**
  (decided in Step 6c). Rationale: professional-grade observability without
  pulling in Logstash encoders or Micrometer for a portfolio project. Concrete
  pieces: (1) `CorrelationIdFilter` puts an `X-Request-Id` in the MDC so every
  log line carries it; (2) `SanitizingRequestLoggingFilter` emits one INFO line
  per request and redacts `Authorization`/`Cookie` headers; (3)
  `logback-spring.xml` sets profile-aware levels (dev/test/prod). Body logging
  is gated behind TRACE + a sensitive-key heuristic and is off by default.
- [x] **API versioning: `/api/v1/...` prefix on all endpoints** (decided: path-based)
- [x] **OpenAPI in prod: disabled.** `application-prod.yml` sets
  `springdoc.swagger-ui.enabled=false` and `springdoc.api-docs.enabled=false`.
  Internal docs must not leak to production.
- [x] **Schema migration strategy: Flyway 10 + SQL versioned files**
  (decided in Step 7b). Rationale: Flyway uses plain SQL (more readable than
  Liquibase XML/YAML for a small, stable schema), has massive adoption in the
  Spring ecosystem, and is the idiom a reviewer expects. Concrete pieces:
  `flyway-core` + `flyway-database-postgresql` (Flyway 10 splits per-DB support
  into separate modules); `src/main/resources/db/migration/V1__init_schema.sql`
  captures the schema matching the JPA entities; `application.yml` sets
  `ddl-auto=validate` in all profiles so Flyway owns the schema end-to-end.
  Changes go in `V2__`, `V3__`, etc. — never edit `V1` after it's been applied.
- [x] **Anti-enumeration: collapse 404 into 403 on authenticated lookups**
  (decided in `fix/review-findings`). Every authenticated GET/PATCH/DELETE on
  a project or task checks `existsByIdAndOwnerId` FIRST. A non-owner, or a
  random id, both get `AccessDeniedException` (→ 403) — never 404. This is
  uniform across `GetProject`, `DeleteProject`, `CreateTask`, `ListTasks`,
  `UpdateTaskStatus`. Same rationale as login: don't let an attacker enumerate
  which resource ids exist.
- [x] **Refresh token rotation: stateless, no server-side blacklist**
  (decided in `fix/review-findings`). `POST /auth/refresh` trades a refresh
  token for a new access+refresh pair. Both old and new refresh stay valid
  until each expires — there is no token store, so one-time-use refresh is not
  possible without Redis/DB. Accepted trade-off: the API stays horizontally
  scalable. If true revocation becomes a requirement, the next step is a
  `refresh_token_jti` table or Redis set.
- [x] **JWT `iss`/`aud` enforced; type check centralized in `JwtService`**
  (decided in `fix/review-findings`). Builder emits `iss`/`aud`; parser calls
  `requireIssuer`/`requireAudience`. Defense-in-depth: if another service ever
  shares this signing key, its tokens are rejected. Token-type validation
  (access vs refresh) moved out of the filter into `JwtService.parseAccessToken`
  / `parseRefreshToken` so filter, use cases, and tests all go through one
  chokepoint. Defaults via `app.jwt.issuer`/`app.jwt.audience`.
- [x] **CORS: env-driven, fail-fast in prod**
  (decided in `fix/review-findings`). `app.cors.allowed-origins` (CSV) drives
  a `CorsConfigurationSource` bean. Dev profile defaults to
  `http://localhost:3000`; prod profile ships the env var EMPTY so the bean
  throws `IllegalStateException` at startup rather than silently allowing any
  origin. `allowCredentials=true` (required for explicit origins + Authorization).
- [x] **BCrypt cost 12, single PasswordEncoder bean**
  (decided in `fix/review-findings`). OWASP 2026 baseline. `BCryptPasswordHasher`
  injects the bean instead of `new BCryptPasswordEncoder(...)` — single source
  of truth, no drift between two encoders.

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

### Mutation testing (PIT) — current state
CI runs scoped PIT against the domain layer on every push (~10s, 90 mutations,
~80% killed). Local whole-project runs still available:
`mvn -P pit test-compile org.pitest:pitest-maven:mutationCoverage
-DtargetClasses=<pkg> -DtargetTests=<pkg>`.
Profile `pit` needs `timeoutConstant=30000` (Testcontainers tests exceed the 8s
default). Recent scores: `common.api.GlobalExceptionHandler` 95%, domain layer
82%. Remaining survivors are **justified false positives** (do not chase):
- `hashCode()` returning 0 — no observable contract without a HashMap.
- `toString()` returning "" — debug-only, not asserted.
- `equals()` on `User`/`Task` entities — identity-based, partially covered by
  reconstitute tests; full coverage is low-value.

### MapStruct
Generates Entity ↔ DTO mappers at compile time. Faster than ModelMapper,
type-safe, no reflection.
