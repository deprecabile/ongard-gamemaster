# Ongard LLM — Ollama Docker Images

Le immagini Docker Ollama contengono i modelli AI baked-in. Vanno costruite **una sola volta** manualmente.
Il `docker-compose.yml` le referenzia con `image:` (senza `build:`), quindi `docker compose --build` non le ricostruisce.

## Build delle immagini

### GPU — Game Master narrativo

```bash
docker build --network=host -t ongard-ollama-gpu ./llm/ollama-gpu/
```

Modello incluso:
- **VitoF/llama-3.1-8b-italian** — LLM 8B italiano per la generazione narrativa creativa

### CPU — Validazione e embedding

```bash
docker build --network=host -t ongard-ollama-cpu ./llm/ollama-cpu/
```

Modelli inclusi:
- **qwen3-embedding** — modello di embedding per RAG e ricerca semantica
- **qwen3:4b** — LLM leggero 4B per validazione deterministica delle regole

## Avvio dei servizi

```bash
# Solo i servizi LLM
docker compose --profile llm up

# Stack completo (db + llm + spring)
docker compose --profile backend up --build
```

## Porte esposte

| Servizio      | Porta host | Porta interna | Accesso interno (Docker network) |
|---------------|------------|---------------|----------------------------------|
| `ollama-gpu`  | 11434      | 11434         | `ollama-gpu:11434`               |
| `ollama-cpu`  | 11435      | 11434         | `ollama-cpu:11434`               |

## Note

- Le immagini sono pesanti (~5-10 GB ciascuna) perché contengono i pesi dei modelli.
- Il build richiede connessione internet per scaricare i modelli da Ollama Hub.
- `ollama-gpu` richiede il NVIDIA Container Toolkit installato sull'host e una GPU compatibile.
- `ollama-cpu` funziona su qualsiasi macchina senza requisiti hardware particolari.
- Se si vuole aggiornare un modello, ricostruire l'immagine corrispondente con lo stesso tag.
