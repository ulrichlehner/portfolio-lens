# Proposal

## Why

The Breakdown page is one long column: summary, coverage, charts, a table of 200 rows, the overlap
matrix and the data status. Selecting a chart tile scrolls the chart out of view, the overlap matrix
sits below the table, and nothing stays in place while scrolling. The treemap only zooms into the
tile of merged small companies, while a click on a sector or country selects a table row. Table
columns change position when a row opens, the grouping changes or rows are added, and the staleness
notice pushes the page down after load. Explanations are hover tooltips, which a touch screen cannot
open. The page is also opened inside an installed web app without browser controls, where the link
back to Portfolio Performance is the only way out and is a small text link today.

## What Changes

- Two panes on wide screens: charts on the left, table on the right. The chart pane stays in view
  while the table scrolls with the page. Narrow screens keep one column.
- A top bar that stays in view: grouping selector, company search, the application link, the
  generation time and the staleness notice. The summary and the coverage bar form a compact block
  below it.
- The chart pane shows one chart at a time, chosen by tabs: Treemap, Top 20, Overlap. The overlap
  matrix moves from below the table into the chart pane.
- Treemap zoom: in a grouped view, a click on a group shows that group's companies over the whole
  chart. A breadcrumb and Escape lead back. The tile of merged small companies stays explorable but
  is drawn as one plain tile, without the cells of its members.
- Selecting a chart element highlights and opens its table row and scrolls only when the row is not
  visible. The chart stays in view.
- Company search over all companies of the embedded data, not only the rendered rows.
- Stable layout: all breakdown tables share fixed column widths, contributions open in a row of
  their own below the company, the staleness notice has reserved room, the chart pane keeps its
  height, and the rows rendered by the server are not rebuilt on load.
- Every explanation that is a tooltip today can also be opened by click, tap or keyboard.
- The page address keeps the chart tab next to the grouping and the treemap level. Addresses of the
  current format keep working or fall back to the overview.

No setting, command, exit status, `status.json` field or cache format changes. The look of
`index.html` is not part of the public interface (AGENTS.md "Versioning").

Out of scope:

- An entry point from Portfolio Performance to Lens (web app manifest shortcut, launcher). It
  belongs to the server setup, not to this repository.
- Data and calculation: company identity across share classes, company names, revenue region
  granularity, provider cross-checks, further provider fields, and the permanent "Partial coverage"
  state. A later change covers them.
- New groupings, new chart kinds, export, and user settings stored by the page.
- Any network request from the page, and any external script, style or font.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `breakdown-view`: page layout (two panes, top bar, chart tabs), treemap group zoom and level
  navigation, selection behavior, company search, layout stability, explanations without hover, and
  the behavior without scripts.

## Impact

- `page/PageWriter` (page structure, tables, chart pane, status rows), `page/Style`, `page/Script`.
- `page/TreemapLayout` and `page/LookThroughViewPresenter` only if the bar chart or a zoomed level
  needs a value they do not provide yet.
- Tests: `PageSmokeTest`, `LargePortfolioTest` (size and element limits), `TreemapParityTest` and a
  new script test run with Node.js in the dev image.
- README: the paragraph on the page and the treemap.
- No new dependency.
