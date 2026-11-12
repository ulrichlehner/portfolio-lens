# Portfolio Lens

Instructions for AI coding agents (Claude Code, Codex, OpenCode and others) and for humans.

Portfolio Lens is a standalone, open-source companion to
[Portfolio Performance](https://github.com/portfolio-performance/portfolio) (PP). It decomposes the
funds of a portfolio into their companies and shows the combined exposure. It reads the portfolio
only through PP's local REST API (v1). It is not part of PP and is not supported by the PP maintainers.

All project-specific knowledge belongs in this file or in `openspec/`. Do not keep it only in an
agent's private memory.

## Layout

```
portfolio-lens/
├── AGENTS.md          this file, the rules for all agents
├── CLAUDE.md          imports this file for Claude Code
├── .devcontainer/     the only dev environment (Java 21, Maven, Node.js, OpenSpec, zsh)
├── .vscode/           editor settings
├── scripts/dev.sh     build and test commands, run in Docker
├── scripts/openspec   the OpenSpec CLI, run in Docker
├── openspec/          specs and changes
└── scratch/           private temporary files (gitignored), deleted after use
```

## Rule 1: Docker only, nothing on the host

- Never install Java, Maven, Node.js or other toolchains on the host. Never run `mvn`, `java`,
  `node`, `npm` or `npx` on the host.
- Run every build and test through `scripts/dev.sh` or inside the dev container. `scripts/dev.sh`
  uses the image `portfolio-lens-dev` (built from `.devcontainer/`) and the volumes
  `portfolio-lens-m2` for the Maven cache and `portfolio-lens-cache` for the cache of
  `scripts/dev.sh check` and `scripts/dev.sh run`.
- When work is finished, clean up with `scripts/dev.sh clean`, and with `scripts/dev.sh purge` if
  the user wants. Never remove Docker containers, images or volumes of other projects.

| Task | Command |
| :--- | :--- |
| Unit tests | `scripts/dev.sh test` |
| One test class | `scripts/dev.sh test LookThroughEngineTest` |
| Build | `scripts/dev.sh package` |
| Check the configuration against the local PP | `scripts/dev.sh check` |
| One Lens run against the local PP | `scripts/dev.sh run` |
| Open the page in the browser on the Mac | `scripts/dev.sh page` |
| Shell in the container | `scripts/dev.sh shell` |
| OpenSpec | `scripts/openspec list` |
| Delete build output | `scripts/dev.sh clean` |
| Also remove image and volumes | `scripts/dev.sh purge` |

Dev container: "Dev Containers: Reopen in Container" in VS Code. The repository is mounted at its
host path and the host's Docker socket is shared, so `scripts/dev.sh` works inside it too; `mvn`
and `openspec` also run directly in its terminal. gh, Claude Code and Codex keep their logins in
shared volumes. Never enable format on save without a checked-in formatter configuration. Java
autobuild stays off (`.vscode/settings.json`), because a background build into `target/classes`
races with `scripts/dev.sh`.

## Rule 2: Everything is public, protect personal data

The repository is public. Never commit personal data of the user or anyone else: names, account
numbers, amounts, holdings, ISINs or portfolio files from real portfolios, provider API keys, PP
REST tokens.

- Real portfolios, REST responses from a real PP, provider payloads and keys go only into `scratch/`
  or a temporary folder outside the repository, and are deleted after use.
- Test fixtures are synthetic. Use well-known public ISINs and invented quantities and amounts.
- Never write real values into examples, comments, commit messages or rules.
- Before every commit and push, scan the diff for leaked values.

## Rule 3: License and code provenance

- The project is licensed under Apache-2.0 (`LICENSE`).
- Copy no file or code written by someone else, in particular no code or resources from PP (EPL-1.0).
  Code the user wrote in the PP development fork may be ported. Data such as country names comes from
  the JDK (`Locale`) or from sources whose license allows it, with attribution where required.
- New dependencies need a license compatible with Apache-2.0. Prefer the JDK over libraries.

## Rule 4: Integration with Portfolio Performance

- Lens talks to PP only through the REST API v1 (`http://127.0.0.1:5712/v1`, bearer token) and only
  with GET requests. It never writes to the portfolio.
- The v1 contract only adds fields and values. Unknown fields and enum values must be tolerated.
  The reference is PP's `openapi.yaml` at the tag `rest-api-v1.0.0`.
- PP only answers loopback requests without an `Origin` header, so Lens runs next to PP (same
  machine or same network namespace), never in a browser. PP checks the `Host` header, so a
  container reaches a PP on the host only through a forwarder on its own loopback
  (`.devcontainer/pp-forward.sh`).

## Rule 5: Data providers

- Fund compositions and company profiles come from external providers (initially Eulerpool) using
  the user's own API key. GLEIF register data is public (CC0).
- Lens is not affiliated with any provider. Provider permissions and data completeness are not
  verified. Users must make sure their own plan allows their use, including local caching. The
  README states this notice; keep it there.
- Keys come from configuration (environment or a secret file), never from the repository, and never
  appear in logs, output pages or diagnostics. Requests contain only instrument identifiers, never
  quantities or amounts.
- Provider payloads are never distributed, committed or published.

## Planning with OpenSpec

- New features are planned with [OpenSpec](https://openspec.dev) before code is written: explore,
  propose (planning only), apply, archive. Claude Code: `/opsx:propose` and so on. Codex:
  `$openspec-propose`. OpenCode: `/opsx-propose`.
- The OpenSpec CLI runs only in Docker. On the host, the skills and commands must call
  `scripts/openspec <args>` wherever they say `openspec <args>`. In the dev container `openspec`
  works directly.
- Project constraints for the artifacts are in `openspec/config.yaml`.
- After changing `OPENSPEC_VERSION` in `.devcontainer/Dockerfile`, rebuild the image and run
  `scripts/openspec update`.
- Keep progress, decisions and verification evidence in the change's artifacts. On resuming,
  inspect the artifacts and the Git state first.

## Git and pull requests

- `main` changes only through pull requests once the repository is on GitHub.
  Branches: `<type>/<openspec-change-name>`, for example `feature/standalone-lens-job`.
- Conventional Commits: `<type>(<optional-scope>): <description>`, subject at most 50 characters,
  body wrapped at 72, explaining what and why.
- Push, open pull requests and publish releases only when the user authorizes it.

## Versioning

Portfolio Lens follows [Semantic Versioning 2.0.0](https://semver.org/).

- The version lives in `pom.xml`. Between releases it is the next planned version with
  `-SNAPSHOT`, for example `0.2.0-SNAPSHOT`. Lens shows it in its log and in the page footer.
- A release is the commit on `main` tagged `vMAJOR.MINOR.PATCH`, for example `v0.1.0`, with the
  `-SNAPSHOT` suffix removed. Tag and publish only when the user authorizes it.
- A release candidate is tagged `vMAJOR.MINOR.PATCH-rc.N`, for example `v1.0.0-rc.1`, and is
  published as a prerelease for testing an integration.
- Every version change is a commit merged through a pull request, because `main` is protected:
  `chore(release): 1.0.0-rc.1` sets the version, and after the merge the user tags the merge commit.
  `chore(release): back to 1.0.0-SNAPSHOT` follows directly after a candidate. After a final
  release, `chore(release): 1.0.1-SNAPSHOT` sets the next planned version (patch by default, raised
  by the first `feat` or breaking commit).
- Pushing a tag `v*` starts `.github/workflows/release.yml`. It fails unless the commit is on `main`
  and the tag equals the `pom.xml` version, runs the tests, and publishes
  `portfolio-lens-<version>.tar.gz` with a `.sha256` file. A manual run of the workflow on any
  branch is a dry run that creates no tag and no release. Every jar in `target/lib/` must be listed
  in `release/THIRD-PARTY.md`, and the README section "Notice" is copied into the archive and the
  release notes, so keep both.
- The public interface that the version describes: the `lens` commands and options, the `LENS_*`
  settings and their meaning, the exit statuses, the fields of `status.json`, and the cache and
  state in `LENS_CACHE_DIR` (a release must read the data of the previous release or rebuild it by
  itself). The look of `index.html` is not part of it.
- MAJOR: an incompatible change of that interface. MINOR: new functionality that stays compatible.
  PATCH: compatible fixes. While the version is `0.y.z`, an incompatible change raises MINOR and
  everything else raises PATCH; `1.0.0` follows once Lens runs in the server integration.
- Conventional Commits show the effect: `feat` is at least MINOR, `fix` PATCH, and `!` or a
  `BREAKING CHANGE:` footer MAJOR (MINOR before `1.0.0`). Name an interface change in the commit
  body and in the pull request.

## Delegating to subagents

This rule always applies. The user does not repeat it per task.

- A subagent never runs on the model of the main session by default. Name its model in the call,
  and pick the smallest one that does the job.
- Smallest tier (for Claude Code: Haiku): running tests and builds and reporting the result,
  searching the code, collecting facts, mechanical edits with an exact instruction.
- Middle tier (Sonnet): self-contained work with a clear brief, such as writing or updating tests
  for code that exists, documentation, and a contained piece of logic with its tests.
- Main session: design decisions, work that touches several coupled files at once, and anything
  where the brief would be as long as the work.
- Delegate what produces long output, such as Maven logs, so it stays out of the main context.
  Ask for a short result: pass or fail, the failing tests and the relevant lines.
- Builds and tests share `target/`. Never let two of them run at the same time, whether from the
  main session, a subagent or the dev container.
- A subagent report is not proof. Check the diff or the test result before stating that
  something is done.

## Page design

The page has a small design system, described at the top of
`src/main/java/io/portfoliolens/page/Style.java`: tokens on `:root` (surfaces, text, one accent
colour for everything interactive, two corner radii, one control height, one shadow) and four
building blocks (controls, cards, chips, type sizes).

- New page elements use these tokens and blocks. Add no colour, radius, control height or shadow
  value outside `:root`.
- Chart colours are data colours. Do not use them for controls, and do not use the accent for data.
- Every colour token has a light and a dark value.

## Writing texts

- Code, comments, commits, docs and specs in English.
- Plain, factual wording. Avoid typical AI phrasing such as em dashes and filler. Only state what is
  verified.
