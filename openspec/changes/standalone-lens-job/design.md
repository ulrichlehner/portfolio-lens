# Design

## Context

See proposal.md for the motivation. Facts that shape the approach, all checked against the sources:

- Fork: `github.com/ulrichlehner/portfolio`, branch `release/fork`, 67
  commits ahead of `upstream/master`, all by the user. The core
  (`name.abuchen.portfolio/src/name/abuchen/portfolio/snapshot/lookthrough`, 32 files) depends on PP
  only in five places: `Security` (in `LookThroughEngine`, `FundOverlap`, `SecuritiesValuation`,
  `SecuritiesValuationAdapter`, `HeldSecurities`), `Client`, `ClientSnapshot` and `Transaction`
  (the two adapters), and `Money`, `MutableMoney`, `ExchangeRateProviderFactory` and related
  currency classes (valuation only). The providers use Apache HttpClient 5 via `WebAccess` and
  `OnlineHelper`; the credentials use Equinox secure storage. Gson is used by the cache codec and
  the providers.
- The fork's UI module holds SWT-free logic that the page needs: `LookThroughSession` (664 lines,
  orchestration of refresh, stores and enrichment), `LookThroughAnalysis`, `LookThroughGrouping`,
  `LookThroughDataStatus`, `LookThroughStatusModel`, `LookThroughOverlapModel`,
  `LookThroughViewPresenter`, `LookThroughBanner`. `LookThroughGrouping` reads PP's
  `TaxonomyTemplate` (regions, market classification, GICS). `LookThroughTreeMap` uses the
  de.engehausen treemap library (`SquarifiedLayout`). English texts live in PP's `Messages`.
- PP REST v1 (tag `rest-api-v1.0.0`): binds 127.0.0.1, rejects requests whose `Host` header is not
  a loopback literal or `localhost` and requests with an `Origin` header; it does not check the
  remote address. `GET /v1/version` needs no token. `GET /v1/files/{file}/holdings` returns
  `date`, `reportingCurrency`, `totalAssets` and items with `type` (open set: `instrument`,
  `cash-account`), `uuid`, `name`, `shares`, `price {value, currency, date}`, `valuation`,
  `localValuation` (only when currencies differ) and `weight`. Amounts are JSON decimal numbers.
  Zero positions are omitted. A currency pair without a rate converts 1:1 silently.
  `GET /v1/files/{file}/instruments` returns `uuid`, `name`, `currencyCode` (nullable), optional
  `isin`, `wkn`, `tickerSymbol`, `note`, `attributes`.
- The fork's request budget (`CompositionRequestBudget`, 20000 units per 24 hours in
  `LookThroughSession`), the refresh cadence and the "no composition" probe memory are in memory
  only. In the fork this was fine because PP ran for hours; a job that lives for seconds would
  reset them on every run.

## Goals / Non-Goals

**Goals:**

- Keep the ported core almost unchanged so its tests carry over with mechanical edits only.
- One run is a pure function of PP data, the cache and the state file; nothing lives between
  runs except files.
- Every run is safe to start every 5 minutes: it is cheap when nothing changed and never exceeds
  the provider budget.

**Non-Goals:**

- No long-running process, no scheduler inside Lens, no HTTP server.
- No reuse of PP code outside the user's own fork work, no PP resources.
- No change to the calculation semantics of the specs beyond what the job model requires.

## Decisions

### D1. Single Maven module, packages by concern

Root package `io.portfoliolens` (changeable before the first release) with subpackages `core` (ported lookthrough), `provider`
(Eulerpool, GLEIF, provider interface, credentials), `source` (PP REST), `job` (CLI, config,
state, run), `page` (page model, SVG layout, HTML writer). A single module keeps the build trivial;
there is no second consumer that would justify a split. Alternative considered: core and app
modules, rejected until a second consumer exists. The ported classes keep their names, so a diff
against the fork stays readable.

### D2. Replace `Security` by a Lens instrument record

`HeldInstrument(uuid, name, currencyCode, identifiers)` replaces `Security` wherever the core uses
it as a map key or label. Identity is the PP `uuid`. `identifiers` maps `isin`, `wkn` and
`tickerSymbol` from `/instruments` into the existing `CompositionInstrument` identifier keys. The
engine, `FundOverlap` and `SecuritiesValuation` change only their type parameter.

### D3. Valuation from REST, without currency conversion code

Holdings arrive valued in `reportingCurrency`, so `CurrencyConverter`, `ExchangeRateProviderFactory`
and `ClientSnapshot` are not needed. `SecuritiesValuationAdapter` becomes `RestValuationAdapter`,
which maps each `instrument` holding to a `Position` or a `Diagnostic`:

| Holding | Result |
| :--- | :--- |
| `shares` < 0 or `valuation.value` < 0 | `UNSUPPORTED_NEGATIVE_POSITION` |
| `price` missing, `price.value` = 0 or `valuation.value` = 0 | `MISSING_VALUATION` |
| `price.currency` differs from `reportingCurrency` and `localValuation` is missing | `MISSING_CONVERSION` |
| `price.currency` differs and `localValuation.value` equals `valuation.value` exactly | `MISSING_CONVERSION` (PP's silent 1:1 fallback) |
| otherwise | `Position` with `valuation` |

`cash-account` holdings are account cash and excluded from the denominator as before. Holdings of an
unknown `type` are excluded from the analysis and counted in a diagnostic, never guessed.
The exact-equality rule may misclassify a real rate of exactly 1 at full precision; that is
accepted because it fails towards "incomplete", never towards a false complete claim.

PP converts with its own exchange rates (ECB series by default), so a USD holding arrives in EUR
without any conversion in Lens. When PP has no rate for a pair, its converter falls back to a rate
of 1 (`FALLBACK_EXCHANGE_RATE`). This also happens for a short time in a fresh PP workspace, before
the first ECB download has completed: every foreign-currency holding then arrives with
`valuation.value` equal to `localValuation.value`. Lens reports these holdings as missing
conversion and withholds whole-portfolio percentages; the first run after PP has downloaded the
rates produces a complete page again, because the changed valuations change the input fingerprint.

**Status:** confirmed by the user on 2026-11-04.

`Money` becomes a small Lens record `Amount(String currency, BigDecimal value)`; the engine already
calculates in `BigDecimal`. Minor-unit rounding (for "rows whose total rounds to zero in the
currency") uses `java.util.Currency.getDefaultFractionDigits()`. `HeldSecurities` is dropped: PP
already omits zero positions and dates.

### D4. JDK HttpClient, one transport per external system

The fork's providers already hide HTTP behind a `Transport` interface with
`Response(status, body, retryAfter)`. Lens implements it once with `java.net.http.HttpClient`:
redirects disabled, connect and request timeouts, user agent `PortfolioLens/<version>`, GET only.
Apache HttpClient 5 brings nothing the JDK lacks here. The PP client uses the same transport type
so that one test double covers all outgoing calls and the GET-only test can assert on every
request.

### D5. Configuration: environment first, optional properties file, secrets from files

Configuration is read from environment variables, optionally from a properties file given with
`--config <file>` (environment wins). Every secret has a `_FILE` variant that names a file with
the value; this matches Docker secrets. Secrets are never accepted as command-line arguments.

| Key | Default | Meaning |
| :--- | :--- | :--- |
| `LENS_PP_URL` | `http://127.0.0.1:5712` | PP base URL; host must be a loopback literal or `localhost` |
| `LENS_PP_TOKEN` / `LENS_PP_TOKEN_FILE` | none, required | PP bearer token |
| `LENS_PP_FILE` | the only enabled file | file id or alias |
| `LENS_REPORTING_CURRENCY` | file base currency | passed to `/holdings` |
| `LENS_EULERPOOL_API_KEY` / `LENS_EULERPOOL_API_KEY_FILE` | none | provider key |
| `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED` | `false` | user confirms the plan allows local storage |
| `LENS_REGISTER_LOOKUP` | `true` | GLEIF lookups |
| `LENS_REQUEST_BUDGET` | `20000` | provider units per rolling 24 hours |
| `LENS_RUN_TIMEOUT` | `PT2M` | upper bound for provider work in one run |
| `LENS_CACHE_DIR` | `$XDG_CACHE_HOME/portfolio-lens`, else `~/.cache/portfolio-lens` | cache and state |
| `LENS_OUTPUT_DIR` | none, required | where `index.html` is written |

The fork's `CompositionCredentials` keeps its validation and state machine; its `SecureStore`
becomes a read-only `ConfiguredKeyStore` that reads the environment or file. Writes and session
replacement are removed with the preferences page. `CompositionPersistencePreferences.InMemory`
is filled from the configuration once per run; the fork's per-account persistence checkbox and the
confirmation collapse into the single confirmation key (see D6).

### D6. Provider data requires the local storage confirmation

A periodic job has no session: each run is a new process. Session-only operation would either
fetch every composition every 5 minutes, which breaks the 24-hour cadence and the budget, or keep
nothing. The page itself is persisted derived data. Therefore:

- With a key and `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true`, the policy is `PERMITTED`
  (as in the fork after confirmation) and compositions, profiles and revenue splits are stored in
  the cache directory.
- Without the confirmation, Lens makes no provider request, deletes any provider data left in the
  cache, and writes a page with the holdings, the direct analysis without provider
  classification and a notice naming the setting.
- GLEIF register data (CC0) is cached regardless.

Alternative considered: session-only mode with a fetch on every run, rejected for quota and because
the page would persist provider-derived results anyway.

**Status:** confirmed by the user on 2026-11-04, with the condition that a forgotten confirmation
must not go unnoticed.

**Safeguard against a forgotten confirmation.** When a provider key is configured and
`LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED` is not exactly `true` (the "unconfirmed key" state):

- `lens check` reports the state, names `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true` as the
  setting to add, and exits with the configuration error status.
- `lens run` logs a warning with the same text on every run, including skipped runs, and records
  the state in `status.json` (`providerData: "unconfirmed"`), so monitoring can alert on it.
- The page shows a banner at the top, above the summary, that is not collapsible and names the
  exact variable and value to set.
- `docs/lens.env.example` and the README configuration table list the variable directly below the
  key settings, with a comment that the key has no effect without it.
- A value other than `true` or `false` (for example `yes` or `TRUE `) is a configuration error, so
  a typo cannot silently mean "off".

The behavior in that state is unchanged: no provider requests, cached provider data deleted,
holdings-only page.

### D7. Run state file instead of in-memory coordination

`state.json` in the cache directory holds, per provider account: budget charges (timestamp, units),
provider `retryAt`, suspension with the configuration revision that caused it, per identifier the
last composition attempt and the "no composition" probe time, and the last input fingerprint. It
holds no provider payload and no amounts. `CompositionRequestBudget` and
`CompositionRefreshCoordinator` get a small snapshot/restore API; their logic is unchanged.
The file is written atomically (write to a temporary file, then move). A missing or unreadable
state file is treated as empty, which is safe because the budget then starts fresh and the cache
still carries retrieval dates.

### D8. One run, step by step

1. Acquire `cache/lens.lock` with `FileChannel.tryLock`; if held, log and exit 0 (another run is
   active).
2. Load configuration; on errors exit 64 with a message that names the key, never the value.
3. `GET /v1/version` without token; require `apiVersion` major `1`, else exit 69.
4. `GET /v1/files`, select the file; `GET .../holdings` and `GET .../instruments`. 401, 404 and 409
   map to distinct messages and exit 69.
5. Compute the input fingerprint: SHA-256 over the canonical holdings (date, reporting currency,
   uuid, type, shares, valuation, price and its date), the instrument identifiers, the effective
   non-secret configuration, the Lens version and the cache revision. If it equals the stored one
   and no provider work is due (no fund past its cadence or backoff, no pending enrichment with
   budget), update `lastCheckedAt` in `status.json` and exit 0 without rendering.
6. Classify and refresh funds through the coordinator within budget and `LENS_RUN_TIMEOUT`, then
   enrich companies in value order within the remaining budget and time. Work left over stays
   pending for the next run.
7. Run the engine, build the page model, write `index.html` and `status.json` atomically to the
   output directory, store state and fingerprint, release the lock, exit 0.

The fork's executors become a bounded thread pool owned by the run and closed at its end; the
scheduled retry callbacks are not needed because the next run is the retry.

### D9. Page: one self-contained HTML file

`index.html` contains inline CSS (light and dark through `prefers-color-scheme`), server-rendered
SVG for the coverage bar, treemap, bar chart and overlap matrix, the table as HTML, and a
`<script type="application/json" id="lens-data">` block with the page model. About 150 lines of
inline vanilla JavaScript switch the grouping (charts are pre-rendered for each grouping and shown
or hidden), sort the table, link chart and table selection, and keep the grouping in the URL
fragment. Without JavaScript the page shows the Company grouping in full. Tooltips are SVG
`<title>` and HTML `title` attributes. Values are HTML-escaped by one writer function; the page
loads nothing from the network. `status.json` beside it carries generation time, last check time,
missing data counts and the exit status for monitoring.

Alternative considered: a template engine or a frontend build, rejected for dependencies and the
no-npm constraint.

### D10. Own layout and classification code

- Treemap: Lens implements the squarified algorithm (Bruls, Huizing, van Wijk 2000) from the
  published description, plus the fork's two-level `GroupLayout` logic (header strip, borders,
  minimum sizes, fill of the area of tiles too small to draw). The de.engehausen library is not
  used.
- Bar chart and coverage bar: the fork's own layout arithmetic (minimum width for a nonzero
  remainder, label room) is ported from `LookThroughBarChart` and `LookThroughCoverageBar`, the SWT
  drawing is replaced by SVG elements.
- Countries: names from `java.util.Locale` (`Locale.ENGLISH`).
- Regions: Lens's own table that maps ISO 3166 alpha-2 codes to seven regions (Africa, Asia,
  Europe, Latin America and Caribbean, Middle East, North America, Oceania), written for this
  project.
- Sectors: Lens's own list of the 11 GICS sector names and codes and the fork's alias map.
- Market classification (developed, emerging, frontier) is removed from the grouping selector in
  this step, because the PP template it came from cannot be copied and no source with a compatible
  license has been chosen. A possible later addition is a grouping from a compatibly licensed
  source, for example the World Bank income groups (CC BY 4.0, with attribution).
  **Status:** confirmed by the user on 2026-11-04, together with the seven-region table.
- Texts: the English strings the user wrote for the fork are ported into one Lens `Texts` class.

### D11. Local development against a PP on macOS

PP on the host binds 127.0.0.1 and accepts any remote address as long as the `Host` header is
loopback. A container cannot reach the host's 127.0.0.1 directly, and a request to
`host.docker.internal` carries a non-loopback `Host` header. Plan:

- `scripts/dev.sh run [args]` starts the dev image with a small TCP forwarder (`socat`, added to
  `.devcontainer/Dockerfile`) that listens on the container's 127.0.0.1:5712 and connects to
  `host.docker.internal:5712`. Lens keeps `LENS_PP_URL=http://127.0.0.1:5712`, so the `Host`
  header stays loopback and no code path has to fake it.
- Whether Docker Desktop delivers these connections to services bound to the Mac's loopback is
  not verified yet; a task checks it before the rest of the tooling is built. If it does not, the
  fallback is Docker Desktop host networking (`--network host`, Docker Desktop 4.34 or later with
  host networking enabled in Settings), where the container's 127.0.0.1 reaches the host. If
  neither works, the documented manual step is to run PP itself in a container and start Lens in
  the same network namespace (`--network container:<pp>`), which is also the production
  arrangement. The README records the result.
- The token is read from `scratch/pp-token` (gitignored) through `LENS_PP_TOKEN_FILE`, the output
  goes to `scratch/out/`, the cache to the Docker volume `portfolio-lens-cache`, which
  `scripts/dev.sh purge` removes as well.
- `.vscode/launch.json` has two Java configurations for the dev container: "lens run" with
  `envFile` pointing to `scratch/lens.env` and "lens check". The forwarder is started by the
  devcontainer `postStartCommand`.

### D12. Tests

- Ported unit tests run unchanged in substance; fixtures that built a PP `Client` are rewritten to
  build `HeldInstrument` and holdings lists.
- Contract tests read synthetic JSON fixtures shaped by `openapi.yaml` (well-known public ISINs,
  invented amounts), including unknown fields, an unknown holding type, null `currencyCode`,
  missing optional fields and problem responses.
- A fake PP server on the JDK `HttpServer` (test scope only) serves the fixtures; `LensRunTest`
  runs the whole job against it with fake provider transports and asserts that every recorded
  request is a GET, carries no `Origin` header and that provider requests contain no amounts.
- A page smoke test renders a synthetic result, parses the output as XML-compatible HTML, checks
  that every SVG is well-formed, that values are escaped, and that the page contains no key, token
  or file path.

## Risks / Trade-offs

- [Docker Desktop may not forward to host loopback] → an early task verifies it; documented
  fallbacks in D11.
- [Exact-equality detection of PP's 1:1 fallback] → fails towards incomplete; the data status
  names the affected instrument so the user can add the rate in PP.
- [Porting `LookThroughSession` from a long-running model to a run model may change behavior] →
  port its tests first and keep the coordinator logic, change only the scheduling shell.
- [The page contains personal financial data] → it is written only to `LENS_OUTPUT_DIR`, never to
  the repository; serving it behind authentication is part of the later infra step.
- [State file loss resets the budget] → the provider still answers rate limits with backoff, and
  stored retrieval dates keep the 24-hour cadence for cached compositions.
- [Market classification is missing] → stated in the README; can come back with a licensable
  source such as the World Bank income groups.
- [A provider key is configured but the confirmation is forgotten] → the safeguard in D6: failing
  `lens check`, a warning on every run, `status.json` state and a top banner on the page.
- [Fresh PP workspace before the first ECB download] → foreign-currency holdings show as missing
  conversion until PP has rates; the page says so and recovers on the next run.

## Migration Plan

No users yet. The fork stays untouched. After this change, the user stops using the fork's
Breakdown, enables PP's REST API, creates a token for Lens in PP and runs `lens run` from a
scheduler. Rollback is to keep using the fork.

## Decisions confirmed by the user (2026-11-04)

- D6: provider data only with `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true`, with the safeguard
  against a forgotten confirmation.
- D10: Market classification removed in this step; Lens's own seven-region table.
- D3: PP's silent 1:1 conversion counts as missing conversion.

## Open Questions

- Package root `io.portfoliolens` versus another name: can change before the first release
  without affecting specs or tasks.
- Default for `LENS_RUN_TIMEOUT`: two minutes is a starting value; tune after the first real runs.
