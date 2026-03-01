# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ongard Gamemaster — an AI-driven Game Master backend using a microservices architecture. The system uses dual-agent LLM architecture (GPU for creative narrative, CPU for deterministic validation) with a Spring Boot backend, PostgreSQL persistence, and Redis caching.

## Build & Run Commands

```bash
# Build ongard-core first (shared library dependency for all services)
mvn -f backend/ongard-core/pom.xml clean install

# Build a single service (e.g., authentication)
mvn -f backend/authentication/pom.xml clean package

# Run tests for a single service (requires Docker for TestContainers)
mvn -f backend/authentication/pom.xml test

# Run a specific test class
mvn -f backend/authentication/pom.xml test -Dtest=AuthControllerTest

# Start full backend stack (databases + migrations + services)
docker compose --profile backend up --build

# Start only databases + migrations (for local service development)
docker compose --profile db up

# Rebuild and restart a single container
docker compose --profile backend up --build authentication
```

There is no parent POM — each service has an independent `pom.xml` under `backend/`. Always build `ongard-core` first when working with a fresh checkout.

## Architecture

```
Client → Gateway (:8080) → Authentication (:8089)
                          → Ongard Chat (:8081)
```

**Gateway** (`backend/gateway/`) — Spring Cloud Gateway (WebFlux). Validates JWT tokens via OAuth2 resource server, then propagates user identity to downstream services as a JSON `x-ongard-user` header (via `JwtToUserHeaderFilter`). Routes `/api/auth/**` publicly; all other routes require authentication.

**Authentication** (`backend/authentication/`) — Handles registration, login, JWT issuance (access + refresh tokens), username availability checks. Uses Argon2 password hashing with pepper+salt. On registration, calls the chat service REST API to create a corresponding game user.

**Ongard Chat** (`backend/ongard-chat/`) — Game/chat service. Currently manages game user creation; intended to grow into the AI chat and narrative engine.

**Ongard Core** (`backend/ongard-core/`) — Shared JAR library. Provides the exception hierarchy (`AppException`, `BadRequestException`, `UnauthorizedException`, etc.), `GameUserHeader` model for user context propagation, `ApiError` response format, and utilities (`HashGenerator`, `GameGsonFactory`).

## Key Technical Details

- **Java 25**, Spring Boot 4.0.2, Lombok for boilerplate reduction
- **No parent POM** — each service builds independently; `ongard-core` must be `mvn install`ed to local repo first
- **Jetty** (not Tomcat) as embedded server for authentication and chat services
- **JPA with `ddl-auto: validate`** — schema is managed exclusively by Liquibase; Hibernate only validates
- **Liquibase 4.27** changelogs live in `database/db-auth/liquibase/changelog/` and `database/db-game/liquibase/changelog/`
- **Two separate PostgreSQL instances**: `db-auth` (port 5433) for auth schema, `db-game` (port 5432) for game schema
- **JWT**: HS256, secret via `JWT_SECRET` env var (Base64-encoded), access tokens 30min, refresh tokens 10 days
- **Tests** use TestContainers (PostgreSQL) + Liquibase for schema setup — Docker must be running
- **Docker builds** are multi-stage: Maven build → JRE Alpine runtime
- **Docker Compose profiles**: `db` (databases+redis+liquibase), `spring` (services only), `backend` (everything)

## Code Conventions

- Standard Spring layering: `controller` → `service` → `repository`
- Entities use Lombok `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`
- Services use `@RequiredArgsConstructor` for constructor injection
- Error responses use `ApiError` with `Message` objects containing level, code (e.g., `"GC_500_00"`), and message
- Group ID: `com.ongard.game`, packages follow `com.ongard.game.<service-name>`

## API Endpoints

- `POST /api/auth/register` — User registration
- `POST /api/auth/login` — Login (returns access + refresh tokens)
- `POST /api/auth/login/refresh` — Refresh access token
- `GET /api/auth/check-username?username=` — Check username availability
- `POST /api/user` — Create game user (internal, called by auth service)

## Frontend (`frontend/`)

React 19 + TypeScript 5.9 + Vite 7. Dettagli nel `frontend/README.md`.

### Build & Run

```bash
cd frontend
npm run dev          # Dev server con HMR (proxy /api → localhost:8080)
npm run build        # tsc -b + vite build
npm run lint         # ESLint
npm run test         # Vitest
npm run format       # Prettier
```

### Regole per generare codice frontend

- **TypeScript strict** — `noEmit: true`, `target: ES2024`, `moduleResolution: bundler`. Mai usare `any`; mai usare
  `composite: true` nei tsconfig (genera .d.ts nelle cartelle sorgente)
- **Path alias** — usare sempre `@/` per gli import da `src/`. Import relativi ascendenti (`../`) sono vietati da ESLint
- **Ordine import** (enforced da `simple-import-sort`): (1) React e librerie esterne → (2) import `@/` di progetto → (3)
  file `.scss` per ultimi. Ogni gruppo separato da riga vuota
- **Stili** — SCSS con CSS Modules: ogni componente ha il suo `NomeComponente.module.scss`. I mixin globali (
  `_mixins.scss`) sono iniettati automaticamente da Vite, non serve `@use`. Design tokens come CSS custom properties in
  `index.scss`
- **Naming convention** (enforced da `check-file`):
    - File `.tsx` → `PascalCase` (componenti React)
    - File `.ts` → `camelCase` (utility, hook, service)
    - Cartelle → `kebab-case`
- **Test** — Vitest + React Testing Library in `tests/` (esterna a `src/`). Globals abilitati (`describe`/`it`/`expect`
  senza import). Environment `jsdom`
- **Prettier** — single quote, trailing comma, printWidth 100, JSX single quote. Integrato in ESLint come regola
