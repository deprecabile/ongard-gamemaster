# Analisi del Progetto Ondgard Gamemaster

Un motore narrativo AI-driven per avventure di ruolo testuali nel mondo di Ondgard. Il sistema orchestra agenti AI
specializzati — un Game Master creativo, revisori di lore, gestori di inventario/quest/scena — attraverso una pipeline
di validazione che garantisce coerenza narrativa prima di consegnare ogni scena al giocatore.

---

## 1. Stack Tecnologico

| Layer              | Tecnologia                                                                                     |
|--------------------|------------------------------------------------------------------------------------------------|
| **Backend**        | Java 25, Spring Boot 4.0.3, Spring AI 2.0.0-M2, Lombok, Jetty                                 |
| **Frontend**       | React 19, TypeScript 5.9, Vite 7, SCSS Modules, Zustand, react-i18next                        |
| **AI Cloud**       | Google GenAI (Gemini) — narrazione, agenti deterministici, embedding, validazione              |
| **AI Locale**      | Ollama GPU (`llama-3.1-8b-italian`) — backup locale                                           |
| **Embedding**      | Gemini (`gemini-embedding-001`, dual-task) — primario. Ollama CPU (`qwen3-embedding`) — backup |
| **Database**       | PostgreSQL (tre istanze: `db-game` :5432, `db-auth` :5433, `db-account` :5434), Liquibase      |
| **Cache/Sessione** | Redis Stack (RediSearch + RedisJSON per RAG, Pub/Sub per eventi, sessioni in-memory)           |
| **Gateway**        | Spring Cloud Gateway 2025.1.0 (WebFlux), JWT HS256, OAuth2, rate limiting Redis                |
| **Infrastruttura** | Docker Compose con profili (`db`, `spring`, `backend`, `fullstack`, `llm`, `frontend`)         |

### 1.1 Modelli AI

Il sistema usa **16 bean** definiti in un'unica classe di configurazione (`LlmModelConfig`). Tutto il resto del codice
inietta esclusivamente l'interfaccia `ChatModel` o `EmbeddingModel` di Spring AI — i provider concreti non appaiono mai
al di fuori della configurazione. Ogni bean `ChatModel` e' decorato con `TokenAwareChatModelDecorator` per il tracking
automatico dei token consumati.

| Bean                     | Provider | Modello config key               | Ruolo                                                  | Temp. |
|--------------------------|----------|----------------------------------|--------------------------------------------------------|-------|
| `gmModel`                | Gemini   | `gemini.gmModel`                 | Game Master — narrazione creativa                      | 0.7   |
| `summaryModel`           | Gemini   | `gemini.summaryModel`            | SummaryAgent — riassunti narrativi deterministici      | 0.0   |
| `inventoryModel`         | Gemini   | `gemini.inventoryModel`          | InventoryUpdater/Initializer (JSON schema)             | 0.0   |
| `inventoryReviewerModel` | Gemini   | `gemini.inventoryReviewerModel`  | InventoryReviewer (JSON schema)                        | 0.0   |
| `questModel`             | Gemini   | `gemini.questModel`              | QuestlogUpdater/Initializer (JSON schema)              | 0.0   |
| `sceneModel`             | Gemini   | `gemini.sceneModel`              | SceneInitializer, SceneUpdater (JSON schema)           | 0.15  |
| `validatorModel`         | Gemini   | `gemini.validatorModel`          | BaseLoreReviewer (JSON schema)                         | 0.0   |
| `ragQueryModel`          | Gemini   | `gemini.ragQueryModel`           | RagQueryAgent — decomposizione query RAG (JSON schema) | 0.0   |
| `advisorModel`           | Gemini   | `gemini.advisorModel`            | Advisor in-game — consigliere per il giocatore         | 0.2   |
| `setupAdvisorModel`      | Gemini   | `gemini.setupAdvisorModel`       | Advisor di campaign setup                              | 0.35  |
| `setupSummaryModel`      | Gemini   | `gemini.setupSummaryModel`       | Estrazione preferenze dalla chat setup                 | 0.0   |
| `nameRaceSetupModel`     | Gemini   | `gemini.nameRaceSetupModel`      | Scelta nome/razza del personaggio (JSON + tools)       | 0.7   |
| `setupGeneratorModel`    | Gemini   | `gemini.setupGeneratorModel`     | Generazione character prompt e scena iniziale          | 0.7   |
| `localGpuModel`          | Ollama   | `ollama.gpu.model`               | Modello locale GPU — backup/sperimentazione            | 0.0   |
| `embeddingModel`         | Gemini   | `gemini.embeddingModel`          | Embedding primario (dual-task)                         | —     |
| `localEmbeddingModel`    | Ollama   | `ollama.cpu.embeddingModel`      | Embedding locale di backup                             | —     |

L'embedding Gemini usa un wrapper dual-task (`GeminiDualTaskEmbeddingModel`) che applica automaticamente
`RETRIEVAL_QUERY` durante la ricerca e `RETRIEVAL_DOCUMENT` durante l'indicizzazione ETL, con enforcement del limite
TPM.

---

## 2. Architettura dei Microservizi

```
                              ┌─────────────┐
                              │   Frontend   │
                              │  React SPA   │
                              └──────┬───────┘
                                     │
                              ┌──────▼───────┐
                              │   Gateway    │
                              │    :8080     │
                              │ JWT + Route  │
                              │ Rate Limit   │
                              └──┬──┬──┬──┬──┘
                                 │  │  │  │
            ┌────────────────────┘  │  │  └──────────────────────┐
            ▼                      ▼  ▼                          ▼
     ┌────────────┐    ┌──────────────────┐    ┌──────────────────┐
     │    Auth    │    │   Ondgard Chat   │    │  Ondgard Account │
     │   :8089   │    │      :8081       │───►│      :8082       │
     │ Login/Reg │    │ Motore di gioco  │    │  Token tracking  │
     └────────────┘    └──────────────────┘    └──────────────────┘
                              │
                              ▼
                       ┌──────────────┐
                       │ Ondgard Mail │
                       │    :8083     │  (interno, no gateway)
                       └──────────────┘
```

### Gateway

Valida il JWT ad ogni richiesta e propaga l'identita' dell'utente come header JSON (`x-ondgard-user`) contenente
`userId` (UUID dal subject del token), `username` e `language` (dal header `Accept-Language`, normalizzato a 2 lettere).
Le rotte `/api/auth/**` e `/api/chat/health` sono pubbliche, tutte le altre richiedono autenticazione. Il backend di
gioco non gestisce mai JWT direttamente — riceve l'identita' gia' validata dal Gateway.

Il Gateway blocca le rotte `/api/*/internal/**` con 403 — queste sono riservate alla comunicazione service-to-service.

**Rate limiting** via `RedisRateLimiter` (chiave: IP remoto) sugli endpoint sensibili: registrazione, conferma email,
password reset, Google OAuth.

**Rotte principali:**

| Rotta                  | Target            | Note                                        |
|------------------------|-------------------|---------------------------------------------|
| `/api/auth/**`         | ondgard-auth:8089 | Alcune con rate limit                       |
| `/api/account/**`      | ondgard-account   | Rewrite a `/api/**`                         |
| `/api/chat/sse/**`     | ondgard-chat      | Timeout 30 min, rewrite a `/api/**`         |
| `/api/chat/**`         | ondgard-chat      | Rewrite a `/api/**`                         |
| `/api/*/internal/**`   | 403               | Bloccato — solo service-to-service diretto  |

### Authentication

Gestisce registrazione, login, emissione JWT (access + refresh token), verifica username, conferma email, reset
password. Alla registrazione chiama il servizio di gioco per creare il `game_user` associato e il servizio account per
inizializzare i limiti token. Argon2 per l'hashing delle password.

**Google OAuth**: un singolo endpoint gestisce sia login (utente esistente, match per email) sia registrazione (utente
nuovo: prima chiamata restituisce 404 con username suggerito, seconda chiamata con username scelto completa la
registrazione). Gli utenti Google ricevono una password random — nessuna colonna `auth_provider`, il linking e'
automatico per email.

**Email**: conferma email alla registrazione e reset password tramite token SHA-256 con scadenza. Le email vengono
inviate tramite il servizio ondgard-mail.

### Ondgard Chat

Il cuore del sistema. Contiene la pipeline multi-agente, il RAG, la gestione sessione, la persistenza di campagna,
l'advisor in-game e il flusso di campaign setup. Analizzato in dettaglio nei paragrafi seguenti.

### Ondgard Account

Servizio dedicato al tracking e ai limiti di consumo token AI. Traccia l'uso per campagna e per utente con limiti
mensili e totali. Reset mensile lazy (nessuno scheduler — confronta `lastResetMonth` con il mese corrente).

**API interne** (service-to-service):
- Inizializzazione limiti utente (alla registrazione)
- Inizializzazione usage per campagna (alla creazione campagna)
- Aggiunta token (fire-and-forget asincrono, chiamato dal decorator ad ogni inferenza)
- Verifica limiti (sincrono, chiamato prima di ogni pipeline)

**API esterne** (via gateway):
- Overview consumo token (limiti, totali, dettaglio per personaggio)
- Aggiornamento limiti utente

### Ondgard Mail

Servizio email stateless tramite **Resend SDK**. Unico endpoint `POST /api/mail/send` — lancia l'invio su virtual
thread e ritorna immediatamente (fire-and-forget). Non esposto via gateway, solo comunicazione interna
service-to-service.

### Ondgard Core

Libreria condivisa (JAR non eseguibile) usata da tutti i servizi. Contiene la gerarchia di eccezioni (`AppException`,
`BadRequestException`, `ConflictException`, `ForbiddenException`, `NoResultException`, `ServiceUnavailableException`,
`TokenLimitExceededException`, `UnauthorizedException`), il modello `ApiError`, il `GameUserHeader` per la propagazione
dell'identita', i contract condivisi per mail e account, e utility generiche (`HashGenerator`, `GameGsonFactory`).

### Ondgard Postprocessing

Annotation processor compile-time (zero dipendenze runtime). L'annotazione `@OndgardLlmSchema("NAME")` su una
classe/record genera una costante `NAME_SCHEMA` contenente lo schema JSON per Gemini. `@LlmRequired` marca i campi come
obbligatori nello schema. Il processore genera `LlmResponseSchema.java` in `ondgard-chat` (target configurabile via
`-AondgardLlmSchema.targetClass`).

---

## 3. Pipeline Multilingua

Il sistema e' completamente multilingua. Il frontend imposta l'header `Accept-Language`, il Gateway lo normalizza e lo
propaga nel `GameUserHeader.language` (codice a 2 lettere, default `"en"`). I servizi derivano:

- `outputLang` — lingua di output dell'AI (nome display, es. "italiano")
- `loreLangCode` — codice lingua per accesso alla lore e filtri RAG (es. "it")

La risoluzione avviene tramite `LoreProvider.resolveLanguage()` con catena di fallback: lingua richiesta → `"en"` →
prima lingua disponibile.

---

## 4. Separazione Lore-Codice

Il codice sorgente e' completamente agnostico rispetto all'ambientazione. Le regole del mondo di Ondgard non sono mai
cablate nella logica di business — risiedono esclusivamente in file Markdown sotto
`resources/game_data/ondgard/{lang}/{categoria}/`, organizzati per lingua e categoria:

```
game_data/ondgard/
├── en/                       → English
│   ├── characters/           → caius_adriano.md, cassian_dograven.md, ...
│   ├── context/              → fantasy_world_rules.md
│   ├── economy/              → economy.md
│   ├── fauna/                → common_animals.md, bestiary.md
│   ├── geography/            → planet_description.md, ecosphere.md, biosphere.md, ...
│   ├── politics/             → geopolitics.md, kingdoms_empires.md, kingdom_naming_rules.md, ...
│   ├── races/                → races.md
│   ├── religion/             → religion.md
│   └── timekeeping/          → day_night_cycle.md
├── it/                       → Italiano (stessa struttura, nomi localizzati)
└── es/                       → Espanol (stessa struttura, nomi localizzati)
```

Il `LoreProvider` carica tutti i file al boot, organizzandoli per lingua in un `LoreRegistry` per ciascuna.
`LoreProvider.get(lang)` restituisce il `LoreRegistry` della lingua richiesta. Ogni `LoreRegistry` espone
`getLore(category)` per i revisori, `getAllLore()` per il GM nella fase full-lore, e `getCategoryKeys()` per le
categorie valide del Router. Le categorie sono derivate dal nome della sottocartella (uppercased). Aggiungere una nuova
cartella di lore crea automaticamente una nuova categoria validabile senza toccare il codice.

Un hash CRC32 del contenuto lore concatenato (tutte le lingue) viene calcolato al boot e confrontato con il valore
salvato in Redis — se il contenuto non e' cambiato, il RAG e' pronto in ~1 secondo senza chiamate a Gemini.

I 18 prompt di sistema sono file `.md` separati sotto `resources/prompts/`, caricati a `@PostConstruct`. I placeholder
(`{{variabile}}`) vengono sostituiti a runtime con `String.replace()`. Placeholder di lingua: `{{language}}` (nome
display, es. "italiano") e `{{loreLang}}` (codice lingua per query RAG).

---

## 5. Il Motore RAG

### 5.1 ETL e Vector Store

All'avvio del sistema, il `RagInitializationService` ascolta l'evento `ApplicationReadyEvent` e verifica l'hash CRC32
del contenuto lore:

1. **Hash invariato** → il RAG e' gia' indicizzato correttamente, pronto in ~1 secondo
2. **Hash diverso** → attende il segnale Redis Pub/Sub (`ondgard:rag:init`) dal sidecar container, poi esegue l'ETL

L'ETL:
1. Pulisce i documenti esistenti nel vector store
2. Carica tutti i file lore dal `LoreProvider` (tutte le lingue)
3. Esegue il chunking con `TokenTextSplitter` (1000 token per chunk, minimo 350 caratteri)
4. Ogni chunk conserva metadati: `category`, `language`, `scenario`
5. I chunk vengono embeddati via Gemini e indicizzati su **RedisVectorStore** (Redis Stack con RediSearch)
6. Salva il nuovo hash CRC32 in Redis

Un `RagReadinessGate` blocca qualsiasi interazione di gioco fino al completamento dell'ETL. Il frontend riceve un
overlay di caricamento tematico e fa polling su `GET /api/health` fino a che il gate si apre.

### 5.2 Retrieval con Query Decomposition

Il retrieval non usa una query singola. Un agente dedicato (`RagQueryAgent`, Gemini, temp 0) riceve il contesto
completo della scena — azione del giocatore, luogo corrente, quest attive, ultimo turno di storia — e produce 2-4
query di ricerca mirate, ognuna focalizzata su un aspetto diverso.

```
Azione: "Pago il taverniere e chiedo informazioni sul ladro"
Scena: Citta' di Ondgard, notte, pioggia
Quest attiva: "Cercare il ladro nella zona portuale"
                  │
                  ▼
           ┌──────────────┐
           │ RagQueryAgent │  → 2-4 query focalizzate
           └──────┬───────┘
                  │
        ┌─────────┼──────────┐
        ▼         ▼          ▼
   "Taverne e    "Crimine   "Leggi e
    locande"      portuale"  coprifuoco"
        │         │          │
        ▼         ▼          ▼
    ┌────────────────────────────┐
    │    Vector Store Search     │  parallelo, topK=10 per query
    │    filter: language='{lang}'
    └────────────┬───────────────┘
                 │
                 ▼
         Deduplica per ID chunk
         (mantieni score piu' alto)
                 │
                 ▼
         Top 10 chunk ordinati
         per score decrescente
```

**Fallback**: se il `RagQueryAgent` fallisce (timeout, parsing error), il sistema ricade su una query singola composta
dall'azione utente + contesto scena.

### 5.3 Configurazione RAG per Contesto

I diversi consumatori del RAG hanno esigenze diverse, percio' i parametri di ricerca sono separati:

| Parametro             | GM Retrieval | Router Retrieval | Scene Retrieval |
|-----------------------|--------------|------------------|-----------------|
| `topK`                | 10           | 5                | 30              |
| `similarityThreshold` | 0.5          | 0.45             | 0.3             |
| `chunkSize`           | —            | 300              | —               |

Il Router usa soglia e topK per-chunk perche' opera per similarita' embedding, non per LLM. La scena usa un topK alto e
soglia bassa per catturare piu' contesto ambientale possibile.

---

## 6. La Pipeline Multi-Agente

Il cuore del sistema segue il pattern **Draft → Validate → Publish**. L'utente non vede mai una scena che non sia stata
validata internamente.

### 6.1 Mappa degli Agenti

Il sistema orchestra agenti specializzati, ognuno con una responsabilita' singola. Nessun agente conosce gli
altri — l'Orchestratore coordina tutto.

**Agenti del turno di gioco:**

| Agente                    | Quando gira                | Cosa fa                                                                                     |
|---------------------------|----------------------------|---------------------------------------------------------------------------------------------|
| **RagQueryAgent**         | Prima della generazione    | Decompone l'azione in 2-4 query RAG mirate                                                  |
| **GM Agent**              | Ogni turno                 | Scrive la scena narrativa. Al retry riceve la bozza precedente e gli errori da correggere   |
| **Router Agent**          | Dopo ogni bozza            | Classifica quali categorie di lore la bozza tocca via ricerca per similarita' embedding      |
| **Lore Reviewer(s)**      | Validazione (parallelo)    | N istanze create dinamicamente, una per categoria. Ogni revisore conosce solo la sua lore   |
| **Inventory Reviewer**    | Validazione (parallelo)    | Sempre attivo. Rileva transazioni impossibili (oggetti non posseduti, valuta insufficiente) |
| **Inventory Updater**     | Post-validazione (parall.) | Produce lo snapshot aggiornato dell'inventario dalla bozza approvata                        |
| **Quest Log Updater**     | Post-validazione (parall.) | Traccia nuove quest avviate e obiettivi completati                                          |
| **Scene Updater**         | Post-validazione (parall.) | Aggiorna luogo, data, ora, meteo, temperatura dalla narrazione                              |
| **Summary Agent**         | Background (ogni N turni)  | Comprime la storia accumulata in un riassunto narrativo compatto                            |

**Agenti di inizializzazione campagna:**

| Agente                    | Quando gira                | Cosa fa                                                                                     |
|---------------------------|----------------------------|---------------------------------------------------------------------------------------------|
| **Inventory Initializer** | Inizio campagna (parall.)  | Genera l'inventario iniziale dal background del personaggio                                 |
| **Quest Log Initializer** | Inizio campagna (parall.)  | Estrae gli obiettivi iniziali dal background                                                |
| **Scene Initializer**     | Inizio campagna (parall.)  | Determina luogo, data, ora e meteo di partenza                                              |

**Agenti di campaign setup:**

| Agente                          | Quando gira       | Cosa fa                                                     |
|---------------------------------|--------------------|-------------------------------------------------------------|
| **CampaignSetupAdvisorAgent**   | Chat di setup      | Guida il giocatore nella creazione del personaggio          |
| **CampaignSetupSummaryAgent**   | Fine chat setup    | Estrae le preferenze dalla conversazione di setup           |
| **NameRaceSetupAgent**          | Setup              | Sceglie nome e razza coerenti con la lore (JSON + tools)   |
| **CampaignSetupGeneratorAgent** | Setup              | Genera il character prompt e la scena iniziale              |

**Agente advisor:**

| Agente            | Quando gira       | Cosa fa                                                        |
|-------------------|--------------------|----------------------------------------------------------------|
| **AdvisorAgent**  | Ask mode in-game   | Risponde alle domande del giocatore durante la campagna        |

### 6.2 Factory Pattern — Agenti Non-Bean

Gli agenti non sono bean Spring con annotation. Vengono creati on-demand dalle factory:

- `LoreReviewerFactory.build(loreLangCode, category)` → crea un `BaseLoreReviewer` con la lore statica della categoria
- `MultilanguageTurnAgentsFactory` → `@Component` Spring che crea `LocalizedFactory` per ciascuna lingua. Ogni
  `LocalizedFactory` produce InventoryReviewer, InventoryUpdater, QuestlogUpdater, SceneUpdater e gli initializer
- `CampaignSetupAgentsFactory` → factory per gli agenti di setup

Le factory sono singleton `@Component`, ma gli agenti prodotti sono oggetti plain Java, stateless. Ricevono tutto lo
stato (bozza, inventario, lore) come parametro. Questo evita problemi di scope con i thread di `CompletableFuture`.

### 6.3 Flusso del Turno di Gioco

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          PIPELINE DI TURNO                                  │
│                                                                             │
│  1. Verifica limiti token (sincrona, via AccountClient)                    │
│        │                                                                    │
│        ▼                                                                    │
│  2. Il giocatore invia un'azione                                           │
│        │                                                                    │
│        ▼                                                                    │
│  3. ┌───────────────┐  Decompone l'azione in 2-4 query mirate             │
│     │ RagQueryAgent  │  considerando scena, quest attive, storia recente   │
│     └──────┬────────┘                                                      │
│            ▼                                                                │
│  4. ┌──────────────┐  Ricerca parallela su RedisVectorStore, deduplica,   │
│     │ RAG Retrieval │  filtro per lingua, top chunk per score              │
│     └──────┬───────┘                                                       │
│            ▼                                                                │
│  5. ┌────────────┐  Scrive la scena narrativa (creativo, temp. alta)      │
│     │  GM Agent   │  Riceve: lore, personaggio, inventario, quest,        │
│     └──────┬─────┘  storia recente, riassunto narrativo, scena            │
│            │ bozza                                                          │
│            ▼                                                                │
│  6. ┌──────────────┐  Classifica le categorie tematiche della bozza       │
│     │ Router Agent  │  via ricerca per similarita' embedding per-chunk     │
│     └──────┬───────┘  (nessun LLM — puro RAG)                             │
│            │ categorie                                                      │
│            ▼                                                                │
│  7. VALIDAZIONE PARALLELA ──────────────────────────────────────────────   │
│     │                                                                       │
│     ├─► ┌─────────────────────┐  Un revisore per ogni categoria dal       │
│     │   │ Lore Reviewer(s)    │  Router. Ogni revisore conosce solo       │
│     │   │ (N istanze)         │  la lore della propria categoria e        │
│     │   └─────────────────────┘  valida la bozza contro di essa.          │
│     │                                                                       │
│     └─► ┌─────────────────────┐  Sempre attivo, indipendente dal Router.  │
│         │ Inventory Reviewer   │  Verifica coerenza con l'inventario      │
│         └─────────────────────┘  corrente del giocatore.                   │
│     │                                                                       │
│     ▼                                                                       │
│  8. Validazione fallita? ──si──► GM riscrive con feedback mirato          │
│     │                            (multi-turn: vede bozza + errori)         │
│     │                            Torna allo step 6 (max 3 tentativi)      │
│     no                                                                      │
│     │                                                                       │
│     ▼                                                                       │
│  9. AGGIORNAMENTO PARALLELO (sulla bozza approvata) ────────────────────  │
│     │                                                                       │
│     ├─► ┌─────────────────────┐  Inventario aggiornato (snapshot JSONB)   │
│     │   │ Inventory Updater    │                                           │
│     │   └─────────────────────┘                                            │
│     │                                                                       │
│     ├─► ┌─────────────────────┐  Quest attive e completate aggiornate     │
│     │   │ Quest Log Updater    │                                           │
│     │   └─────────────────────┘                                            │
│     │                                                                       │
│     └─► ┌─────────────────────┐  Luogo, data, ora, meteo aggiornati      │
│         │ Scene Updater        │                                           │
│         └─────────────────────┘                                            │
│     │                                                                       │
│     ▼                                                                       │
│ 10. Persistenza selettiva + stream della scena al giocatore via SSE       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.4 Il Router Agent — Classificazione per Similarita'

Il Router non usa un LLM. Opera interamente per similarita' embedding:

1. Rimuove il markup Markdown dalla bozza del GM
2. Spezza il testo in chunk di 300 caratteri
3. Per ogni chunk, esegue una ricerca per similarita' sul RedisVectorStore (topK=5, soglia 0.45, filtro lingua)
4. Estrae la `category` dai metadati di ogni documento trovato
5. Le categorie vengono deduplicate e filtrate contro le categorie valide del `LoreRegistry`

Il risultato e' l'insieme delle categorie di lore toccate dalla bozza (es. `ECONOMIA`, `FAUNA`, `POLITICA`).

### 6.5 Retry e Feedback Multi-Turn

Quando la validazione fallisce, il GM non rigenera da zero. La rigenerazione e' una conversazione a 4 messaggi:

1. **System prompt** completo (istruzioni + lore + personaggio + campagna)
2. **Azione del giocatore** (messaggio utente originale)
3. **Bozza precedente** (messaggio assistente — la bozza respinta)
4. **Feedback aggregato** (messaggio utente — gli errori specifici da correggere)

Questo preserva il contesto e permette al modello di capire cosa correggere senza perdere le parti buone della bozza.
Il feedback e' assemblato deterministicamente in Java dal template `gm_feedback.md`:

```
- [ECONOMIA]: Una spada di bronzo non costa oro.
- [INVENTARIO]: Il giocatore non possiede una pozione di cura.
```

Se tutti e 3 i tentativi falliscono, l'ultima bozza viene consegnata con un flag `hasWarning = true` nell'evento SSE
`COMPLETED`. Il frontend puo' mostrare un indicatore visivo.

### 6.6 Comunicazione Real-Time (SSE)

La pipeline puo' richiedere decine di secondi (generazione + routing + validazione + updater). Per evitare che il
giocatore rimanga senza feedback, il sistema usa Server-Sent Events con eventi tipizzati.

**Progress codes** (il backend invia codici, il frontend li traduce in stringhe localizzate con selezione casuale tra
varianti):

| Codice                   | Contesto           | Significato                               |
|--------------------------|--------------------|-------------------------------------------|
| `GM_WRITING`             | Turno              | Il GM sta scrivendo la scena              |
| `GM_INCONSISTENCY`       | Turno              | Rilevata incoerenza nella bozza           |
| `GM_REWRITING`           | Turno              | Il GM sta riscrivendo dopo feedback       |
| `VALIDATORS_RUNNING`     | Turno              | Validazione in corso                      |
| `UPDATERS_RUNNING`       | Turno              | Aggiornamento inventario/quest/scena      |
| `CAMPAIGN_PREPARING`     | Init campagna      | Preparazione campagna                     |
| `INIT_AGENTS`            | Init campagna      | Avvio agenti di inizializzazione          |
| `AGENTS_WORKING`         | Init campagna      | Agenti al lavoro                          |
| `CAMPAIGN_SAVING`        | Init campagna      | Salvataggio campagna                      |
| `ADVISOR_THINKING`       | Advisor            | L'advisor sta pensando                    |
| `SETUP_ADVISOR_THINKING` | Setup advisor      | L'advisor di setup sta pensando           |

Un heartbeat (thread schedulato, `AutoCloseable`) invia eventi `PROGRESS` ogni 10 secondi durante le inferenze
lunghe per mantenere la connessione viva.

---

## 7. Campaign Setup

Il campaign setup e' un flusso conversazionale guidato dall'AI che aiuta il giocatore a creare il proprio personaggio.
A differenza della creazione diretta, il giocatore chatta con un advisor che lo guida nella definizione del background.

```
Giocatore apre "Nuova Campagna"
        │
        ▼
┌───────────────────────┐
│ CampaignSetupAdvisor  │  Chat conversazionale guidata
│ (Gemini, temp 0.35)   │  Il giocatore descrive il personaggio
└──────────┬────────────┘
           │
           ▼ (giocatore conferma)
┌───────────────────────┐
│ CampaignSetupSummary  │  Estrae preferenze dalla conversazione
│ (Gemini, temp 0)      │
└──────────┬────────────┘
           │
           ▼
┌───────────────────────┐
│   NameRaceSetupAgent  │  Sceglie nome e razza coerenti
│ (Gemini, temp 0.7)    │  con la lore (JSON + tool calling)
└──────────┬────────────┘
           │
           ▼
┌───────────────────────┐
│ CampaignSetupGenerator│  Genera character prompt + scena
│ (Gemini, temp 0.7)    │
└───────────────────────┘
```

---

## 8. Inizializzazione della Campagna

Quando il personaggio e' stato creato e il giocatore avvia la campagna, una pipeline dedicata (`CampaignInitService`)
esegue tre agenti di inizializzazione in parallelo a partire dal background narrativo del personaggio:

```
  Background del personaggio
        │
        ▼
  ┌─────┴─────┬─────────────────┐
  │            │                 │
  ▼            ▼                 ▼
┌──────┐  ┌────────┐  ┌──────────────┐
│ Inv. │  │ Quest  │  │    Scene     │
│ Init │  │ Init   │  │    Init      │
└──┬───┘  └───┬────┘  └──────┬───────┘
   │          │              │
   ▼          ▼              ▼
Inventario  Obiettivi     Luogo, data,
iniziale    iniziali      ora, meteo
```

Tutti e tre gli agenti ricevono lo stesso contesto iniziale e producono il loro output indipendentemente. La scena
include il luogo di partenza coerente con il background, la data e ora nel calendario di Ondgard (giornata di 32 ore),
e le condizioni meteo. I risultati vengono salvati nel `CampaignContext` e persistiti su Redis e PostgreSQL.

Il servizio inizializza anche il tracking token per la campagna su ondgard-account.

Questa pipeline ha il proprio stream SSE (`POST /api/sse/campaign/init`) separato da quello di turno.

---

## 9. Gestione della Sessione e Persistenza

### 9.1 CampaignContext

Il `CampaignContext` e' l'oggetto centrale di stato di una campagna. Vive in Redis durante la sessione attiva e viene
ricostruito dal database in caso di cache miss.

```
CampaignContext
├── campaignId                    (PK del DB)
├── currentTurn                   (contatore turni)
├── scene                        (GameScene: luogo, data, ora, meteo, temperatura)
├── inventory                    (Inventory: valuta, contenitori, oggetti, trasporto)
├── questLog                     (CampaignQuestLog: quest attive, completate)
├── narrativeSummary             (riassunto compresso della storia)
├── summaryVersion               (versione del summary, 0 = mai generato)
├── turnsSinceLastSummary        (contatore per trigger summary)
├── rawSummaryBuffer             (turni usciti dalla sliding window, non ancora compressi)
├── recentHistory                (ultimi N turni in chiaro — ricostruita dal DB)
├── advisorFacts                 (fatti estratti dall'advisor per il contesto GM)
├── turnsSinceLastFlush          (contatore per write-behind su DB)
└── lastUpdate                   (timestamp)
```

### 9.2 Strategia Write-Behind

Redis e' la sorgente di lettura veloce per tutta la sessione attiva. PostgreSQL resta la fonte di verita'. La
persistenza avviene in tre casi:

1. **Flush periodico** — ogni 5 turni, `CampaignService.save()` scrive su PostgreSQL e resetta il contatore.
   La finestra massima di perdita dati in caso di crash Redis e' di 5 turni.
2. **Logout esplicito** — il frontend chiama `POST /api/campaign/session/end`, il backend fa flush completo su DB e
   cancella le chiavi Redis.
3. **Scadenza per inattivita'** — Redis TTL di 30 minuti. Un `SessionExpirationListener` (keyspace notification)
   intercetta l'evento di scadenza e scrive su DB. Un `SessionStartupScan` al boot verifica che non ci siano sessioni
   orfane nel sorted set `ondgard:active-sessions`.

### 9.3 Chiavi Redis

| Chiave                               | Tipo       | Contenuto                         | TTL     |
|--------------------------------------|------------|-----------------------------------|------------|
| `ondgard:ctx:{characterHash}`        | String     | CampaignContext serializzato JSON | 30 min  |
| `ondgard:session-timer:{charHash}`   | String     | Marker per keyspace notification  | 30 min  |
| `ondgard:active-sessions`            | Sorted Set | characterHash, score = timestamp  | nessuno |

---

## 10. Memoria a Tre Livelli

Il GM non deve mai perdere contesto. La storia del giocatore e' organizzata su tre livelli che lavorano insieme:

| Livello               | Contenuto                                             | Nel prompt del GM come            |
|-----------------------|-------------------------------------------------------|-----------------------------------|
| **Sliding window**    | Ultimi N turni in chiaro (user + GM)                  | `## STORIA RECENTE`               |
| **Event buffer**      | Turni usciti dalla window, non ancora compressi       | `## EVENTI RECENTI NON RIASSUNTI` |
| **Narrative summary** | Riassunto LLM-compresso di tutta la storia precedente | `## RIASSUNTO NARRATIVO`          |

Ad ogni turno il GM riceve tutti e tre i livelli, dal piu' vecchio al piu' recente: summary → buffer → window. Zero
turni persi, zero duplicazioni.

### 10.1 Switch Full-Lore → RAG

Nei primi 12 turni (`fullLoreTurns = 12`) il GM riceve la **lore completa** dal `LoreRegistry` invece dei chunk RAG.
Questo garantisce massima coerenza nella fase di stabilizzazione della scena. Quando `summaryVersion` passa a 1
(primo summary generato), l'`OrchestratorService` attiva il retrieval RAG al posto della lore completa.

### 10.2 Generazione Asincrona del Summary

Il `SummaryService` opera in modo asincrono fire-and-forget. L'`OrchestratorService` non aspetta il nuovo summary —
se un turno parte mentre la compressione e' in corso, usa la versione precedente. Il risultato viene applicato al
prossimo `CampaignService.load()`. Se la sessione e' scaduta nel frattempo, il summary viene scritto direttamente su
PostgreSQL.

---

## 11. Inventario

Il GM non gestisce calcoli di inventario. Due agenti dedicati se ne occupano:

- **Inventory Reviewer** — Sempre attivo nella validazione, indipendente dal Router. Verifica che la bozza non usi
  oggetti non posseduti, non spenda valuta non disponibile, non violi limiti di peso. Partecipa al retry loop.
- **Inventory Updater** — Dopo la validazione, produce l'intero snapshot aggiornato dell'inventario. Nessun "add/remove"
  incrementale — produce sempre il risultato completo.

L'inventario e' un oggetto ricco, non una flat list:

```
Inventory
├── Valuta (oro, argento, bronzo)
├── TrasportoPersonale[] (cavallo, carro — nome, tipo, stato, extra[])
└── Contenitore[] (zaino, borsa — nome, oggetti[])
    └── InventoryItem[] (nome, descrizione, quantita, peso, extra[])
```

Persistito come snapshot JSONB append-only su `player_inventory`. Ogni snapshot ha `campaign_id`, `version`,
`turn_number` e il JSONB completo. Persistenza condizionale: solo se l'inventario e' effettivamente cambiato.

---

## 12. Quest Log

Il `QuestlogUpdaterAgent` traccia l'evoluzione delle quest dalla bozza approvata. Produce due campi di testo:
`questActive` (quest in corso) e `questCompleted` (quest completate di recente). Persistito come snapshot su
`campaign_questlog` (append-only). Persistenza condizionale: solo se lo stato e' cambiato.

Le quest attive vengono anche iniettate nel prompt del `RagQueryAgent` per migliorare la pertinenza del retrieval.

---

## 13. Scena

La `GameScene` rappresenta lo stato ambientale corrente: luogo, data di gioco, ora, meteo, temperatura. Il mondo di
Ondgard ha una giornata di 32 ore (definita nella lore).

Due agenti dedicati:

- **Scene Initializer** — Determina la scena iniziale dal background del personaggio
- **Scene Updater** — Aggiorna la scena ad ogni turno dalla bozza approvata

Entrambi usano un `SceneLoreProvider` che recupera lore ambientale rilevante via RAG (topK=30, soglia 0.3) con lazy
cache thread-safe.

---

## 14. Token Tracking

Il tracking dei token e' distribuito tra ondgard-chat e ondgard-account:

1. **`TokenAwareChatModelDecorator`** — Decora ogni bean `ChatModel` in ondgard-chat. Dopo ogni chiamata LLM, estrae il
   consumo token dalla risposta e lo invia a ondgard-account via `AccountClient.addTokensAsync()` (fire-and-forget)
2. **`TokenTrackingContext`** — `ThreadLocal` che mantiene il `characterHash` nel contesto del thread, propagato
   attraverso i confini asincroni via `wrap(Runnable/Supplier)`
3. **Verifica limiti** — L'`OrchestratorService` chiama `AccountClient.checkLimit()` in modo sincrono prima di avviare
   la pipeline. Se il limite e' superato, lancia `TokenLimitExceededException` (HTTP 429)
4. **ondgard-account** — Mantiene contatori `totalTokens` e `monthTokens` per campagna, con reset mensile lazy e limiti
   configurabili dall'utente

---

## 15. Schema Database di Gioco

### db-game (:5432)

```
┌──────────────┐     ┌───────────────────┐     ┌──────────────┐
│  game_user   │     │ player_character  │     │   cfg_race   │
│              │     │                   │     │              │
│ PK id        │◄────│ FK user_id        │     │ PK id        │
│ UQ user_hash │     │ FK race_id ───────│────►│ UQ code      │
│ UQ username  │     │ PK id             │     │ JSONB attrs  │
└──────────────┘     │ UQ character_hash │     └──────────────┘
                     └────────┬──────────┘
                              │ 1:1 (shared PK)
                     ┌────────▼──────────┐
                     │     campaign      │
                     │                   │
                     │ PK/FK character_id│
                     │ current_location  │
                     │ game_date/time    │
                     │ meteo/temperature │
                     │ turn_count        │
                     │ narrative_summary │
                     │ summary_version   │
                     └────────┬──────────┘
                              │ 1:N
          ┌───────────────────┼───────────────┬──────────────────┐
          │                   │               │                  │
 ┌────────▼───────┐ ┌────────▼───────┐ ┌─────▼────────────┐ ┌───▼───────────┐
 │ adventure_log  │ │player_inventory│ │campaign_questlog │ │  advisor_log  │
 │                │ │                │ │                  │ │               │
 │ FK campaign_id │ │ FK campaign_id │ │ FK campaign_id   │ │ FK campaign_id│
 │ role, content  │ │ version        │ │ turn_number      │ │ turn_number   │
 │ turn_number    │ │ turn_number    │ │ quest_active     │ │ user_message  │
 └────────────────┘ │ JSONB inv.     │ │ quest_completed  │ │ advisor_resp  │
                    └────────────────┘ └──────────────────┘ └───────────────┘

                     ┌──────────────────┐
                     │ campaign_notes   │
                     │                  │
                     │ PK/FK campaign_id│
                     │ content (TEXT)   │
                     └──────────────────┘
```

- `adventure_log` — Log cronologico di ogni scambio azione/risposta, fonte di verita' per la sliding window e il
  summary buffer
- `player_inventory` — Snapshot JSONB append-only, un record per ogni cambio di inventario
- `campaign_questlog` — Snapshot quest append-only, un record per ogni cambio di stato delle quest
- `advisor_log` — Log delle interazioni ask-mode (domanda giocatore → risposta advisor)
- `campaign_notes` — Note personali del giocatore (una riga per campagna)

### db-auth (:5433)

```
┌──────────────┐     ┌──────────────────┐
│   app_user   │     │  refresh_token   │
│              │     │                  │
│ PK id        │◄────│ FK user_id       │
│ UQ user_hash │     │ token_hash       │
│ UQ username  │     │ expires_at       │
│ UQ email     │     │ revoked          │
│ password_hash│     └──────────────────┘
│ enabled      │
│ locked_until │     ┌──────────────────┐
│ last_login   │     │email_confirmation│
│ last_email.. │     │                  │
└──────────────┘     │ FK/UQ user_id    │
        │            │ token_hash       │
        └───────────►│ expiry           │
                     └──────────────────┘
                     ┌──────────────────┐
                     │ password_reset   │
                     │                  │
                     │ FK/UQ user_id    │
                     │ token_hash       │
                     │ expiry           │
                     └──────────────────┘
```

### db-account (:5434)

```
┌────────────────────┐     ┌────────────────────────┐
│ token_usage_limit  │     │ campaign_token_usage    │
│                    │     │                        │
│ PK id              │     │ PK character_hash      │
│ UQ user_hash       │     │ user_hash              │
│ limit_month        │     │ total_tokens           │
│ limit_total        │     │ month_tokens           │
│ version            │     │ last_reset_month       │
└────────────────────┘     └────────────────────────┘
```

---

## 16. Endpoint API

### Ondgard Auth

| Metodo | Path                              | Descrizione                          |
|--------|-----------------------------------|--------------------------------------|
| `POST` | `/api/auth/register`              | Registrazione utente                 |
| `POST` | `/api/auth/confirm`               | Conferma email                       |
| `POST` | `/api/auth/resend-confirmation`   | Rinvio email di conferma             |
| `POST` | `/api/auth/login`                 | Login (access + refresh token)       |
| `POST` | `/api/auth/login/refresh`         | Refresh access token                 |
| `GET`  | `/api/auth/check-username`        | Verifica disponibilita' username     |
| `POST` | `/api/auth/google`                | Google OAuth (login o registrazione) |
| `POST` | `/api/auth/forgot-password`       | Richiesta reset password             |
| `POST` | `/api/auth/reset-password`        | Reset password con token             |

### Ondgard Chat

| Metodo   | Path                             | Descrizione                                          |
|----------|----------------------------------|------------------------------------------------------|
| `POST`   | `/api/user`                      | Crea game user (interno, chiamato dal servizio auth) |
| `GET`    | `/api/character/all`             | Lista personaggi del giocatore                       |
| `GET`    | `/api/character/{hash}`          | Dettaglio personaggio                                |
| `POST`   | `/api/character`                 | Crea personaggio                                     |
| `GET`    | `/api/config/races`              | Lista razze giocabili                                |
| `GET`    | `/api/campaign/turn`             | Stato turno corrente (inventario, quest, scena)      |
| `GET`    | `/api/campaign/exists`           | Verifica se l'utente ha campagne attive              |
| `GET`    | `/api/campaign/list`             | Lista campagne con metadati                          |
| `GET`    | `/api/campaign/history`          | Storico turni della campagna                         |
| `GET`    | `/api/campaign/quest-active`     | Quest attive correnti                                |
| `GET`    | `/api/campaign/advisor-log`      | Log domande/risposte advisor                         |
| `GET`    | `/api/campaign/playerNotes`      | Note personali del giocatore                         |
| `PUT`    | `/api/campaign/playerNotes`      | Aggiorna note personali                              |
| `DELETE` | `/api/campaign/{hash}`           | Elimina campagna e personaggio                       |
| `POST`   | `/api/campaign/session/end`      | Flush sessione (logout/navigazione)                  |
| `POST`   | `/api/sse/interaction`           | Turno di gioco (stream SSE)                          |
| `POST`   | `/api/sse/campaign/init`         | Inizializzazione campagna (stream SSE)               |
| `GET`    | `/api/health`                    | Health check + readiness RAG                         |

### Ondgard Account

| Metodo | Path                              | Descrizione                                  |
|--------|-----------------------------------|----------------------------------------------|
| `GET`  | `/api/token-usage`                | Overview consumo token (via gateway)         |
| `PUT`  | `/api/token-usage/limits`         | Aggiorna limiti token (via gateway)          |
| `POST` | `/api/internal/user/init`         | Init limiti utente (service-to-service)      |
| `POST` | `/api/internal/campaign/init`     | Init usage campagna (service-to-service)     |
| `POST` | `/api/internal/tokens`            | Aggiungi token (service-to-service, async)   |
| `GET`  | `/api/internal/check-limit`       | Verifica limiti (service-to-service, sync)   |

### Ondgard Mail

| Metodo | Path                | Descrizione                                     |
|--------|---------------------|-------------------------------------------------|
| `POST` | `/api/mail/send`    | Invio email (service-to-service, fire-and-forget)|

---

## 17. Ciclo di Vita Completo di una Campagna

### 17.1 Campaign Setup (opzionale)

Il giocatore puo' creare il personaggio tramite un flusso conversazionale guidato dall'AI. Il
`CampaignSetupAdvisorAgent` chatta con il giocatore per definire background, personalita' e preferenze. Al termine, il
sistema estrae le preferenze, sceglie nome e razza, e genera il character prompt.

### 17.2 Creazione del Personaggio

Il giocatore sceglie razza e nome (o li riceve dal setup), scrive un background narrativo. Il backend crea il
`player_character` e la `campaign` associata (1:1 shared PK). A questo punto `turnCount = 0`.

### 17.3 Inizializzazione

Il frontend avvia la SSE di init (`POST /api/sse/campaign/init`). I tre agenti di inizializzazione girano in parallelo e
producono inventario, quest e scena iniziali. Il `CampaignContext` viene salvato su Redis. Il tracking token per la
campagna viene inizializzato su ondgard-account. La campagna e' pronta.

### 17.4 Primi 12 Turni (Fase Full-Lore)

Il GM riceve la **lore completa** dal `LoreRegistry`, non il RAG. Questo da' massima coerenza nella fase di
stabilizzazione della scena. La sliding window accumula i turni in chiaro. `summaryVersion` resta 0.

### 17.5 Primo Summary e Switch RAG

Quando il buffer accumula abbastanza turni raw, il `SummaryService` genera il primo riassunto narrativo in background.
`summaryVersion` passa a 1. Da questo momento l'`OrchestratorService` attiva il retrieval RAG al posto della lore
completa.

### 17.6 Campagna in Corso

Ogni turno segue la pipeline completa: verifica limiti token → RAG → GM → Router → validazione → aggiornamento →
persistenza. Il summary cresce di versione periodicamente. La sessione vive in Redis con TTL rinnovato ad ogni
interazione. Il flush su DB avviene ogni 5 turni, al logout, o alla scadenza per inattivita'.

Il giocatore puo' usare l'**Advisor** (ask mode) per fare domande durante la campagna senza consumare un turno. Le
interazioni vengono loggate in `advisor_log`. Puo' anche salvare **note personali** in `campaign_notes`.

### 17.7 Ripresa di una Campagna

Il giocatore seleziona una campagna dalla lista (`GET /api/campaign/list`, solo campagne con `turnCount > 0`).
Il `CampaignService.load()` verifica Redis (hit) o ricostruisce dal DB (miss): carica la sliding window da
`adventure_log`, il buffer raw dai turni intermedi, l'inventario dall'ultimo snapshot JSONB, le quest dall'ultimo
snapshot, il summary dalla colonna `narrative_summary`. Il giocatore riprende esattamente dove aveva lasciato.

### 17.8 Uscita dalla Campagna

Al logout o alla navigazione fuori dalla pagina di campagna, il frontend chiama `POST /api/campaign/session/end`.
Il backend esegue il flush completo su DB e cancella le chiavi Redis. Se il frontend non riesce a chiamare l'endpoint
(chiusura tab, crash), il `SessionExpirationListener` fa da safety net dopo 30 minuti.

---

## 18. Frontend

React 19 + TypeScript 5.9 + Vite 7. SCSS con CSS Modules. State management via Zustand.

### 18.1 Internazionalizzazione

`react-i18next` con `i18next-http-backend` + `i18next-browser-languagedetector`. Lingue supportate: inglese (bundled in
JS, fallback), italiano e spagnolo (caricati on-demand da `public/locales/{lng}/translation.json`). Le lingue
disponibili vengono auto-scoperte dalle directory sotto `public/locales/` al build time. La lingua scelta viene salvata
in localStorage e propagata al backend come header `Accept-Language`.

### 18.2 Autenticazione

Login email + Google OAuth (`@react-oauth/google`). Lo store auth (`useAuthStore`) usa `loginWithTokens()` come entry
point unico per tutti i flussi di login — lo username viene estratto dal JWT, mai dall'input utente.

---

## 19. Catena di Avvio

```
postgres-game + postgres-auth + postgres-account       redis
     │              │              │                      │
     ▼              ▼              ▼                      │
liquibase-game  liquibase-auth  liquibase-account         │
     │              │              │                      │
     │              ▼              ▼                      │
     │         ondgard-auth   ondgard-account              │
     │              │                                      │
     ▼              ▼                                      ▼
ondgard-chat ◄────────────────────────────────────────  gateway
     │                                                  (:8080)
     │ (ApplicationReadyEvent)
     ▼
RagInitializationService
  │
  ├─► CRC32 hash match? ──si──► RAG pronto (~1s)
  │
  └─► CRC32 mismatch ──► attende segnale Redis (ondgard:rag:init)
        │                      │
        │               ondgard-warmup / rag-init-trigger
        │                      │ pubblica su ondgard:rag:init
        ▼                      ▼
     ETL: chunking + embedding + indicizzazione RedisVectorStore
        │
        ▼
     RagReadinessGate si apre
        │
        ▼
     Il sistema e' pronto per le interazioni di gioco
```

**Dipendenze critiche:**

- `ondgard-chat` parte dopo che Liquibase ha applicato le migrazioni e Redis e' healthy
- `ondgard-warmup` parte dopo `ondgard-chat` (deve poter pubblicare su Redis)
- Il Gateway parte dopo `ondgard-auth` (necessario per validare i JWT)
- `ollama-gpu` e `ollama-cpu` sono nel profilo `llm`, separato dai servizi Spring

**Servizi Docker Compose per profilo:**

| Profilo     | Servizi                                                                   |
|-------------|---------------------------------------------------------------------------|
| `db`        | postgres-game, postgres-auth, postgres-account, liquibase-*, redis        |
| `llm`       | ollama-gpu, ollama-cpu                                                    |
| `spring`    | ondgard-chat, ondgard-auth, ondgard-account, ondgard-mail, gateway, ondgard-warmup + tutto `db` |
| `backend`   | tutto `spring` + tutto `db`                                               |
| `fullstack` | tutto `backend` + frontend + Caddy proxy                                  |
| `frontend`  | frontend + Caddy proxy                                                    |
