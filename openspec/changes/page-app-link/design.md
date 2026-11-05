# Design

## Context

`LensConfig.load` reads `LENS_*` settings from the environment or a `--config` file and collects
`Error(setting, message)` entries that `check` and `run` report with exit status 64. A run is
skipped when the input fingerprint (portfolio plus `Settings.fingerprintPart()` and the version)
is unchanged and no provider work is due. `PageWriter` renders the header as
`<header class="page"><h1>…</h1><p class="meta">…</p>`. `PageInput` holds everything the page
shows and never a secret or a file path.

## Decisions

### D1 Settings

- `LENS_APP_LINK_URL`: parsed with `java.net.URI`; accepted only when absolute, scheme `http` or
  `https` (case-insensitive), with a host and without user information. Stored as the normalized
  string. Otherwise `Error(LENS_APP_LINK_URL, "must be an absolute http or https URL without user name or password, for example https://example.org/")`.
- `LENS_APP_LINK_LABEL`: trimmed, default "Portfolio Performance", at most 60 characters, no line
  breaks. Set without a URL: `Error(LENS_APP_LINK_LABEL, "has no effect without LENS_APP_LINK_URL")`.
- `Settings` gets `Optional<AppLink> appLink` with `record AppLink(String url, String label)`.
  Both values are part of `fingerprintPart()`, so changing them writes the page on the next run.

The URL is not a secret, but a URL with user information could carry one, which is why it is
rejected rather than shown.

### D2 Page

`PageInput` gets `Optional<AppLink> appLink`. When present, `PageWriter` adds to the header, after
the meta line:

```html
<p class="app-link"><a href="https://example.org/" rel="noopener">Portfolio Performance</a></p>
```

URL and label are HTML-escaped with the existing `e()` helper. No `target`, so the link opens in
the same tab; `rel="noopener"` is kept as asked even though it only matters for new tabs. No
script, no prefetch, no favicon: the page stays self-contained and makes no request. A small
style rule places the link at the right of the header on wide screens and below the title on
narrow ones, in both themes.

### D3 Status file

`status.json` does not change. The link is presentation only.

## Risks / Trade-offs

- A wrong URL only produces a broken link; Lens never requests it.
- A label could be used for misleading text; it is the operator's own setting and is escaped.

## Open Questions

None.
