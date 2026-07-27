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
**Status:** Accepted (after the first controller-layer review)

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
enforced field-by-field by `ErrorResponseContractIT` for 8 status codes
(400/401/403/404/405/409/429/500). A 500 never leaks the stack or exception class.

### 6. Anti-enumeration: collapse 404 into 403 on authenticated lookups
**Status:** Accepted (2026-07)

Every authenticated GET/PATCH/DELETE on a project or task checks
`existsByIdAndOwnerId` FIRST. A non-owner, or a random id, both get
`AccessDeniedException` (→ 403) — never 404. Uniform across `GetProject`,
`DeleteProject`, `CreateTask`, `ListTasks`, `UpdateTaskStatus`. Same rationale
as login: don't let an attacker enumerate which resource ids exist.

### 7. Refresh token rotation — SUPERSEDED (2026-07)
**Status:** Superseded by decision #17 (issue #11)

Previously: `POST /auth/refresh` traded a refresh token for a new access+refresh
pair with both the old and the new refresh staying valid until each expired —
no token store, so one-time-use refresh was impossible without Redis/DB. The
accepted trade-off was that the API stayed horizontally scalable and stateless
(see limitation [1] for the original framing). This is now reversed: a
`revoked_refresh_tokens` table backs one-time-use rotation and `/auth/logout`
(decision #17). The HTTP/session layer is still stateless (no HTTP session,
CSRF off); only refresh-token rotation is now stateful.

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

Justified survivors on the domain layer (do not chase):
- `hashCode()` returning 0 — no observable contract without a HashMap.
- `toString()` returning "" — debug-only, not asserted.
- `equals()` on `User`/`Task` entities — identity-based, partially covered by
  reconstitute tests; full coverage is low-value.

### 14. Maven Surefire (`*Test`) / Failsafe (`*IT`) split + JaCoCo merge
**Status:** Accepted

Unit tests run fast with Surefire (no Docker). Integration tests run with
Failsafe (Testcontainers). JaCoCo merges both `.exec` files before the 80%
LINE gate runs, so the check reflects both suites. Gate is at LINE ≥80%; not
pushing to 100% — remaining gaps are defensive error handling not worth
provoking in honest tests.

### 15. Auth-endpoint rate limiting: in-memory per-IP token bucket (Bucket4j)
**Status:** Accepted (2026-07) — closes issue #7

A per-source-IP token bucket (Bucket4j `bucket4j-core`) throttles
**`/api/v1/auth/login` and `/api/v1/auth/refresh`** (only) to **10 requests /
minute / IP** with a shared bucket across both endpoints. Closing the
brute-force gap that anti-enumeration (decision #6) does not cover:
anti-enumeration defeats single-shot probing, but not volumetric attacks.
BCrypt cost 12 slows each guess but does not cap the guess rate.

**Why only login + refresh (not register):** these are the only endpoints where
an attacker benefits from volume — guessing passwords or rotating refresh
tokens. Register is a creation, not a guess; throttling it would also block
legitimate onboarding from shared NATs (a whole office signing up in the same
minute). The filter uses an explicit path set, not a prefix match, so adding a
new auth endpoint does not silently get throttled.

**Why in-memory and not distributed:** state lives in a Caffeine cache inside
the process. It is not shared across instances and resets on restart. For a
single-instance portfolio this is the correct trade-off (no Redis dependency);
for a multi-instance deployment it would under-count and need a shared backend.
See limitation [1] / issue #11 — if token revocation later pulls in Redis,
`bucket4j-redis` can back the same `RateLimiter` interface without touching the
filter.

**Why Caffeine and not a plain `ConcurrentHashMap`:** the bucket key is the
client IP, which is spoofable via `X-Forwarded-For` (see below). An unbounded
map would let an attacker flood distinct IPs and exhaust the heap — turning
the brute-force defense into a cheap DoS. Caffeine caps the map
(`maximumSize`, default 100k) and evicts idle buckets (`expireAfterAccess`,
default 60 min), bounding memory regardless of key diversity.

**Why per-IP and not per-account:** per-account throttling requires knowing
the account exists, which reintroduces the enumeration surface that decision
#6 collapses. Per-IP is the standard for login throttling.

**Why login + refresh share a bucket:** both are equally brute-force-sensitive
auth paths; separate buckets would double an attacker's budget.

**Why the limiter counts every attempt (including 400 and 401):** the token is
consumed in the filter, before the controller runs. This is intentional: it
keeps BCrypt cost 12 off the hot path for floods, so an attacker cannot burn
CPU by spamming malformed bodies. The trade-off is that a legitimate user
fumbling their password (or CAPS LOCK) consumes their own budget; with a
10/min limit this is tolerable (10 wrong guesses → 1 min cooldown).

**Why a filter that writes the response (not an exception for the advice):**
the filter runs before the `DispatcherServlet`, so an exception here is not
caught by `@RestControllerAdvice` — it would surface as a generic 500. The
filter delegates serialization to `HttpErrorWriter`, the same writer used by
`JsonAuthenticationEntryPoint` (401), so the six-field `ErrorResponse` contract
holds regardless of which layer rejects the request.

**`X-Forwarded-For` trust is opt-in (`app.rate-limit.trust-forwarded-for`):
default false.** Behind a reverse proxy that overwrites (not appends to) XFF,
enable it so `getRemoteAddr()` (the proxy's IP) is replaced by the real client.
With it disabled — the default, and the only safe mode when the app is directly
exposed — the resolver uses the raw socket address, which a client cannot
spoof, so rate limiting cannot be bypassed by header manipulation.

### 16. Testcontainers PostgreSQL container exposed as a `@Bean`
**Status:** Accepted (2026-07)

The integration-test container is declared as a `@Bean` in
`TestContainersConfig` with `@ServiceConnection`, not as a static `@Container`
field on each test class. A static `@Container` field is started/stopped by the
Testcontainers JUnit extension per class; when several test classes share the
same cached Spring `ApplicationContext`, the container started by class A is
torn down when A finishes, but the cached context still references it, so class
B reconnects to a dead container and fails with `Connection refused`. Defining
the container as a `@Bean` moves its lifecycle into Spring: it lives exactly as
long as the `ApplicationContext` that owns it, so it is started once and reused
across cached contexts — no race, no premature teardown. `@ServiceConnection`
auto-wires the JDBC URL/credentials without `@DynamicPropertySource` boilerplate.

### 17. Refresh-token revocation: server-side token store (PostgreSQL)
**Status:** Accepted (2026-07) — closes issue #11, resolves limitation [1]

A `revoked_refresh_tokens` table records the `jti` of every refresh token that
has been either (a) rotated out by `POST /auth/refresh` (one-time-use
rotation) or (b) explicitly revoked by `POST /auth/logout`. `RefreshTokenUseCase`
checks the store on every refresh and rejects a known `jti` with the same
`InvalidCredentialsException` → 401 used for any invalid token (anti-enumeration,
decision #6).

**Why PostgreSQL over Redis:** no new infra dependency. The revocation write
shares the same transaction as the new token pair's issuance, so atomicity is
free — if the new issuance fails, the old token stays valid; if the revocation
insert fails, no new tokens are minted. Redis would add a second datastore and
a distributed-transaction seam that a portfolio does not need. The lookup is
indexed (PK on `jti`), so latency is a single PK existence check per refresh.

**Why one-time-use rotation (and not just logout):** stateless rotation let a
replayed refresh token mint tokens indefinitely until its own expiry. One-time-use
closes that replay window and is the canonical "did the client implement
rotation correctly?" guarantee. It also gives `/auth/logout` teeth: the revoked
refresh is rejected on the next refresh attempt.

**What is NOT revoked:** access tokens. They are short-lived (15 min) and
revoking them server-side would require a `jti` lookup on every authenticated
request. The trade-off (let an in-flight access token live out its 15 min
after logout) is the standard industry answer and is acceptable here. The
long-lived refresh token is the one that matters, and that one IS revoked.

**Why `/auth/logout` is not rate-limited:** logout carries no guessing surface
(the caller already holds the token), so it does not belong in the brute-force
path set (decision #15). Throttling it would also block legitimate logout from
multiple devices behind a shared NAT. `RateLimitFilter` keeps its explicit
path set (`/auth/login`, `/auth/refresh`); `/auth/logout` is deliberately out.

**Idempotency:** revoking the same `jti` twice is a no-op (`existsById` before
insert in the adapter). Calling `/auth/logout` twice returns 204 both times;
calling `/auth/refresh` with an already-rotated token returns 401 the second
time (the row is already there).

**Cleanup of dead rows:** `RevokedRefreshTokenRepository.deleteExpired(now)`
purges rows whose `expires_at` has passed. No scheduler invokes it today
(limitation [5] — no async work). The hook is on the port so the future
scheduler does not have to reach past the domain. Dead rows are harmless: the
`isRevokedAndActive` query already filters on `expires_at > now`, so they are
invisible to the check and only cost storage.

---

## Known limitations (accepted trade-offs)

These are **not bugs** — they are consciously out of scope, with the path to
close each one documented.

### [1] No refresh-token revocation — RESOLVED (2026-07)
**Status:** Resolved — implemented via decision #17 (issue #11)

Previously accepted as a stateless trade-off: without a server-side token
store, the old refresh token stayed valid until its own expiry after
rotation. Logout was a client-side concern (drop the tokens); there was
no server session to clear. This is now closed: a `revoked_refresh_tokens`
table backs one-time-use rotation and `POST /auth/logout`. See decision #17
for the design. This entry is kept for history — the decision log records
what was accepted and when it was reversed, not just the current state.

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

### [6] No async messaging (Kafka / RabbitMQ)
There is no message broker. The API does not publish or consume events.
Adding one "to have it" would be complexity without a use case at this scale.
**Close it when:** a real async use case appears — most likely email
notifications (see [9]) or out-of-band audit writes (see issue #9).
**How:** start with a single consumer for one concrete event; do not stand
up infrastructure speculatively.

### [7] Hard deletes, no soft delete
`DELETE /projects/{id}` is a hard delete — the row is removed. There is no
`deleted_at` tombstone. This is the simplest correct behavior for a
portfolio, but in systems with compliance (GDPR), legal, or audit exposure,
nothing is ever truly deleted.
**Close it when:** audit/compliance requirements demand recoverability (this
pairs naturally with the audit log in issue #9).
**How:** add a `deleted_at timestamptz` column, filter `WHERE deleted_at IS
NULL` in repository queries, and exclude tombstoned rows from listings.

### [8] Single-owner model — no collaboration or sharing
Projects and tasks have exactly one owner; there are no assignees, no
invitations, no shared workspaces. The domain models a personal task list,
not a team tool.
**Close it when:** the product narrative shifts from "personal task manager"
to "team task management" — that is a domain redesign, not a feature add.
**Why kept:** collaboration multiplies complexity (permissions, invitations,
notifications, conflict resolution) for little portfolio payoff unless
multi-user scenarios are the point of the showcase.

### [9] No email notifications (confirmation, password reset)
There is no email sending — no signup confirmation, no password-reset flow,
no welcome message. Implementing it would require an SMTP/SES integration
and almost certainly async delivery (pulling in limitation [5]/[6]).
**Close it when:** outbound email becomes a real product requirement.
**How:** SMTP provider (SES/Postmark) + an async sender (message broker per
[6], or `@Async` per [5]). Do not send email synchronously on the request
thread.

---

## How to read this file

### Adding a new decision
Append it under **Active decisions** using the template:
**Status** (Accepted/Superseded + date) · **Context** (the problem) ·
**Decision** (what was chosen) · **Consequences** (trade-offs accepted).
One paragraph per section is fine.

For accepted limitations, append under **Known limitations** with
"Close it when" + "How".

### Scheduling active work
Something that **will** be done goes into a GitHub Issue — don't grow this
file into a roadmap. The split: this document is for what is **accepted**
(out of scope by choice); Issues are for what is **planned**. When an accepted
limitation is promoted to planned work, mark it **Superseded** here and link
the issue (see limitation [1] → issue #11).

### Reversing a decision
Don't delete the old entry — mark it **Superseded** and point to the new one.
The history is the point of the log.
