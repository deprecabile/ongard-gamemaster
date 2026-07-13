# Modalita Chat Ask

## Intento

Fornire al giocatore un canale di comunicazione diretto con un Advisor AI che non influenzi lo stato della partita.
Il giocatore puo fare domande sulla lore del mondo, chiarire la situazione corrente, chiedere dettagli su regole o
meccaniche, il tutto senza che il turno avanzi o che lo stato di gioco venga modificato. L'agente che risponde in
questa modalita si chiama "Advisor" (non GM), per separare chiaramente il ruolo informativo da quello narrativo.

## Descrizione

Quando il giocatore attiva il tab "Ask" nel pannello sinistro, la partita entra in pausa logica. L'interazione diventa
una conversazione di domanda-risposta con l'Advisor: il giocatore scrive una domanda, l'Advisor risponde basandosi sulla
lore,
sullo stato corrente della campagna e sulla storia recente. La pipeline completa del turno (router, validatori lore,
aggiornamento inventario/quest/scena, avanzamento turno) viene interamente bypassata.

La conversazione ask e separata dalla narrazione principale: i messaggi appaiono in un pannello dedicato sopra la text
area di input, non nell'area centrale della storia. Ogni scambio riporta il numero di turno in cui e avvenuto.

## Requisiti

### R1 — Nessun avanzamento di stato

- Il turno (`currentTurn`) non viene incrementato.
- Inventario, quest log e scena restano invariati: nessun updater viene eseguito.
- Nessun router e nessun validatore lore viene invocato.
- La history narrativa (`recentHistory`, `rawSummaryBuffer`) non viene alterata.
- La conversazione ask non influenza il riepilogo narrativo (`narrativeSummary`).

### R2 — Pipeline backend semplificata

- Quando `mode = ASK`, il backend esegue una pipeline ridotta: caricamento contesto + chiamata singola
  all'Advisor + risposta SSE. Nessuna fase di validazione o aggiornamento.
- L'Advisor riceve come contesto: lore pertinente (via RAG), stato corrente della campagna (scena, quest attive,
  inventario), storia recente, e la domanda del giocatore.
- Il system prompt dell'Advisor e distinto da quello narrativo del GM: l'Advisor risponde in modo informativo,
  non narrativo.

### R3 — Pannello chat ask nel frontend

- Quando il tab "Ask" e attivo, sopra la text area di input compare un pannello scrollabile con lo storico
  della conversazione ask.
- Il layout dei messaggi segue lo stile di un'app di messaggistica (tipo WhatsApp):
    - I messaggi del giocatore sono allineati a sinistra, con un fumetto di un colore.
    - I messaggi dell'Advisor sono allineati a destra, con un fumetto di colore diverso.
- Ogni fumetto riporta il numero di turno la data/ora reali in cui l'interazione e avvenuta.
- Il pannello e visibile solo in modalita ask; quando si torna ad "Action", il pannello scompare (ma lo stato
  viene conservato).

### R4 — Text area ridotta

- In modalita ask, la text area di input si riduce a 3 righe (ci si aspettano domande sintetiche, non
  racconti).
- In modalita action, la text area mantiene le dimensioni attuali.

### R5 — Persistenza della conversazione ask

- La conversazione ask viene salvata separatamente rispetto all'adventure log narrativo.
- Al rientro in sessione (resume), la cronologia ask viene ricaricata insieme alla storia.

### R6 — Token tracking

- Le chiamate AI in modalita ask consumano token e devono essere tracciate normalmente.
- I limiti token si applicano anche alla modalita ask.

---

## Advisor Agent — Design

### Il problema: classificazione dello scenario

La domanda del giocatore puo ricadere in scenari molto diversi, ciascuno con esigenze di contesto differenti:

| Scenario        | Esempio                                                                  | Contesto necessario                                    |
|-----------------|--------------------------------------------------------------------------|--------------------------------------------------------|
| Scena corrente  | "Descrivi la stanza in cui mi trovo"                                     | Conversazioni recenti, scena, inventario               |
| Lore del mondo  | "Chi sono i nani di Ondgard?"                                            | Chunk lore via RAG                                     |
| Passato lontano | "Cosa successe 15 anni fa a Ostria?"                                     | Riassunto narrativo, eventualmente adventure_log da DB |
| Misto           | "Questo tempio ha a che fare con la profezia di cui parlava il vecchio?" | Scena + lore + storia recente                          |

Caricare tutto il contesto a priori per ogni domanda sarebbe costoso in token e spesso inutile (una domanda
sulla lore non richiede l'inventario, una sulla scena non richiede chunk lore). Serve un meccanismo che
recuperi solo il contesto pertinente alla domanda.

Un approccio con router pre-classificatore (come fa la pipeline GM) aggiungerebbe una chiamata LLM extra
con relativo costo in token. Esiste un'alternativa migliore.

### Approccio: Spring AI Tool Calling

Spring AI 2.0.0-M2 (la versione in uso nel progetto) supporta nativamente il **tool calling**: il modello
puo richiedere l'invocazione di funzioni Java durante la generazione della risposta. Il framework intercetta
la richiesta, esegue il metodo, e restituisce il risultato al modello che lo integra nella risposta.

Questo elimina la necessita di un router: l'Advisor stesso decide quali informazioni gli servono in base
alla domanda, e le recupera invocando i tool appropriati. Nessun token speso per la classificazione.

### Contesto conversazionale

La chat recente utente-Advisor viene inclusa nel prompt come conversation history. Questo permette
al giocatore di fare follow-up naturali (es. "e quel personaggio di cui mi hai parlato prima?")
senza che l'Advisor perda il filo del discorso.

La history ask e persistita su DB (vedi R5) e ricaricata al resume della sessione. Viene passata
al modello come sequenza di messaggi user/assistant — e contesto base della chiamata, non contesto
on-demand (non e un tool).

**Filtro di inclusione nel prompt — doppia condizione:**

Uno scambio ask viene incluso nel prompt solo se soddisfa **entrambe** le condizioni:

1. E tra gli ultimi **N** scambi (es. N = 10)
2. Il turno in cui e avvenuto e entro un **delta massimo D** dal turno corrente
   (es. `currentTurn - askTurn <= D`, con D = 30)

Questo evita di trascinare nel prompt scambi obsoleti riferiti a turni molto lontani.
Esempio: partita al turno 85, scambi ask al turno 20 (delta 65) vengono esclusi anche se
rientrano negli ultimi 10; scambi al turno 70 (delta 15) vengono inclusi.

N e D saranno configurabili nelle properties (`ondgard.game.advisor.*`).

Gli scambi esclusi dal prompt restano comunque visibili nel pannello frontend (la UI mostra
l'intera cronologia ask, il filtro si applica solo al contesto del modello).

### Tool previsti

**`searchLore(query)`** — Esegue RAG retrieval sui documenti di lore usando la query fornita dal modello.
Restituisce i chunk piu rilevanti. Usato per domande sul mondo, razze, fazioni, luoghi, magia, ecc.

**`getCurrentScene()`** — Restituisce lo stato corrente della partita: scena (`GameScene`), quest attive,
inventario. Senza parametri, costa pochi token. Usato per domande sulla situazione attuale.

**`getRecentHistory()`** — Restituisce le ultime N conversazioni (da `recentHistory` nel `CampaignContext`).
Usato per domande su eventi recenti o per contestualizzare la scena.

**`searchPastEvents(query)`** — Cerca nel passato della campagna. Prima livello: ricerca nel
`narrativeSummary`. Secondo livello (evoluzione futura): full-text search su `adventure_log` in PostgreSQL.
Usato per domande su eventi lontani nel tempo.

### Propagazione dei fatti inventati: `registerAdvisorFact`

L'Advisor puo colmare vuoti narrativi inventando dettagli (es. il nome di un PNG, una descrizione
ambientale non ancora narrata). Questi dettagli devono essere coerenti con i turni successivi del GM.

Il meccanismo di propagazione sfrutta lo stesso tool calling: un quinto tool, **`registerAdvisorFact(fact)`**,
che l'Advisor invoca ogni volta che nella sua risposta introduce un dettaglio non presente nella lore
o nella storia narrata. Il system prompt dell'Advisor include l'istruzione esplicita di chiamare questo
tool in tali casi.

**Cosa succede quando viene invocato:**

- Il fatto viene aggiunto a una lista `advisorFacts` (`List<String>`) nel `CampaignContext`.
- Al turno action successivo, il GM riceve questi fatti come sezione aggiuntiva nel prompt
  (es. `## Fatti stabiliti dall'Advisor\n- Il taverniere si chiama Grok\n- ...`).
- Il costo in token per il GM e minimo: poche righe di bullet point.

**Ciclo di vita — consume-and-clear:**

- La lista vive nel `CampaignContext` in Redis durante la sessione. Non viene persistita su PostgreSQL.
- I fatti si accumulano durante le interazioni ASK. Al primo turno ACTION successivo, il GM li
  riceve nel prompt e la lista viene **svuotata**. Da quel momento i fatti vivono nella narrazione
  (recentHistory → narrativeSummary) e non vengono piu ripetuti.
- Senza svuotamento, gli stessi fatti verrebbero ripetuti nel prompt del GM a ogni turno ACTION
  per tutta la sessione, sprecando token per informazioni gia incorporate nella narrazione.
  Inoltre, se la narrazione evolve e contraddice un fatto (es. un PNG muore), il fatto resterebbe
  nel prompt a creare incoerenza.
- Al loading di una nuova sessione la lista riparte vuota. Questo e accettabile: i fatti
  dell'Advisor sono dettagli minori di colore. Se il GM non li ritrova nella sessione successiva,
  semplicemente potra stabilirne di nuovi. Non si tratta di fatti strutturali (quelli sono gestiti
  da inventario, quest log e scena).

**Affidabilita:** il modello potrebbe occasionalmente dimenticare di chiamare il tool. Il worst case
e una piccola incoerenza — non un errore bloccante. Nel caso opposto (tool chiamato per un fatto
gia esistente) il risultato e un bullet ridondante, innocuo.

Temperatura del modello: **0.15–0.3** — permette il margine creativo necessario per colmare i vuoti
narrativi, restando controllato.

### Flusso di esecuzione

```
Domanda utente
    │
    ▼
Advisor (Gemini, temp 0.15–0.3, con tool registrati)
    │
    ├──► il modello decide autonomamente quali tool invocare
    │       │
    │       ├─ searchLore("nani ondgard")                        ← recupero contesto
    │       ├─ getCurrentScene()                                  ← recupero contesto
    │       ├─ getRecentHistory()                                 ← recupero contesto
    │       ├─ searchPastEvents("villaggio X")                    ← recupero contesto
    │       └─ registerAdvisorFact("Il taverniere si chiama X")   ← propagazione fatto nuovo
    │
    ▼
Risposta ──► SSE COMPLETED ──► frontend (fumetto Advisor)
                          ──► advisorFacts aggiornati nel CampaignContext
```

### Vantaggi

- **Zero token per la classificazione** — nessun router, il modello classifica implicitamente scegliendo i tool.
- **Contesto on-demand** — si carica solo cio che serve per la domanda specifica.
- **Propagazione leggera** — i fatti nuovi arrivano al GM come bullet point, costo token trascurabile.
- **Estensibile** — aggiungere un nuovo tipo di contesto significa aggiungere un nuovo tool, senza
  modificare prompt o logica di orchestrazione.

---

## Integrazione nel flusso attuale

### Stato attuale

Il controller `CampaignSseController` riceve la request e chiama direttamente
`GameMasterService.processAction()`, che contiene sia il setup comune (validazione, token check,
caricamento contesto) sia l'intera pipeline del turno (RAG, GM, router, validatori, updater,
persistenza). Il campo `mode` della request viene ignorato.

### Refactoring: ChatInteractionService

Si introduce un nuovo servizio `ChatInteractionService` che diventa il punto di ingresso unico
per tutte le interazioni chat. Contiene il setup condiviso e dispatcha al servizio corretto
in base al mode.

**`CampaignSseController`** — diventa un passthrough:

```java
@PostMapping( value = "/interaction", produces = TEXT_EVENT_STREAM_VALUE )
public SseEmitter interaction(
    @RequestHeader( HEADER_NAME ) GameUserHeader userHeader,
    @RequestBody ChatInteractionRequest request) {
  return chatInteractionService.interact(userHeader, request);
}
```

**`ChatInteractionService`** — setup condiviso + dispatch:

```java
public SseEmitter interact(GameUserHeader userHeader, ChatInteractionRequest request) {
  // 1. Validazione characterHash
  // 2. Token limit check (accountClient.checkLimit)
  // 3. Caricamento PlayerCharacter
  // 4. Caricamento CampaignContext
  // 5. Risoluzione lingua (outputLang, loreLangCode)
  // 6. TokenTrackingContext.set()

  // 7. Dispatch
  return switch(request.mode()){
    case ACTION -> orchestratorService.processAction(interactionContext);
    case ASK -> advisorService.processAsk(interactionContext);
  };
}
```

Il contesto condiviso viene passato ai servizi tramite un record:

```java
public record InteractionContext(
    GameUserHeader userHeader,
    String message,
    String characterHash,
    PlayerCharacter character,
    CampaignContext campaignContext,
    String outputLang,
    String loreLangCode
) {
}
```

**`GameMasterService`** — perde il setup iniziale, riceve `InteractionContext`:

- Il metodo `processAction(InteractionContext ctx)` parte direttamente dalla creazione
  dell'`SseEmitter` e dalla fase RAG/GM.
- Nessuna modifica alla logica della pipeline.

**`AdvisorService`** (nuovo) — riceve `InteractionContext`:

- Crea il suo `SseEmitter`.
- Carica la chat ask recente (filtro N + delta turno).
- Invoca il modello Advisor con tool calling.
- Persiste lo scambio ask su DB.
- Aggiorna `advisorFacts` nel `CampaignContext`.
- Invia SSE COMPLETED.

### Eventi SSE dell'Advisor

L'AdvisorService usa event type SSE propri, distinti da quelli del turno narrativo. Il frontend
apre la stessa connessione SSE (`POST /api/sse/interaction`) ma discrimina i messaggi in base
al type per capire su quale canale di comunicazione operare (pannello storia vs pannello ask).

**Event type Advisor:**

| Event type          | Payload                           | Descrizione                                   |
|---------------------|-----------------------------------|-----------------------------------------------|
| `advisor_started`   | `{ ok: true }`                    | L'Advisor ha iniziato l'elaborazione          |
| `advisor_thinking`  | `{ tms }`                         | L'Advisor sta elaborando (heartbeat/progress) |
| `advisor_completed` | `{ tms, advisorOutput }`          | Risposta dell'Advisor pronta                  |
| `advisor_error`     | `{ tms, errorCode, description }` | Errore durante l'elaborazione                 |

Il prefisso `advisor_` permette al frontend di distinguere immediatamente il canale senza
logiche aggiuntive. Gli event type del turno narrativo (`started`, `progress`, `completed`,
`error`) restano invariati.

### Flusso risultante

```
CampaignSseController
    │
    ▼
ChatInteractionService.interact()
    ├─ validazione characterHash
    ├─ token limit check
    ├─ caricamento PlayerCharacter
    ├─ caricamento CampaignContext
    ├─ risoluzione lingua
    ├─ TokenTrackingContext.set()
    │
    ├─ ACTION → GameMasterService.processAction(ctx)
    │              └─ crea SseEmitter
    │              └─ pipeline completa (RAG → GM → router → validatori → updater → persistenza)
    │
    └─ ASK    → AdvisorService.processAsk(ctx)
                   └─ crea SseEmitter
                   └─ carica chat ask recente
                   └─ Advisor con tool calling
                   └─ persiste scambio ask
                   └─ aggiorna advisorFacts
```
