# Tasks

Source of the port, read only: `github.com/ulrichlehner/portfolio`, branch
`release/fork`, inspected with `git -C <fork> show release/fork:<path>`. The fork is never changed.
Every build and test runs through `scripts/dev.sh`; nothing is installed on the host.

## 1. Branch, build and dev tooling

- [ ] 1.1 Create the branch `feature/standalone-lens-job` from `main` and verify with `git branch --show-current`.
- [ ] 1.2 Add `pom.xml` (Java 21, single module, Gson and JUnit 5 only, Surefire, a runnable jar with `Main-Class`, Apache-2.0 license header) and an empty `io.portfoliolens` package; verify `scripts/dev.sh package` succeeds and `scripts/dev.sh test` runs zero tests without failure.
- [ ] 1.3 Add `socat` to `.devcontainer/Dockerfile` and a `postStartCommand` that starts the loopback forwarder (127.0.0.1:5712 to `host.docker.internal:5712`); verify the image builds with `scripts/dev.sh shell` and `socat -V` runs inside it.
- [ ] 1.4 Verify the local networking path before building on it: with PP running on the Mac and its REST API enabled, run `curl -s http://127.0.0.1:5712/v1/version` inside the dev container through the forwarder. If it fails, try Docker Desktop host networking (`--network host`); record which path works in the README development section and in this task's notes. Verification: a `/v1/version` answer from inside the container.
- [ ] 1.5 Add `scripts/dev.sh run [args]`: starts the dev image with the forwarder (or the path chosen in 1.4), mounts the repository, uses the volume `portfolio-lens-cache` as `LENS_CACHE_DIR`, reads `scratch/lens.env` if present, writes to `scratch/out/`, and runs the jar; extend `purge` to remove `portfolio-lens-cache`; update `usage` and the command table in `AGENTS.md`. Verify `scripts/dev.sh run check` prints the configuration errors when `scratch/lens.env` is absent.
- [ ] 1.6 Add `.vscode/launch.json` with "lens run" and "lens check" Java launch configurations for the dev container, using `envFile` `${workspaceFolder}/scratch/lens.env`, and a tracked `docs/lens.env.example` with placeholder values only (the real file stays in the gitignored `scratch/`). Verify both configurations start in the dev container.

## 2. Port the core

- [ ] 2.1 Copy the 32 files of `name.abuchen.portfolio/src/name/abuchen/portfolio/snapshot/lookthrough/` into `io.portfoliolens.core`, keeping class names and logic, changing only package names and the PP types listed in design D2 and D3. Verify `scripts/dev.sh package` compiles.
- [ ] 2.2 Add `HeldInstrument` and `Amount`; replace `Security` and `Money` in `LookThroughEngine`, `FundOverlap`, `SecuritiesValuation` and `LookThroughResult`; drop `HeldSecurities` and the PP-backed `SecuritiesValuationAdapter`. Verify the core has no import outside `java.*`, `com.google.gson.*` and `io.portfoliolens.*` with a `grep` that is added as a test (`DependencyRulesTest`).
- [ ] 2.3 Add snapshot and restore of `CompositionRequestBudget` and `CompositionRefreshCoordinator` state (design D7) without changing their admission logic. Verify with new unit tests that a restored budget refuses requests beyond capacity within the window and that a restored retry deadline defers requests.
- [ ] 2.4 Port the core test classes from `name.abuchen.portfolio.tests/src/name/abuchen/portfolio/snapshot/lookthrough/` (CompanyAttributes, CompanyEnrichmentCache, CompanyEnrichmentProgress, CompanyEnrichmentService, CompanyRevenueEnrichment, CompositionPersistencePolicy, CompositionPersistencePreferences, CompositionRefreshCoordinator, CompositionRequestBudget, CompositionStorePreferenceToggle, CompositionStore, CompositionValidator, FundOverlap, InstrumentResolver, LookThroughEngine, RevenueSplit); rewrite fixtures that built a PP `Client` to build `HeldInstrument` and positions; check that every fixture uses invented amounts and public ISINs only. Verify `scripts/dev.sh test` passes.
- [ ] 2.5 Drop `HeldSecuritiesTest` and port the cases of `SecuritiesValuationAdapterTest` that still apply into `RestValuationAdapterTest` (task 4.3). Record in this task which cases were dropped and why (PP-side valuation, transaction fallback).

## 3. Port the providers

- [ ] 3.1 Port `FundCompositionProvider`, `EulerpoolFundCompositionProvider` and `GleifCompanyRegister` into `io.portfoliolens.provider`; replace `WebAccess`, `OnlineHelper` and Apache HttpClient 5 with one JDK `HttpClient` transport (design D4). Verify the provider code compiles without Apache imports.
- [ ] 3.2 Port `CompositionCredentials` with a read-only `ConfiguredKeyStore` (environment or file, design D5); remove `EquinoxCompositionCredentialStore`, its test and the session replacement path. Verify with `CompositionCredentialsTest` that valid keys are returned, invalid keys are refused and the key never appears in `toString` or exceptions.
- [ ] 3.3 Port `EulerpoolFundCompositionProviderTest`, `GleifCompanyRegisterTest` and `FundCompositionProviderTest` with fake transports and synthetic payloads written for this repository (no recorded provider responses). Add a test that provider requests contain only identifiers and the key header. Verify `scripts/dev.sh test` passes.

## 4. PP REST source

- [ ] 4.1 Add synthetic fixtures under `src/test/resources/fixtures/pp/` shaped by PP's `openapi.yaml` at `rest-api-v1.0.0`: version, files (one and several), holdings (instruments, cash account, unknown type, negative shares, missing price, USD instrument with and without `localValuation`, 1:1 fallback), instruments (with and without ISIN, null `currencyCode`, unknown fields), and problem responses 401, 404, 409 (`file-not-open`, `ambiguous-alias`). Verify a fixture scan test finds no ISIN outside an allow-list of well-known public ISINs.
- [ ] 4.2 Implement the PP client (`/v1/version`, `/v1/files`, `/holdings`, `/instruments`) on the shared transport with loopback URL validation, bearer token, no `Origin` header and tolerant parsing. Verify with contract tests over the fixtures for every scenario in `specs/pp-rest-source/spec.md`.
- [ ] 4.3 Implement `RestValuationAdapter` with the table in design D3. Verify with `RestValuationAdapterTest`, including the silent 1:1 conversion and the converted holding scenarios.
- [ ] 4.4 Add a test-scope fake PP server on the JDK `HttpServer` that serves the fixtures and records every request. Verify a test that rejects a request with an `Origin` header or a non-loopback `Host`, like PP does.

## 5. Job

- [ ] 5.1 Implement configuration loading (design D5): environment, optional properties file, `_FILE` secrets, conflicts and validation with messages that name settings only. Verify with `LensConfigTest` for every scenario in `specs/lens-job/spec.md` "Configuration sources" and "Secrets never in output", and for the provider settings in `specs/composition-provider-settings/spec.md`.
- [ ] 5.2 Implement the cache layout, `state.json` and the lock (design D7, D8 step 1); provider data only after confirmation; deletion of provider data when the confirmation is off. Verify with tests for overlapping runs, lost state and "Persistence withdrawal and cleanup".
- [ ] 5.3 Port the orchestration of `LookThroughSession` and `LookThroughAnalysis` from `name.abuchen.portfolio.ui/src/name/abuchen/portfolio/ui/views/lookthrough/` into `io.portfoliolens.job` as a single bounded run with a run-owned thread pool and the run time limit; port `LookThroughSessionTest` and `LookThroughAnalysisTest` from `name.abuchen.portfolio.ui.tests` where their cases apply. Verify the ported tests pass.
- [ ] 5.4 Implement the input fingerprint and skip logic (design D8 step 5). Verify with tests for "Nothing changed" and "Price changed".
- [ ] 5.5 Implement `lens run` and `lens check`, exit statuses and logging to standard error. Verify with `LensRunTest` against the fake PP server and fake provider transports: a full run writes the page; every PP request is a GET without `Origin`; no log line contains an amount, a holding name, the token or the key.

## 6. Page

- [ ] 6.1 Port the SWT-free models `LookThroughViewPresenter`, `LookThroughDataStatus`, `LookThroughStatusModel`, `LookThroughOverlapModel`, `LookThroughBanner` and `LookThroughGrouping` into `io.portfoliolens.page`, with the English texts in one `Texts` class; port their tests from `name.abuchen.portfolio.ui.tests` (ViewPresenter, DataStatus, StatusModel, Banner, CompanyDataText). Remove refresh, progress and session wording per `specs/breakdown-view/spec.md`. Verify the ported tests pass.
- [ ] 6.2 Replace `TaxonomyTemplate` with Lens's own region table, the 11 GICS sector names and the alias map, and `Locale.ENGLISH` country names (design D10); drop Market classification from the groupings. Verify with tests for "Country not in template" and "Provider alias for a sector".
- [ ] 6.3 Implement the squarified treemap layout with the two-level group layout, and the bar chart, coverage bar and overlap matrix geometry, as plain Java producing rectangles. Verify with layout tests: no unpainted area, every company keeps a tile in size order, nonzero unresolved remainder keeps a minimum width.
- [ ] 6.4 Implement the SVG and HTML writers with one escaping function, inline CSS for light and dark, embedded JSON and the inline script for grouping, sorting, linked selection, URL fragment and staleness notice; atomic write of `index.html` and `status.json`. Verify with a page smoke test that parses the HTML, checks well-formed SVG, escaping of a company name with `<` and `&`, no external resource reference, and absence of token, key and paths.
- [ ] 6.5 Open a page rendered from the synthetic fixtures in a browser from `scratch/out/` and check the scenarios of `specs/breakdown-view/spec.md` by hand (grouping switch, sorting, tile click selects row, reload keeps grouping, scripts disabled, dark mode); record the result in this task.

## 7. Documentation and integration

- [ ] 7.1 Update `README.md`: what Lens does, PP setup (enable REST API, enable the file, create a token with "Add client"), configuration table, local storage confirmation and its effect, scheduling examples (cron, systemd timer, shell loop), local development path from 1.4, the existing provider notice unchanged, and that Market classification is not available yet. Verify every documented command runs as written in the dev container.
- [ ] 7.2 Run `scripts/dev.sh test` and `scripts/openspec validate standalone-lens-job --strict`; both pass.
- [ ] 7.3 Run Lens once against the user's real PP with `scripts/dev.sh run run` (user present, token and key in `scratch/`), check the page in a browser, then delete `scratch/out/` and the cache volume content. Verify nothing from the real run is tracked by `git status`.
- [ ] 7.4 Privacy scan of the full diff before each commit: `git diff main...HEAD` searched for ISINs outside the allow-list, amounts from the real run, names, file paths under `/Users/`, tokens and keys; verify the scan finds nothing.
- [ ] 7.5 Commit with Conventional Commits, push the branch and open a pull request into `main` once the repository is on GitHub and the user authorizes it; verify the pull request shows a green build.

## Workflow follow-up

- Archive the change after the pull request is merged and review is complete.
- Verify the archived main specs no longer mention the refresh button, preferences page or session-only mode.
