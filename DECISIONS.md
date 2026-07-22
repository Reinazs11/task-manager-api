# Engineering decisions, limitations, and accepted trade-offs

This document is the project's **decision log and known-limitations register**.
It captures the "why" behind non-obvious choices and is transparent about
what the project deliberately does NOT do. Anything here is either:

- a **decision** (chosen path with rationale), or
- an **accepted limitation** (consciously not implemented, with the path to
  close it if it ever becomes a requirement).

The README stays short; the AGENTS.md stays focused on agent rules. This is
where trade-offs live.

---

## Active decisions

### 1. Simplified DDD (not full)
**Status:** Accepted (project bootstrap)

Full DDD adds aggregates-of-aggregates, domain events, anti-corruption layers,
context mapping — useful in large systems, ceremony without payoff at this
scale. We keep what earns its place: bounded contexts (`users`, `tasks`),
`domain/application/infrastructure/api` layers, ports/adapters. We skip the
rest. The pragmatic middle between flat `controller/service/repository` and
enterprise DDD.

### 2. Flyway owns the schema; Hibernate runs `validate` in all profiles
**Status:** Accepted

`ddl-auto=validate` in dev, test, and prod. Flyway applies
`V1__init_schema.sql` (and future `V2__`, `V3__`) before Hibernate validates.
Hibernate never mutates the schema. Changes go in new versioned files — never
edit `V1` after it has been applied. Rationale: schema is versioned, reviewable,
and matches what a reviewer expects to see.

### 3. Testcontainers over H2 (real PostgreSQL everywhere)
**Status:** Accepted

Both `projects.owner_id` and `tasks.owner_id` carry FK constraints to
`users(id)`. An in-memory H2 might not enforce them. Real PostgreSQL enforces
them — and the FK violation caught while writing `ProjectRepositoryImplIT` is
exactly the kind of bug this decision exists to surface.

### 4. Test strategy by layer (not one-size-fits-all)
**Status:** Accepted (after the Step 7 review)

| Layer | Strategy | Why |
|---|---|---|
| Domain + application services | Mockito, no Spring | Pure logic, fast feedback |
| Authenticated controllers | `@SpringBootTest` + Testcontainers | The `JwtAuthenticationFilter` + `SecurityConfig` chain IS the contract — slicing with `@WebMvcTest` would hide bugs (tampered tokens, refresh-as-access, missing `Bearer`) |
| Public controllers (no JWT) | `@WebMvcTest` + MockMvc | Nothing security-related to lose by slicing |
| Repositories | `@DataJpaTest` + Testcontainers | Real SQL, real constraints |

The earlier "Prefer `@WebMvcTest` for controllers" rule was aspirational and
didn't match what the project needed. Documenting the real decision is more
honest than a rule the codebase quietly violated.

### 5. Centralized error contract (6-field JSON shape)
**Status:** Accepted

Every error returns `{ timestamp, status, error, message, path, details }`,
enforced field-by-field by `ErrorResponseContractIT` for 7 status codes
(400/401/403/404/405/409/500). A 500 never leaks the stack or exception class.

### 6. Anti-enumeration: collapse 404 into 403 on authenticated lookups
**Status:** Accepted (2026-07)

Every authenticated GET/PATCH/DELETE on a project or task checks
`existsByIdAndOwnerId` FIRST. A non-owner, or a random id, both get
`AccessDeniedException` (→ 403) — never 404. Uniform across `GetProject`,
`DeleteProject`, `CreateTask`, `ListTasks`, `UpdateTaskStatus`. Same rationale
as login: don't let an attacker enumerate which resource ids exist.

### 7. Refresh token rotation: stateless, no server-side blacklist
**Status:** Accepted (2026-07)

`POST /auth/refresh` trades a refresh token for a new access+refresh pair.
Both the old and the new refresh stay valid until each expires — there is no
token store, so one-time-use refresh is not possible without Redis/DB. Accepted
trade-off: the API stays horizontally scalable and stateless. See limitation
[1] below for the path to close it.

### 8. JWT `iss`/`aud` enforced; type check centralized in `JwtService`
**Status:** Accepted (2026-07)

The builder emits `iss`/`aud`; the parser calls `requireIssuer`/`requireAudience`.
Defense-in-depth: if another service ever shares this signing key, its tokens
are rejected. Token-type validation (access vs refresh) moved out of the filter
into `JwtService.parseAccessToken` / `parseRefreshToken` so filter, use cases,
and tests all go through one chokepoint. Configured via `app.jwt.issuer` /
`app.jwt.audience` (with sane defaults).

### 9. CORS: env-driven, fail-fast in prod
**Status:** Accepted (2026-07)

`app.cors.allowed-origins` (CSV) drives a `CorsConfigurationSource` bean. Dev
defaults to `http://localhost:3000`; the prod profile ships the env var EMPTY
so the bean throws `IllegalStateException` at startup rather than silently
allowing any origin. `allowCredentials=true` (required for explicit origins +
Authorization header).

### 10. BCrypt cost 12, single `PasswordEncoder` bean
**Status:** Accepted (2026-07)

OWASP 2026 baseline. `BCryptPasswordHasher` injects the bean instead of
`new BCryptPasswordEncoder(...)` — single source of truth, no drift between
two independent encoders.

### 11. OpenAPI disabled in prod
**Status:** Accepted

`application-prod.yml` sets `springdoc.swagger-ui.enabled=false` and
`springdoc.api-docs.enabled=false`. Internal docs must not leak to the public
internet.

### 12. Actuator: only `/actuator/health` exposed
**Status:** Accepted (2026-07)

`management.endpoints.web.exposure.include=health` and
`endpoint.health.show-details=never`. Liveness/readiness for Docker/K8s without
leaking env, beans, heap dumps, or component details. The endpoint is public
(no JWT) so probes work.

### 13. PIT mutation testing scoped to the domain layer in CI
**Status:** Accepted (2026-07)

CI runs scoped PIT against the domain packages on every push (~10s, 90
mutations, ~80% killed). The domain is where invariants live and earns the
continuous check. Application and infrastructure layers can be mutation-tested
on demand locally. Whole-project runs in CI would burn ~10 min for little added
value at this size.

### 14. Maven Surefire (`*Test`) / Failsafe (`*IT`) split + JaCoCo merge
**Status:** Accepted

Unit tests run fast with Surefire (no Docker). Integration tests run with
Failsafe (Testcontainers). JaCoCo merges both `.exec` files before the 80%
LINE gate runs, so the check reflects both suites. Gate is at LINE ≥80%; not
pushing to 100% — remaining gaps are defensive error handling not worth
provoking in honest tests.

---

## Known limitations (accepted trade-offs)

These are **not bugs** — they are consciously out of scope, with the path to
close each one documented.

### [1] No refresh-token revocation
Without a server-side token store, the old refresh token stays valid until its
own expiry after rotation. Logout is a client-side concern (drop the tokens);
there is no server session to clear.
**Close it when:** revocation becomes a real requirement.
**How:** a `refresh_token_jti` table or a Redis set.

### [2] `UserId` / `ProjectId` / `TaskId` are three near-identical classes
Each is a `final class` wrapping a `UUID` with `generate()`/`of()` factories
and value-based equals/hashCode. A generic `record Identifier<U>` or sealed
hierarchy would collapse them to ~1/3 of the code.
**Why kept:** refactor of large surface, breaks ArchUnit rules, low payoff at
this scale. Left as documented debt.

### [3] PIT covers only the domain layer in CI
Application and infrastructure layers are mutation-testable on demand but not
continuously.
**Close it when:** the test suite grows past ~100 tests and the wall-clock
cost of a broader PIT run is justified.

### [4] No role-based authorization beyond `ROLE_USER`
Every authenticated user has the same role; authorization is owner-based
(`ownerId`), not role-based. There are no admin endpoints.
**Close it when:** admin or multi-tenant features are needed.

### [5] No async work / job queue
Everything is synchronous. There is no `@Async`, no scheduler, no message broker.
**Close it when:** background jobs (welcome emails, periodic cleanup) become a
requirement.

---

## How to read this file

- Adding a non-obvious engineering choice? Append it under **Active decisions**
  with Status, Context, Decision, and Consequences (one paragraph each is fine).
- Accepting a limitation on purpose? Append it under **Known limitations** with
  "Close it when" + "How".
- Reversing a decision? Don't delete it — mark it **Superseded** and point to
  the new one. The history is the point of the log.
