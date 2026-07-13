# Task Manager API

> Portfólio em construção. API REST para gestão de tarefas com autenticação
> JWT, construída com Java 21 e Spring Boot 3.

## 🚀 Stack

- **Java 21** + **Spring Boot 3.3**
- **Spring Security** com **JWT** (access token 15min + refresh token 7 dias)
- **PostgreSQL 16** + Spring Data JPA
- **Testcontainers** para testes de integração com banco real
- **Docker** + **docker-compose** (um comando sobe tudo)
- **OpenAPI 3 / Swagger UI** para documentação
- **JaCoCo** com cobertura mínima de 80%
- **DDD simplificado** + **Clean Code** + **TDD**

## 📋 Como rodar

### Pré-requisitos
- Docker e Docker Compose instalados

### Subir tudo (Postgres + API)

```bash
cp .env.example .env
docker compose up --build
```

A API sobe em `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

### Apenas o banco (para rodar a app via IDE)

```bash
docker compose up postgres
```

Depois rode a aplicação pela sua IDE (IntelliJ IDEA) com profile `dev`.

## 🧪 Testes

```bash
./mvnw test         # roda testes unitários + integração
./mvnw verify       # roda testes + verifica cobertura JaCoCo (mínimo 80%)
```

Relatório de cobertura em `target/site/jacoco/index.html`.

## 🏗️ Arquitetura

DDD simplificado, organizado por **bounded context**:

```
com.renan.taskmanager.<context>/
  domain/         # entidades, value objects, regras (sem Spring)
  application/    # serviços de aplicação, use cases
  infrastructure/ # JPA repositories, configs de segurança
  api/            # controllers, advice, DTOs
```

Contextos: `users` (autenticação) e `tasks` (projetos e tarefas).

## 📌 Status

- [x] Etapa 1: Bootstrap do projeto
- [ ] Etapa 2: Domínio Users
- [ ] Etapa 3: Infra Users + Auth
- [ ] Etapa 4: Domínio Tasks
- [ ] Etapa 5: Infra Tasks + API CRUD
- [ ] Etapa 6: Cross-cutting (erros, docs, logs)
- [ ] Etapa 7: Polish final

## 📄 Licença

Projeto de portfólio — uso educacional.
