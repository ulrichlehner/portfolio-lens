# Proposal

## Why

Portfolio Lens is meant to run on the user's server, next to Portfolio Performance, from a
scheduler. Today it exists only as source code that is built with `scripts/dev.sh package`. The
server integration in the user's infrastructure repository needs a versioned, verifiable artifact
that it can download and run with a plain Java 21 runtime, and Lens needs a defined way to publish
one: first a release candidate for the server test, then 1.0.0 once the integration works.

## What Changes

- A GitHub Actions workflow `.github/workflows/release.yml` publishes a release when a tag
  `vX.Y.Z` or `vX.Y.Z-rc.N` is pushed for a commit on `main`. It builds and tests like CI, fails
  unless the tag equals the version in `pom.xml`, and attaches `portfolio-lens-<version>.tar.gz`
  with `portfolio-lens.jar`, `lib/`, `LICENSE` and `NOTICE`, plus a SHA-256 checksum file. Release
  candidates are marked as prereleases. The release notes carry the notice from the README.
- A manual dry run of the same workflow builds the archive and checksum as a workflow artifact
  without creating a tag or a release.
- The version flow from `0.1.0-SNAPSHOT` over `1.0.0-rc.1` to `1.0.0` and then `1.0.1-SNAPSHOT`,
  with release commits merged through pull requests, documented in AGENTS.md and the README.
- The documented contract that a consumer such as a container on the server relies on: archive
  layout, checksum verification, Java 21 runtime, commands, exit statuses, `status.json`, the
  `LENS_*` settings, the required local storage confirmation for provider data, and loopback
  access to Portfolio Performance.

Out of scope:

- The server integration itself: container image, schedule, reverse proxy (Caddy), authentication
  (Authelia) and secrets live in the user's infrastructure repository.
- Container images, package registries, signing beyond a checksum, and reproducible builds.
- Publishing a release or pushing a tag: both need the user's authorization each time.
- Changes to Lens's runtime behaviour.

## Capabilities

### New Capabilities

- `release`: how versions are released and published as verifiable archives, and the contract
  that a consumer of a release archive relies on.

### Modified Capabilities

None.

## Impact

- New `.github/workflows/release.yml`; `ci.yml` unchanged.
- `AGENTS.md` "Versioning" (release candidates, release commits through pull requests, next
  snapshot) and a README section "Releases".
- GitHub: tags `v*` and releases on `ulrichlehner/portfolio-lens`, created only with the user's
  authorization.
