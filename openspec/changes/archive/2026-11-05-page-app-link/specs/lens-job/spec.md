## ADDED Requirements

### Requirement: Application link settings
The job SHALL accept the optional settings `LENS_APP_LINK_URL` and `LENS_APP_LINK_LABEL` (default "Portfolio Performance"). `check` and `run` SHALL reject, with the configuration exit status and the setting's name, a URL that is not absolute `http` or `https` with a host, a URL with user information, a label longer than 60 characters or with a line break, and a label without a URL. A change of either setting SHALL make the next run write the page even when the portfolio is unchanged.

#### Scenario: Valid link
- **WHEN** `LENS_APP_LINK_URL` is `https://example.org/` and no label is set
- **THEN** `check` succeeds and the page links to `https://example.org/` with the label "Portfolio Performance".

#### Scenario: Invalid scheme or user information
- **WHEN** `LENS_APP_LINK_URL` is `javascript:alert(1)`, `ftp://example.org/` or `https://user:secret@example.org/`
- **THEN** `check` fails with exit status 64, names `LENS_APP_LINK_URL`, and does not print the value.

#### Scenario: Label without URL
- **WHEN** only `LENS_APP_LINK_LABEL` is set
- **THEN** `check` fails with exit status 64 and names `LENS_APP_LINK_LABEL`.

#### Scenario: Link changed on unchanged portfolio
- **WHEN** the portfolio is unchanged, no provider work is due, and `LENS_APP_LINK_URL` changed since the last run
- **THEN** the run is not skipped and the new page contains the new link.
