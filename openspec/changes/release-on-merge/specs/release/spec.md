# Spec Delta

## ADDED Requirements

### Requirement: Release on merge
A push to `main` SHALL publish a final release for the pushed commit when the commits since the last final release tag contain at least one releasable commit. The workflow SHALL create the tag `vX.Y.Z` on that commit and the GitHub release, without a version commit. When the commits contain no releasable commit, or the pushed commit already carries a final release tag, the workflow SHALL publish nothing and SHALL end successfully.

#### Scenario: Merged feature
- **WHEN** the last final release is `v1.0.0` and a pull request with a `feat` commit is merged into `main`
- **THEN** the workflow builds and tests the merged commit, tags it `v1.1.0` and publishes the release `v1.1.0`.

#### Scenario: Merge without releasable commits
- **WHEN** a pull request whose commits are all of the types `docs` and `chore` is merged into `main` directly after a release
- **THEN** the workflow creates no tag and no release and ends successfully.

#### Scenario: Unreleased commits from earlier merges
- **WHEN** a `docs` pull request is merged while an earlier merged `fix` commit is still unreleased
- **THEN** the workflow publishes a release that contains the fix.

#### Scenario: Failing test
- **WHEN** a test fails on the merged commit
- **THEN** no tag and no release are created.

### Requirement: Version from commits
The version of a release on merge SHALL be computed from the Conventional Commits between the last final release tag and the pushed commit, ignoring release candidate tags and merge commits. A commit with `!` after its type or scope, or with a `BREAKING CHANGE:` footer, SHALL raise MAJOR. Otherwise a `feat` commit SHALL raise MINOR. Otherwise a `fix` or `perf` commit SHALL raise PATCH. Other commit types SHALL NOT raise the version. The highest of these applies once.

#### Scenario: Fix only
- **WHEN** the last final release is `v1.1.0` and the commits since are one `fix` and two `docs`
- **THEN** the next version is `1.1.1`.

#### Scenario: Feature and fix
- **WHEN** the last final release is `v1.1.1` and the commits since are two `feat` and one `fix`
- **THEN** the next version is `1.2.0`.

#### Scenario: Breaking change
- **WHEN** the last final release is `v1.2.0` and one commit since is `feat(job)!: rename a setting`
- **THEN** the next version is `2.0.0`.

#### Scenario: Release candidate in between
- **WHEN** the last final release is `v1.2.0`, a tag `v1.3.0-rc.1` exists on a later commit, and a `feat` commit follows
- **THEN** the next version is `1.3.0`, computed from all commits since `v1.2.0`.

#### Scenario: Commit that follows no convention
- **WHEN** a commit subject does not start with a Conventional Commits type
- **THEN** it does not raise the version.

### Requirement: Release by tag
A pushed tag `vX.Y.Z-rc.N` or `vX.Y.Z` whose commit is on `main` SHALL publish a release with the version of the tag. A tag on a commit outside `main`, or a tag of another form, SHALL stop the workflow before anything is published.

#### Scenario: Release candidate
- **WHEN** the tag `v1.3.0-rc.1` is pushed for a commit on `main`
- **THEN** the workflow builds, tests and publishes the prerelease `v1.3.0-rc.1`, and the archive reports the version `1.3.0-rc.1`.

#### Scenario: Commit outside main
- **WHEN** a version tag is pushed for a commit that is not on `main`
- **THEN** the workflow fails and creates no release.

### Requirement: Version as build input
The version SHALL be passed to the build by the workflow and SHALL NOT be stored in `pom.xml`. The archive name, its top-level directory and the version that Lens reports SHALL equal the released version. A build without a given version SHALL have the version `0.0.0-SNAPSHOT`.

#### Scenario: Released archive
- **WHEN** the release `v1.1.0` is published
- **THEN** the archive is `portfolio-lens-1.1.0.tar.gz` and `java -jar portfolio-lens.jar check` reports the version `1.1.0`.

#### Scenario: Local build
- **WHEN** a developer builds without giving a version
- **THEN** Lens reports the version `0.0.0-SNAPSHOT` in its log and in the page footer.

### Requirement: Release authorization
Merging a pull request into `main` SHALL count as the authorization to release it. Pull requests SHALL be merged, and release candidate tags SHALL be pushed, only with the user's authorization.

#### Scenario: Agent finishes a change
- **WHEN** an agent has opened a pull request for a `feat` change
- **THEN** it does not merge the pull request itself, and the release happens when the user merges it.

## MODIFIED Requirements

### Requirement: Release notes and prereleases
The release notes SHALL contain the version, the notice from the README, the checksum, the Java 21 requirement and, for a release on merge, the subjects of the releasable commits since the previous final release. A release for a `-rc.N` tag SHALL be marked as a prerelease; a release for `vX.Y.Z` SHALL NOT.

#### Scenario: Release candidate
- **WHEN** the tag `v1.0.0-rc.1` is released
- **THEN** the GitHub release is marked as a prerelease and its notes contain the notice.

#### Scenario: Changes of a release on merge
- **WHEN** a release on merge contains one `feat` and one `fix` commit and three `docs` commits
- **THEN** its notes list the subjects of the `feat` and the `fix` commit and not those of the `docs` commits.

### Requirement: Dry run without publishing
The workflow SHALL support a manual run on any branch that builds and tests, creates the archive and checksum, and uploads them as a workflow artifact, without creating a tag or a release. Its version SHALL be the version that a merge of that branch would release, or the next patch version when no release would be due, with the suffix `-SNAPSHOT`.

#### Scenario: Manual run on a branch
- **WHEN** the workflow is started manually on a branch that adds a `feat` commit to `main`, whose last final release is `v1.1.0`
- **THEN** a workflow artifact with `portfolio-lens-1.2.0-SNAPSHOT.tar.gz` and its checksum exists, and the repository has no new tag or release.

## REMOVED Requirements

### Requirement: Release trigger
**Reason**: A release is no longer built only for a pushed tag that must equal the version in `pom.xml`. The version is not stored in `pom.xml` any more.
**Migration**: "Release on merge" covers releases from pushes to `main`, and "Release by tag" keeps tags for release candidates and manual final releases, including the rule that the commit must be on `main`.

### Requirement: Version flow
**Reason**: Versions are no longer changed through commits, so there is no flow from a release version to a `-SNAPSHOT` version.
**Migration**: "Version from commits" and "Version as build input" define the version, and "Release authorization" keeps the rule that the user decides what is released.
