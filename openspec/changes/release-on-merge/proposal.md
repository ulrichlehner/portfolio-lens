# Proposal

## Why

A release takes two extra pull requests and a manual tag today: one sets the version in `pom.xml`,
the user tags the merge commit, and a second pull request sets the next `-SNAPSHOT` version. For a
project with one maintainer this is overhead on every release, and merged work stays unreleased
until someone does it. Since `v1.0.0`, two feature changes are merged and not released.

## What Changes

- A merge into `main` publishes a release by itself when the merged commits contain a `feat`, a
  `fix`, a `perf` or a breaking change. A merge with only other commit types (`docs`, `chore`,
  `test`, `ci`, `refactor`, `build`, `style`) publishes nothing.
- The version of that release is computed from the Conventional Commits since the last final
  release tag: a breaking change raises MAJOR, a `feat` raises MINOR, a `fix` or `perf` raises
  PATCH. The workflow creates the tag `vX.Y.Z` on the merged commit and the GitHub release.
- **BREAKING** for the release process, not for users of a release: the version no longer lives in
  `pom.xml`. The build takes it as a parameter, and the latest release tag is the version. The
  pull requests `chore(release): X.Y.Z` and `chore(release): X.Y.Z-SNAPSHOT` are no longer used.
  A build without that parameter, for example on a developer machine, has the version
  `0.0.0-SNAPSHOT`.
- Release candidates stay manual: a pushed tag `vX.Y.Z-rc.N` on `main` publishes a prerelease. A
  manually pushed final tag `vX.Y.Z` on `main` still publishes that release.
- The release notes get a list of the changes since the previous release, taken from the commit
  subjects.
- The manual dry run stays and builds the version that the next merge would release, with
  `-SNAPSHOT`.
- `AGENTS.md` (section "Versioning") and the README sections "Releases" and "Versions" describe the
  new flow. Merging a pull request into `main` is the authorization to release it.

The first merge into `main` after this change publishes `1.1.0`, because `main` holds unreleased
`feat` commits since `v1.0.0`.

The archive, its checksum, the notice, the settings, exit statuses, `status.json` and the cache
format do not change. This change is `ci`, so it raises no version by itself.

## Out of scope

- Deployment. A release is still not installed anywhere; the server setup that runs Lens is in
  another repository and decides how it picks up a release.
- A changelog file in the repository. The release notes carry the changes.
- Automatic release candidates.
- Signing the archive or publishing to a package registry.
- Changing the branch protection of `main` or how pull requests are merged.
- A version shown by local development builds that names the branch or commit.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `release`: the trigger (merge into `main`, or a manual tag), how the version is determined, when
  no release is made, the changes in the release notes, the dry run version, and the version flow
  without version commits.

## Impact

- `.github/workflows/release.yml`: runs on pushes to `main` and on version tags; computes the
  version; creates the tag and the release.
- `scripts/next-version.sh` (new) with `scripts/next-version.test.sh`: the version rule as a shell
  script with its tests, run in CI.
- `.github/workflows/ci.yml`: runs the script tests.
- `pom.xml`: the version becomes a build parameter with the default `0.0.0-SNAPSHOT`.
- `AGENTS.md`, `README.md`: versioning and release description.
- `openspec/specs/release/spec.md` through the delta of this change.
- No new runtime dependency. No change to the Java code.
- Needs the repository to allow the workflow token to create tags `v*`; if a tag rule forbids it,
  the rule has to be adjusted by the user.
