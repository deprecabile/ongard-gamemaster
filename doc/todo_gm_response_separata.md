# TODO: Separare invio GM response dagli updater

## Obiettivo

Inviare il draft validato del GM al frontend via SSE **subito dopo la validazione**, senza aspettare che i 3 updater
paralleli (inventario, quest, scena) finiscano. L'evento `COMPLETED` resta invariato: viene emesso solo dopo che tutti
gli updater hanno terminato.

Risultato: l'utente vede il testo narrativo del GM prima, riducendo la latenza percepita.

## Flusso attuale

```
GM genera → Router → Validatori → [retry loop] → UPDATERS_RUNNING → completeTurn() → COMPLETED(draft + stato)
```

Il frontend riceve il testo GM solo dentro `COMPLETED`.

## Flusso proposto

```
GM genera → Router → Validatori → [retry loop] → GM_RESPONSE(draft) → UPDATERS_RUNNING → completeTurn() → COMPLETED(stato)
```

## Modifiche backend

### 1. Nuovo evento SSE `GM_RESPONSE`

**File:** `SseEventType.java`

Aggiungere:

```java
GM_RESPONSE("gm_response"),
```

### 2. Nuovo record per il payload

**File:** nuovo `SseGmResponseEvent.java`

```java
public record SseGmResponseEvent(LocalDateTime tms, String gmOutput, boolean inconsistencyDetected) {}
```

### 3. Emissione anticipata in `GameMasterService.processAction()`

**File:** `GameMasterService.java`, nel blocco `CompletableFuture.runAsync()`

Dopo `generateAndValidate()` e prima di `completeTurn()`:

```java
GenerationResult result = generateAndValidate(emitter, gmContext, request);

// --- NUOVO: manda subito il testo al frontend ---
sendEvent(emitter, SseEventType.GM_RESPONSE,
    new SseGmResponseEvent(LocalDateTime.now(), result.draft(), !result.validated()));

request.getCampaignContext().getAdvisorFacts().clear();

sendProgress(emitter, SseProgressCode.UPDATERS_RUNNING);
turnCompletionService.completeTurn(request, result.agents(), result.draft());
```

### 4. Rimuovere `gmOutput` da `SseCompletedEvent`

**File:** `SseCompletedEvent.java`

```java
// prima
public record SseCompletedEvent(LocalDateTime tms, String gmOutput, boolean inconsistencyDetected) {}

// dopo
public record SseCompletedEvent(LocalDateTime tms) {}
```

E aggiornare l'emissione in `GameMasterService`:

```java
sendEvent(emitter, SseEventType.COMPLETED, new SseCompletedEvent(LocalDateTime.now()));
```

## Modifiche frontend

### 1. Listener per il nuovo evento

Aggiungere handler per `gm_response` nello stream SSE. Al ricevimento:

- Mostrare il testo narrativo del GM nella chat
- Il flag `inconsistencyDetected` va gestito qui (non piu in `COMPLETED`)

### 2. Gestione stato intermedio

Tra `gm_response` e `completed`, inventario/quest/scena non sono ancora aggiornati. Opzioni:

- Mostrare un indicatore di loading sulla sidebar inventario/quest/scena
- Oppure semplicemente aggiornare silenziosamente quando arriva `completed`

### 3. Handler `completed`

Non contiene piu `gmOutput`. Usarlo solo per:

- Aggiornare inventario, quest, scena nella UI
- Rimuovere eventuali indicatori di loading

## File coinvolti (riepilogo)

| File | Modifica |
|------|----------|
| `SseEventType.java` | Aggiungere `GM_RESPONSE` |
| Nuovo `SseGmResponseEvent.java` | Record per il payload |
| `SseCompletedEvent.java` | Rimuovere `gmOutput` e `inconsistencyDetected` |
| `GameMasterService.java` | Emettere `GM_RESPONSE` prima degli updater |
| Frontend SSE handler | Nuovo listener `gm_response` + stato intermedio |
