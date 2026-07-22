# Code Review — Task Manager API

**Data:** 2026-07-22
**Branch:** `fix/review-findings` (30 commits ahead of `main`)
**Reviewer:** ZCode (automated, evidence-based — every finding verified directly in code)
**Status:** ✅ **All findings resolved** (every P0 + P1 + P2 either fixed, or explicitly accepted with documented rationale)

> **Honest verdict:** every issue identified during the static review has been
> addressed in this branch. The project moved from "impresses on first read,
> fails a sharp interview" (7.5/10) to a state where the main risks a senior
> would hunt for — refresh-token half-feature, missing CORS, enumeration
> leak, JWT issuer/audience gaps, BCrypt cost drift, dead code, doc-vs-code
> drift — are closed. Ready to send publicly.

---

## 1. What this branch delivered

| # | Finding (severity) | Resolution | Commit |
|---|---|---|---|
| 1 | Refresh token emitted but never consumable (P0) | **Fixed:** new `POST /api/v1/auth/refresh` with rotation + 5-case IT | `7669a79` |
| 2 | No CORS anywhere (P0) | **Fixed:** `CorsConfigurationSource` bean + env-driven origins + 3-case IT | `0d7acee` |
| 3 | Enumeration leak in 2/3 use cases (P0) | **Fixed:** collapsed to 4 use cases (was 4-vs-2 after re-verification); 403 uniformly | `28441fb` |
| 4 | BCrypt cost 10 + duplicate encoder (P1) | **Fixed:** cost 12 + hasher now injects the bean | `af5a62b` |
| 5 | JWT parser didn't enforce iss/aud (P1) | **Fixed:** iss/aud claims + `parseAccessToken`/`parseRefreshToken` centralized | `cc6a8e5` |
| 6 | PIT documented as "proves" but never ran in CI (P1) | **Fixed:** scoped domain-layer PIT runs on every push; false comment removed | `cf067d5` |
| 7 | `application-prod.yml` comment lied about ddl-auto (P1) | **Fixed** (rolled into CORS commit) | `0d7acee` |
| 8 | Dead code in domain (P2) | **Fixed:** removed `Priority.weight`, `Task.complete`, `Project.TaskAdded`; tightened `canTransitionTo` to package-private | `2d66cc2` |
| 9 | Test gaps: ListTasks/DeleteProject use cases (P2) | **Fixed:** focused unit tests with anti-enumeration assertions | `e973356` |
| 10 | Test gaps: Project/Task repository adapters (P2) | **Fixed:** `ProjectRepositoryImplIT` + `TaskRepositoryImplIT` (14 tests against real Postgres) | `abcdbbd` |
| 11 | `JwtAuthenticationFilter` had no direct test (P2) | **Fixed:** 5-branch unit test driving each decision path | `22f0d41` |
| 12 | No app healthcheck, CI had no test reporter (P2) | **Fixed:** actuator `/actuator/health` + Docker healthcheck + `dorny/test-reporter` | `ded5f9a` |

Final build: `./mvnw -B -ntp clean verify` → BUILD SUCCESS, JaCoCo gate ≥80%
LINE passes. Test count grew from 55 to **80** (24 unit + 56 integration),
including dedicated coverage for every use case, repository, the JWT filter,
the new refresh endpoint, CORS, and the actuator probe.

---

## 2. Re-verification log (false positives caught)

The first pass of the review made three claims that didn't survive direct
inspection. They are documented here — both to fix the record and as a
reminder that the review process itself must be auditable.

### False positive P1-1 — "JwtService Javadoc lies about iss/aud"
**Original claim:** the Javadoc listed iss/aud as enforced claims the parser
never checked.
**Reality:** `grep -nE "requireIssuer|requireAudience|setIssuer" JwtService.java`
returned nothing — but so did `grep "iss|audience"` on the Javadoc. The
Javadoc never mentioned iss/aud. The real weakness was different: the parser
only validated signature+exp, and the type check (access vs refresh) lived
in the filter, not the service.
**Resolution applied:** added iss/aud claims AND centralized the type check
into `parseAccessToken`/`parseRefreshToken`. The fix ended up broader than
the original (misframed) finding suggested.

### False positive P2-2 — "delete `TaskStatus.canTransitionTo`"
**Original claim:** `canTransitionTo` was dead code (only tests called it).
**Reality:** it has 1 production caller — `TaskStatus.assertTransitionTo`
(line 53 of the same file) uses it internally. The method was only
unnecessarily `public`.
**Resolution applied:** tightened to package-private. Tests in the same
package still exercise it; external callers go through `Task.transitionTo`.

### False positive P1-4 (sub-claim) — "BCrypt tests hardcode timing"
**Original claim:** bumping cost to 12 would require adjusting tests that
hardcoded timing assumptions.
**Reality:** `BCryptPasswordHasherTest` asserts only on hash format
(`$2a$`), random salt, and match/no-match — nothing about cost or timing.
**Resolution applied:** cost bumped to 12 with zero test changes; the test
was updated only to construct the hasher with the injected `PasswordEncoder`
mirror.

### Two new sub-findings surfaced during re-verification
These weren't in the first review at all — both were doc-vs-code drift that
the same long-session AI pattern produced:
- `SecurityConfig.java:25-26` claimed `BCryptPasswordHasher` "delegates to
  it internally" while the hasher constructed its own encoder. Resolved
  in `af5a62b` (the delegation is now real).
- `ci.yml:63` claimed PIT "kicks in only if someone triggers the profile
  manually via workflow_dispatch", but `on:` never declared
  `workflow_dispatch`. Resolved in `cf067d5` (PIT now runs automatically;
  the false comment is gone).

---

## 3. What genuinely impresses (still true, still verified)

1. **Pure domain, for real.** Zero Spring/JPA imports in `*/domain/`.
2. **State machine is real.** `TaskStatus` uses a `Map<Status, Set<Status>>`
   with typed `InvalidStatusTransitionException`; not a cosmetic switch.
3. **Behavioral tests, not mirrors.** Anti-enumeration invariants, defensive
   copies, PIT-aware boundary cases.
4. **Zero cheater patterns.** No `@Disabled`, no tautological assertions,
   mocks used surgically with comments explaining why a dependency was
   NOT mocked.
5. **ArchUnit is substantive** — cross-context isolation, stereotype
   placement, naming rules. Catches real erosion.
6. **Centralized 6-field error contract**, tested for 7 status codes.
7. **CI is well-wired** — Maven cache, `concurrency`, JaCoCo 80% LINE gate,
   PIT runs continuously (scoped), test results published inline.
8. **Docker done right** — multi-stage, non-root, layer caching, app
   healthcheck via Actuator.
9. **JWT pipeline now defensive in depth** — signature + exp + iss/aud +
   type=access, all enforced centrally in `parseAccessToken`.
10. **CORS configured with fail-fast in prod** — empty
    `CORS_ALLOWED_ORIGINS` throws at startup rather than silently allowing
    any origin.

---

## 4. What NOT to change (protection list)

These are correct and should not be touched in any future refactor pass:

- Simplified DDD structure (bounded contexts `users`/`tasks`, layers
  `domain/application/infrastructure/api`).
- Pure domain layer — no Spring/JPA imports.
- `ErrorResponse` six-field shape and `GlobalExceptionHandler`.
- Centralized `JwtAuthenticationFilter` + `JsonAuthenticationEntryPoint`.
- `CorrelationIdFilter` + `SanitizingRequestLoggingFilter`.
- Flyway ownership of schema with `ddl-auto=validate` in all profiles.
- The 3 distinct Spring boots in CI (slice JPA vs full context vs prod
  profile) — semantically distinct, cache already optimized.
- Surefire (`*Test`) / Failsafe (`*IT`) split and JaCoCo merge.
- The anti-enumeration collapse (404 → 403) — it is now uniform across all
  authenticated use cases.

---

## 5. Known accepted trade-offs

The full list of engineering decisions and accepted limitations now lives in
**`DECISIONS.md`** (project root). Highlights relevant to this review:

- **Stateless refresh rotation** — old refresh stays valid until expiry; no
  token store. Path to close: Redis or a `refresh_token_jti` table.
- **`UserId`/`ProjectId`/`TaskId` are three near-identical classes** — left
  as documented debt; refactor risks ArchUnit for little payoff.
- **PIT scoped to the domain layer in CI** — other layers testable on demand.
- **Coverage gate at 80% LINE** — remaining gaps are defensive error-handling
  paths not worth provoking in honest tests.

See `DECISIONS.md` for the rationale and the "close it when" notes on each.

---

## 6. Final grade

| Tier | Score |
|------|-------|
| Median junior portfolio | 4/10 |
| "Trying" junior portfolio | 6/10 |
| This project, pre-`fix/review-findings` | 7.5/10 |
| **This project, after this branch** | **8.5/10** |

The gap to 9-10 is now mostly about *depth*, not correctness: richer
business invariants, role-based authorization beyond `ROLE_USER`, audit
logging, async work. Those are features, not fixes — they belong in a
future scope, not this branch.

---

## 7. On the use of AI in development

The seams that originally gave away AI-assisted development — doc-vs-code
drift, the half-finished refresh token, inconsistent enumeration posture —
are exactly what this branch repaired. The lesson is reinforced rather than
softened:

- **Long sessions are where AI drifts.** Each individual change looked
  correct; the divergence appeared across sessions that didn't share
  context. Documenting decisions in `AGENTS.md` (as this project does) is
  the right defense — it carries context forward.
- **Re-verification is mandatory.** Three of the first review's claims
  were false positives. An uncritical fix pass would have either deleted a
  method still in use (`canTransitionTo`) or fixed a problem that wasn't
  there ("iss/aud mentioned in Javadoc") while missing the real one
  (type-check scattered between filter and use case).
- **The difference between "a developer who uses AI well" and "a developer
  the AI uses" is the ability to catch these divergences — before each
  commit, not only in a final review.**
