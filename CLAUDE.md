# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ondgard Gamemaster — an AI-driven Game Master backend using a microservices architecture. Hybrid Gemini + Ollama
multi-agent system: Gemini for creative narration, deterministic agents, and embedding; Ollama GPU for router and lore
reviewers. Spring Boot backend, PostgreSQL persistence, Redis caching, SSE for real-time updates.

## Build & Run Commands

```bash
# Build ondgard-core first (shared library dependency for all services)
mvn -f backend/ondgard-core/pom.xml clean install

# Build ondgard-postprocessing (annotation processor, only needed once or when it changes)
mvn -f backend/ondgard-postprocessing/pom.xml clean install

# Build a single service (e.g., ondgard-auth)
mvn -f backend/ondgard-auth/pom.xml clean package

# Run tests for a single service (requires Docker for TestContainers)
mvn -f backend/ondgard-auth/pom.xml test

# Run a specific test class
mvn -f backend/ondgard-auth/pom.xml test -Dtest=AuthControllerTest

# Start full backend stack (databases + migrations + services)
docker compose --profile backend up --build

# Start only databases + migrations (for local service development)
docker compose --profile db up

# Rebuild and restart a single container
docker compose --profile backend up --build ondgard-auth
```

There is no parent POM — each service has an independent `pom.xml` under `backend/`. Always build `ondgard-core` and
`ondgard-postprocessing` first when working with a fresh checkout.

## Architecture

```
Client → Gateway (:8080) → Ondgard Auth (:8089)
                          → Ondgard Chat (:8081) --RestClient--> Ondgard Account (:8082)
                          → Ondgard Account (:8082)
                          Ondgard Mail (:8083) — internal only, no gateway routes
```

**Gateway** (`backend/gateway/`) — Spring Cloud Gateway (WebFlux). Validates JWT via OAuth2, propagates user identity as
JSON `x-ondgard-user` header (`JwtToUserHeaderFilter`). Routes `/api/auth/**` publicly; all other routes require auth.
Rewrites `/api/account/**` → `/api/**` for ondgard-account, `/api/chat/**` → `/api/**` for ondgard-chat. Blocks
`/api/**/internal/**` with 403 (service-to-service only).

**Ondgard Auth** (`backend/ondgard-auth/`) — Registration, login, JWT issuance (access + refresh), username checks.
Argon2 hashing. On registration calls chat service to create game user. Google OAuth: single endpoint handles both login
(existing user by email) and registration (new user, two-step: first call returns 404 with suggested username, second
call with chosen username completes registration). Google users get a random password — no schema changes, no
`auth_provider` column. Account linking is automatic by email.

**Ondgard Chat** (`backend/ondgard-chat/`) — Main game service. Multi-agent AI pipeline: GM narration, lore validation,
inventory/quest/scene management. SSE for real-time turn progress. Calls ondgard-account via `AccountClient` for token
tracking and limit checks.

**Ondgard Account** (`backend/ondgard-account/`) — Token usage tracking and limits. Tracks per-campaign token
consumption,
enforces monthly/total limits, lazy monthly reset (no scheduler). Internal API (`/api/internal/**`) for
service-to-service
calls from ondgard-chat; external API (`/api/token-usage`) for frontend via gateway.

**Ondgard Mail** (`backend/ondgard-mail/`) — Transactional email service using **Resend** SDK (`resend-java` 4.12).
Stateless, no database. Single endpoint `POST /api/mail/send` accepts `SendMailRequest(to, subject, htmlBody)` (record
in ondgard-core), launches email on virtual thread, returns immediately (fire-and-forget). Not exposed via gateway —
internal service-to-service only. Configured via `resend.api.key` env var (`RESEND_API_KEY`) and `resend.from`. Jetty
server on port 8083.

**Ondgard Core** (`backend/ondgard-core/`) — Shared JAR: exception hierarchy (`AppException`, `BadRequestException`,
etc.), `GameUserHeader`, `ApiError`, mail contracts (`SendMailRequest`, `SendMailResponse`), utilities (`HashGenerator`,
`GameGsonFactory`).

**Ondgard Postprocessing** (`backend/ondgard-postprocessing/`) — Annotation processor (compile-time only, zero
dependencies). `@OndgardLlmSchema("NAME")` on a class/record generates a `NAME_SCHEMA` constant with the Gemini JSON
schema. `@LlmRequired` marks fields as required in the schema (no Jackson runtime side effects). The processor generates
`LlmResponseSchema.java` into `ondgard-chat` (target class configurable via `-AondgardLlmSchema.targetClass`).

## Ondgard Chat — Key Structure

### AI Models (`config/ai/LlmModelConfig.java`)

6 beans — all injected as `ChatModel`/`EmbeddingModel` interface only:

- `gmModel` (Gemini, temp 0.7) — GM narration
- `deterministicGmModel` (Gemini, temp 0) — inventory/quest agents
- `sceneModel` (Gemini, temp 0.15) — scene agents
- `lightModel` (Ollama GPU, temp 0) — Router, lore reviewers
- `embeddingModel` (Gemini, primary) — RAG via `GeminiDualTaskEmbeddingModel`
- `localEmbeddingModel` (Ollama CPU) — backup embedding

### Agent Pipeline (`service/OrchestratorService.java`)

Turn pipeline: RAG → GM generate → Router classify themes → parallel validation (lore reviewers + inventory reviewer) →
retry loop with multi-turn regeneration → 3 parallel updaters (inventory + questlog + scene) → selective persistence →
SSE completed event.

### Agents — Factory Pattern (not Spring beans)

- `LoreReviewerFactory.build(category)` → `BaseLoreReviewer` (Ollama, constrained decoding)
- `TurnAgentsFactory` → builds: InventoryReviewer, InventoryUpdater, QuestlogUpdater, SceneUpdater, + initializers
- Agents are plain Java objects, stateless, created per-request. State passed via constructor/method params.

### Key Files

- **Prompts:** `src/main/resources/prompts/*.md` (11 files). Loaded at `@PostConstruct`, placeholders via
  `String.replace()`. Language placeholders: `{{language}}` (output language display name, e.g. "Italian") and
  `{{loreLang}}` (lore language for RAG queries). Replaced at runtime via `LanguageUtils.toDisplayName()`
- **Lore:** `src/main/resources/game_data/ondgard/{lang}/{category}/*.md` — loaded by `LoreProvider` (multilingual).
  `LoreProvider.get(lang)` returns a `LoreRegistry` per language. `resolveLanguage(requested)` fallback chain:
  requested → "en" → first available. Categories = subfolder names uppercased. Currently `it` + `en`
- **Config properties:** `OndgardAiProperties` (ondgard.ai.*), `RagProperties` (ondgard.rag.*), `PipelineProperties` (
  ondgard.game.pipeline.*)
- **Campaign init:** `CampaignInitService` — 3 parallel init agents (inventory, quest, scene) via SSE
- **Token tracking:** `TokenAwareChatModelDecorator` wraps all `ChatModel` beans, tracks token usage via
  `AccountClient.addTokensAsync()` (fire-and-forget). Uses `ScopedValue<TokenTrackingInfo>` (`TokenTrackingHolder`)
  for request context (userHash, characterHash). `OrchestratorService` checks limits before pipeline start via
  `AccountClient.checkLimit()` — throws `TokenLimitExceededException` (HTTP 429)

### Models

- `CampaignContext` — in-memory state: inventory, questLog, scene, recentHistory (`Collection<ChatEntry>`),
  narrativeSummary, currentTurn
- `Inventory` — rich object: `Valuta` + `TrasportoPersonale[]` + `Contenitore[] { InventoryItem[] { ExtraProperty[] } }`
- `CampaignQuestLog(turnNumber, questActive, questCompleted)` — replaces old worldFlags
- `GameScene(currentLocation, gameDate, gameTime, meteo, temperature)`

## Key Technical Details

- **Java 25**, Spring Boot 4.0.3, Spring AI, Lombok
- **No parent POM** — each service builds independently; `ondgard-core` and `ondgard-postprocessing` must be `mvn install`ed first
- **Jetty** (not Tomcat) as embedded server for ondgard-auth and chat services
- **JPA with `ddl-auto: validate`** — schema managed exclusively by Liquibase; Hibernate only validates
- **Liquibase 4.27** changelogs in `database/db-auth/liquibase/changelog/`, `database/db-game/liquibase/changelog/`, and
  `database/db-account/liquibase/changelog/`
- **Three PostgreSQL instances**: `db-auth` (port 5433), `db-game` (port 5432), `db-account` (port 5434)
- **JWT**: HS256, `JWT_SECRET` env var (Base64), access 30min, refresh 10 days
- **Tests** use TestContainers (PostgreSQL) + Liquibase — Docker must be running
- **Docker builds** are multi-stage: Maven build → JRE Alpine runtime
- **Docker Compose profiles**: `db` (databases+redis+liquibase), `spring` (services only), `backend` (everything)
- **SSE** (not WebSocket) for turn progress and campaign init. Two event families: `SseEventType` (turn) and
  `SseEventInitCmpType` (init). Backend sends **codes** (`GM_WRITING`, `VALIDATORS_RUNNING`, etc. via `SseProgressCode`
  enum), frontend translates them to localized strings with random variant selection
- **RAG** uses **RedisVectorStore** (Redis Stack with RediSearch). Metadata fields: `scenario`, `language`, `category`.
  `RagInitializationService` validates a CRC32 content hash at boot — if lore unchanged, RAG ready in ~1s (no Gemini
  calls). Re-indexing triggered by sidecar container publishing on `ondgard:rag:init` Redis topic
- **Multilingual pipeline**: Frontend sets `Accept-Language` header (axios interceptor + SSE fetch). Gateway
  `JwtToUserHeaderFilter` extracts it into `GameUserHeader.language` (2-letter code, default "en"). Services derive
  `outputLang` (for AI output) and `loreLang` (for lore access + RAG filters) via `LoreProvider.resolveLanguage()`

## Code Conventions

- Standard Spring layering: `controller` → `service` → `repository`
- Entities use Lombok `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`
- Services use `@RequiredArgsConstructor` for constructor injection
- Error responses use `ApiError` with `Message` objects containing level, code (e.g., `"GC_500_00"`), and message
- Group ID: `com.ondgard.game`, packages follow `com.ondgard.game.<service-name>`

## API Endpoints

### Ondgard Auth
- `POST /api/auth/register` — User registration
- `POST /api/auth/login` — Login (returns access + refresh tokens)
- `POST /api/auth/login/refresh` — Refresh access token
- `GET /api/auth/check-username?username=` — Check username availability
- `POST /api/auth/google` — Google auth (login or register). Without `username`: returns 200 (existing user) or 404
  `{email, suggestedUsername}` (new user). With `username`: completes registration, returns 200 with tokens

### Ondgard Chat
- `POST /api/user` — Create game user (internal, called by auth service)
- `GET /api/character/all` — List player's characters
- `GET /api/character/{characterHash}` — Get character details
- `POST /api/character` — Create character
- `GET /api/config/races` — List playable races
- `GET /api/campaign/turn?characterHash=` — Current turn state (inventory, quest, scene)
- `POST /api/campaign/session/end?characterHash=` — Flush session (logout)
- `POST /api/sse/interaction` — Game turn (SSE stream)
- `POST /api/sse/campaign/init` — Campaign initialization (SSE stream)
- `GET /api/health` — Health check

### Ondgard Account

- `GET /api/account/token-usage` — Token usage overview (limits, usage totals, per-character breakdown)
- `PUT /api/account/token-usage/limits` — Update token limits (validates: > 0, limitMonth <= limitTotal)
- `POST /api/account/internal/user/init` — Init user limits (service-to-service, from ondgard-chat)
- `POST /api/account/internal/campaign/init` — Init campaign token usage row (service-to-service)
- `POST /api/account/internal/tokens` — Add tokens (service-to-service, async fire-and-forget)
- `GET /api/account/internal/check-limit` — Check token limits (service-to-service, sync)

### Ondgard Mail
- `POST /api/mail/send` — Send email (service-to-service, fire-and-forget)

## Frontend (`frontend/`)

React 19 + TypeScript 5.9 + Vite 7. **i18n**: `react-i18next` with `i18next-http-backend` +
`i18next-browser-languagedetector`. English bundled in JS (`src/locales/en.json`, fallback), Italian loaded on-demand
(`public/locales/it/translation.json`). Supported languages auto-discovered from `public/locales/` dirs at build time.
Language stored in localStorage. Dettagli nel `frontend/README.md`. **Google OAuth**: `@react-oauth/google` library,
`GoogleOAuthProvider` wraps `PublicLayout`, Client ID injected via `__GOOGLE_CLIENT_ID__` in `vite.config.ts`.
**Auth store** (`useAuthStore`): `loginWithTokens(response)` is the single entry point for all login flows (email and
Google) — username is always extracted from the JWT (`parseJwtUsername`), never from user input.

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
- **Tipizzazione forte sempre** — preferire tipi dedicati, `as const` objects, union types e interfacce specifiche
  rispetto a `string`, `Record<string, unknown>`, `object` o mappe generiche. Parametri di funzione e return types
  devono usare i tipi più specifici possibili (es. `SseProgressCodeValue` invece di `string`). Quando un tipo deve
  accettare valori da più domini, usare union di tipi nominali (es. `CodeA | CodeB`), mai allargare a `string`
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
