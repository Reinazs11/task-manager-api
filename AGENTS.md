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

### Test strategy by layer
The full strategy and its rationale live in **`DECISIONS.md` #4** (single source
of truth). Summary: domain + application services use Mockito (no Spring);
authenticated controllers use `@SpringBootTest` + Testcontainers because the
`JwtAuthenticationFilter`/`SecurityConfig` chain IS the contract that matters;
public controllers use `@WebMvcTest`; repositories use `@DataJpaTest` + Testcontainers.
Do not duplicate the rationale here — see DECISIONS.md #4.

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
5. When you find a bug, a possible improvement, or a new feature idea during
   work: first search the [GitHub Issues](https://github.com/Reinazs11/task-manager-api/issues)
   for an existing one. If it already exists, drop it. If not, **propose** a
   new issue (title, why, trade-offs) and wait for explicit confirmation
   before creating it — never grow the tracker with duplicates or half-formed
   ideas during a long session. This keeps "accepted limitations" (DECISIONS.md)
   and "planned work" (Issues) cleanly separated.
6. Filter outputs: never return full `mvn`/`docker` logs. Use `| tail -n` or equivalent.

## AI-assisted development — guardrails (learned the hard way)

These rules exist because every defect found during the pre-release review
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

## Engineering decisions and known limitations

All design decisions, accepted trade-offs, and known limitations live in
**`DECISIONS.md`** (project root). That is the single source of truth for
"why we chose X" and "what we consciously do NOT do".

When a new decision is made or a limitation accepted during agent work:
- Add it to `DECISIONS.md` under the appropriate section.
- Do NOT duplicate the rationale here — `AGENTS.md` is for agent rules and
  project conventions, not for decision history.

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
default). CI-scoped score on the domain layer: ~80% killed (consistent with
DECISIONS.md #13). Other layers are mutation-testable on demand locally.
Remaining survivors are **justified false positives** (do not chase):
- `hashCode()` returning 0 — no observable contract without a HashMap.
- `toString()` returning "" — debug-only, not asserted.
- `equals()` on `User`/`Task` entities — identity-based, partially covered by
  reconstitute tests; full coverage is low-value.

### MapStruct
Generates Entity ↔ DTO mappers at compile time. Faster than ModelMapper,
type-safe, no reflection.
