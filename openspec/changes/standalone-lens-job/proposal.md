# Proposal

## Why

The look-through analysis (Breakdown) exists only inside a personal development fork of Portfolio
Performance (PP). The user is moving back to the official PP, which since 0.89.0 offers a local,
read-only usable REST API v1. Lens must become a standalone tool that reads the portfolio through
that API, so the analysis keeps working without the fork and without changes to PP.

## What Changes

- Port the look-through core from the fork (`snapshot/lookthrough`, 32 files), the providers
  Eulerpool and GLEIF, the provider interface and the credentials logic, with their 18 unit test
  classes, to a single Maven module on Java 21. The fork code is the user's own work; nothing else
  is copied from PP.
- Port the SWT-free models of the fork's Breakdown view (session, analysis, grouping, data status,
  status model, overlap model, presenter logic) as the model of the output page.
- Replace PP dependencies: `Security`, `Client`, `ClientSnapshot`, `Transaction` and the currency
  classes by data read from `GET /v1/files/{file}/holdings` and `/instruments`; `WebAccess` and
  Apache HttpClient 5 by the JDK `HttpClient`; Equinox secure storage and Eclipse preferences by
  environment variables and secret files; PP taxonomy templates by Lens's own country, region and
  sector tables; the de.engehausen treemap by Lens's own squarified layout.
- Add a CLI `lens run` that reads the configuration, checks `/v1/version`, fetches holdings and
  instruments with GET requests only, resolves compositions and company data within the provider
  budget, runs the engine and writes a static `index.html` with server-rendered SVG charts, embedded
  JSON and minimal inline JavaScript. `lens check` validates configuration and PP access without
  provider requests.
- The job is meant to run every 5 to 15 minutes from an external scheduler. It skips the run when
  its inputs are unchanged and no provider work is due, keeps request budget, attempt times and
  backoff in a state file across runs, and prevents overlapping runs with a lock.
- **BREAKING** (against the fork's specs): no live view, refresh button, progress line, preferences
  page or session-only mode. Provider data is fetched and the page shows provider-derived exposure
  only when the user confirms in the configuration that their plan allows local storage; without
  that confirmation the page shows the holdings and a notice, and no provider request is made.
- Dev tooling: `pom.xml`, `scripts/dev.sh run` (CLI in the dev image against a PP on the host via a
  loopback forwarder inside the container), `.vscode/launch.json` for running and debugging in the
  dev container.

## Capabilities

### New Capabilities

- `pp-rest-source`: reading the portfolio from PP's REST API v1: connection, version check, file
  selection, holdings and instruments mapping, tolerance of unknown fields and values, GET only.
- `lens-job`: the CLI job: configuration, one run from input to output page, change detection,
  persisted run state, locking, exit codes and logging without secrets.

### Modified Capabilities

- `breakdown-view`: the Breakdown becomes a static page; live refresh, progress, restore and
  calculating states are removed; selection, grouping and status work inside the page.
- `composition-provider-settings`: the preferences page becomes configuration: key from an
  environment variable or secret file, validation at start, local storage confirmation as a
  setting.
- `portfolio-look-through`: feature identity becomes Portfolio Lens; the portfolio scope is the
  holdings of the configured PP file; limitations no longer refer to reopening PP.
- `fund-compositions`: storage, cleanup and refresh cadence move to the Lens cache directory and
  across job runs; session-only operation is removed; release gates refer to Lens.
- `company-enrichment`: enrichment continues across runs; cache rules follow the configuration;
  register opt-out is a setting.
- `revenue-exposure`: caching across job runs instead of PP restarts.

`fund-overlap` needs no delta: its requirements describe the calculation only and contain no
in-app wording. The overlap chart is covered by `breakdown-view`.

## Impact

- New code: `pom.xml`, `src/main/java/...`, `src/test/java/...`, `src/test/resources/fixtures/`
  (synthetic only).
- Dependencies: Gson (Apache-2.0) for JSON, JUnit 5 for tests. No Apache HttpClient, no frontend
  build, no web server.
- Tooling: `scripts/dev.sh run`, a TCP forwarder in the dev image (`.devcontainer/Dockerfile`),
  `.vscode/launch.json`, README sections for configuration and local development.
- External systems: PP REST API v1 (read only), Eulerpool with the user's own key, GLEIF (public).
- Data on disk: a cache directory with provider data (only after confirmation), register data and
  run state, and an output directory with the page. Both contain personal portfolio data and stay
  outside the repository.

## Out of scope

- Infrastructure integration: the sidecar container next to PP, the Caddy route `/lens` behind
  Authelia, the noVNC switch, scheduling in the server container.
- Docker image publishing, GitHub repository creation, releases.
- A web server, live updates, a refresh button, writing to the portfolio, other providers.
- Translations: the page is English only in this step.
