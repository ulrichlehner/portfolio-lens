# Design

## Context

See proposal.md for the motivation. The current state that shapes the approach:

- `.github/workflows/release.yml` runs for pushed tags `vX.Y.Z` and `vX.Y.Z-rc.N` and for a manual
  run. Its `build` job reads the version from `pom.xml`, checks that the tag equals it and that the
  commit is on `main`, runs `mvn -B verify`, packs the archive and uploads it. Its `release` job
  runs only for tags and calls `gh release create`.
- `pom.xml` holds `<version>1.1.0-SNAPSHOT</version>` and writes `${project.version}` into the jar
  manifest as `Implementation-Version`. `Version.java` reads it from there and falls back to a
  development value outside a jar.
- `main` is protected and changes only through pull requests. Pull requests are merged with merge
  commits ("Merge pull request #N ..."), so the commits of a pull request are reachable from `main`
  with their own subjects.
- The last final tag is `v1.0.0`. A release candidate tag `v1.0.0-rc.1` exists.
- `.github/workflows/ci.yml` runs `mvn -B verify` for pull requests and pushes to `main`.
- Commits follow Conventional Commits, and `AGENTS.md` already maps `feat`, `fix` and breaking
  changes to version parts.

## Goals / Non-Goals

**Goals:**

- A merge into `main` releases without any commit to `main` by the workflow, so branch protection
  stays as it is.
- The version rule is a small script that is tested without GitHub.
- One workflow file keeps building the archive the same way for every trigger.

**Non-Goals:**

- A release tool as a dependency (release-please, semantic-release).
- Changing how `Version.java` reads the version.
- Rewriting existing tags or releases.

## Decisions

### D1 Version as the Maven property `revision`

`pom.xml` gets `<version>${revision}</version>` and the property
`<revision>0.0.0-SNAPSHOT</revision>`. The workflow builds with `-Drevision=<version>`. This is
Maven's own mechanism for a version given from outside; for a single module that is not published
to a Maven repository it needs no extra plugin. The manifest entry and the archive name follow
from `project.version` as today.

Alternative: `versions:set` in the workflow before the build. Rejected: it rewrites `pom.xml` in
the workspace and needs a plugin download for something a property does.

Alternative: keep the version in `pom.xml` and let the workflow commit the change. Rejected: the
workflow would need to push to the protected branch.

### D2 The version rule as a script

`scripts/next-version.sh` reads two things: the last final version, and commit messages on
standard input, separated by a NUL byte (`git log -z --format=%B`). It prints the next version, or
nothing when no release is due.

- The subject line is matched against `^(type)(\(scope\))?(!)?: `.
- A `!` there, or a line starting with `BREAKING CHANGE:` or `BREAKING-CHANGE:` anywhere in the
  message, gives MAJOR.
- `feat` gives MINOR, `fix` and `perf` give PATCH. Every other type and every subject that does not
  match gives nothing.
- The highest level found is applied once to the last version.

With the option `--changes` it prints the subjects of the releasable commits, one per line, for the
release notes.

`scripts/next-version.test.sh` feeds fixed inputs and compares the output: the scenarios of the
requirement "Version from commits", plus a body that mentions "BREAKING CHANGE" in the middle of a
line (no bump), a `fix!` (MAJOR), and an empty input. It needs only `bash`, so it runs in CI, in
the dev container and on the host. `ci.yml` runs it as a step.

Alternative: the rule in Java with a JUnit test. Rejected: the release workflow would have to
build the project to learn the version it must build with.

The script lists commits with `git log --no-merges <last tag>..HEAD`. The last final tag is found
with `git describe --tags --abbrev=0 --match 'v[0-9]*.[0-9]*.[0-9]*' --exclude '*-rc.*'`. The
workflow already checks out with full history.

### D3 One workflow, three triggers

`release.yml` runs on `push` to `main`, on `push` of version tags, and on `workflow_dispatch`. A
first step decides the mode and the version and writes both as step outputs:

| Trigger | Version | Publishes |
| :--- | :--- | :--- |
| push to `main` | from the script; empty means no release | tag and release, when not empty |
| tag `vX.Y.Z` or `vX.Y.Z-rc.N` | the tag without `v`; the commit must be on `main` | release for that tag |
| manual run | the script's version, or the next patch version, plus `-SNAPSHOT` | nothing |

On a push to `main` without a due release the build job ends after that step with success, so the
run is green and short. On a push to `main` whose commit already has a final tag, the same happens.

The `release` job runs when a version is to be published. For a push to `main` it calls
`gh release create v<version> --target <sha>`, which creates the tag on the merged commit through
the API. For a pushed tag it keeps `--verify-tag`. Only this job has `contents: write`.

A tag created with the workflow token does not start another workflow run, so the release does not
trigger itself.

### D4 Order of releases

The workflow uses one concurrency group for pushes to `main` with `cancel-in-progress: false`. Two
merges shortly after each other then release one after the other. If GitHub drops a waiting run in
favour of a newer one, the newer run still covers all commits, because the version is computed
from everything since the last final tag.

A run that finds the tag of its computed version already present on another commit fails with a
clear message. This can only happen when a final tag was pushed by hand in between.

### D5 Release notes

The notes keep their present content. For a release on merge a section "Changes" lists the
subjects from `next-version.sh --changes`. Commit subjects are public already and contain no
personal data by the rules of the repository.

### D6 Documentation and rules

- `AGENTS.md`, section "Versioning": the version is the latest release tag; `pom.xml` holds the
  placeholder; the commit types decide the version part; no `chore(release)` commits; release
  candidates by tag; merging is the user's step and the authorization to release. The section "Git
  and pull requests" names that agents do not merge pull requests.
- `README.md`, sections "Releases" and "Versions": releases follow merges into `main`; how to find
  the latest release; development builds report `0.0.0-SNAPSHOT`.

The page footer and the log of a development build then show `0.0.0-SNAPSHOT` instead of the next
planned version. The version is part of the input fingerprint of a run; a development build
already differs from a release there, so nothing changes for the cache.

## Risks / Trade-offs

- [Every `feat` or `fix` on `main` is published at once] → `main` must be releasable at any time.
  Pull requests run the full suite, and the release workflow runs it again before publishing.
- [A wrongly typed commit raises the wrong version part, for example `feat` for a fix] → the
  version is still valid and ordered; the pull request title and commits are reviewed before the
  merge. A release cannot be withdrawn cleanly, so the next release corrects it.
- [A merge of only `docs` commits releases earlier unreleased fixes] → intended, and stated in the
  spec. It keeps the rule simple: `main` is released whenever it holds something releasable.
- [A tag rule or a restricted workflow token blocks the tag] → the release job fails visibly and
  nothing is half published; the task list has a check of the repository settings before the
  first release.
- [The first run publishes `1.1.0` as soon as this change is merged] → named in the proposal and in
  the pull request, so the merge is a decision to release.
- [Local builds no longer show which version is in development] → accepted; the latest tag and the
  commits tell it.

## Changes during implementation

- D3: the decision is a job of its own, `plan`, before the build job, not a first step of the build
  job. The build job has the condition that a version is set, so a push to `main` without a due
  release ends after a few seconds without setting up Java. The release job reads tag, version and
  commit range from the outputs of `plan`.
- D2: a breaking commit of any type is releasable, for example `refactor!`, and is listed in the
  changes of the release notes.
- Repository settings (task 1.3): the repository has no rulesets and no tag protection. The default
  workflow token permission is read; the release job asks for `contents: write` itself.
- The decision step was run locally with the workflow's own shell code for a push to `main`
  (`1.1.0`, publish), a manual run (`1.1.0-SNAPSHOT`, no publish), a release candidate tag, a tag
  outside `main` (fails) and a commit that already has a final tag (nothing to release). The
  publishing itself cannot be tried before the merge.

## Migration Plan

1. Merge this change. The push to `main` runs the new workflow, which computes `1.1.0` from the
   commits since `v1.0.0` and publishes it.
2. If that run fails before publishing, nothing is released; fix forward with a `ci` commit.
3. Rollback: revert the workflow, the script and `pom.xml` in one pull request and set the version
   in `pom.xml` to the next planned `-SNAPSHOT`. Published releases stay.

## Open Questions

- Whether the server setup should follow the latest release by itself. It does not change this
  design; the README names where the latest release is found.
