# Proposal

## Why

The page presents its content in the order the calculation produces it, not in the order a reader
asks questions. Data quality is spread over four places (the "Resolved" figure, the coverage
notice, the coverage bar and the data status), the overlap matrix sits among the charts of a
grouping although it describes funds, several labels use terms of the calculation, and the
"Partial coverage" warning is shown on every page, so it carries no signal.

A layout defect from the change `breakdown-two-pane-layout` is fixed along the way: in the fund
rows of the data status the collapse arrow overlaps the first letter of the fund name.

## What Changes

- The page gets three named sections in this order: Exposure, Funds, Data quality. The top bar
  links to each of them.
- Exposure holds the summary, the charts and the table. The summary shows only figures about the
  portfolio: securities value, share of the ten largest companies, largest company, and spread.
- One line below the summary states the data quality in short form (looked-through share, number
  of funds looked through, and a state) and leads to the Data quality section.
- Funds is new. It holds a table of the held funds (value, share of the securities value, number
  of companies, looked-through share, date of the composition, status) and the overlap matrix.
- The overlap matrix leaves the chart tabs. The chart pane offers Treemap and Top 20.
- Data quality holds the coverage bar, the notices, the storage and provider lines and the
  securities of unknown classification. Fund rows move from here to the Funds section.
- The warning state is shown only when something needs attention: a failed lookup, a rejected
  composition, a fund without composition, a missing valuation, or unresolved value. Unvalidated
  provider weights alone no longer raise a warning; the Data quality section still explains them.
- Plain labels: "Looked through" for "Resolved", "Funds looked through" for "Funds broken down",
  "Held directly" and "Through funds" for "Direct" and "Indirect", and "Spread" with the wording
  "like N equal companies" for "Concentration".
- Treemap levels of merged companies always make progress. Today a level can consist almost only
  of one merged tile when its companies are of similar size, so that some companies are never
  shown as a tile. A level now limits the merged tile to half of the chart and draws the other
  companies as small tiles without label.
- Fix: the collapse arrow of fund rows gets the same gutter as in the breakdown tables. This fix
  goes into the open pull request of `breakdown-two-pane-layout`, because that change caused it.

No setting, exit status, `status.json` field or cache format changes. The look and wording of
`index.html` are not part of the public interface, so this is a compatible change.

## Out of scope

- Visible grouping tabs in place of the selector.
- Computed findings at the top of the page.
- Share bars in table rows, a second line under company names, and a new form of the contribution
  list.
- Colouring the Company treemap by sector.
- Account cash as a coverage segment.
- Company identity and names (share classes, provider names), provider cross-checks and further
  provider fields. These are separate changes listed in `breakdown-two-pane-layout`.
- New data from providers or from Portfolio Performance. Every value shown is already part of the
  look-through result.
- The texts and values of the calculation itself.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `breakdown-view`: page sections with links in the bar; the Funds section with fund table and
  overlap matrix; the data quality line and the rule for its warning state; the summary figures
  and their labels; chart tabs without Overlap; the data status without fund rows; table column
  labels; the limit of the merged tile on explored treemap levels.

## Impact

- Code: `page/PageWriter.java` (section structure, summary, fund table, data quality section),
  `page/Style.java` (sections, fund table, arrow gutter), `page/Script.java` (chart tabs and
  address without the overlap tab, merge rule of explored levels), `page/Texts.java` (labels),
  `page/LookThroughViewPresenter.java` (attention rule, fund figures for the page).
- Tests: `PageSmokeTest`, `PageLogicTest`, presenter tests for the attention rule and the fund
  figures.
- Docs: the README paragraph on the page.
- Depends on `breakdown-two-pane-layout` being merged and archived, because this change modifies
  requirements that change adds.
- No new dependency. Version stays `1.1.0-SNAPSHOT` if that version is not released before; the
  change is a `feat`.
