# Code Review — Task Manager API

**Data:** 2026-07-21
**Branch:** `fix/review-findings`
**Reviewer:** ZCode (automated, evidence-based — verified directly in code)
**Scope:** static review (code, tests, CI, Docker, docs). All findings
include `file:line` references verified independently, not inherited from
previous reviews.

> **Honest verdict:** the project is above the average junior/mid portfolio
> — real architecture, honest tests, adult choices (Flyway owns schema,
> `ddl-auto=validate`, Testcontainers instead of H2). But it has
> **3 weaknesses a senior reviewer would catch in 5 minutes**: the refresh
> token is an incomplete feature (no endpoint consumes it), CORS is not
> configured, and there's an **enumeration leak** that contradicts the
> project's own security posture. It impresses on first read but does not
> survive a sharp technical interview without fixes.

---

## 1. Executive summary

| Verdict | Detail |
|---------|--------|
| Architecture | Simplified DDD executed honestly. Domain layer is genuinely framework-free. |
| Tests | 55 passing (55/55), 96.7% instruction coverage, zero cheater patterns. Behavioral assertions, not implementation mirroring. |
| Security | JWT pipeline works for the happy path and 6 token variants. **Refresh is incomplete**, **CORS missing**, **enumeration leak** in 2 of 3 use cases. |
| Build / CI | JaCoCo gate at 80% LINE bound to `verify`, Surefire/Failsafe split clean. PIT profile exists but is never activated by CI — documented as proven when it isn't. |
| Docker | Multi-stage, non-root user, layer caching. App has no healthcheck of its own. |

**Bottom line:** presentable as a portfolio today, but two findings
(refresh endpoint + CORS) can turn a technical interview from "wow" into
"wait, this doesn't work?" Fix those before sending the link publicly.

---

## 2. What genuinely impresses (verified, not README decoration)

1. **Pure domain, for real.** `grep -rE "org\.springframework|jakarta\." src/main/java/com/renan/taskmanager/*/domain/` returns nothing. `UserRepository`, `PasswordHasher` are interfaces defined in the domain package. The domain is readable without knowing Spring exists.
2. **State machine is real.** `tasks/domain/TaskStatus.java:34-56` uses `Map<TaskStatus, Set<TaskStatus>>` with explicit transitions and a typed `InvalidStatusTransitionException`. `Task.transitionTo` delegates. Not a cosmetic switch.
3. **Behavioral tests, not mirrors.**
   - `LoginUseCaseTest:147-164` — verifies that unknown email and wrong password yield the **same** error message (anti-enumeration invariant).
   - `ProjectTest:117-132` — mutates the task list after `reconstitute` to prove the defensive copy works.
   - `PasswordTest:88-107` — covers both sides of the boundary (7 and 8 chars) explicitly to kill PIT mutations.
4. **Zero cheater patterns.** `grep -rE "@Disabled|@Ignore|assertThat\(true\)"` returns nothing. `LoginUseCaseTest:55-56` has a comment justifying why `JwtService` is **not** mocked — correct posture, the test stays end-to-end.
5. **ArchUnit is substantive.** `ArchitectureTest` enforces cross-context isolation (`tasks` ↔ `users` cannot depend on each other), restricts `@RestController` to `..api..`, forbids `Service`/`Controller`/`Config` suffixes in the domain. Would catch real erosion in a future PR.
6. **Centralized error contract, tested field-by-field.** `ErrorResponseContractIT` validates `{timestamp, status, error, message, path, details}` for 7 status codes (400/401/403/404/405/409/500), including malformed body and multi-error validation.
7. **CI is well-wired.** Maven cache, `concurrency` cancels stale runs, JaCoCo 80% LINE gate at `verify`, clean Surefire/Failsafe split.
8. **Docker done right.** Multi-stage, `appuser` (non-root), `eclipse-temurin:21-jre-alpine`, `COPY pom.xml` before `src` for layer caching.

---

## 3. Findings by severity

### 🔴 P0 — Incomplete feature that misrepresents capabilities

#### P0-1. Refresh token is emitted but never consumable

**Files:** `common/security/JwtService.java:75` (emits), `users/api/AuthController.java` (no `/refresh` route)

The README advertises "**access token (15 min) + refresh token (7 days)**" as a delivered capability. Reality:

- `JwtService.generateRefreshToken` (line 75) is called in `LoginUseCase` and the refresh token is returned on login.
- `grep -rn "refresh" src/main/java/` shows **no `/auth/refresh` endpoint**.
- `TokenResponse.java:8` admits in a comment: *"refresh endpoint, future work."*

**Why it matters in a portfolio:** a senior reading "refresh token" will ask
"show me the route that exchanges a refresh for a new access token." It does
not exist. This is worse than not advertising refresh — it advertises what
does not work, and turns into an awkward moment in interviews.

**Fix (~2h, TDD):**
1. Write `RefreshTokenIT` first (Red): `POST /api/v1/auth/refresh` with a valid refresh token → 200 + new token pair; with an access token used as refresh → 401; with expired refresh → 401.
2. Add `RefreshTokenUseCase` in `users/application/` that validates `type=refresh` via `JwtService.parseAndValidate` and reissues the pair.
3. Add the route in `AuthController`. Reuse the existing request/response records.

This **closes the story** and removes the most exposed surface in interviews.

---

### 🔴 P0 — Security posture contradicted

#### P0-2. CORS is not configured anywhere

**Files:** `common/security/SecurityConfig.java` (no `.cors(...)`, no `CorsConfigurationSource` bean)

`grep -rni "cors" src/main/java/` returns **zero hits**. Without `CorsConfigurationSource`, any browser-based SPA (`http://localhost:3000`, etc.) calling this API fails CORS preflight. Postman/Swagger never surface this — the bug only appears in a real front-end demo.

**Why it matters:** the README sells "production-grade backend" but the API cannot be called from a browser. This is a hole a senior reviewer notes immediately.

**Fix (~20 lines):**
```java
@Bean
CorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.allowed-origins:http://localhost:3000}") String[] origins) {
    var cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(Arrays.asList(origins));
    cfg.setAllowedMethods(List.of("GET","POST","PATCH","DELETE","OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization","Content-Type","X-Request-Id"));
    cfg.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
}
```
Add `.cors(Customizer.withDefaults())` to the `SecurityFilterChain`. In prod, `app.cors.allowed-origins` comes from an env var.

---

#### P0-3. Enumeration leak in 2 of 3 owner-authorization use cases

**Files:**
- `tasks/application/GetProjectUseCase.java:28` — `.orElseThrow(ProjectNotFoundException)` → 404 if absent
- `tasks/application/CreateTaskUseCase.java:30` — same pattern → 404 if project absent
- `tasks/application/DeleteProjectUseCase.java:38-40` — explicitly collapses 404 into 403 (with a comment explaining the choice)

Inconsistency: `DeleteProjectUseCase` adopted the anti-enumeration posture (a non-owner gets 403 whether the resource exists or not), but `GetProjectUseCase` and `CreateTaskUseCase` reveal existence — a non-owner can distinguish "exists but not mine" (403) from "does not exist" (404).

**Why this is the most damning finding:** the code **commits** to anti-enumeration and then **breaks** the commitment in 2 of 3 cases. That is the exact signature of rushed code review — a senior would treat it as evidence the author didn't reconcile their own decisions.

**Fix:** standardize. Either check ownership **before** revealing 404 in every authenticated use case (align with `DeleteProjectUseCase`), or accept the leak everywhere. Recommended: align with `DeleteProjectUseCase` — 404 → 403 for non-owners. Update the relevant ITs to assert the collapsed behavior.

---

### 🟡 P1 — Documentation that lies about the code

#### P1-1. JWT Javadoc lists claims the parser never validates

**File:** `common/security/JwtService.java:38` (Javadoc) vs. lines 97-103 (parser)

The Javadoc enumerates the token claims as if `iss`/`aud` were enforced. `grep -nE "requireIssuer|requireAudience|setIssuer|require\(" JwtService.java` returns **nothing**. The parser only checks signature + `exp`. Without `iss`/`aud`, a token minted for one service is valid for another sharing the same key.

**Impact for a solo portfolio:** low. But the Javadoc states something untrue — a reviewer reading the doc then opening the parser notices.

**Fix:** either add `.issuer(...)` to the builder + `.requireIssuer(...)` (and equivalent for `aud`) in ~10 lines, or correct the Javadoc to describe what is actually validated. Do not let the doc lie.

---

#### P1-2. PIT is documented as proven but CI never runs it

**Files:** `README.md:27` ("Mutation testing with PIT proves the tests actually catch bugs"), `.github/workflows/ci.yml` (no `-Ppit` activation), `pom.xml` lines 342-395 (profile defined)

The CI uploads `target/pit-reports/**` (ci.yml:60-69) but `verify` never activates the `pit` profile, so the artifact is never produced. PIT runs only locally, on demand.

**Why it matters:** "PIT proves" is a strong claim. The repository does not attest it continuously. A reviewer who clones and runs `mvn verify` gets no mutation evidence.

**Fix:** either activate `-Ppit` in CI with scoped `targetClasses`/`targetTests` so the run stays under ~5 min, or change the README to "PIT available locally (scoped runs)" — honest about what actually happens.

---

#### P1-3. `application-prod.yml` comment describes behavior that is not true

**File:** `src/main/resources/application-prod.yml:7-8`

Comment: *"ddl-auto=update in dev is a development convenience."*
Reality: `application.yml:21` shows dev is **also** `validate` (`${DDL_AUTO:validate}`). The comment was written for an earlier version and never updated. Small, but it undermines trust in the rest of the file's documentation.

**Fix:** one line. Align the comment with the real default (`validate` everywhere, Flyway owns schema in all profiles).

---

#### P1-4. BCrypt cost below OWASP 2026 recommendation, with duplicated encoder

**Files:**
- `common/security/SecurityConfig.java:75` — `BCryptPasswordEncoder` with cost 10
- `users/infrastructure/BCryptPasswordHasher.java:29` — **another** `BCryptPasswordEncoder` with cost 10

Two independent encoders, two sources of truth. If one's cost changes, the other does not follow. The hasher's Javadoc claims it delegates to the SecurityConfig bean, but it constructs its own instance.

OWASP 2026 recommends BCrypt cost **12**. Cost 10 is fine in 2020, dated in 2026.

**Fix:** inject the `PasswordEncoder` bean into `BCryptPasswordHasher` instead of `new`-ing one; bump cost to 12 in `SecurityConfig` (single source of truth). Adjust the existing BCrypt tests for the new cost if they hardcode timing assumptions.

---

### 🟢 P2 — Polish / honesty debt

#### P2-1. Test gaps in application + infrastructure of `tasks`

Honest gaps (no cheater patterns, just missing dedicated coverage):

- `ListProjectsUseCase`, `ListTasksUseCase`, `DeleteProjectUseCase` — no `*UseCaseTest`. Covered only indirectly via `ProjectTaskIT` (happy path). Branch-level cases (non-owner, pagination edge) have no focused unit test.
- `ProjectRepositoryImpl`, `TaskRepositoryImpl` — **no `*ImplIT`** (contrast with `UserRepositoryImplIT`, which tests the real unique constraint). Custom queries filtering by `ownerId`/`status` are not exercised against real PostgreSQL.
- `JwtAuthenticationFilter` (100 LOC) — no direct test; covered only indirectly via `JwtAuthorizationIT`.

**Fix:** add focused unit/slice tests for these. Not filler — these are the layers where real bugs hide.

---

#### P2-2. Dead code / over-engineering in the domain

- `tasks/domain/Priority.java:29` — `Priority.weight()` is called only in tests. No production call site uses weight for sorting.
- `tasks/domain/Task.java:83` — `Task.complete()` shortcut is called only in tests.
- `tasks/domain/TaskStatus.java:43` — `canTransitionTo` is public but only tests use it (`assertTransitionTo` already covers production).
- `tasks/domain/Project.java:121` — `TaskAdded` record is reserved for extension that does not exist (YAGNI).
- `UserId` / `ProjectId` / `TaskId` — three near-identical copies. A generic `record` or sealed hierarchy would collapse them to ~1/3 of the code.

**Fix:** delete the unused ones, or justify each with a comment explaining why it's kept. Inflating the public API without payoff is the kind of thing reviewers penalize.

---

#### P2-3. Minor infra polish

- **Docker app has no healthcheck.** `docker-compose.yml` defines a healthcheck for Postgres (good) but not for the `app` service. Add a `/actuator/health` probe (requires `spring-boot-starter-actuator`) or at minimum a `wget --spider` check.
- **CI has no test-report step.** Surefire/Failsafe XML is not published via `dorny/test-reporter` or similar; the GitHub UI shows only raw logs on failure.
- **CI has no JDK matrix.** Only temurin 21 is tested. Adding 17 LTS would support the "Java 21" claim and catch `SequencedCollection`/pattern-matching leakage assumptions.
- **Spring Boot 3.3.2 is ~12 months behind.** 3.4/3.5 are stable and maintain Java 21. MapStruct 1.5.5 → 1.6.x available. Not critical, but the portfolio loses the "current stack" argument.

---

## 4. What NOT to change (explicit protection list)

These are correct and should not be touched in a refactor pass:

- Simplified DDD structure (bounded contexts `users`/`tasks`, layers `domain/application/infrastructure/api`).
- Pure domain layer — no Spring/JPA imports in `*/domain/`.
- `ErrorResponse` six-field shape and `GlobalExceptionHandler`.
- Centralized `JwtAuthenticationFilter` + `JsonAuthenticationEntryPoint` design.
- `CorrelationIdFilter` + `SanitizingRequestLoggingFilter` (X-Request-Id in MDC, header redaction).
- Flyway ownership of schema with `ddl-auto=validate` in all profiles.
- The decision to keep 3 distinct Spring boots in CI (slice JPA vs full context vs prod profile) — semantically distinct, cache already optimized.
- 96.7% coverage — do not push artificially to 100%.
- Surefire (`*Test`) / Failsafe (`*IT`) split and the JaCoCo merge of exec files.

---

## 5. Prioritized action plan

Each item is an atomic commit. Ordered by interview impact per hour of effort.

| # | Action | Effort | Why |
|---|--------|--------|-----|
| 1 | **Implement `/auth/refresh` route (TDD)** — `RefreshTokenUseCase` + endpoint + `RefreshTokenIT` | ~2h | Closes the half-feature most exposed in interviews |
| 2 | **Standardize enumeration leak** — align `Get`/`Create` with `DeleteProjectUseCase` | ~1h | Fixes the inconsistency that signals rushed review |
| 3 | **Add CORS** — `CorsConfigurationSource` + `.cors(...)` in `SecurityConfig` | ~30min | Without this, any browser demo fails |
| 4 | **JWT `iss`/`aud`** — add to builder + parser (or correct the Javadoc) | ~30min | Doc must not lie about the code |
| 5 | **Activate PIT in CI** (scoped) or adjust README wording | ~1h | Don't claim "proven" without continuous attestation |
| 6 | **BCrypt cost 12**, inject `PasswordEncoder` bean into the hasher | ~15min | OWASP 2026 + single source of truth |
| 7 | **Fix `application-prod.yml` comment** | ~5min | Small, but reflects attention |
| 8 | **Unit tests for `ListProjectsUseCase`, `DeleteProjectUseCase`** + `ProjectRepositoryImplIT`, `TaskRepositoryImplIT` | ~2h | Closes real coverage holes in tasks layers |
| 9 | **Delete dead code** (`Priority.weight`, `Task.complete`, `TaskAdded`, redundant `canTransitionTo`) | ~30min | Tightens the public API |
| 10 | **App healthcheck in docker-compose**, test-report step in CI, bump Spring Boot 3.3 → 3.5 | ~2h | Polish that separates "good" from "memorable" |

Doing **1 + 2 + 3** alone makes the project interview-proof. The rest is
polish that takes it from "good portfolio" to "memorable portfolio."

---

## 6. Final grade (honest scale)

| Tier | Description | Score |
|------|-------------|-------|
| Median junior portfolio | CRUD, no tests, H2, no migrations, no auth | 4/10 |
| "Trying" junior portfolio | Some unit tests, JPA, basic auth | 6/10 |
| **This project today** | Architecture, observability, honest tests, CI/Docker — but inconsistencies + incomplete feature | **7.5/10** |
| **This project after fixes 1-6** | — | **8.5/10** |

For the stated goal (junior/mid Java back-end role in Brazil): **yes, ready**,
but you are leaving points on the table if you send the link now without
fixing at least the refresh endpoint and CORS. Those two alone can flip a
technical interview from "wow" to "wait, this doesn't work?"

---

## 7. On the use of AI in development (asked by the author)

**What worked well:**
- `AGENTS.md` is **excellent**. Documenting decisions ("Simplified DDD, not
  full", "Testcontainers over H2", "keep 3 Spring boots in CI") with
  rationale is what separates "used AI to write code" from "used AI to
  think with me." The "Pending design decisions → document here when
  decided" section is professional-grade.
- This very document is honest — including the section below acknowledging
  where the AI-generated code shows seams.
- Step-based workflow with `[x]` checkboxes and commit hashes
  synchronizing the roadmap with the real project state is a senior habit.

**Where AI use left detectable seams:**
- The inconsistencies (enumeration leak in 2/3 cases, Javadoc listing
  unenforced claims, `application-prod.yml` comment out of date) are the
  **classic pattern of long-session AI-assisted code**: the agent writes
  the first version correctly, then in a later session loses the context
  and introduces a divergence a human holding the whole picture would not.
- The half-finished refresh token smells the same — the agent implemented
  "access + refresh tokens" because it was the default shape, but the
  consuming route fell into "future work" and nobody came back to close it.

**Lesson for the market:** when you say "I used AI" in an interview, the
senior reviewer will **hunt for exactly these inconsistencies**. The
difference between "a developer who uses AI well" and "a developer the AI
uses" is your ability to catch these divergences. This `REVIEW.md` shows
you already have that muscle — the next step is applying it **before each
commit**, not only in a final review session.
