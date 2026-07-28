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

Full DDD (aggregates-of-aggregates, domain events, anti-corruption layers,
context mapping) is ceremony without payoff at this scale. We keep what earns
its place — bounded contexts (`users`, `tasks`), `domain/application/
infrastructure/api` layers, ports/adapters — and skip the rest. The pragmatic
middle between flat `controller/service/repository` and enterprise DDD. Full
diagram and package layout: `README.md → Architecture`.

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
**Status:** Superseded by decision #17 (issue #11). Previously rotation was
stateless (old + new refresh both valid until expiry, no token store); now
backed by a `revoked_refresh_tokens` table for one-time-use rotation and
`/auth/logout`. The HTTP/session layer stays stateless (no session, CSRF off).

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

### 12. Actuator: `/actuator/health` and `/actuator/prometheus` exposed
**Status:** Accepted (2026-07) — updated (2026-07) for Prometheus (see #19)

`management.endpoints.web.exposure.include=health,prometheus` and
`endpoint.health.show-details=never`. Liveness/readiness for Docker/K8s without
leaking env, beans, heap dumps, or component details. `/actuator/health` is
public (no JWT) so probes work; `/actuator/prometheus` is JWT-protected (it
falls under `SecurityConfig`'s `anyRequest().authenticated()`). Everything
else stays unexposed.

### 13. PIT mutation testing scoped to the domain layer in CI
**Status:** Accepted (2026-07)

CI runs scoped PIT against the domain packages on every push (~10s, 90
mutations, ~80% killed). The domain is where invariants live and earns the
continuous check. Application and infrastructure layers can be mutation-tested
on demand locally. Whole-project runs in CI would burn ~10 min for little added
value at this size. **The CI scoping is an accepted limitation** — broaden it
when the suite grows past ~100 tests and the wall-clock cost is justified.

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

**Context:** Anti-enumeration (decision #6) defeats single-shot probing but not
volumetric attacks; BCrypt cost 12 slows each guess but doesn't cap the guess
rate. A per-source-IP token bucket (Bucket4j `bucket4j-core`) throttles only
**`/auth/login` and `/auth/refresh`** to **10 req/min/IP** with a shared bucket
across both.

**Decision:** Only login + refresh — they're the only endpoints where volume
pays off for an attacker (guessing passwords, rotating refresh). Register is a
creation, not a guess, and throttling it would block legitimate onboarding from
shared NATs. The filter uses an explicit path set (not prefix match), so new
auth endpoints don't silently get throttled. Per-IP (not per-account, which
would reintroduce the enumeration surface #6 collapses); login + refresh share
a bucket (both are equally brute-force-sensitive; separate buckets double the
budget). The bucket key (IP) is spoofable via `X-Forwarded-For`, so an unbounded
map would let an attacker flood distinct IPs and exhaust the heap — Caffeine
caps it (`maximumSize`, default 100k) and evicts idle buckets (`expireAfterAccess`,
default 60 min). State is in-memory (correct for single-instance; resets on
restart; would under-count multi-instance — then `bucket4j-redis` can back the
same `RateLimiter` interface). The token is consumed in the filter, before the
controller, so BCrypt stays off the hot path for floods (a legitimate user
fumbling their password spends their own budget; 10/min is tolerable).

**Consequences:** The filter runs before the `DispatcherServlet`, so on
rejection it delegates serialization to `HttpErrorWriter` (the same writer
`JsonAuthenticationEntryPoint` uses) — the six-field `ErrorResponse` contract
holds (an exception here would bypass `@RestControllerAdvice` and surface as a
500). `app.rate-limit.trust-forwarded-for` defaults to **false**: behind a
reverse proxy that overwrites (not appends to) XFF, enable it to replace
`getRemoteAddr()` (the proxy's IP) with the real client; disabled, the resolver
uses the raw socket address, which a client cannot spoof, so rate limiting
cannot be bypassed by header manipulation.

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

**Context:** A `revoked_refresh_tokens` table records the `jti` of every
refresh token that was rotated out by `POST /auth/refresh` (one-time-use
rotation) or revoked by `POST /auth/logout`. `RefreshTokenUseCase` checks the
store on every refresh and rejects a known `jti` with the same
`InvalidCredentialsException` → 401 used for any invalid token (anti-enumeration,
decision #6). One-time-use rotation closes the replay window that stateless
rotation left open (a replayed token minting indefinitely) and gives
`/auth/logout` teeth.

**Decision — PostgreSQL over Redis:** no new infra. The revocation write shares
the new token pair's transaction, so atomicity is free — if issuance fails the
old token stays valid; if revocation fails, no new tokens are minted. Redis
would add a second datastore and a distributed-transaction seam a portfolio
doesn't need. Lookup is indexed (PK on `jti`). Access tokens are NOT revoked
(short-lived 15 min; a `jti` check on every request is too costly) — the
long-lived refresh token is the one that matters. `/auth/logout` is out of the
rate-limit set (no guessing surface; throttling it would block multi-device
logout behind a NAT). Revoking the same `jti` twice is a no-op (`existsById`
before insert), so logout returns 204 twice and replayed refresh returns 401.

**Consequences:** `RevokedRefreshTokenRepository.deleteExpired(now)` purges
expired rows, but no scheduler invokes it today (limitation [5]). The hook is
on the port so a future scheduler need not reach past the domain. Dead rows
are harmless — `isRevokedAndActive` filters on `expires_at > now`.

---

### Decision #18 — Publish Docker image to GHCR on merge to main

**Status:** Accepted (2026-07) — closes issue #8

**Context:** CI was test-only — no distributable artifact, so a reviewer could
not `docker pull` the app, and the planned deploy (#5) and metrics (#6) had no
image to consume.

**Decision:** A **separate** workflow (`docker-publish.yml`, not an extension of
`ci.yml` — single responsibility: tests vs release). Multi-arch
`linux/amd64,linux/arm64` via QEMU + Buildx; auth via the automatic
`GITHUB_TOKEN` (no PAT); `type=gha` cache. Tags: on push to `main` → `latest`,
`main`, `sha-<short>`; on `v*` git tags → semver `1.2.3`, `1.2`, `1`.

**Consequences:** Image is public at `ghcr.io/reinazs11/task-manager-api`,
unblocking #5 and #6. Cost: ~5–7 min multi-arch per run; the first publish is
**private** by default and must be flipped to public manually. The image ships
only the dev `JWT_SECRET` placeholder (the `JwtService` enforces only the
≥256-bit HMAC length, which the placeholder satisfies), so non-local deploys
**must** override `JWT_SECRET`. Image signing (cosign) is out of scope.

### Decision #19 — Prometheus metrics via Micrometer (JWT-protected endpoint + binary login counter)

**Status:** Accepted (2026-07) — closes issue #6

**Context:** The app had a health probe and structured logs but no metrics —
nothing answered "is the API slow before a user complains?" The GHCR image
(#18) unblocked this: a reviewer can now `docker pull`, run, and scrape.

**Decision:**
- Add `micrometer-registry-prometheus`; expose `/actuator/prometheus`
  (`management.endpoints.web.exposure.include=health,prometheus`).
- Enable `percentiles-histogram` for `http.server.requests` so p95/p99 are
  computed server-side via `histogram_quantile()` (a flat sum/count only
  gives the mean, useless for SLOs).
- `/actuator/prometheus` is **JWT-protected**, not public like `/health`. It
  falls under `SecurityConfig`'s `anyRequest().authenticated()` with no
  `permitAll` matcher. A scraper must send a bearer token. Chosen over
  "public like /health" because the portfolio value is in documenting a
  defensible posture (metrics are operational, not a public contract); a
  reviewer reads "protected" as a conscious trade-off, not an oversight.
- Custom counter `auth.login.attempts` with a **binary** tag
  `result=success|failure`. The failure tag is identical for "unknown email"
  and "wrong password" — a per-reason tag would let an attacker enumerate
  valid emails by reading `/actuator/prometheus`, defeating the
  anti-enumeration already enforced in `LoginUseCase` (#6).

**Gotcha learned (registry-first key):** the auto-config
`PrometheusMetricsExportAutoConfiguration` is gated by
`@ConditionalOnEnabledMetricsExport("prometheus")`, which reads the key
`management.prometheus.metrics.export.enabled` (**registry-first**), not
`management.metrics.export.prometheus.enabled` (metrics-first). With the
wrong shape, no `PrometheusMeterRegistry` bean is created and
`/actuator/prometheus` returns 404. The key is set explicitly in
`application.yml` to make the intent visible and to bind the contract.

**Consequences:** Prometheus can now scrape request rate, latency percentiles,
JDBC pool usage, and login success/failure ratio. Cost: a scraper needs a
static/long-lived bearer token (the access-token TTL of 15 min makes scrape
config awkward — a future deploy may mint a dedicated metrics token). The
login counter does NOT split by failure reason by design.

### Decision #20 — Structured JSON logging in prod (logstash-logback-encoder)

**Status:** Accepted (2026-07) — closes issue #10

**Context:** Plain-text logs are unparseable in production aggregators (Loki,
ELK, Datadog): fields can't be queried and the correlation id is buried in the
message string. Pairs with #19 — logs say *what happened*, metrics say *how
often/how slow*.

**Decision:**
- `logstash-logback-encoder` **pinned to 8.1, not 9.0**: 9.0 migrated to
  Jackson 3, incompatible with the Jackson 2.x that Spring Boot 3.3 manages.
- Profile split in `logback-spring.xml`: `dev`/`test` keep human-readable
  output (DX); only `prod` switches to a `LogstashEncoder` appender.
- MDC `requestId` → JSON field **`correlationId`** (the name aggregators index
  by default). The internal MDC key is unchanged — no break to
  `CorrelationIdFilter`, the dev `%X{requestId}` pattern, or existing tests.
- HTTP context (`method`/`uri`/`status`/`latencyMs`/`client`) emitted as
  `StructuredArguments.kv(...)`: readable `key=value` in dev, promoted to
  JSON fields in prod. This is what makes request logs queryable.
- Secret redaction lives in `SanitizingRequestLoggingFilter`, upstream of the
  encoder — switching to JSON does not weaken it. The encoder never sees raw
  headers.
- JSON is tested two ways because `ListAppender` captures events *before*
  encoding and never sees JSON: `StructuredLoggingEncoderTest` drives the
  encoder in isolation to assert the serialized output;
  `StructuredLoggingProdProfileIT` parses the XML to assert the prod block
  references a `LogstashEncoder` (it reads the file, not the runtime
  `LoggerContext`, which is a JVM singleton reused across ITs and reflects
  whichever profile won the cache race).

**Consequences:** Prod logs are machine-parseable; dev readability is
preserved. The 9.0 upgrade is deferred until Spring Boot adopts Jackson 3.
Logging stays synchronous (request thread) — async is a follow-up only if the
load test (#12) finds I/O contention.

---

## Known limitations (accepted trade-offs)

These are **not bugs** — they are consciously out of scope, with the path to
close each one documented.

### [1] No refresh-token revocation — RESOLVED (2026-07)
**Status:** Resolved via decision #17 (issue #11). Kept for history: the log
records what was accepted and when it was reversed.

### [2] `UserId` / `ProjectId` / `TaskId` are three near-identical classes
Each is a `final class` wrapping a `UUID` with `generate()`/`of()` factories
and value-based equals/hashCode. A generic `record Identifier<U>` or sealed
hierarchy would collapse them to ~1/3 of the code.
**Why kept:** refactor of large surface, breaks ArchUnit rules, low payoff at
this scale. Left as documented debt.

### [4] No role-based authorization beyond `ROLE_USER`
Every authenticated user has the same role; authorization is owner-based
(`ownerId`), not role-based. There are no admin endpoints.
**Close it when:** admin or multi-tenant features are needed.

### [5] No async work / job queue / message broker
Everything is synchronous: no `@Async`, no scheduler, no message broker
(Kafka/RabbitMQ). The API publishes and consumes no events. Adding a broker
"to have it" would be complexity without a use case at this scale.
**Close it when:** a real async use case appears — background jobs (welcome
emails, periodic cleanup), out-of-band audit writes (see issue #9), or email
notifications (see [9]).
**How:** start with a single consumer for one concrete event (`@Async` or a
broker); do not stand up infrastructure speculatively.

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
and almost certainly async delivery (pulling in limitation [5]).
**Close it when:** outbound email becomes a real product requirement.
**How:** SMTP provider (SES/Postmark) + an async sender (`@Async` or a broker,
per [5]). Do not send email synchronously on the request thread.

---

## How to read this file

- **Active decisions** follow the template: **Status** (Accepted/Superseded +
  date) · **Context** (the problem) · **Decision** (what was chosen) ·
  **Consequences** (trade-offs accepted). One paragraph per section.
- **Known limitations** carry a "Close it when" and "How".
- This document is for what is **accepted** (out of scope by choice); GitHub
  Issues are for what is **planned**. When a limitation is promoted to planned
  work, mark it **Superseded** here and link the issue (see [1] → issue #11).
- Don't delete a reversed decision — mark it **Superseded** and point to the
  new one. The history is the point of the log.

> Limitation numbers are stable identifiers referenced from code and tests
> (e.g. `RevokedRefreshTokenRepository` references [5]). Removed entries leave
> a gap rather than renumbering — `[3]` and `[6]` were merged into #13 and
> [5] respectively.
