# Agenti Storia Multi-livello

## Idea originale

Per aiutare il GM a creare storie coinvolgenti, creare due agenti per linee narrative lunghe multi-turno a cui il GM si
può appoggiare.

1. **Long Story** — chiamato solo a init campaign, si legge tutta la lore, la descrizione del personaggio, e crea una
   linea narrativa lunga che sarà il background di tutta la campagna (non nota al giocatore)
2. **Short Story** — ogni X turni, si crea la prossima linea narrativa, che sarà il background multi-turno dei prossimi
   N turni, serve a guidare il GM in una direzione e non andare completamente a caso
3. Determinare quando la linea narrativa si esaurisce e richiamare l'agent per generare la prossima, dandogli in input
   la long story, la short story appena completata, tutti gli scambi utente-GM durante la precedente short story e le
   attuali quest attive
4. Modificare di conseguenza prompt e input al GM in modo che possa tener conto di queste due informazioni

Problema aperto: l'utente potrebbe decidere qualsiasi cosa, non essendone nemmeno a conoscenza potrebbe decidere di
viaggiare e andare altrove. Bisognerebbe gestire questo caso invalidando la short story e creandone un'altra partendo
dagli intenti del giocatore.

---

## Analisi e approccio consigliato

L'idea di fondo è valida — senza direzione narrativa il GM tende a generare episodi scollegati — ma iniettare due
blocchi narrativi lunghi nel system prompt del GM è rischioso:

- **Prompt bloat**: il GM riceve già lore (RAG), character, scene, inventory, quest log, advisor facts, narrative
  summary, recent history. Aggiungere due sezioni narrative lunghe diluisce l'attenzione del modello su ogni sezione.
- **Railroading**: se la Long Story dice "il villain pianifica X" ma il giocatore fa tutt'altro, il GM è in tensione tra
  arco narrativo e reattività al giocatore.
- **Overlap**: la Short Story si sovrappone concettualmente a quest log + advisor facts + narrative summary.

### Approccio alternativo: Plot Hooks + Narrative Pulse

Invece di due agenti che producono prosa narrativa, usare l'infrastruttura esistente con dati strutturati e leggeri.

#### 1. Campaign Arc (sostituto della Long Story)

- Generato all'init della campagna
- NON un blocco narrativo lungo, ma **3-5 plot hooks** concisi, strutturati come **JSON** con localizzazione geografica
- Ogni hook deve contenere almeno: descrizione (1-2 frasi), luogo/regione dove si manifesta, raggio d'influenza
- La localizzazione è fondamentale: se un evento avviene in una regione lontana dal giocatore, il QuestlogUpdater non
  deve proporlo — il giocatore non ne verrebbe mai a conoscenza. Gli hook diventano rilevanti solo quando il giocatore
  si trova nella zona d'influenza o ne sente parlare in modo plausibile
- Esempio:
  ```json
  {
    "hook": "Il culto di Xareth sta infiltrando la gilda mercantile",
    "location": "Valdris",
    "radius": "Valdris e villaggi limitrofi",
    "severity": "high"
  }
  ```
- Diventano **seed per le quest**: il QuestlogUpdater può pescare da questi hooks quando il giocatore si trova nella
  zona d'influenza o quando la narrazione lo rende plausibile

#### 2. Narrative Pulse (sostituto della Short Story)

- Agente leggero (flash-lite ad esempio) che **ogni N turni valuta** lo stato narrativo:
  "la campagna ha direzione o sta vagando?"
- Se sta vagando → genera un **singolo evento catalizzatore** iniettato come advisor fact (infra già esistente)
- Se ha direzione → non fa nulla

#### 3. Gestione del giocatore che devia

- Non è un bug, è una feature — un buon GM reagisce, non forza
- I plot hooks passivi funzionano perché **aspettano il giocatore**: il culto esiste indipendentemente dal fatto che il
  giocatore lo scopra
- Quando il giocatore va in una nuova zona, il RAG recupera lore rilevante e gli hooks si manifestano organicamente

### Approccio alternativo: GM con tool use (pattern AdvisorTools)

Invece di iniettare dati nel prompt (push), dare al GM dei tools per recuperare informazioni on-demand (pull), seguendo
il pattern già collaudato di `AdvisorTools` / `AdvisorService`.

#### Tools ipotizzati

- `getCampaignArc()` — restituisce i plot hooks rilevanti per la posizione attuale del giocatore
- `searchLore(query)` — il GM si cerca la lore di cui ha bisogno (come fa già l'Advisor)
- `getNarrativeDirection()` — restituisce lo stato narrativo attuale (campagna ha direzione o sta vagando?)

#### Pro

- **Zero prompt bloat** — il GM recupera solo ciò che gli serve, quando gli serve
- **Ragionamento più profondo sulla lore** — il GM potrebbe ragionare meglio sulla coerenza narrativa prima di
  generare, riducendo i fallimenti nei lore reviewer e quindi evitando cicli di retry. Il costo token aggiuntivo delle
  tool call potrebbe essere compensato dall'eliminazione di round-trip di rigenerazione
- **Flessibilità** — il GM decide autonomamente quando ha bisogno di contesto aggiuntivo
- **Pattern collaudato** — l'AdvisorAgent con tools funziona già bene nel progetto

#### Contro

- **Temperatura** — il GM usa temp 0.7 (creativo). Il tool use è più affidabile con temperature basse. Rischio che il
  modello non chiami i tools quando servono, o li chiami inutilmente
- **Cambio architetturale** — il GM oggi è single-shot (`ChatClient.prompt().call()`). Diventerebbe un agente
  multi-turn con loop agentico, aumentando la complessità
- **Latenza** — ogni tool call aggiunge un round-trip al modello. Anche se potrebbe ridurre i retry, la latenza
  percepita per turno aumenterebbe

#### Variante ibrida: pre-filtraggio nell'Orchestrator

Via di mezzo che mantiene il GM single-shot: l'`OrchestratorService` filtra i plot hooks per prossimità alla
`GameScene.currentLocation` **prima** di chiamare il GM, e inietta solo quelli rilevanti come sezione piccola (analogo
a come già funziona il RAG per la lore e gli `advisorFacts`). Il GM resta veloce e prevedibile, ma riceve direzione
narrativa localizzata senza doverla cercare.

---

## Confronto approcci — da analizzare per scegliere

| Approccio                          | Pro                                                  | Contro                                                           |
|------------------------------------|------------------------------------------------------|------------------------------------------------------------------|
| Long Story + Short Story           | Narrativa ricca e coerente                           | Prompt bloat, railroading, overlap con quest/advisor/summary     |
| Plot Hooks + Narrative Pulse       | Leggero, usa infra esistente, rispetta player agency  | Narrazione meno "scripted", richiede che il GM tessa bene i fili |
| GM con tool use                    | Zero bloat, ragionamento profondo, meno retry         | Temp 0.7 inaffidabile per tools, latenza, cambio architetturale  |
| Ibrido (pre-filtraggio Orchestrator) | GM resta single-shot, hooks localizzati, semplicità | Meno flessibile del tool use, logica di filtraggio da definire   |
