# Ondgard Gamemaster

An AI-driven Game Master and Narrative Engine for cohesive, immersive text RPG adventures.

The backend orchestrates multiple specialized AI agents — a creative Game Master, semantic routers, lore reviewers, and
more — each with its own role in generating and validating every scene. LLM providers are swappable: local models via
Ollama, cloud APIs (e.g., Google Gemini), or a mix of both.

## 🚀 Features

* **Multi-Agent Architecture:** 12 specialized AI agents collaborate through a pipeline to produce consistent,
  rule-abiding narrative. Each agent can be backed by a different model.
* **Flexible LLM Backend:** Switch freely between local Ollama models and cloud APIs. The application is
  provider-agnostic — model configuration lives entirely in YAML.
* **Draft → Validate → Publish Pipeline:** Every GM-generated scene is routed, validated in parallel by reviewer
  agents, and only published if all checks pass. Failures trigger automatic rewrites with targeted corrections.
* **RAG-Powered Lore:** World-building rules and lore are stored as Markdown, chunked and embedded into a vector store
  at startup, then retrieved at generation time. The Java application is fully agnostic to the game setting.
* **Multilingual:** The entire experience — AI narration, lore, and UI — adapts to the player's language.
  Currently supported: English, Italian, Spanish.
* **In-Game Advisor:** An AI advisor the player can consult at any time to get tips and information — about game
  rules, lore, their current situation, or past events — without advancing the story. It can search the world's
  lore, inspect the player's inventory and quests, and register new facts for the Game Master to maintain
  consistency.
* **Token Usage Limits:** Players can set monthly and total token budgets. Usage is tracked per campaign and
  enforced automatically before each turn.
* **Session Management:** Redis-backed player context with write-behind to PostgreSQL.
* **Real-Time Feedback (SSE):** Streams the AI's progress to the frontend in real time (e.g., "Writing scene...", "
  Validating lore...").

## 🛠️ Technology Stack

* **Backend:** Java 25, Spring Boot 4, Spring AI, Lombok
*   **Frontend:** React, TypeScript, SCSS
* **AI:** Ollama (local), Google Gemini (cloud) — pluggable via configuration
* **Database:** PostgreSQL (Liquibase migrations)
* **Cache / Session:** Redis

## 🏗️ Architecture Overview

Lore and business logic are strictly separated. Rules live in Markdown files and are injected via RAG; the application
code knows nothing about the specific game world.

### Agent Map

The system orchestrates **12 specialized agents**, each with a single responsibility. No agent knows about the others —
the Orchestrator coordinates everything.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        GAME TURN PIPELINE                               │
│                                                                         │
│  1. Player sends action                                                 │
│        │                                                                │
│        ▼                                                                │
│  2. ┌──────────────┐  An AI agent formulates targeted search queries     │
│     │ RAG Retrieval │  from the player's action, scene and quest context │
│     └──────┬───────┘  and retrieves the relevant lore from the vector   │
│            │          store                                              │
│            ▼                                                            │
│  3. ┌────────────┐  Writes the narrative scene (creative, high temp.)   │
│     │  GM Agent   │  Knows: lore, character, scene, inventory, quests,  │
│     └──────┬─────┘  recent history, narrative summary                   │
│            │ draft                                                       │
│            ▼                                                            │
│  4. ┌──────────────┐  The draft is split into chunks and each chunk     │
│     │ Theme detect  │  is searched against the lore vector store.        │
│     └──────┬───────┘  The matching lore categories (e.g. MAGIC,         │
│            │ themes   ECONOMY, GEOGRAPHY) determine which reviewers     │
│            ▼          to activate.                                       │
│                                                                         │
│  5. PARALLEL VALIDATION ─────────────────────────────────────────────   │
│     │                                                                   │
│     ├─► ┌─────────────────────┐  One reviewer is created dynamically    │
│     │   │ Lore Reviewer(s)    │  for EACH theme detected in the draft.  │
│     │   │ (N instances)       │  Each receives only its own category's  │
│     │   └─────────────────────┘  lore rules (e.g. magic rules, economy  │
│     │   Created on-the-fly        rules). If 3 themes are detected, 3   │
│     │   from a factory.           independent reviewers run             │
│     │   Categories come from      simultaneously. Valid categories are  │
│     │   the lore folder            derived from the lore folder          │
│     │   structure.                 structure at startup.                 │
│     │                                                                   │
│     └─► ┌─────────────────────┐  Always active, regardless of Router.   │
│         │ Inventory Reviewer   │  Checks: does the GM use items the     │
│         └─────────────────────┘  player doesn't own? Spend currency     │
│                                  they don't have? Violate weight rules? │
│     │                                                                   │
│     ▼                                                                   │
│  6. Any failure? ──yes──► GM Agent rewrites with targeted feedback       │
│     │                     (multi-turn: sees its previous draft +         │
│     │                      the specific errors to fix)                   │
│     │                     Loop back to step 4 (up to N retries)          │
│     no                                                                  │
│     │                                                                   │
│     ▼                                                                   │
│  7. PARALLEL UPDATE (on the approved draft) ─────────────────────────   │
│     │                                                                   │
│     ├─► ┌─────────────────────┐  Reads the narrative and returns the    │
│     │   │ Inventory Updater    │  full updated inventory snapshot        │
│     │   └─────────────────────┘  (currency, containers, items, weight)  │
│     │                                                                   │
│     ├─► ┌─────────────────────┐  Extracts quest changes: new quests     │
│     │   │ Quest Log Updater    │  started, objectives completed          │
│     │   └─────────────────────┘                                         │
│     │                                                                   │
│     └─► ┌─────────────────────┐  Updates location, date, time,          │
│         │ Scene Updater        │  weather, temperature from the          │
│         └─────────────────────┘  narrative events                       │
│     │                                                                   │
│     ▼                                                                   │
│  8. Save & stream final scene to the player via SSE                     │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

#### Campaign Initialization

When a player starts a new campaign, a separate pipeline runs **three initializer agents in parallel** to bootstrap the
game world from the character's backstory:

```
  Character backstory
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
Starting   Initial quest   Starting location,
inventory  objectives      date, time, weather
```

#### Agent Summary

| Agent                     | When it runs               | What it does                                                                                                                                                                                                                                               |
|---------------------------|----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **GM Agent**              | Every turn                 | Writes the narrative scene. On retry, receives its previous draft and the specific errors to correct                                                                                                                                                       |
| **Lore Reviewer(s)**      | Validation (parallel)      | Dynamically created for each lore theme detected in the draft. Each one knows only its category's rules and checks the draft against them. Categories match the lore folder structure — adding a new lore folder automatically makes it a reviewable theme |
| **Inventory Reviewer**    | Validation (parallel)      | Always active. Catches impossible transactions: using items not owned, spending currency not available                                                                                                                                                     |
| **Inventory Updater**     | Post-validation (parallel) | Produces the updated inventory snapshot from the approved narrative                                                                                                                                                                                        |
| **Quest Log Updater**     | Post-validation (parallel) | Tracks quest progression: new objectives, completed quests                                                                                                                                                                                                 |
| **Scene Updater**         | Post-validation (parallel) | Evolves the game scene: location changes, time passing, weather shifts                                                                                                                                                                                     |
| **Inventory Initializer** | Campaign start (parallel)  | Generates the starting inventory from the character's backstory                                                                                                                                                                                            |
| **Quest Log Initializer** | Campaign start (parallel)  | Extracts initial quest objectives from the backstory                                                                                                                                                                                                       |
| **Scene Initializer**     | Campaign start (parallel)  | Determines the starting location, date, time, and weather                                                                                                                                                                                                  |
| **RAG Query Agent**       | Before each turn           | Reads the player's action, current scene, and active quests to formulate smart search queries for the lore retrieval step                                                                                                                                  |
| **Summary Agent**         | Background                 | Compresses older turns into a rolling narrative summary, keeping the story's full arc without losing important details (see Long-Term Memory below)                                                                                                        |
| **Advisor Agent**         | On player request          | Gives the player advice and information about rules, lore, inventory, or past events without advancing the story. Can search the world's knowledge base and register new facts for the GM to maintain consistency                                          |

## 🧠 Long-Term Memory

The Game Master never loses track of the story. Player–GM exchanges are organized into three memory tiers that work
together so the narrative stays coherent across arbitrarily long campaigns:

| Tier                  | What it holds                                                         | Purpose                                                  |
|-----------------------|-----------------------------------------------------------------------|----------------------------------------------------------|
| **Recent history**    | The last *N* turns, verbatim                                          | Full detail — the GM sees exactly what was said and done |
| **Event buffer**      | Turns that aged out of recent history but haven't been summarized yet | Preserves every detail until the summarizer runs         |
| **Narrative summary** | An AI-compressed recap of everything older                            | Keeps the full arc of the story in a compact form        |

As the player progresses, turns flow through these tiers automatically:

1. Each new turn enters **recent history**. When the window is full, the oldest turn is moved to the **event buffer**.
2. Once the buffer accumulates enough turns, the **Summary Agent** compresses the existing summary together with the
   buffered events into a new, updated **narrative summary**, and the buffer is cleared.
3. The cycle repeats — the summary grows richer over time while staying compact.

On every turn the GM prompt includes all three tiers, from oldest context to newest: *summary → buffer → recent
history*. This means the model always has both the big picture (compressed past) and the fine detail (recent
exchanges), with no gaps in between.

## 📄 License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)** - see the [LICENSE](LICENSE) file for details.

