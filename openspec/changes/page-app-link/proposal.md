# Proposal

## Why

On the server the page is served at `https://<host>/lens/`, next to Portfolio Performance at
`https://<host>/`. A reader of the page has no way back to Portfolio Performance except typing the
address. A configurable link in the page header closes that gap without Lens knowing anything about
the server layout.

## What Changes

- Two optional settings: `LENS_APP_LINK_URL`, an absolute `http` or `https` URL, and
  `LENS_APP_LINK_LABEL`, the link text, default "Portfolio Performance".
- `check` and `run` reject a URL with another scheme, without a host, or with user information
  (`user:password@`), and name the setting. A label without a URL is a configuration error too.
- When the URL is set, the page header shows a plain link with that label to that URL: same tab,
  `rel="noopener"`, escaped, works without JavaScript, and loads nothing. Without the URL the page
  is unchanged.
- Changing either setting makes the next run write the page even when the portfolio is unchanged.
- README configuration table documents both settings.

This is an additive, compatible change of the `LENS_*` settings (MINOR under AGENTS.md
"Versioning") and is planned to ship in 1.0.0.

Out of scope:

- Links to anything other than one application URL, icons, several links or a navigation bar.
- Checking that the URL is reachable, and any request to it.
- Relative URLs.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `lens-job`: two optional settings, their validation, and their effect on skipping unchanged runs.
- `breakdown-view`: an optional link in the page header.

## Impact

- `job/LensConfig` (settings, validation, fingerprint), `page/PageInput`, `page/PageWriter`,
  `page/Style`.
- Tests: `LensConfigTest`, `PageSmokeTest` or a new page test, `LensRunTest` for the skip.
- README configuration table.
