# Tasks

Branch `feature/page-app-link` from `origin/main`. Ships in 1.0.0 (before the `1.0.0-rc.1` release
commit of `release-workflow`, or in a later candidate).

## 1. Settings

- [x] 1.1 `LensConfig`: `LENS_APP_LINK_URL`, `LENS_APP_LINK_LABEL`, `Settings.appLink`, validation per design D1, both in `fingerprintPart()`. Tests in `LensConfigTest`: valid https and http URL, default and custom label, rejected `javascript:`, `ftp:`, relative URL, URL without host, URL with user information (error does not contain the value), overlong label, label with a line break, label without URL; the config file source behaves like the environment. Done: `LensConfigTest` (6 new tests).

## 2. Page

- [x] 2.1 `PageInput.appLink`, header link in `PageWriter` per design D2, style rule in `Style` for light and dark theme and narrow screens. Done: `PageWriter.appLink()`, `Style` rule (absolute at the right from 700px, below the title otherwise; theme variables).
- [x] 2.2 Tests: page with a link (exact markup, escaping of URL and label, no `target`, `rel="noopener"`), page without a link (no `app-link` element, header unchanged), page script still passes `node --check` (`PageSmokeTest`). Done: `PageSmokeTest` (2 new tests; the existing structure test keeps the `node --check` run).
- [x] 2.3 `LensRunTest`: a changed link on an unchanged portfolio is not skipped and the new page contains it. Done: `LensRunTest.testChangedAppLinkOnUnchangedPortfolioWritesThePage`.

## 3. Documentation

- [x] 3.1 README configuration table: both settings, default label, accepted URLs, and the server example (page at `https://<host>/lens/`, link to `https://<host>/`). Done: two rows in the README table.

## 4. Verification and pull request

- [x] 4.1 `scripts/dev.sh test` passes; `scripts/openspec validate page-app-link --strict` and `scripts/openspec validate --specs --strict` pass. Done: 480 tests, 0 failures; both validations pass.
- [x] 4.2 Check the header in the browser pane with a synthetic page, light and dark, wide and narrow.
  - Evidence (2026-11-05, orchestrator): checked on a page generated from a real portfolio with `LENS_APP_LINK_URL` set (nothing from it is in the repository): the link sits at the top right of the header in light mode at desktop width and below the subtitle in dark mode at 375 px, readable and without overflow.
- [x] 4.3 Privacy scan of the diff; commit `feat(page): link back to the application`; push and open a pull request into `main` (minor, compatible interface change named in the body); do not merge without the user. Open: privacy scan, commit and pull request done; merge by the user.
  - Evidence (2026-11-05): pull request 7 opened by the implementing agent after a diff scan for home paths, tokens and ISIN-like strings; reviewed and merged by the user; shipped in 1.0.0 and used on the server with `LENS_APP_LINK_URL` set.
