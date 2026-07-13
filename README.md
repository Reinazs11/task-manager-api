# Task Manager API

> Work in progress. REST API for task management with JWT authentication,
> built with Java 21 and Spring Boot 3.

## 🚀 Stack

- **Java 21** + **Spring Boot 3.3**
- **Spring Security** with **JWT** (access token 15min + refresh token 7 days)
- **PostgreSQL 16** + Spring Data JPA
- **Testcontainers** for integration tests against a real database
- **Docker** + **docker-compose** (one command brings everything up)
- **OpenAPI 3 / Swagger UI** for documentation
- **JaCoCo** with minimum coverage of 80%
- **Simplified DDD** + **Clean Code** + **TDD**

## 📋 How to run

### Prerequisites
- Docker and Docker Compose installed

### Bring everything up (Postgres + API)

```bash
cp .env.example .env
docker compose up --build
```

The API starts at `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

### Database only (to run the app from your IDE)

```bash
docker compose up postgres
```

Then run the application from your IDE (IntelliJ IDEA) with the `dev` profile.

## 🧪 Tests

```bash
./mvnw test      # runs unit + integration tests
./mvnw verify    # runs tests + checks JaCoCo coverage (min 80%)
```

Coverage report at `target/site/jacoco/index.html`.

## 🏗️ Architecture

Simplified DDD, organized by **bounded context**:

```
com.renan.taskmanager.<context>/
  domain/         # entities, value objects, business rules (no Spring)
  application/    # application services, use cases
  infrastructure/ # JPA repositories, security configs
  api/            # controllers, advice, DTOs
```

Contexts: `users` (authentication) and `tasks` (projects and tasks).

## 📄 License

Portfolio project — educational use.
