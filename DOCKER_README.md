# Docker — Ongard Gamemaster

## Struttura

```
docker-compose.yml          ← orchestrazione di tutti i servizi
database/
├── db-auth/                ← PostgreSQL auth + changelog Liquibase
└── db-game/                ← PostgreSQL game + changelog Liquibase
backend/
├── authentication/         ← Dockerfile multi-stage (Maven → JRE)
├── gateway/                ← Dockerfile multi-stage
└── ongard-chat/            ← Dockerfile multi-stage
llm/
├── ollama-gpu/Dockerfile   ← immagine con VitoF/llama-3.1-8b-italian
└── ollama-cpu/Dockerfile   ← immagine con qwen3:4b + qwen3-embedding
```

## Prerequisiti — Build immagini LLM

Le immagini Ollama contengono i modelli baked-in e vanno costruite **una sola volta** manualmente. Il compose le referenzia con `image:` (senza `build:`), quindi `docker compose --build` non le tocca.

```bash
# GPU — modello narrativo italiano (richiede NVIDIA Container Toolkit)
docker build --network=host -t ongard-ollama-gpu ./llm/ollama-gpu/

# CPU — validazione regole + embedding
docker build --network=host -t ongard-ollama-cpu ./llm/ollama-cpu/
```

## Comandi principali

```bash
# Solo database (postgres + liquibase + redis)
docker compose --profile db up

# Solo LLM (ollama-gpu + ollama-cpu)
docker compose --profile llm up

# Solo servizi Spring (richiede profile db attivo)
docker compose --profile spring up --build

# Stack completo (db + llm + spring)
docker compose --profile backend up --build

# Rebuild e restart di un singolo servizio
docker compose --profile backend up --build authentication

# Stop e rimozione di tutti i container
docker compose --profile backend down
```

## Profili

| Profilo    | Servizi inclusi                                                        |
|------------|------------------------------------------------------------------------|
| `db`       | postgres-game, postgres-auth, liquibase-game, liquibase-auth, redis    |
| `llm`      | ollama-gpu, ollama-cpu                                                 |
| `spring`   | authentication, gateway, ongard-chat                                   |
| `backend`  | db + llm + spring (tutto)                                              |

## Mapping porte

| Servizio        | Porta host | Porta container | Descrizione                        |
|-----------------|------------|-----------------|------------------------------------|
| postgres-game   | 5432       | 5432            | PostgreSQL — schema game           |
| postgres-auth   | 5433       | 5432            | PostgreSQL — schema auth           |
| redis           | 6379       | 6379            | Cache e pub/sub                    |
| ollama-gpu      | 11434      | 11434           | LLM narrativo (GPU)               |
| ollama-cpu      | 11435      | 11434           | LLM validazione + embedding (CPU) |
| authentication  | 8089       | 8089            | Servizio autenticazione            |
| gateway         | 8080       | 8080            | API Gateway (entry point)          |
| ongard-chat     | 8081       | 8081            | Servizio chat/game                 |

## Rete

Tutti i servizi sono sulla rete bridge `ongard-network`. I servizi Spring raggiungono gli LLM internamente come `ollama-gpu:11434` e `ollama-cpu:11434`.
