# Google Login — Analisi

## Flusso di navigazione

```
Pagina Login/Register
  │
  ├─ Click "Accedi con Google" → Google SDK → credential (ID token)
  │
  ├─ POST /api/auth/google {credential}
  │    ├─ Utente esistente (enabled)    → 200 + tokens → loginWithTokens() → dashboard
  │    ├─ Utente esistente (disabled)   → attiva account + crea game user → 200 + tokens → dashboard
  │    └─ Utente non trovato            → 404 {email, suggestedUsername}
  │                                          │
  │                                          ▼
  │                                     Modale scelta username
  │                                     (suggerito pre-compilato, check disponibilità con debounce)
  │                                          │
  │                                          ▼
  │                                     POST /api/auth/google {credential, username}
  │                                     → crea utente (password random) + game user
  │                                     → 200 + tokens → loginWithTokens() → dashboard
  │
  └─ Errori:
       ├─ Token invalido/scaduto → 401 → messaggio "riprova"
       └─ Username occupato      → 400 → errore inline nel campo
```

## Scelte architetturali

### Endpoint unico a due step

Un solo `POST /api/auth/google` gestisce tutto. Senza `username` è una probe (login o 404); con `username` è una
registrazione. Evita endpoint separati e mantiene il flusso transazionale.

### Nessuna modifica allo schema

Gli utenti Google hanno una password random hashata (Argon2) nel DB — righe identiche agli utenti email.

### Account linking automatico per email

Se un utente si registra via email e poi usa Google con la stessa email, il login funziona: Google certifica l'email
verificata, il backend trova l'utente per email. L'utente può usare entrambi i metodi.

### Attivazione automatica utenti non confermati

Se un utente si era registrato via email senza confermare (enabled=false) e poi usa Google con la stessa email, viene
attivato automaticamente (Google garantisce email verificata). Viene creato anche il game user (che normalmente avviene
alla conferma email).

### Username dal JWT, mai dall'input

`loginWithTokens()` è il punto d'ingresso unico per tutti i login (email e Google). Lo username viene sempre estratto
dal JWT decodificato, mai dall'input utente.

### PasswordService estratto

La logica di hashing (Argon2 + salt + pepper) è stata estratta in `PasswordService`, usato sia dal flusso email che da
quello Google. `hashRandom()` genera una password casuale senza input utente.

### GoogleAuthResult sealed interface

Il service ritorna un `sealed interface` con due implementazioni (`LoginSuccess` e `RegistrationRequired`), gestito nel
controller con pattern matching per decidere lo status HTTP (200 vs 404).

### Rate limiting dedicato nel gateway

Rotta `auth-google-rate-limited`: 1 req/sec, burst 5, key per IP. Stessi parametri della registrazione email.

### GoogleOAuthProvider sul PublicLayout

Il provider wrappa solo le pagine pubbliche (login/register), non tutta l'app. Client ID iniettato via
`__GOOGLE_CLIENT_ID__` in `vite.config.ts`.

### Due funzioni API separate nel frontend

`googleLogin(credential)` ritorna `LoginResponse | GoogleRegistrationRequired` — il chiamante distingue con
`'accessToken' in response`. `googleRegister(credential, username)` ritorna direttamente `LoginResponse`. Nessun
wrapper, il codice HTTP guida naturalmente il tipo di risposta.
