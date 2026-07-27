# Task Manager API — Project Guidelines

## Context
Java/Spring REST API portfolio project. **Goal: demonstrate mastery of
professional REST APIs.** Code must impress technical clients in 15 seconds
of GitHub reading.

## Stack (fixed, do not change without discussion)
Authoritative list and rationale: **`README.md` → Tech stack**. Key invariants:
PostgreSQL 16 in both prod and tests (no H2), JWT via jjwt with `iss`/`aud`
enforced in the parser, access 15 min + refresh 7 days rotated via
`/auth/refresh`, Maven wrapper (`./mvnw`), Testcontainers for integration tests.

## Architectural Patterns

### Simplified DDD (not full DDD — this is a portfolio, not enterprise)
Package structure organized by bounded context, not by technical layer.
Contexts: `users` (authentication) and `tasks` (projects and tasks). Full
diagram and rationale: **`README.md` → Architecture** and **`DECISIONS.md` #1**.

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
- Single standardized JSON shape (6 fields) — see **`DECISIONS.md` #5**.

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
1. **MANDATORY planning before every feature.** Before writing any code,
   produce a written plan covering: files to create/edit, design decisions,
   test plan (what to test, which layer: unit vs integration), and edge cases to
   cover. Get explicit approval before executing. This prevents coverage gaps
   like missing authorization tests caught in an earlier review.
2. Break implementation into phases (e.g. "phase 1: bootstrap + config",
   "phase 2: users domain"...).
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

### 5. Review discipline — one pass, then merge or follow-up
Learned from PR #14, which ran 3 review rounds for a ~300-line feature. Round 1
caught 2 real production bugs (worth it). Rounds 2 and 3 each produced ~1 minor
fix plus a false-positive "blocker" that had to be disproved empirically.
Net: ~3-4h of marginal work for marginal value.

**Before requesting review, set a stop-loss:**
- "This is the only planned pass. Security/contract/crash bugs get fixed
  in-PR; everything else goes to a follow-up issue and the PR merges."
- Without this, every round generates work for the next one.

**Triage every finding into one bucket, and act only on the first two:**
1. **Block merge** — security bypass, broken contract, crash, data loss. Fix now.
2. **Fix in this PR** — doc-vs-code drift, inconsistency between analogous
   endpoints (rule #3), anti-regression test for the current fix.
3. **Follow-up issue** — style, speculative defense (null-guards against
   impossible states), "nice-to-have" abstraction, polish.

**Every "X is a bug" claim must come with proof, not a guess.**
This applies to the reviewer too. Demand `file:line` and the grep/test/IT that
reproduces it. The two false-positive "blockers" in PR #14 (trailing-slash
bypass, ignored config) would have died at the source if the reviewer had run
one IT or measured the YAML indentation before claiming. Rule #4 already says
"verify before acting" — this says "the reviewer must verify before claiming".

**For portfolio work specifically: stop earlier.** A PR that demonstrates the
technique with sound decisions documented has done its job after one solid
review. Time spent polishing an already-correct feature has negative ROI vs.
shipping the next feature (metrics, deploy, another endpoint).

## Engineering decisions and known limitations

All design decisions, accepted trade-offs, and known limitations live in
**`DECISIONS.md`** (project root). That is the single source of truth for
"why we chose X" and "what we consciously do NOT do".

When a new decision is made or a limitation accepted during agent work:
- Add it to `DECISIONS.md` under the appropriate section.
- Do NOT duplicate the rationale here — `AGENTS.md` is for agent rules and
  project conventions, not for decision history.
