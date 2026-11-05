## ADDED Requirements

### Requirement: Release trigger
A release SHALL be built only for a pushed tag `vX.Y.Z` or `vX.Y.Z-rc.N` whose commit is on `main` and whose version without the `v` equals the version in `pom.xml`. Any other tag, a commit outside `main` or a mismatching version SHALL stop the workflow before anything is published.

#### Scenario: Tag matches the release commit
- **WHEN** `pom.xml` on `main` is `1.0.0-rc.1` and the tag `v1.0.0-rc.1` is pushed for that commit
- **THEN** the workflow builds, tests and publishes the release `v1.0.0-rc.1`.

#### Scenario: Tag does not match the version
- **WHEN** the tag `v1.0.0` is pushed for a commit whose `pom.xml` is `1.0.0-SNAPSHOT`
- **THEN** the workflow fails and creates no release.

#### Scenario: Commit outside main
- **WHEN** a matching tag is pushed for a commit that is not on `main`
- **THEN** the workflow fails and creates no release.

### Requirement: Tested build
The release workflow SHALL run the same build and tests as CI (`mvn -B verify` with Java 21 and Node.js 22) and SHALL fail when the tests that need Node.js were skipped.

#### Scenario: Failing test
- **WHEN** a test fails on the tagged commit
- **THEN** no archive is published.

### Requirement: Release archive
A release SHALL attach `portfolio-lens-<version>.tar.gz` with one top-level directory `portfolio-lens-<version>/` containing `portfolio-lens.jar`, `lib/` with the runtime dependencies, `LICENSE` and a `NOTICE` with the README notice and the third-party licenses, and SHALL attach `portfolio-lens-<version>.tar.gz.sha256` in `sha256sum` format.

#### Scenario: Verify and run
- **WHEN** a consumer downloads both files, runs `sha256sum -c` and unpacks the archive
- **THEN** the checksum matches, and `java -jar portfolio-lens.jar check` runs with Java 21 and reports the release version.

### Requirement: Release notes and prereleases
The release notes SHALL contain the version, the notice from the README, the checksum and the Java 21 requirement. A release for a `-rc.N` tag SHALL be marked as a prerelease; a release for `vX.Y.Z` SHALL NOT.

#### Scenario: Release candidate
- **WHEN** the tag `v1.0.0-rc.1` is released
- **THEN** the GitHub release is marked as a prerelease and its notes contain the notice.

### Requirement: Least privilege
The workflow SHALL default to read-only repository access. Only the job that creates the release SHALL have write access to contents, and actions SHALL be pinned by major version as in CI.

#### Scenario: Build job
- **WHEN** the build and test job runs
- **THEN** it has read access to contents only and cannot create releases.

### Requirement: Dry run without publishing
The workflow SHALL support a manual run on any branch that builds and tests, creates the archive and checksum from the version in `pom.xml`, and uploads them as a workflow artifact, without creating a tag or a release.

#### Scenario: Manual run on a branch
- **WHEN** the workflow is started manually on a branch with version `1.0.0-SNAPSHOT`
- **THEN** a workflow artifact with `portfolio-lens-1.0.0-SNAPSHOT.tar.gz` and its checksum exists, and the repository has no new tag or release.

### Requirement: Version flow
Every version change SHALL be a commit merged into `main` through a pull request. Before a release the version SHALL be set to the release version; after it, to the next planned version with `-SNAPSHOT` (after a release candidate the same final version, after a final release the next patch version). Tags and releases SHALL be created only with the user's authorization.

#### Scenario: From release candidate to final release
- **WHEN** `v1.0.0-rc.1` was released and the server integration with it works
- **THEN** `main` goes from `1.0.0-SNAPSHOT` to `1.0.0`, is tagged `v1.0.0` with the user's authorization, and then moves to `1.0.1-SNAPSHOT`.

### Requirement: Consumer contract
The README SHALL document what a consumer of a release archive relies on: checksum verification, a Java 21 runtime with `lib/` next to the jar, the `check` and `run` commands, exit statuses, `status.json` in the output directory, configuration through `LENS_*` settings only, `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true` for provider data, loopback access to Portfolio Performance on `127.0.0.1:5712`, and persistent cache and output directories.

#### Scenario: Container on the server
- **WHEN** an infrastructure repository runs a release in a Java 21 container that shares Portfolio Performance's network namespace
- **THEN** the README states every setting, path, exit status and file it needs, without reference to the Lens source tree.
