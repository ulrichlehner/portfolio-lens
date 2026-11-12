# Portfolio Lens

Look-through analysis for [Portfolio Performance](https://github.com/portfolio-performance/portfolio):
decompose the funds of a portfolio into their companies and see the combined exposure by company,
sector, country and region.

Portfolio Lens runs next to Portfolio Performance and reads the open portfolio through its local
REST API. It never changes the portfolio.

**Status:** early development. The command-line job has been tested against synthetic data and once
against Portfolio Performance 0.90.0. The plan is in [`openspec/`](openspec/).

## Notice

Portfolio Lens is an independent project. It is not part of Portfolio Performance and is not
supported by the Portfolio Performance maintainers. It is not affiliated with any data provider.
Fund and company data comes from providers using your own account. Provider permissions and data
completeness are not verified: make sure your plan allows your use, including local caching. Use at
your own risk.

## What it does

The `run` command is a job that runs once and exits:

1. It reads the holdings and instruments of one portfolio file from Portfolio Performance (GET
   requests only).
2. It asks Eulerpool, with your own API key, for the compositions of your funds and for company
   data, and GLEIF (public, CC0) for company headquarters.
3. It calculates the combined exposure by company.
4. It writes a static page, `index.html`, with charts and a table, and `status.json` for monitoring.

Run it every 5 to 15 minutes from a scheduler. A run whose inputs did not change and that has no
provider work due does nothing except record the time of the check. Provider requests stay within a
request budget and a 24-hour cadence per fund across runs.

The first fill needs about two Eulerpool requests per company (profile and revenue regions) plus a
few per fund: about 8,000 of the default 20,000 daily units for 4,000 companies. Because each run
stops provider work after `LENS_RUN_TIMEOUT`, a first fill usually spreads over several runs; funds
are filled largest first, and nothing resolved in an earlier run is lost while the rest follows.
`status.json` and the page show the units used.

Some funds report holdings that add up to slightly more than 100%, balanced by negative cash and
currency rows without a security identifier. Lens uses such a composition with its real weights
when these rows net to at most -10% of the fund, and shows the net as "Net cash of funds" in the
table, the summary and the fund's row in the fund table; the charts do not draw it and name it in a note. A
larger negative net likely means leverage, and the fund stays unresolved. After upgrading to a
version with this rule, the first run requests the compositions of funds that were rejected or
failed before once more, instead of waiting for their 24-hour cadence.

The page is one self-contained file. It loads nothing from the network, follows the light or dark
system setting, and shows when it was generated. Grouping by country, region, sector, industry,
currency and revenue region needs JavaScript; without it the company breakdown is shown.
Grouping by market classification (developed, emerging) is not available yet; it could come back
from a source with a compatible license, for example the World Bank income groups (CC BY 4.0).

## Set up Portfolio Performance

Portfolio Lens needs Portfolio Performance 0.89.0 or newer, running and with the file open.

1. Preferences > **MCP Server & REST API**: tick *Enable MCP server and REST API (localhost only)*.
2. Enable the portfolio file there, optionally with an alias.
3. Click **Add client** and copy the token. It is shown once.

Portfolio Performance answers only requests to `127.0.0.1`, `[::1]` or `localhost`, so Lens must run
on the same machine or in the same network namespace (a container started with
`--network container:<pp>`).

## Configuration

Settings come from environment variables, or from a properties file given with
`--config <file>` (environment variables win). Every secret can also be read from a file named by
the `_FILE` variant. Secrets are never accepted as command-line arguments and never appear in logs,
the page or the status file.

| Setting | Default | Meaning |
| :--- | :--- | :--- |
| `LENS_PP_URL` | `http://127.0.0.1:5712` | Portfolio Performance REST API; loopback only |
| `LENS_PP_TOKEN` / `LENS_PP_TOKEN_FILE` | required | token from *Add client* |
| `LENS_PP_FILE` | the only enabled file | file id or alias |
| `LENS_REPORTING_CURRENCY` | the file's base currency | currency of all amounts |
| `LENS_EULERPOOL_API_KEY` / `LENS_EULERPOOL_API_KEY_FILE` | none | your Eulerpool API key |
| `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED` | `false` | `true` confirms that your Eulerpool plan allows storing its data locally; the key has no effect without it |
| `LENS_REGISTER_LOOKUP` | `true` | look up company headquarters at GLEIF |
| `LENS_REQUEST_BUDGET` | `20000` | Eulerpool request units per rolling 24 hours |
| `LENS_RUN_TIMEOUT` | `PT2M` | time limit for provider work in one run; the rest waits for the next run |
| `LENS_CACHE_DIR` | `$XDG_CACHE_HOME/portfolio-lens`, else `~/.cache/portfolio-lens` | cache and run state |
| `LENS_OUTPUT_DIR` | required for `run` | where `index.html` and `status.json` are written |
| `LENS_APP_LINK_URL` | none | adds a link to this address in the page header; absolute `http` or `https` URL without user name or password. Lens never requests it. Example for a server with the page at `https://<host>/lens/`: `https://<host>/` |
| `LENS_APP_LINK_LABEL` | `Portfolio Performance` | text of that link, at most 60 characters; needs `LENS_APP_LINK_URL` |
| `LENS_LOG_LEVEL` | `INFO` | `FINE` adds one line per provider and register request (type, outcome, HTTP status, ISIN) and one per company lookup put on hold; JDK loggers stay at `INFO` |

`true` and `false` are the only accepted values of the switches; anything else is an error.

### Local storage confirmation

Each run is a new process, so Lens can only use provider data that it stores in its cache, and the
page itself keeps results derived from it. Therefore provider data is requested and used only with
`LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true`.

If a key is set without that confirmation, Lens makes no provider request, deletes provider data
left in its cache, and shows the holdings without a breakdown. This state does not go unnoticed:

- `check` fails and names the setting,
- every `run` logs a warning,
- `status.json` has `"providerData": "unconfirmed"`,
- the page shows a banner at the top naming `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true`.

Turning the confirmation off later deletes the stored provider data on the next run. Register data
from GLEIF is kept.

## Commands and exit statuses

The build produces `target/portfolio-lens.jar` with its library in `target/lib/`:

```bash
java -jar portfolio-lens.jar check   # configuration, PP version and file; no provider request, no page
```

```bash
java -jar portfolio-lens.jar run     # one complete run
```

| Status | Meaning |
| :--- | :--- |
| 0 | success, nothing changed, or another run holds the lock |
| 64 | configuration error, or `check` found a key without the confirmation |
| 69 | Portfolio Performance not reachable, token rejected, file not open, or incompatible API |
| 70 | unexpected error |
| 73 | output or cache directory cannot be written |

A failed run leaves the previous page in place and records the failure in `status.json`.

To run a published release instead of a build from source, see [Releases](#releases).

Each run logs one line with its provider and register requests by type (fund composition, company
profile, classification search, revenue split, GLEIF register) and outcome (found, not found,
failed). A lookup that fails waits before its retry, from one hour up to a week, so a company the
provider cannot answer does not cost a request on every run.

## Scheduling

The job does not schedule itself. Examples, with the configuration in `/etc/portfolio-lens.env`:

```bash
# cron, every 10 minutes
*/10 * * * * set -a; . /etc/portfolio-lens.env; java -jar /opt/portfolio-lens/portfolio-lens.jar run
```

```bash
# a shell loop, for example in a container
while true; do java -jar portfolio-lens.jar run; sleep 600; done
```

A systemd timer with `OnUnitActiveSec=10min` and `EnvironmentFile=` works the same way.
Overlapping runs are safe: a run that finds another one active exits without changes.

## Data on disk

- `LENS_CACHE_DIR`: compositions, company profiles and revenue splits (only with the confirmation),
  GLEIF register data, and `state.json` (request budget, backoff, attempt times, lookups on hold by
  identifier, input fingerprint; no amounts, no provider payloads, no secrets). The directory is created readable only by its owner.
- `LENS_OUTPUT_DIR`: `index.html` and `status.json`. The page contains your holdings and values;
  serve it only behind authentication.

The page has three sections, and a bar that stays at the top links to them and holds the grouping,
the company search, the application link and the generation time.

- Exposure: the summary, one line on data quality, and on screens at least 1100 pixels wide two
  panes: the chart on the left stays in view while the table on the right scrolls. Tabs switch the
  chart between treemap and the 20 largest.
- Funds: a table of the held funds with value, share, number of companies, looked-through share,
  composition date and status, and the overlap matrix of the funds.
- Data quality: the coverage bar, the notes on the calculation and the data status.

The data quality line shows a warning only when a lookup failed, a composition was rejected, a fund
has no composition, a security has no valuation or value is unresolved.

In a grouped treemap a click on a group shows that group's companies; a breadcrumb, the Back
button or Escape lead back. Companies too small for their own tile share one tile whose area is their
total value; it shows their number and share, and clicking it (or Enter) explores them. On an
explored level that tile covers at most half of the chart, and the other companies are drawn as
small tiles without label, so every company is reached after a few levels. Grouping, chart tab and
treemap level are kept in the page address.

The page holds every company once, in its embedded data, and renders the 200 largest rows; further
rows, the companies of a group, search results and treemap levels are built on demand. Without
scripts it shows the Company grouping with both charts below each other, and the other sections. For 4,000 companies
it is about 1.6 MB, about 0.3 MB compressed. Serve it compressed, for example with Caddy:

```
handle_path /lens/* {
	root * /srv/portfolio-lens
	encode zstd gzip
	file_server
}
```

## Development

Requirements on the host: git and Docker. No Java or Maven.

```bash
scripts/dev.sh            # list of commands
scripts/dev.sh test       # unit and integration tests
scripts/openspec list     # OpenSpec changes
```

Or open the folder in VS Code and choose "Dev Containers: Reopen in Container". The rules for
contributors and AI agents are in [AGENTS.md](AGENTS.md).

### Running against Portfolio Performance on this machine

Portfolio Performance on the host accepts only loopback requests. `scripts/dev.sh check` and
`scripts/dev.sh run` start a forwarder inside the container (`.devcontainer/pp-forward.sh`, socat) that listens on the
container's `127.0.0.1:5712` and connects to `host.docker.internal:5712`, so Lens keeps talking to
`http://127.0.0.1:5712`. With Docker Desktop on macOS this reaches Portfolio Performance on the
Mac's loopback; this was checked with Portfolio Performance 0.90.0. If it does not work, enable
host networking in Docker Desktop and run the container with `--network host`.

1. Copy [`docs/lens.env.example`](docs/lens.env.example) to `scratch/lens.env` (gitignored).
2. Put the PP token into `scratch/pp-token` and, if you use Eulerpool, the key into
   `scratch/eulerpool-key`.
3. Start Portfolio Performance with the file open, then check the configuration:

```bash
scripts/dev.sh check
```

4. Make a run:

```bash
scripts/dev.sh run
```

The page is written to `scratch/out/index.html`; `scripts/dev.sh page` opens it in the browser on
the Mac. In a dev container terminal in VS Code it serves the page inside the container on
`localhost:8765` (loopback only) and opens it through VS Code, which forwards the port to the Mac. The cache lives in the Docker volume
`portfolio-lens-cache` (`scripts/dev.sh purge` removes it). Options after the command go to Lens,
for example `scripts/dev.sh run --config <file>`.

In VS Code, *Run and Debug* has the same commands: "Lens check (Docker)", "Lens run (Docker)",
"Lens run and open the page (Docker)", "Open the page" and "Tests (Docker)" work in a window on
the Mac and in the dev container. "Lens run (debug)" and "Lens check (debug)" start Lens in the Java
debugger and work only in the dev container; they keep their cache in `scratch/cache`.

A fresh Portfolio Performance workspace has no exchange rates until its first ECB download; until
then Portfolio Performance converts foreign currencies 1:1, and Lens marks those holdings as
missing conversion instead of using the values.

## Releases

Releases are published on the GitHub releases page of this repository. A version such as
`1.0.0-rc.1` is a prerelease for testing an integration; `1.0.0` is a release. Each release has two
files:

- `portfolio-lens-<version>.tar.gz`
- `portfolio-lens-<version>.tar.gz.sha256`

Verify and unpack (Linux; on macOS use `shasum -a 256 -c`):

```bash
sha256sum -c portfolio-lens-<version>.tar.gz.sha256
tar -xzf portfolio-lens-<version>.tar.gz
```

The archive has one directory `portfolio-lens-<version>/` with `portfolio-lens.jar`, `lib/` (the
runtime library), `LICENSE` and `NOTICE` (the notice above and the third-party licenses). `lib/`
must stay next to the jar. The checksum detects damaged downloads; the archive is not signed.

What a program that runs a release, for example a container on a server, can rely on:

- It needs a Java 21 runtime, for example the image `eclipse-temurin:21-jre`, and runs
  `java -jar portfolio-lens.jar check` and `java -jar portfolio-lens.jar run` as described in
  [Commands and exit statuses](#commands-and-exit-statuses). The exit statuses are 0, 64, 69, 70
  and 73.
- Configuration is only through the `LENS_*` settings in [Configuration](#configuration), as
  environment variables or a `--config` file. Secrets are never passed as arguments.
- Provider data needs `LENS_EULERPOOL_API_KEY` and `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true`;
  with the key alone, `check` fails with status 64.
- Portfolio Performance's REST API must be reachable at `127.0.0.1:5712` on the loopback interface
  of the process, so the container shares Portfolio Performance's network namespace or uses a
  forwarder that keeps the `Host` header.
- `LENS_OUTPUT_DIR` receives `index.html` and `status.json`; monitor `status.json`. `LENS_CACHE_DIR`
  holds the cache and the run state. Both must be persistent directories. Only one run at a time
  uses a cache directory; a lock file guards it, and a second run exits with status 0.
- A release reads the cache of the previous release or rebuilds it by itself.

## Versions

Portfolio Lens uses [Semantic Versioning](https://semver.org/). Releases are tagged `vX.Y.Z`. The
commands, settings, exit statuses, `status.json` and the cache format are the interface the version
number describes; before `1.0.0` an incompatible change raises the minor version. Details are in
[AGENTS.md](AGENTS.md#versioning).

## License

[Apache License 2.0](LICENSE)
