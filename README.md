# Portfolio Lens

Look-through analysis for [Portfolio Performance](https://github.com/portfolio-performance/portfolio):
decompose the funds of a portfolio into their companies and see the combined exposure by company,
sector, country and region.

Portfolio Lens runs next to Portfolio Performance and reads the open portfolio through its local
REST API. It never changes the portfolio.

**Status:** early development. The command-line job works against synthetic test data; it has not
been run against a real Portfolio Performance yet. The plan is in [`openspec/`](openspec/).

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
| `LENS_LOG_LEVEL` | `INFO` | `FINE` adds instrument identifiers to the log |

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
  GLEIF register data, and `state.json` (request budget, backoff, attempt times, input fingerprint;
  no amounts, no provider payloads, no secrets). The directory is created readable only by its owner.
- `LENS_OUTPUT_DIR`: `index.html` and `status.json`. The page contains your holdings and values;
  serve it only behind authentication.

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

Portfolio Performance on the host accepts only loopback requests. `scripts/dev.sh run` starts a
forwarder inside the container (`.devcontainer/pp-forward.sh`, socat) that listens on the
container's `127.0.0.1:5712` and connects to `host.docker.internal:5712`, so Lens keeps talking to
`http://127.0.0.1:5712`. With Docker Desktop on macOS this reaches services bound to the Mac's
loopback; this was checked with a stand-in server, not yet with Portfolio Performance itself. If it
does not work, enable host networking in Docker Desktop and run the container with
`--network host`.

1. Copy [`docs/lens.env.example`](docs/lens.env.example) to `scratch/lens.env` (gitignored).
2. Put the PP token into `scratch/pp-token` and, if you use Eulerpool, the key into
   `scratch/eulerpool-key`.
3. Run:

```bash
scripts/dev.sh run check
```

```bash
scripts/dev.sh run run
```

The page is written to `scratch/out/index.html`; the cache lives in the Docker volume
`portfolio-lens-cache` (`scripts/dev.sh purge` removes it). In the dev container, the launch
configurations "lens run" and "lens check" in `.vscode/launch.json` do the same with the debugger.

A fresh Portfolio Performance workspace has no exchange rates until its first ECB download; until
then Portfolio Performance converts foreign currencies 1:1, and Lens marks those holdings as
missing conversion instead of using the values.

## License

[Apache License 2.0](LICENSE)
