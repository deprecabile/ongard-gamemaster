# Campaign Setup Agents

Questo è determinante per andare live, perché la gente non ha fantasia, e scrivere un buon prompt iniziale è
fondamentale per trovarsi in una storia coinvolgente.

## Panoramica

Due componenti nel frontend della pagina di creazione campagna:

- **Bottone "Aiutami a creare"** (nel form) — apre modale con 3 archetipi random, il giocatore sceglie e il sistema
  genera razza + nome + character prompt + scena iniziale
- **Chat Advisor** (icona chat in basso a sinistra) — pannello chat che conosce la lore e vede lo stato corrente del
  form. L'utente può chiedere info sul mondo, fare domande contestualizzate al personaggio/scena che sta creando, e
  chiedere proposte di modifiche mirate ai prompt

I due output della generazione finiscono nelle textarea del form. L'utente può modificarli liberamente prima di creare
la campagna.

## UI

```
┌─────────────────────────────────────────────────────────────────────┐
│ Pagina creazione campagna                                           │
│                                                                     │
│  ┌────────────────────────────────────────────────────────────┐      │
│  │  [Select razza]  [Input nome]                              │      │
│  │                                                            │      │
│  │  [Aiutami a creare]                                        │      │
│  │                                                            │      │
│  │  "Crea il tuo personaggio"   [Rigenera personaggio 🔄]     │      │
│  │  [textarea characterPrompt]                                │      │
│  │                                                            │      │
│  │  "Contesto iniziale"         [Rigenera scena 🔄]           │      │
│  │  [MDEditor startingSituation]                              │      │
│  │                                                            │      │
│  │  [Crea campagna]                                           │      │
│  └────────────────────────────────────────────────────────────┘      │
│                                                                     │
│  ┌──┐                                                               │
│  │💬│  ← icona chat (basso sx), apre pannello advisor               │
│  └──┘                                                               │
└─────────────────────────────────────────────────────────────────────┘
```

### Modale selezione archetipo

Cliccando "Aiutami a creare" si apre una modale (`<dialog>`) con 3 archetipi pescati random dal pool:

```
┌─────────────────────────────────────────────────────────────┐
│                    Scegli il tuo spunto                      │
│                                                             │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐      │
│  │ Pirata nel    │ │ Investigatore │ │ Naturalista   │      │
│  │ mare tropicale│ │ nei sobborghi │ │ in spedizione │      │
│  │               │ │ di una grande │ │ in un luogo   │      │
│  │               │ │ città         │ │ remoto        │      │
│  │  [Scegli]     │ │  [Scegli]     │ │  [Scegli]     │      │
│  └───────────────┘ └───────────────┘ └───────────────┘      │
│                                                             │
│                   [🔄 Altre opzioni]                         │
└─────────────────────────────────────────────────────────────┘
```

- La modale mostra solo `code` + `description` (player-facing). Il `theme` non viene mostrato — contiene direttive
  interne per il generatore
- **"Altre opzioni"** richiama `GET /api/campaign/setup/archetypes` per ottenere altri 3 dal backend
- Dopo la scelta, la modale si chiude e parte la generazione in modalità FULL

### Pannello chat advisor

L'icona chat in basso a sinistra apre un pannello fixed-position (380×520px) con la chat:

```
┌──────────────────────────────┐
│ [Cancella] Consigliere  [✕]  │
│                              │
│  [history chat — scrollable] │
│  (messaggi markdown advisor) │
│  (messaggi plain text user)  │
│  (indicatore "sta scrivendo")│
│                              │
│  [textarea input]   [Invia]  │
└──────────────────────────────┘
```

- Solo il pulsante "Invia", nessun pulsante "Genera" — la generazione si attiva esclusivamente dal form principale
- **Enter** invia il messaggio, **Shift+Enter** = a capo
- Le risposte dell'advisor sono renderizzate in Markdown
- **"Cancella conversazione"** svuota la chat locale e chiama `DELETE /api/campaign/setup/session/history`
  (cancella solo la history, mantiene i dati form nella sessione)

### Pulsanti "Rigenera"

- **"Rigenera personaggio"** e **"Rigenera scena"** compaiono solo dopo una generazione avvenuta (`hasGenerated`)
- Usano lo **stesso archetipo** scelto dall'utente nella modale — preservano la scelta
- Rigenerano solo il pezzo corrispondente (mode CHARACTER o SCENE)
- Per cambiare archetipo, l'utente clicca di nuovo "Aiutami a creare" e sceglie un nuovo spunto → rigenera tutto

## Flussi

### Flusso "Aiutami a creare" (generazione FULL)

```
1. Click "Aiutami a creare"
2. GET /api/campaign/setup/archetypes → 3 archetipi random (code + description)
3. Modale mostra le 3 card, utente sceglie → modale si chiude
4. POST /api/sse/campaign/setup/generate { archetypeCode, mode: FULL }
   SSE stream:
     a. Se esiste sessione advisor con history → SummaryAgent(history) → summary
        Se no → summary = null
     b. NameRaceSetupAgent(archetype, summary?) → { raceCode, characterName }
        → evento NAME_PICKED con { raceCode, characterName }
     c. GeneratorAgent(archetype, summary?, name, race, mode=CHARACTER) → characterPrompt
        → evento CHARACTER_GENERATED con { characterPrompt }
     d. GeneratorAgent(archetype, summary?, characterPrompt, name, race, mode=SCENE) → startingSituation
     → evento COMPLETED con { characterPrompt, startingSituation, raceCode, characterName }
5. Frontend popola select razza, input nome e textarea
6. Backend salva risultati nella sessione Redis
```

### Flusso "Rigenera personaggio"

```
POST /api/sse/campaign/setup/generate { archetypeCode, mode: CHARACTER, raceCode, characterName }
  → SSE stream
  → Usa lo stesso archetypeCode salvato
  → Esegue solo step (a) + (c) — nessun name/race picking
  → Evento COMPLETED con { characterPrompt }
  → Frontend aggiorna solo la textarea character prompt
```

### Flusso "Rigenera scena"

```
POST /api/sse/campaign/setup/generate { archetypeCode, mode: SCENE, characterPrompt, raceCode, characterName }
  → SSE stream
  → Usa lo stesso archetypeCode salvato
  → Riceve il characterPrompt corrente dalla textarea (potrebbe essere stato editato dall'utente)
  → Esegue solo step (a) + (d)
  → Evento COMPLETED con { startingSituation }
  → Frontend aggiorna solo la textarea scena iniziale
```

### Flusso "Invia" (chat advisor)

```
POST /api/sse/campaign/setup/interaction { message, characterPrompt?, startingSituation?, raceCode?, characterName? }
  → SSE stream:
    → STARTED → THINKING (heartbeat 10s) → COMPLETED con { advisorOutput }
  → CampaignSetupAdvisorAgent (Gemini, temp 0.35, tool: searchLore)
  → L'advisor vede nel system prompt: nome, razza, characterPrompt, startingSituation correnti
  → Mantiene chat history in sessione Redis (aggiunge user + advisor message)
```

### Flusso restore (mount pagina / refresh)

```
1. Al mount di InitCampaign → GET /api/campaign/setup/session/status
2. Se active: false → form vuoto, niente loading
3. Se active: true → lancio parallelo:
   a. GET /api/campaign/setup/session (bloccante con overlay blur + spinner)
      → popola razza, nome, characterPrompt, startingSituation, archetypeCode
      → se archetypeCode presente → hasGenerated = true (mostra bottoni rigenera)
   b. GET /api/campaign/setup/session/history (non bloccante, nessun loading)
      → popola chat advisor silenziosamente
4. Se sessione scaduta tra status e GET → overlay rimosso, form vuoto, nessun errore
```

### Flusso persistenza campi form

```
- characterPrompt e startingSituation: debounce 5s + flush on blur + flush on beforeunload (keepalive)
- characterName: debounce 3s + flush on blur + flush on beforeunload
- raceCode: PUT immediata su onChange (un click = valore definitivo)
- archetypeCode: salvato dal backend dopo generazione (nessun endpoint PUT dedicato)
- Tutte fire-and-forget, nessun feedback UI
```

### Cleanup alla creazione campagna

```
- Backend: CampaignInitService.initCampaign() chiama setupSessionService.delete(userHash)
  → cancella l'intera sessione (history + dati form) automaticamente
- Frontend: resetta stato locale (archetypeCode, hasGenerated, chat messages)
  → nessuna chiamata DELETE esplicita, il backend ha già pulito
```

## Componenti backend

### 1. NameRaceSetupAgent (scelta razza e nome)

- **Modello**: Gemini 2.5-flash, temperatura 0.7, output JSON vincolato da schema (`NAME_RACE_SETUP`)
- **Input**: descrizione archetipo + summary preferenze (opzionale)
- **Output**: `{ raceCode, characterName }` — JSON strutturato
- **Tool**: `searchLore(query)` — cerca convenzioni culturali/naming della razza scelta
- **Comportamento**:
    - Se le preferenze indicano razza e/o nome, li usa
    - Altrimenti sceglie razza random (variando) e genera nome culturalmente coerente
    - Ha il mapping completo `raceCode → nome razza` iniettato nel system prompt (`{{raceMapping}}`)
    - Fallback in caso di errore: razza umana (UMN)
- **Invocato solo in mode FULL** — in CHARACTER e SCENE razza e nome arrivano dal form

### 2. CampaignSetupAdvisorAgent (chat advisor)

- **Modello**: Gemini 3-flash-preview, temperatura 0.35 — abbastanza bassa da rispettare la lore quando la trova,
  abbastanza alta da inventare in modo naturale dove la lore non copre
- **Input**: messaggio utente + history conversazione. Il system prompt contiene lo stato corrente del form come
  contesto (nome, razza, characterPrompt, startingSituation) via placeholder compilati dalla factory
- **History**: intera chat della sessione, ricostruita come coppie user/assistant nel `ChatClient`
- **Tool**: `searchLore(query)` — RAG search sulla knowledge base del mondo
- **Comportamento**: risponde a domande sulla lore, dà risposte contestualizzate ai prompt correnti dell'utente
  (es. "ha senso che il mio personaggio sia a Dograven?"), e se richiesto propone modifiche mirate ai testi.
  Regola fondamentale sulla lore:
    - Prima di rispondere su qualsiasi aspetto del mondo, cerca nella lore via searchLore
  - Ciò che trova nella lore è CANONICO: non contraddirlo, non inventare alternative
  - Ciò che la lore NON menziona può inventarlo liberamente, purché coerente con il contesto trovato

### 3. CampaignSetupSummaryAgent (summarizer)

- **Modello**: Gemini 2.5-flash, temperatura 0 — deterministico
- **Input**: intera chat history advisor, formattata come testo `GIOCATORE: ... / CONSIGLIERE: ...` in un singolo user
  message (il summarizer vede la conversazione come dato da analizzare, non come dialogo a cui partecipa)
- **Output**: summary testuale per categoria (razza, personalità, location, stile gioco, vincoli, equipaggiamento,
  tono).
  Solo categorie con informazioni reali. Max 150 parole. Stringa vuota se nessuna preferenza rilevante
- **Nessun tool** — lavora solo sulla chat che ha già
- **Scopo downstream**: alimenta NameRaceSetupAgent e GeneratorAgent. Il LLM sa chi consuma l'output
- Se la chat è vuota o non c'è sessione, viene saltato (summary = null)

### 4. CampaignSetupGeneratorAgent (generatore character/scene)

- **Modello**: Gemini 3-flash-preview, temperatura 0.7
- **Tool**: `searchLore(query)` — per verificare coerenza geografica, culturale, ecc. mentre genera
- **Due modalità** (stesso codice Java, system prompt diverso):
    - `CHARACTER`: genera il character prompt (biografia in prima persona, 150-300 parole). Riceve nome + razza +
      archetipo + inventoryHints + summary. Non genera location né inventario
    - `SCENE`: genera la scena iniziale (200-400 parole). Riceve tutto: theme, character prompt, nome + razza,
      inventoryHints, summary

#### Architettura system/user split

I dati dinamici (archetipo, inventoryHints, summary, theme, characterPrompt, nome, razza) sono nel **user message**,
non nel system prompt. Il system prompt contiene solo istruzioni statiche + `{{language}}`. Questo migliora il
comportamento del modello: il system prompt ha più "autorità" sulle regole, il user message è dove il modello si
aspetta di trovare i dati da elaborare.

#### Vincoli per la scena iniziale (nel system prompt — checklist 7 requisiti)

La scena iniziale DEVE:

1. Menzionare oggetti fisici che il personaggio ha con sé (altrimenti l'inventario partirà vuoto)
2. **Descrivere abbigliamento esplicitamente** — vestiti ed equipaggiamento coerenti con professione, status sociale e
   clima del bioma. Critico: l'inventory initializer a valle estrae oggetti dalla scena, senza vestiti descritti il
   personaggio risulta nudo
3. Indicare una certa quantità iniziale di Denanti coerente con il setup scelto
4. Specificare una location concreta con latitudine e longitudine
5. Includere un riferimento temporale (momento del giorno, stagione)
6. **Descrivere il bioma con fantasia e dettaglio** — esplorare biomi diversi ad ogni generazione, non fossilizzarsi
   su boschi temperati
7. Terminare con una situazione aperta e ricca di ganci narrativi

#### Note sulla coerenza geografica

Il generatore ha il tool searchLore per cercarsi le coordinate dei regni. I regni noti hanno indicazioni su
latitudine/longitudine della loro estensione, quindi il generatore può dedurre il bioma coerente. Esempio:

- Ducato di Dograven (10°S – 26°S) → foresta pluviale, savana. Mai fiordi o steppa
- Impero di Voth (56°N – 16°S) → qualsiasi bioma, ma una foresta fredda notturna sarà nella parte nord

Non esiste un'enciclopedia completa di ogni borgo — il generatore può inventare villaggi e luoghi minori purché siano
geograficamente coerenti con il regno in cui li colloca.

## Archetipi

File JSON multilingua in `resources/game_data/ondgard/{lang}/archetypes.json` (un file per lingua: `it`, `en`, `es`).
Caricati a boot da `ArchetypeService` con `PathMatchingResourcePatternResolver`, stessa meccanica di
`SceneLoreProvider`.
Salvati in `ConcurrentHashMap<String, List<CampaignArchetype>>` (chiave = codice lingua).

Ogni archetipo è uno spunto tematico che il generatore usa come punto di partenza, poi lo adatta al summary della
chat advisor (se presente).

Struttura:

```json
{
  "code": "SHIPWRECK_AMNESIA",
  "description": "Sopravvissuto a un evento catastrofico con amnesia totale.",
  "theme": "L'evento catastrofico non è specificato... Crea un risveglio disorientante...",
  "inventoryHints": [
    "oggetto personale enigmatico",
    "vestiti rovinati dall'acqua salata",
    "pochi Denanti"
  ]
}
```

Campi:

- `code` — identificatore univoco (UPPER_SNAKE), identico tra le lingue
- `description` — player-facing, mostrata nella modale + usata dall'agent character generator
- `theme` — direttive creative solo per il scene generator (ganci narrativi, tono, vincoli). NON mostrata al giocatore
- `inventoryHints` — oggetti iniziali nella lingua del file, usati da character e scene generator

L'endpoint `GET /api/campaign/setup/archetypes` restituisce `CampaignArchetypeResponse` (solo `code` + `description`),
non l'intero archetipo. Il backend pesca 3 random dal pool nella lingua dell'utente.

## Sessione setup (Redis)

### Modello

```
CampaignSetupSession
  userHash          -- chiave Redis (un utente = al massimo una sessione)
  history           -- Collection<ChatEntry> (chat con l'advisor)
  raceCode          -- razza selezionata nel form (nullable)
  characterName     -- nome personaggio (nullable)
  characterPrompt   -- contenuto textarea description (nullable)
  startingSituation -- contenuto textarea context (nullable)
  archetypeCode     -- archetipo scelto nella modale (nullable)
```

- Key Redis: `ondgard:setup-session:{userHash}`
- TTL: 20 minuti (configurabile via `ondgard.setup.session.ttl`), refreshato ad ogni accesso
- Nessun `sessionId` — la chiave è lo `userHash` dall'header autenticato. L'utente può accedere solo alla propria
  sessione, nessuna validazione di ownership necessaria
- Nessun `AdvisorState` — la sessione è un semplice contenitore di dati, lo stato è implicito nel contenuto
- Serializzazione con Jackson (`RedisTemplate`)
- Se scade, l'utente ricomincia da zero (form vuoto). Se inizia a chattare, `getOrCreate` crea una nuova sessione

### Aggiornamento campi

- I dati del form vengono salvati con PUT singole (debounced dal frontend)
- I risultati della generazione vengono salvati dal `CampaignSetupGenerateService` dopo ogni step completato
- La history cresce ad ogni scambio con l'advisor

### Cleanup

- Alla creazione della campagna (`CampaignInitService`), il backend cancella automaticamente l'intera sessione
- Endpoint `DELETE /session` disponibile anche per cancellazione esplicita

## Modelli LLM

| Bean                  | Modello                | Temp | Output | Scopo                                |
|-----------------------|------------------------|------|--------|--------------------------------------|
| `setupAdvisorModel`   | gemini-3-flash-preview | 0.35 | testo  | Advisor: lore-grounded ma fluido     |
| `setupSummaryModel`   | gemini-2.5-flash       | 0.0  | testo  | Summarizer: deterministico           |
| `setupGeneratorModel` | gemini-3-flash-preview | 0.7  | testo  | Generatore character/scene: creativo |
| `nameRaceSetupModel`  | gemini-2.5-flash       | 0.7  | JSON   | Scelta razza/nome: schema vincolato  |

Tutti wrappati da `TokenAwareChatModelDecorator` per il tracking token. Configurabili via
`ondgard.ai.gemini.setup-*-model` in `application.yaml`.

## Factory

`CampaignSetupAgentsFactory` (`@Component`) carica i template dei prompt a `@PostConstruct` e costruisce gli agenti
con la lingua corretta e i placeholder compilati:

- `buildAdvisorAgent(language, characterPrompt, startingSituation, raceCode, characterName)` — compila tutti i
  placeholder di contesto nel template advisor
- `buildSummaryAgent(language)` — compila solo `{{language}}`
- `buildGeneratorAgent(language)` — compila `{{language}}` in entrambi i template (character + scene). Crea tools
  con `loreLangCode` risolto
- `buildNameRaceSetupAgent(language)` — compila `{{raceMapping}}` (tutte le razze disponibili) + `{{language}}`

Gli agenti non sono Spring beans — sono oggetti plain Java stateless, creati per-request dalla factory.

## API

### CampaignSetupSseController

`@RestController @RequestMapping("/api/sse/campaign/setup")`

```
POST /generate  (produces: text/event-stream)
  Body: { mode?, archetypeCode, characterPrompt?, raceCode?, characterName? }
  - mode: FULL (default) | CHARACTER | SCENE
  - characterPrompt: obbligatorio se mode=SCENE
  - raceCode, characterName: passati dal form per CHARACTER e SCENE (in FULL li genera NameRaceSetupAgent)
  Pre-checks: RAG readiness, token limit
  Response: SSE stream
    → setup_generate_started        { ok }
    → setup_generate_progress       { tms, code }  (heartbeat 10s: SETUP_SUMMARIZING, SETUP_PICKING_NAME,
                                                     SETUP_GENERATING_CHARACTER, SETUP_GENERATING_SCENE)
    → setup_generate_name_picked    { raceCode, characterName }  (solo FULL)
    → setup_generate_character_generated  { characterPrompt }    (solo FULL, per aggiornamento immediato UI)
    → setup_generate_completed      { tms, characterPrompt, startingSituation, raceCode?, characterName? }
    → setup_generate_error          { tms, errorCode, description }

POST /interaction  (produces: text/event-stream)
  Body: { message, characterPrompt?, startingSituation?, raceCode?, characterName? }
  Pre-checks: RAG readiness, token limit
  Response: SSE stream
    → setup_interaction_started     { ok }
    → setup_interaction_thinking    {}  (heartbeat 10s durante elaborazione)
    → setup_interaction_completed   { tms, advisorOutput }
    → setup_interaction_error       { tms, errorCode, description }
```

### CampaignSetupController

`@RestController @RequestMapping("/api/campaign/setup")`

```
GET /archetypes
  Response: List<CampaignArchetypeResponse> — 3 archetipi random (code + description) nella lingua dell'utente

GET /session/status
  Response: { active: boolean } — sempre 200, mai 404

GET /session
  Response: { raceCode, characterName, characterPrompt, startingSituation, archetypeCode } — 204 se non esiste

GET /session/history
  Response: Collection<ChatEntry> — lista vuota se sessione non esiste

PUT /session/character-prompt      Body: { value }  → 204
PUT /session/starting-situation    Body: { value }  → 204
PUT /session/race-code             Body: { value }  → 204
PUT /session/character-name        Body: { value }  → 204
  Tutti creano la sessione se non esiste, resettano il TTL

DELETE /session/history  → 204 (cancella solo history, mantiene dati form)
DELETE /session          → 204 (cancella tutto — history + dati form)
```

Nessun endpoint PUT per `archetypeCode` — viene salvato esclusivamente dal `CampaignSetupGenerateService` al momento
della generazione.

## Scelte architetturali chiave

### Sessione senza sessionId

La sessione è identificata dallo `userHash` (estratto dal JWT via `GameUserHeader`), non da un `sessionId` separato.
Conseguenze:

- Un utente ha al massimo una sessione setup alla volta
- Nessun sessionId da gestire lato frontend — ogni request manda automaticamente il JWT, il backend identifica l'utente
- Nessuna validazione di ownership — la chiave Redis È l'identità dell'utente

### Generazione razza/nome nel flusso FULL

L'analisi originale prevedeva che razza e nome venissero dal form utente. L'implementazione aggiunge
`NameRaceSetupAgent` che li genera automaticamente in mode FULL (coerentemente con l'archetipo e le preferenze), con
un evento SSE `NAME_PICKED` dedicato che aggiorna il form in tempo reale. In mode CHARACTER e SCENE, razza e nome
arrivano dal form (già scelti dall'utente o generati in precedenza).

### Persistenza dati form in Redis

La sessione Redis non contiene solo la chat history ma anche tutti i dati del form (razza, nome, prompt, scena,
archetipo). Questo permette il restore completo al refresh/navigazione. I campi vengono salvati con debounce dal
frontend (5s per textarea, 3s per nome, immediato per razza) + flush on blur e on beforeunload.

### System/user message split

Nel GeneratorAgent i dati dinamici (archetipo, hints, summary, ecc.) vanno nel user message, le istruzioni statiche
nel system prompt. Questo migliora il comportamento del modello: il system prompt ha più "autorità" sulle regole.

### Eventi SSE intermedi (NAME_PICKED, CHARACTER_GENERATED)

In mode FULL la generazione è lunga (4 step LLM). Gli eventi intermedi permettono al frontend di aggiornare
progressivamente il form: prima appaiono razza e nome, poi il character prompt, infine la scena. L'utente vede i
risultati arrivare in tempo reale invece di aspettare tutto alla fine.

## Note

- L'utente può sempre modificare gli output nelle textarea prima di confermare — se il generatore dice che sei un umano
  e tu vuoi essere un orco, te lo cambi
- Multilingua: l'advisor e il generatore rispondono nella lingua dell'utente (`Accept-Language`), stessa meccanica del
  pipeline di gioco esistente
- "Rigenera scena" passa il characterPrompt corrente dalla textarea, così se l'utente lo ha editato la scena sarà
  coerente con la versione editata
- Tutti gli endpoint SSE fanno pre-check su RAG readiness e token limit prima di avviare la generazione
- Heartbeat attivo durante generazioni lunghe (ogni 10s) per mantenere la connessione SSE aperta
