# Docker — Ondgard Gamemaster

## Prerequisiti

- Docker e Docker Compose
- Immagini LLM (build una tantum, vedi sotto)

## Avvio rapido

```bash
# Applicazione completa (backend + frontend + reverse proxy HTTPS)
docker compose --profile fullstack up --build

# Solo backend (senza frontend)
docker compose --profile backend up --build

# Solo database (per sviluppo locale dei servizi)
docker compose --profile db up
```

L'app sarà disponibile su **https://localhost** (certificato self-signed).

Per usare un dominio personalizzato in locale, aggiungi `127.0.0.1 mio-dominio` in
`C:\Windows\System32\drivers\etc\hosts` e avvia con:

```bash
SITE_DOMAIN=mio-dominio docker compose --profile fullstack up --build
```

## Build immagini LLM (una tantum)

Le immagini Ollama contengono i modelli baked-in. Vanno costruite una sola volta.

```bash
# GPU (richiede NVIDIA Container Toolkit)
docker build --network=host -t ondgard-ollama-gpu-gemma ./llm/ollama-gpu/

# CPU
docker build --network=host -t ondgard-ollama-cpu ./llm/ollama-cpu/
```

## Stop

```bash
docker compose --profile fullstack down
```
