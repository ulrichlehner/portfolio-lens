# Tasks

Branch `feature/page-app-link` from `origin/main`. Ships in 1.0.0 (before the `1.0.0-rc.1` release
commit of `release-workflow`, or in a later candidate).

## 1. Settings

- [ ] 1.1 `LensConfig`: `LENS_APP_LINK_URL`, `LENS_APP_LINK_LABEL`, `Settings.appLink`, validation per design D1, both in `fingerprintPart()`. Tests in `LensConfigTest`: valid https and http URL, default and custom label, rejected `javascript:`, `ftp:`, relative URL, URL without host, URL with user information (error does not contain the value), overlong label, label with a line break, label without URL; the config file source behaves like the environment.

## 2. Page

- [ ] 2.1 `PageInput.appLink`, header link in `PageWriter` per design D2, style rule in `Style` for light and dark theme and narrow screens.
- [ ] 2.2 Tests: page with a link (exact markup, escaping of URL and label, no `target`, `rel="noopener"`), page without a link (no `app-link` element, header unchanged), page script still passes `node --check` (`PageSmokeTest`).
- [ ] 2.3 `LensRunTest`: a changed link on an unchanged portfolio is not skipped and the new page contains it.

## 3. Documentation

- [ ] 3.1 README configuration table: both settings, default label, accepted URLs, and the server example (page at `https://<host>/lens/`, link to `https://<host>/`).

## 4. Verification and pull request

- [ ] 4.1 `scripts/dev.sh test` passes; `scripts/openspec validate page-app-link --strict` and `scripts/openspec validate --specs --strict` pass.
- [ ] 4.2 Check the header in the browser pane with a synthetic page, light and dark, wide and narrow.
- [ ] 4.3 Privacy scan of the diff; commit `feat(page): link back to the application`; push and open a pull request into `main` (minor, compatible interface change named in the body); do not merge without the user.
