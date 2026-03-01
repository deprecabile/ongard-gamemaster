# Ongard Gamemaster — Frontend

Frontend React per il progetto Ongard Gamemaster, un Game Master guidato da intelligenza artificiale.

## Tech Stack

| Tecnologia            | Versione | Ruolo                     |
|-----------------------|----------|---------------------------|
| React                 | 19       | UI library                |
| TypeScript            | 5.9      | Type safety (strict mode) |
| Vite                  | 7        | Dev server e bundler      |
| SCSS (Dart Sass)      | 1.97     | Stili con preprocessore   |
| Vitest                | 4        | Unit testing              |
| React Testing Library | 16       | Test sui componenti       |
| ESLint                | 9        | Linting (flat config)     |
| Prettier              | 3.8      | Formattazione codice      |

## Comandi

```bash
npm run dev          # Dev server con HMR
npm run build        # Type-check (tsc -b) + build produzione
npm run lint         # ESLint
npm run test         # Vitest (singola esecuzione)
npm run test:watch   # Vitest in watch mode
npm run format       # Prettier su src/**/*.{ts,tsx,scss}
npm run preview      # Anteprima build di produzione
```

## Struttura del progetto

```
frontend/
├── public/                    # Asset statici
├── src/
│   ├── main.tsx               # Entry point React
│   ├── App.tsx                # Componente root
│   ├── assets/                # Asset importati nel bundle
│   └── styles/
│       ├── _mixins.scss       # Breakpoint responsive + flex helpers
│       ├── _variables.scss    # Variabili SCSS di progetto
│       └── index.scss         # Stili globali, reset, design tokens
├── tests/                     # Test (esterni a src/)
│   ├── setup.ts               # Setup Vitest (jest-dom matchers)
│   └── App.test.tsx           # Test placeholder
├── tsconfig.json              # Root — project references
├── tsconfig.app.json          # Config per src/
├── tsconfig.node.json         # Config per vite.config.ts, vitest.config.ts
├── tsconfig.test.json         # Config per tests/
├── vite.config.ts             # Configurazione Vite
├── vitest.config.ts           # Configurazione Vitest
├── eslint.config.js           # ESLint flat config
└── .prettierrc                # Regole Prettier
```

## Scelte implementative

### TypeScript — strict mode con project references

Il progetto usa **3 tsconfig separati** orchestrati da un root `tsconfig.json` con project references:

- **`tsconfig.app.json`** — codice applicativo (`src/`), con `jsx: react-jsx`, path alias `@/*`, e tipi `vite/client`
- **`tsconfig.node.json`** — file di configurazione build (`vite.config.ts`, `vitest.config.ts`), con tipi `node`
- **`tsconfig.test.json`** — test (`tests/`), con `vitest/globals` per avere `describe`/`it`/`expect` senza import

Tutti condividono: `target: ES2024`, `moduleResolution: bundler`, `strict: true`, `noEmit: true`,
`verbatimModuleSyntax: true`.

La scelta di `noEmit: true` (invece di `composite: true`) evita la generazione di file `.d.ts` nelle cartelle sorgente,
delegando interamente la compilazione a Vite.

### Path alias `@/`

L'alias `@/` punta a `./src/` ed è configurato in tre posti per coerenza:

- **TypeScript** (`paths` in tsconfig.app.json e tsconfig.test.json)
- **Vite** (`resolve.alias` in vite.config.ts)
- **Vitest** (`resolve.alias` in vitest.config.ts)

Gli import relativi ascendenti (`../`) sono vietati da ESLint (`no-restricted-imports`), forzando l'uso dell'alias.

### SCSS

Il preprocessore SCSS è integrato in Vite con auto-import globale dei mixin:

```ts
// vite.config.ts
scss: { additionalData: `@use "@/styles/mixins" as *;\n` }
```

Ogni file `.scss` ha automaticamente accesso ai mixin senza bisogno di `@use`. La struttura degli stili:

- **`_mixins.scss`** — breakpoint responsive mobile-first (`sm`/`md`/`lg`/`xl`/`xxl`) e flex helpers (`flex-center`,
  `flex-col`, `flex-between`)
- **`_variables.scss`** — variabili SCSS di progetto (placeholder)
- **`index.scss`** — CSS custom properties (design tokens), reset globale, tema dark/light con `prefers-color-scheme`

### ESLint — flat config con type-checking

Configurazione ESLint 9 flat config con regole strict:

- **`typescript-eslint`** — `strictTypeChecked` + `stylisticTypeChecked` (type-aware linting)
- **`eslint-plugin-prettier`** — formattazione come regola di lint
- **`eslint-plugin-jsx-a11y`** — accessibilità in modalita strict
- **`eslint-plugin-check-file`** — naming convention forzate:
    - File `.tsx` → `PASCAL_CASE` (componenti)
    - File `.ts` → `camelCase` (utility)
    - Cartelle → `kebab-case`
- **`eslint-plugin-simple-import-sort`** — ordine import automatico:
    1. React e librerie esterne
    2. Import di progetto (`@/`)
    3. File di stile (`.scss`) per ultimi

### Prettier

```json
{
  "semi": true,
  "tabWidth": 2,
  "printWidth": 100,
  "singleQuote": true,
  "trailingComma": "all",
  "jsxSingleQuote": true,
  "endOfLine": "auto"
}
```

Integrato in ESLint tramite `eslint-plugin-prettier` + `eslint-config-prettier`, così le violazioni di formattazione
sono errori di lint risolvibili con `--fix`.

### Test — Vitest + React Testing Library

I test risiedono in `tests/` (esterna a `src/`) e usano:

- **Vitest** con `globals: true` — `describe`, `it`, `expect` disponibili senza import
- **jsdom** come environment per simulare il DOM
- **React Testing Library** per query semantiche sui componenti
- **`@testing-library/jest-dom`** per matcher aggiuntivi (`toBeInTheDocument`, `toHaveTextContent`, ecc.)

Il file `tests/setup.ts` importa i matcher jest-dom e viene eseguito prima di ogni suite.

```bash
npm run test         # Esecuzione singola
npm run test:watch   # Watch mode durante lo sviluppo
```

### Proxy API

Il dev server Vite fa proxy delle chiamate `/api` verso il gateway backend:

```ts
server: {
  proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } }
}
```

In sviluppo locale, avviare il backend (o almeno il gateway) sulla porta 8080.

## Getting started

```bash
# Installare le dipendenze
npm install

# Avviare il dev server
npm run dev

# In un altro terminale, verificare che tutto funzioni
npm run lint && npm run test && npm run build
```
