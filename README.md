# Task Manager API

[![CI](https://github.com/Reinazs11/task-manager-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Reinazs11/task-manager-api/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)
[![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![GHCR](https://img.shields.io/badge/GHCR-task--manager--api-blue?logo=docker)](https://github.com/Reinazs11/task-manager-api/pkgs/container/task-manager-api)

A Java 21 REST API for personal projects and tasks. It demonstrates secure
authentication, ownership rules, PostgreSQL concurrency, observable operations,
and reproducible delivery without pretending to be a complete product.

**Status:** `v1.0.0 candidate — maintenance only; release pending demo verification`

**Deployment example:** [Render + Neon blueprint](render.yaml) and its
[setup notes](docs/deployment-example.md). No public demo is claimed here:
the example is intentionally not presented as a completed deployment.
Render Free services sleep after inactivity, so a deployed instance may take
about one minute to wake. Never use a real email address or password in a demo.

## What this project demonstrates

- Atomic one-time-use refresh rotation: concurrent replay yields one `200` and one `401`.
- Login anti-enumeration: unknown email and wrong password use the same BCrypt path and `401` contract.
- Concurrent registration: PostgreSQL uniqueness produces one `201`, one `409`, one user and one audit event.
- Pure domain boundaries enforced by ArchUnit; persistence and HTTP stay in adapters.
- PostgreSQL 16 integration tests with Testcontainers; no H2 replacement.
- CI gates tests, JaCoCo, PIT and architecture before GHCR publication or Render deploy.

## Stack

| Area | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 4.1, Spring MVC, Spring Security |
| Data | PostgreSQL 16, Spring Data JPA, Hibernate, Flyway |
| Auth | jjwt 0.13, HS256, BCrypt cost 12, access 15 min / refresh 7 days |
| Contracts | Bean Validation, springdoc OpenAPI 3 / Swagger UI |
| Observability | Actuator, Micrometer/Prometheus, native structured Logstash JSON |
| Quality | JUnit, Mockito, Testcontainers 2, JaCoCo, PIT, ArchUnit |
| Delivery | Maven Wrapper 3.3.4, Docker, GitHub Actions, GHCR, Render + Neon |

BCrypt is retained for project compatibility. New systems should normally
prefer Argon2id; this API explicitly rejects passwords over BCrypt's 72-byte
UTF-8 limit. See the [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

## Architecture

```text
HTTP -> api -> application -> domain
                  |           ^
                  v           |
             infrastructure --+
                  |
             PostgreSQL 16
```

`users` and `tasks` are isolated bounded contexts. The domain is plain Java;
controllers live in `api`; JPA, JWT and BCrypt implementations live in
`infrastructure`. `common` contains the small shared kernel and cross-cutting
security, errors, audit and observability.

## Endpoints

All application routes use the `/api/v1` prefix.

| Method | Path | Auth | Purpose |
|---|---|---:|---|
| POST | `/auth/register` | No | Register |
| POST | `/auth/login` | No | Issue access and refresh tokens |
| POST | `/auth/refresh` | No | Atomically rotate a refresh token |
| POST | `/auth/logout` | No | Idempotently revoke a refresh token |
| POST | `/projects` | JWT | Create a project |
| GET | `/projects` | JWT | List owned projects |
| GET | `/projects/{id}` | JWT | Read an owned project |
| DELETE | `/projects/{id}` | JWT | Delete an owned project |
| POST | `/projects/{id}/tasks` | JWT | Create a task |
| GET | `/projects/{id}/tasks` | JWT | List/filter project tasks |
| PATCH | `/tasks/{id}/status` | JWT | Apply a valid status transition |
| GET | `/audit/events` | JWT | Read the caller's audit events |
| GET | `/actuator/health` | No | Health probe |
| GET | `/actuator/prometheus` | JWT | Prometheus exposition |

Errors use one six-field shape:
`{ timestamp, status, error, message, path, details }`.

## Run locally

Prerequisite: Docker with Compose.

```bash
git clone https://github.com/Reinazs11/task-manager-api.git
cd task-manager-api
docker compose up --build
```

Swagger: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

Run the complete build directly:

```bash
./mvnw -B -ntp clean verify       # Linux/macOS
.\mvnw.cmd -B -ntp clean verify  # Windows
```

Published image:

```bash
docker pull ghcr.io/reinazs11/task-manager-api:latest
```

The `latest` image is published from `main`; the semantic `1.0.0` image tag is
intentionally reserved for the release after the demo is externally verified.

Production requires `JWT_SECRET`, database credentials, issuer, audience and
restricted CORS. `prod` disables Swagger; `prod,demo` enables it explicitly.
Both `JDBC_DATABASE_URL` / `PORT` and the local `DB_*` / `SERVER_PORT`
fallbacks are supported.

## Tests and evidence

Current `clean verify`: **417 tests** (297 unit + 120 integration), **96.78%**
line coverage and **86.70%** branch coverage. The authentication context is
**94.16% lines / 88.89% branches**. The scoped PIT gate scores **87% mutation /
93% test strength**. Thresholds fail the build below 80%/70% globally,
90%/80% for authentication, or 80% mutation score.

```bash
./mvnw -B -ntp clean verify
./mvnw -B -ntp -Ppit test-compile org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses=com.renan.taskmanager.*.domain.*,com.renan.taskmanager.users.application.LoginUseCase,com.renan.taskmanager.users.application.RegisterUserUseCase,com.renan.taskmanager.users.application.RefreshTokenUseCase,com.renan.taskmanager.users.application.LogoutUseCase \
  -DtargetTests=com.renan.taskmanager.*.domain.*,com.renan.taskmanager.users.* \
  -DmutationThreshold=80
```

## Decisions and limitations

[DECISIONS.md](DECISIONS.md) records rationale and accepted limits, including
single-node rate limiting, owner-only authorization, hard deletes, no password
reset/email flow, synchronous processing, and no retention scheduler. These are
scope choices, not hidden claims.

[AGENTS.md](AGENTS.md) documents the TDD, architecture, review and
AI-assisted-development guardrails used to finish the project.

## License

MIT — see [LICENSE](LICENSE).
