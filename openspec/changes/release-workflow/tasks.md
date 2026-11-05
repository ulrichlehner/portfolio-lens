# Tasks

Branch `feature/release-workflow` from `origin/main` for the implementation (this planning change
is on `docs/release-workflow`).

## 1. Workflow

- [ ] 1.1 Add `release/THIRD-PARTY.md` (Gson, Apache-2.0, with its version) and a check in the workflow that every jar in `target/lib/` is listed there.
- [ ] 1.2 Add `.github/workflows/release.yml` per design D1 to D4 and D6: tag patterns and `workflow_dispatch`, read-only default permissions, build job with the `main` and version checks, `mvn -B verify`, the skipped-Node.js-tests check from `ci.yml`, archive and checksum, artifact upload; release job with `contents: write` that runs `gh release create --verify-tag`, `--prerelease` for `-rc.N`. Actions pinned to major versions as in `ci.yml`.
- [ ] 1.3 Verify the version check locally in the dev container: `scripts/dev.sh shell`, then `mvn -B -q help:evaluate -Dexpression=project.version -DforceStdout` prints the pom version; build the archive with the workflow's commands and run `sha256sum -c` and `java -jar portfolio-lens.jar check` from the unpacked directory (exit status 64 without configuration is expected).

## 2. Documentation

- [ ] 2.1 AGENTS.md "Versioning": release candidates `vX.Y.Z-rc.N`, release commits through pull requests, the next snapshot after each release (design D5).
- [ ] 2.2 README section "Releases": download, checksum, archive layout, Java 21 (for example `eclipse-temurin:21-jre`), and the consumer contract of design D7; link it from "Commands and exit statuses".

## 3. Verification and pull request

- [ ] 3.1 `scripts/dev.sh test` passes; `scripts/openspec validate release-workflow --strict` and `scripts/openspec validate --specs --strict` pass.
- [ ] 3.2 Privacy scan of the diff (no real values, ISINs, names, home paths, tokens or keys).
- [ ] 3.3 Commit with Conventional Commits (`ci(release): ...`, `docs: ...`), push `feature/release-workflow` and open a pull request into `main`.
- [ ] 3.4 Dry run: start the workflow manually on the pull request branch (`gh workflow run release.yml --ref feature/release-workflow`), download the artifact, check the archive layout, `sha256sum -c` and that no tag or release was created. Record the run URL here.
- [ ] 3.5 After review, merge the pull request (user's decision) and archive this change.

## 4. Release candidate (each step needs the user's authorization)

- [ ] 4.1 Pull request `chore(release): 1.0.0-rc.1` setting `pom.xml` to `1.0.0-rc.1`; merge after CI passes.
- [ ] 4.2 Tag the merge commit `v1.0.0-rc.1` and push the tag.
- [ ] 4.3 Verify the release: marked as prerelease, both assets present, `sha256sum -c` passes on a downloaded copy, the unpacked jar reports `1.0.0-rc.1` in `java -jar portfolio-lens.jar check`, the notes contain the notice.
- [ ] 4.4 Pull request `chore(release): back to 1.0.0-SNAPSHOT`.

## 5. Final release (only after the server integration is verified)

- [ ] 5.1 The infrastructure repository runs `v1.0.0-rc.N` on the server: `check` and scheduled `run` succeed, `status.json` is monitored, the page is served behind authentication. Record the evidence without real portfolio values.
- [ ] 5.2 With the user's authorization: pull request `chore(release): 1.0.0`, tag `v1.0.0`, verify as in 4.3 (not a prerelease).
- [ ] 5.3 Pull request `chore(release): 1.0.1-SNAPSHOT`.
