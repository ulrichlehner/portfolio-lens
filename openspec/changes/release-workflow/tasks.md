# Tasks

Branch `feature/release-workflow` from `origin/main` for the implementation (this planning change
is on `docs/release-workflow`).

## 1. Workflow

- [x] 1.1 Add `release/THIRD-PARTY.md` (Gson, Apache-2.0, with its version) and a check in the workflow that every jar in `target/lib/` is listed there. Done: `release/THIRD-PARTY.md` lists Gson 2.11.0 and its dependency error_prone_annotations 2.27.0 (both in `target/lib/`); the build job checks every jar name against it.
- [x] 1.2 Add `.github/workflows/release.yml` per design D1 to D4 and D6: tag patterns and `workflow_dispatch`, read-only default permissions, build job with the `main` and version checks, `mvn -B verify`, the skipped-Node.js-tests check from `ci.yml`, archive and checksum, artifact upload; release job with `contents: write` that runs `gh release create --verify-tag`, `--prerelease` for `-rc.N`. Actions pinned to major versions as in `ci.yml`. Done: `.github/workflows/release.yml`; `actionlint` 1.7.7 reports no findings.
- [x] 1.3 Verify the version check locally in the dev container: `scripts/dev.sh shell`, then `mvn -B -q help:evaluate -Dexpression=project.version -DforceStdout` prints the pom version; build the archive with the workflow's commands and run `sha256sum -c` and `java -jar portfolio-lens.jar check` from the unpacked directory (exit status 64 without configuration is expected). Done in the dev image: version `0.1.0-SNAPSHOT`, archive with `lib/`, `LICENSE`, `NOTICE`, `sha256sum -c` OK, `check` exits 64 without configuration, manifest `Implementation-Version` equals the version. Deviation: `check` without configuration does not log the version, so the workflow compares the jar manifest instead.

## 2. Documentation

- [x] 2.1 AGENTS.md "Versioning": release candidates `vX.Y.Z-rc.N`, release commits through pull requests, the next snapshot after each release (design D5). Done.
- [x] 2.2 README section "Releases": download, checksum, archive layout, Java 21 (for example `eclipse-temurin:21-jre`), and the consumer contract of design D7; link it from "Commands and exit statuses". Done.

## 3. Verification and pull request

- [x] 3.1 `scripts/dev.sh test` passes; `scripts/openspec validate release-workflow --strict` and `scripts/openspec validate --specs --strict` pass. Done: 471 tests, 0 failures; both validations pass.
- [x] 3.2 Privacy scan of the diff (no real values, ISINs, names, home paths, tokens or keys). Done.
- [x] 3.3 Commit with Conventional Commits (`ci(release): ...`, `docs: ...`), push `feature/release-workflow` and open a pull request into `main`. Done (pull request opened).
- [x] 3.4 Dry run: start the workflow manually on the pull request branch (`gh workflow run release.yml --ref feature/release-workflow`), download the artifact, check the archive layout, `sha256sum -c` and that no tag or release was created. Record the run URL here. Open: needs the workflow on the default branch, the orchestrator runs it after the merge.
  - Evidence (2026-11-05): after the merge, `gh workflow run release.yml --ref main` (run 42043776395): job "Build archive" passed, "Publish release" skipped; the downloaded artifact `portfolio-lens-0.1.0-SNAPSHOT.tar.gz` passes `shasum -a 256 -c` and contains `portfolio-lens.jar`, `lib/` (Gson and its annotations), `LICENSE` and `NOTICE`; no tag and no release exist afterwards. Ran on `main` because a manual run needs the workflow on the default branch.
- [x] 3.5 After review, merge the pull request (user's decision) and archive this change.
  - Evidence (2026-11-05): pull request 8 merged by the user. The change is archived after the final release (group 5).

## 4. Release candidate (each step needs the user's authorization)

- [x] 4.1 After `page-app-link` is merged: pull request `chore(release): 1.0.0-rc.1` setting `pom.xml` to `1.0.0-rc.1`; merge after CI passes.
  - Evidence (2026-11-05): this pull request sets `pom.xml` to `1.0.0-rc.1`; the user authorized the release candidate.
- [x] 4.2 Tag the merge commit `v1.0.0-rc.1` and push the tag.
  - Evidence (2026-11-05): pull request 9 merged by the user (merge commit 01044ca); the user created and pushed the annotated tag `v1.0.0-rc.1`.
- [x] 4.3 Verify the release: marked as prerelease, both assets present, `sha256sum -c` passes on a downloaded copy, the unpacked jar reports `1.0.0-rc.1` in `java -jar portfolio-lens.jar check`, the notes contain the notice.
  - Evidence (2026-11-05): release run 42045076457 passed (build and publish). The release "Portfolio Lens 1.0.0-rc.1" is a prerelease with `portfolio-lens-1.0.0-rc.1.tar.gz` and its `.sha256`; a downloaded copy passes `shasum -a 256 -c`; the jar manifest has `Implementation-Version: 1.0.0-rc.1`; the notes contain the README notice.
- [x] 4.4 Pull request `chore(release): back to 1.0.0-SNAPSHOT`.
  - Evidence (2026-11-05): this pull request sets `pom.xml` back to `1.0.0-SNAPSHOT`.

## 5. Final release (only after the server integration is verified)

- [x] 5.1 The infrastructure repository runs `v1.0.0-rc.N` on the server: `check` and scheduled `run` succeed, `status.json` is monitored, the page is served behind authentication. Record the evidence without real portfolio values.
  - Evidence (2026-11-05): the infrastructure repository (inspirespace/infra pull requests 44 and 45) runs `v1.0.0-rc.1` next to PP in a container sharing its network namespace. `lens check` passed once the portfolio file was enabled in PP's REST settings; the first scheduled run wrote the page after about 2 minutes; the container healthcheck (`lastCheckedAt` younger than 30 minutes and `result` not `failed`) reports healthy; the page is served under `/lens/` behind the same two-factor login as PP (without login a redirect to the login portal). The user confirmed the page and the link back to PP in the browser. No portfolio values are recorded here.
- [ ] 5.2 With the user's authorization: pull request `chore(release): 1.0.0`, tag `v1.0.0`, verify as in 4.3 (not a prerelease).
- [ ] 5.3 Pull request `chore(release): 1.0.1-SNAPSHOT`.
