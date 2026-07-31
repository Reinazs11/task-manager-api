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

BCrypt is retained for project compatibility at cost 12.
`BCryptPasswordHasher` injects the bean instead of constructing another
encoder, and `Password` rejects inputs above BCrypt's 72-byte UTF-8 limit.
OWASP prefers Argon2id for new systems; that migration is outside v1 scope.

### 11. OpenAPI disabled in prod, explicitly enabled for the demo
**Status:** Accepted

`application-prod.yml` disables Swagger and API docs. `application-demo.yml`
overrides both only when `prod,demo` is selected for the portfolio deployment.
Tests lock both profiles and the standard error contract.

### 12. Actuator: `/actuator/health` and `/actuator/prometheus` exposed
**Status:** Accepted (2026-07) — updated (2026-07) for Prometheus (see #19)

`management.endpoints.web.exposure.include=health,prometheus` and
`endpoint.health.show-details=never`. Liveness/readiness for Docker/K8s without
leaking env, beans, heap dumps, or component details. `/actuator/health` is
public (no JWT) so probes work; `/actuator/prometheus` is JWT-protected (it
falls under `SecurityConfig`'s `anyRequest().authenticated()`). Everything
else stays unexposed.

### 13. PIT mutation testing scoped to domain and authentication in CI
**Status:** Accepted (2026-07)

CI mutates all domain packages plus login, registration, refresh and logout use
cases, with an 80% mutation threshold. The v1.0.0 gate generated 234 mutations,
killed 203 (87%) and reported 93% test strength. Whole-project mutation remains
out of scope because Spring integration startup dominates its cost.

Justified survivors on the domain layer (do not chase):
- `hashCode()` returning 0 — no observable contract without a HashMap.
- `toString()` returning "" — debug-only, not asserted.
- `equals()` on `User`/`Task` entities — identity-based, partially covered by
  reconstitute tests; full coverage is low-value.

### 14. Maven Surefire (`*Test`) / Failsafe (`*IT`) split + JaCoCo merge
**Status:** Accepted

Unit tests run fast with Surefire (no Docker). Integration tests run with
Failsafe (Testcontainers). JaCoCo merges both `.exec` files before checking
global LINE ≥80% and BRANCH ≥70%. The `users` authentication context has a
separate LINE ≥90% / BRANCH ≥80% gate. ArchUnit runs in every build.

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
rotation) or revoked by `POST /auth/logout`. `RefreshTokenUseCase` atomically
claims a `jti` and rejects a conflict with the same
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
long-lived refresh token is the one that matters. PostgreSQL
`INSERT ... ON CONFLICT DO NOTHING` is the authority: exactly one concurrent
refresh acquires the token and mints a new pair. Logout ignores a conflict and
therefore remains idempotent with 204.

**Consequences:** `RevokedRefreshTokenRepository.deleteExpired(now)` purges
expired rows, but no scheduler invokes it today (limitation [5]). The hook is
on the port so a future scheduler need not reach past the domain. Dead rows are
harmless because a repeated `jti` remains rejected after token expiry.

---

### Decision #18 — Publish Docker image to GHCR on merge to main

**Status:** Accepted (2026-07) — closes issue #8

**Context:** CI was test-only — no distributable artifact, so a reviewer could
not `docker pull` the app, and the planned deploy (#5) and metrics (#6) had no
image to consume.

**Decision:** `docker-publish.yml` is a reusable workflow with no independent
trigger. `ci.yml` calls it only after verify and PIT pass: PRs never publish;
`main` and `v*` tags do. Multi-arch
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

### Decision #20 — Native Spring Boot structured JSON logging in prod

**Status:** Accepted (2026-07) — closes issue #10

**Context:** Plain-text logs are unparseable in production aggregators (Loki,
ELK, Datadog): fields can't be queried and the correlation id is buried in the
message string. Pairs with #19 — logs say *what happened*, metrics say *how
often/how slow*.

**Decision:**
- Spring Boot 4.1's native `logging.structured.format.console=logstash`
  replaces the third-party encoder.
- `dev`/`test` remain human-readable; `prod` emits one JSON object per line.
- MDC `requestId` is preserved as a first-class JSON field.
- HTTP context (`method`/`uri`/`status`/`latencyMs`/`client`) uses SLF4J
  fluent key-value pairs and is promoted to JSON fields in prod.
- Secret redaction lives in `SanitizingRequestLoggingFilter`, upstream of the
  encoder — switching to JSON does not weaken it. The encoder never sees raw
  headers.
- `StructuredLoggingEncoderTest` drives Boot's native encoder; the prod-profile
  integration test proves the selected format and service identity.

**Consequences:** Prod logs are machine-parseable without another runtime
dependency; dev readability is preserved. Logging stays synchronous.

---

### Decision #21 — Audit logging: same-transaction writes with a forensic exception for failed logins

**Status:** Accepted (2026-07) — closes issue #9

**Context:** State-changing operations mutated data with no record of who did
what, when. This is the third observability pillar alongside #19 (metrics) and
#20 (logs): logs say *what happened in this request*, metrics say *how often*,
audit says *who changed what over time*.

**Decision:** A new `common.audit` context (shared kernel, like `UserId` —
ArchUnit forbids direct `tasks`↔`users` deps) records an immutable
`audit_events` row for every state-changing operation (project/task CRUD,
register, login, logout, refresh rotation). The recorder runs inside the use
case's `@Transactional(REQUIRED)` *after* the mutation succeeds — so if the tx
rolls back, the audit row is discarded too: the trail records only *committed*
facts. The single exception is `USER_LOGIN_FAILED`, which runs in
`REQUIRES_NEW`: a failed login is the most valuable forensic signal, so its row
must survive the `InvalidCredentialsException` that follows.

Anti-enumeration (#6) is preserved two ways: `USER_LOGIN_FAILED` never records
an actor id (even when the userId is known — wrong password on a valid email),
and the `AuditEvent` constructor *enforces* this by rejecting a non-null actor
on that action. `metadata` is a flat allowlisted map of operational fields only
(`{from,to}` for status changes, `{priority}` for task creation) — never request
bodies, passwords, or emails. The request correlation id flows in via a
`CorrelationIdProvider` (not a static `MDC.get` in use cases — that would be
untestable in unit tests). The read endpoint `GET /audit/events` is self-scoped
(no admin path; limitation [4]); `AuditEventEntity.id` is domain-assigned with
no `@GeneratedValue`, matching `RevokedRefreshTokenEntity`.

**Consequences:** The trail is coherent and forensically complete. A failing
audit insert would roll back the business operation — acceptable, since such an
insert only fails on a real integrity error (DB down), where the business op is
failing anyway. The table grows unbounded; retention is a documented follow-up
(limitation [10]).

### Decision #22 — Spring Boot 4.1 and domain-assigned persistence identity

**Status:** Accepted (2026-07)

The project migrated through Spring Boot 3.5.16 before 4.1.0, following the
official migration path. Boot 4 modular starters, Jackson 3, springdoc 3,
Testcontainers 2, MapStruct 1.6.3 and jjwt 0.13 form the v1 stack. API version
metadata comes from Maven build properties.

Hibernate 6.6+ treats a domain-assigned UUID as an existing entity when
`save` chooses merge. Repository adapters therefore call `EntityManager.persist`
for new aggregates and merge only known task updates. Domain IDs remain the
source of identity; generated IDs would create a second authority.

### Decision #23 — Free demo blueprint is an explicit profile

**Status:** Accepted (2026-07) — tracks issue #5

`render.yaml` defines the intended free Docker web service with
`autoDeployTrigger: checksPass`; an external PostgreSQL 16 provider such as
Neon supplies TLS connectivity. `prod,demo` is the only public-docs
combination. Secrets remain external, Hikari is intentionally small, and no
keep-alive traffic defeats Render's idle policy. The documented cold start is
accepted for a zero-cost portfolio demo, but this repository does not claim a
live deployment until issue #5 is completed and smoke-tested.


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

### [10] No audit retention / purge policy
The `audit_events` table (decision #21) is append-only and grows unbounded.
There is no scheduled job to archive or purge old rows. The
`idx_audit_events_actor` index keeps the "my recent activity" query cheap
regardless of table size, so this is a storage concern, not a performance one.
**Close it when:** the table reaches a size where storage cost or backup time
matters, or when a compliance regime demands a defined retention window.
**How:** add a `deleteExpired`/`deleteOlderThan` to `AuditEventRepository`
mirroring `RevokedRefreshTokenRepository.deleteExpired`, and a scheduler to
invoke it (pulls in limitation [5]). A retention window (e.g. 365 days) and
a "cold archive to object storage" step would be the production shape.

### [24] Cloud deployment files are illustrative until externally verified
The repository includes a Render blueprint and PostgreSQL connection example
to demonstrate how the container could be hosted with an external database.
They do not provision accounts, create a database, store secrets, or prove a
live deployment. Keeping the example in Git makes the intended environment
reviewable without presenting an unverified URL as portfolio evidence.
**Close it when:** a real deployment is created, smoke-tested, and its public
URL is recorded in the README.

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
