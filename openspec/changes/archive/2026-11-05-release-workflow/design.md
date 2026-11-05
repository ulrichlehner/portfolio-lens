# Design

## Context

- `pom.xml` is at `0.1.0-SNAPSHOT` and builds `target/portfolio-lens.jar` with a manifest class
  path to `lib/` and `target/lib/` with the runtime dependencies (Gson, Apache-2.0). The jar
  manifest carries `Implementation-Version`, which Lens shows in its log and page footer.
- `.github/workflows/ci.yml` runs `mvn -B verify` with Temurin Java 21 and Node.js 22 and fails
  when the Node.js tests (`TreemapParityTest`, `PageSmokeTest`) were skipped. It pins actions by
  major version (`actions/checkout@v4`, `actions/setup-java@v4`, `actions/setup-node@v4`) and has
  `contents: read`.
- `main` is protected: changes only through pull requests, and the branch must be up to date with
  a passing "Maven build" check. AGENTS.md "Versioning" defines Semantic Versioning, releases as
  tags `vMAJOR.MINOR.PATCH` on `main`, and the public interface (commands, `LENS_*` settings, exit
  statuses, `status.json`, cache and state).

## Goals / Non-Goals

Goals: one tag push produces a tested, versioned archive with a checksum; a dry run proves the
workflow without publishing; the version shown by Lens equals the tag; the consumer contract is
written down.

Non-goals: container images, registries, signing, reproducible builds, the server integration.

## Decisions

### D1 Trigger and checks

`on.push.tags` with the patterns `v[0-9]+.[0-9]+.[0-9]+` and `v[0-9]+.[0-9]+.[0-9]+-rc.[0-9]+`, and
`workflow_dispatch` for the dry run. For a tag the build job checks, before building:

- the tag commit is on `main`: `git merge-base --is-ancestor "$GITHUB_SHA" origin/main` after a
  checkout with `fetch-depth: 0`;
- the tag without its `v` equals `project.version` from `pom.xml`
  (`mvn -B -q help:evaluate -Dexpression=project.version -DforceStdout`), which therefore has no
  `-SNAPSHOT`.

Either failure stops the workflow before anything is published.

### D2 Jobs and permissions

- `build` (`contents: read`): checkout, Java 21 and Node.js 22 as in CI, the checks of D1,
  `mvn -B verify`, the same skipped-Node.js-tests check as CI, then the archive and checksum
  (D3), uploaded with `actions/upload-artifact@v4`.
- `release` (only for tag pushes, `needs: build`, `contents: write`): downloads the artifact with
  `actions/download-artifact@v4` and runs
  `gh release create "$TAG" <archive> <archive>.sha256 --verify-tag --title "Portfolio Lens <version>" --notes-file notes.md`,
  adding `--prerelease` for `-rc.N` tags. `GH_TOKEN` is the job's `GITHUB_TOKEN`.

The workflow default is `permissions: contents: read`; only the release job gets write access.
`concurrency: release-${{ github.ref }}` without cancelling, so a release is never cut half way.

### D3 Archive

`portfolio-lens-<version>.tar.gz` with one top-level directory `portfolio-lens-<version>/`:

```
portfolio-lens-<version>/
├── portfolio-lens.jar
├── lib/            runtime dependencies (gson-<v>.jar)
├── LICENSE         Apache-2.0
└── NOTICE          the README notice and the third-party list (Gson, Apache-2.0)
```

`NOTICE` is generated in the workflow from the README "Notice" section and a fixed third-party
list kept in the repository (`release/THIRD-PARTY.md`), so a new dependency must be listed there.
The checksum file `portfolio-lens-<version>.tar.gz.sha256` is the output of `sha256sum` (hash, two
spaces, file name), so `sha256sum -c` verifies it.

### D4 Release notes

`notes.md` contains the version, the README "Notice" section verbatim, the checksum line, the
Java 21 requirement and a link to the README sections "Configuration" and "Releases". Release
candidates add one line that they are for testing the server integration.

### D5 Version flow

Every version change is a commit merged into `main` through a pull request, because `main` is
protected:

1. `chore(release): 1.0.0-rc.1` sets `pom.xml` to `1.0.0-rc.1`. After the merge the user tags the
   merge commit `v1.0.0-rc.1` and pushes the tag.
2. `chore(release): back to 1.0.0-SNAPSHOT` follows directly, so builds from `main` never claim a
   released version. Further candidates repeat 1 and 2 with `rc.2` and so on.
3. Once the server integration with a candidate is verified, `chore(release): 1.0.0` and the tag
   `v1.0.0`.
4. Then `chore(release): 1.0.1-SNAPSHOT`. The next planned version is a patch by default and is
   raised to `1.1.0-SNAPSHOT` by the first `feat` commit, or `2.0.0-SNAPSHOT` by a breaking one.

Under Semantic Versioning `1.0.0-SNAPSHOT` sorts before `1.0.0-rc.1`; Lens never compares versions,
so this only affects the label of development builds. AGENTS.md "Versioning" is extended with
release candidates and these steps.

### D6 Dry run

`workflow_dispatch` on any branch runs the build job with the version from `pom.xml` (a snapshot
is allowed), skips the tag and `main` checks, and uploads the archive and checksum as a workflow
artifact. The release job does not run. The dry run before the first release is run on the
workflow's own pull request branch and its artifact is downloaded and checked.

### D7 Consumer contract

The README section "Releases" states what a consumer relies on, which the infrastructure
repository uses:

- download the archive and its `.sha256` from the GitHub release and verify with `sha256sum -c`;
- unpack and run `java -jar portfolio-lens.jar check` and `run` with a Java 21 runtime, for
  example `eclipse-temurin:21-jre`; `lib/` must stay next to the jar;
- exit statuses 0, 64, 69, 70, 73 as in "Commands and exit statuses";
- `status.json` in `LENS_OUTPUT_DIR` for monitoring, next to `index.html`;
- configuration only through `LENS_*` settings (environment or `--config` file); secrets never as
  arguments;
- provider data needs `LENS_EULERPOOL_API_KEY` and `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true`,
  otherwise `check` fails with 64;
- Portfolio Performance's REST API on `127.0.0.1:5712` must be reachable on loopback, so the
  container shares PP's network namespace or uses a forwarder that keeps the `Host` header;
- `LENS_CACHE_DIR` and `LENS_OUTPUT_DIR` are persistent volumes; one run at a time per cache
  directory (a lock file guards it).

## Risks / Trade-offs

- A tag can only be pushed by someone with write access; the checks of D1 still prevent a release
  from a commit outside `main` or with a mismatching version.
- `gh release create` with `--verify-tag` fails if the tag disappeared between push and job, which
  is the wanted outcome.
- The archive is not signed. The checksum detects transfer errors, not a compromised account.

## Migration Plan

None: the workflow only adds releases. The first release is `v1.0.0-rc.1`.

## Open Questions

- Should a release also be blocked unless the CI run of the same commit passed? The workflow runs
  the same build and tests itself, so this design does not wait for CI.
- Keep `1.0.0-SNAPSHOT` on `main` between candidates (D5), or leave `main` at the candidate version
  until the next one?
