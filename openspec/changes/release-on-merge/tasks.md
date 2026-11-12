# Tasks

## 1. Branch and baseline

- [x] 1.1 Verify that no pull request that should be released separately is open, then create the branch `ci/release-on-merge` from `main` and verify with `git status` that it is clean
- [x] 1.2 Run `scripts/dev.sh test` and verify that all tests pass before any change
- [x] 1.3 Read the repository settings with `gh api repos/{owner}/{repo}/rulesets` and `gh api repos/{owner}/{repo}/actions/permissions/workflow` and verify that no rule forbids the workflow token to create tags `v*`; if one does, stop and report it to the user

## 2. Version rule (design D2)

- [x] 2.1 Add `scripts/next-version.sh`: last final version as argument, NUL-separated commit messages on standard input, the next version or nothing on standard output, and `--changes` for the subjects of the releasable commits
- [x] 2.2 Add `scripts/next-version.test.sh` with the scenarios of "Version from commits" (fix only, feature and fix, breaking change with `!`, breaking change by footer, commit without convention) and the cases: only `docs` and `chore` gives no output, empty input gives no output, "BREAKING CHANGE" inside a sentence gives no MAJOR, `fix!` gives MAJOR, `--changes` lists only releasable subjects; verify by running `bash scripts/next-version.test.sh` with exit status 0
- [x] 2.3 Verify the script against the real history: with `1.0.0` and the commits since the tag `v1.0.0` it prints `1.1.0`
- [x] 2.4 Run the script tests as a step of `.github/workflows/ci.yml`

## 3. Version as build input (design D1)

- [x] 3.1 Change `pom.xml` to `<version>${revision}</version>` with the property `revision` set to `0.0.0-SNAPSHOT`; verify with `scripts/dev.sh test` that all tests pass
- [x] 3.2 Verify in the dev container that `mvn -B -q -Drevision=9.9.9 package -DskipTests` writes `Implementation-Version: 9.9.9` into the manifest of `target/portfolio-lens.jar`, and that a build without the parameter writes `0.0.0-SNAPSHOT`
- [x] 3.3 Run `scripts/dev.sh run` against the local Portfolio Performance and verify that the page footer shows `0.0.0-SNAPSHOT`; keep the output in `scratch/` only

## 4. Workflow (design D3, D4, D5)

- [x] 4.1 Add the triggers `push` to `main` next to the tags and the manual run, and a first step that decides mode and version as in the table of design D3, including the check that a tagged commit is on `main`
- [x] 4.2 End the build job early with success when a push to `main` has no release due or its commit already has a final tag
- [x] 4.3 Pass the version to every Maven call with `-Drevision`, and keep the archive, checksum, manifest and third-party checks as they are
- [x] 4.4 Create the release for a push to `main` with `gh release create v<version> --target <sha>`, keep `--verify-tag` for pushed tags, and add the "Changes" section to the notes of a release on merge
- [x] 4.5 Set the concurrency group for pushes to `main` as in design D4 and fail with a clear message when the computed tag exists on another commit
- [ ] 4.6 Verify the workflow file with a manual dry run on the branch (`gh workflow run release.yml --ref ci/release-on-merge` after the user authorizes the push): the artifact is `portfolio-lens-1.1.0-SNAPSHOT.tar.gz` with its checksum, and the repository has no new tag or release

## 5. Documentation and rules (design D6)

- [x] 5.1 Rewrite the section "Versioning" in `AGENTS.md` for the new flow and add to "Git and pull requests" that agents do not merge pull requests; verify that no sentence still describes `chore(release)` commits or a version in `pom.xml`
- [x] 5.2 Update the README sections "Releases" and "Versions" and verify that they describe only what the workflow does
- [x] 5.3 Verify with `scripts/openspec validate release-on-merge --strict` that the change is valid

## 6. Privacy scan and pull request

- [x] 6.1 Run `scripts/dev.sh test` and `bash scripts/next-version.test.sh` and verify that both pass
- [x] 6.2 Scan the diff for personal data (names, amounts, holdings, ISINs of a real portfolio, keys, tokens) and verify that `scratch/` is not staged
- [x] 6.3 Commit with Conventional Commits as `ci(release): ...`, naming in the body that the version no longer lives in `pom.xml`
- [ ] 6.4 After the user authorizes it, push the branch and open the pull request into `main`, stating that merging it publishes `1.1.0`
- [ ] 6.5 Run `scripts/dev.sh clean`

## Workflow follow-up

- After the user merges the pull request: check that the release run published `v1.1.0` with the
  archive, the checksum and the "Changes" section, and that `java -jar portfolio-lens.jar check`
  from the downloaded archive reports `1.1.0`. If the run failed, report the failing step.
- Archive the change after the release is verified.
- Server setup, outside this repository: decide how the server picks up a new release.
