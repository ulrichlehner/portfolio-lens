# Design

## Context

See proposal.md for the motivation. The current state that shapes the approach:

- `PageWriter` writes one `section.view` per grouping (seven), each holding its treemap, its bar
  chart and its table. Only the Company view is visible without scripts. The overlap matrix is one
  `section.overlap` after all views, so it ends up below the table.
- Tables use automatic column widths. The contributions of a company are a `<details>` element
  inside the name cell, so opening it widens that column.
- `Script.LAYOUT` is a copy of the squarified layout, the merge of small tiles, the merged label and
  the tile colour in JavaScript. `TreemapParityTest` runs it with Node.js against the Java code.
  The rest of the script works on the DOM and has no test except a syntax check.
- The embedded data already holds what a group zoom needs: `c` (every company once), `g` (members
  of every group with their factor) and `m` (members of every merged tile).
- The page address is `#grouping=<NAME>` with an optional `&zoom=<key>.<depth>`, where `key` is
  `all` or a group number and `depth` counts levels of merged tiles, starting at 1.
- The spec requires one self-contained file, a working page without scripts, and for 4,000
  companies less than 3 MB and 30,000 elements. A real page of that size has about 14,700 elements.
- On load the script draws every member of every merged tile as a cell ("mosaic"), and rebuilds the
  200 company rows that the server already rendered.

## Goals / Non-Goals

**Goals:**

- The layout, zoom, search and stability behavior of the spec delta, with CSS doing the layout and
  the script only changing visibility and building rows and zoomed levels.
- Script logic that can be tested without a browser.
- No change to the embedded data format beyond additions, and no new dependency.

**Non-Goals:**

- A browser test framework or a DOM library in the build.
- Changing `LookThroughViewPresenter` models, texts of the calculation, or any value shown.
- A redesign of colours or typography beyond what the new layout needs.

## Decisions

### D1 Page structure by pane, not by grouping

```
<header class="bar">      title, grouping select, search, app link, generated time, stale notice
<section class="summary"> figures, notices
<section class="coverage">
<main class="panes">
  <div class="chart-pane">
    tabs
    <figure data-chart="treemap" data-grouping="COMPANY"> ... one per grouping
    <figure data-chart="bars"    data-grouping="COMPANY"> ... one per grouping
    <figure data-chart="overlap">                          one
  <div class="table-pane">
    <div data-grouping="COMPANY"> notices of the grouping, table ... one per grouping
<section class="status">
<footer>
```

The script shows the elements whose `data-grouping` and `data-chart` match the current state and
hides the others. Without scripts, the server marks the elements of the other groupings `hidden`,
and the three Company charts are all visible, one below the other.

Alternative: keep `section.view` per grouping and repeat the overlap matrix in each. Rejected: seven
copies of the matrix, and a tab state per view.

`<html>` gets the class `js` from a first one-line inline script in the head, so styles that need
the script (sticky pane, tabs, hidden search) apply before the first paint and nothing jumps when
the main script runs. The page then has three script elements: this line, the data block and the
main script.

### D2 Layout in CSS

- `body` max width grows from 1360 to 1680 pixels.
- `main.panes` is a grid: `minmax(0, 5fr) minmax(0, 6fr)` from 1100 pixels viewport width, one
  column below.
- `.js .chart-pane` is `position: sticky` below the bar, with
  `max-height: calc(100vh - bar height)` and `overflow: auto`. This keeps one page scrollbar and
  still reaches every part of the pane on a low viewport. Without the `js` class the pane is not
  sticky, because three stacked charts are taller than a viewport.
- The bar is `position: sticky; top: 0` with the panel background and a bottom border. Its height
  is a custom property that the pane offset uses. On narrow viewports the bar wraps to two lines and
  the property changes in the same media query.
- `html { scrollbar-gutter: stable }`.

Alternative: a table pane with its own scrollbar. Rejected: two scrollbars, and the browser's find
and anchor navigation behave worse in nested scroll areas.

### D3 Chart pane of constant height

All three chart kinds are drawn into the same box. The treemap keeps its 640 x 380 view box. The
bar chart uses the same view box with 20 rows of 19 units. The overlap matrix is scaled into the
box (`preserveAspectRatio` meet), and scrolls inside the pane only if its cells would get smaller
than a readable size. The legend line below the chart has a fixed height for all three kinds, empty
for the overlap matrix. The breadcrumb line above the treemap is always present with a fixed
height, showing the chart caption when no level is explored.

### D4 Tables with fixed columns

Every breakdown table gets `table-layout: fixed` and the same `<colgroup>`: rank 3.5em, name the
remaining width, direct, indirect and total 17ch each, share 8ch. Names wrap inside their column.
The existing rule that hides direct and indirect below 600 pixels stays and applies to the `col`
elements too.

Contributions move out of the name cell into a second row of the same `tbody`:

```
<tbody class="block company">
  <tr class="top">    rank | <details><summary>name</summary></details> | amounts
  <tr class="detail"> <td colspan="6"> detail text, contributions </td>
```

The detail row is shown by `tbody:has(details[open]) > tr.detail`. This needs no script, so the
page without scripts keeps working. Inside `@supports not selector(:has(*))` the detail rows are
always shown. The script builds further rows in the same shape.

Alternative: keep the list inside the name cell and rely on the fixed layout alone. Rejected: the
name column is about 380 pixels wide in the right pane, too narrow for "via fund (origin)" lines.

Server-rendered detail rows add two elements per rendered company (400 for 200 rows), which stays
far below the element limit.

### D5 Treemap levels

A level is `{grouping, key, depth}`:

| key | depth | shows |
| :--- | :--- | :--- |
| none | none | overview, as rendered by the server |
| group number | 0 | the companies of that group |
| group number | n >= 1 | the n-th level of merged small companies of that group |
| `all` | n >= 1 | the n-th level of merged small companies of the Company view |

Addresses of the current format have the same meaning as before, so `#grouping=SECTOR&zoom=3.1`
still shows the smaller companies of group 3. The companies in that level can differ slightly from
today, because the minimum tile area now applies to the zoomed group and not to the overview.
`zoom=<n>.0` is new. A key or depth that does not exist falls back to the overview.

In a grouped overview, one transparent rectangle per group lies on top of its tiles and takes the
click and the focus, with the group's label and values as accessible name. An SVG element cannot take clicks and let hover pass
to the tiles below it, so the overlay has its own tooltip: the group, its value and "Click to show
its companies". On a zoomed level company tiles are links to their rows as today.

The first click inside a group therefore always zooms, and a company is selected on the zoomed
level. This is the common behavior of zoomable treemaps and removes the question of which part of a
group is clickable.

Zoomed levels are drawn by the script from `g` with `Lens.squarify` and `Lens.merge`, as merged
levels are today. The merged label and the tile colour stay as they are, so `TreemapParityTest`
keeps covering them. The mosaic of member cells is removed; the merged tile is a grey tile with its
label and "Click to explore". This also removes several thousand rectangles drawn on load.

The breadcrumb reads "All", then the group label, then "Smaller companies" once per merged level.
A "Back" button left of it returns one level; Escape does the same unless the search field has the
focus.

### D6 Selection

`selectRow` opens the row's `<details>`, marks row and chart element, and calls
`scrollIntoView({block: 'nearest'})`. `scroll-margin-top` on rows equals the bar height, so a
revealed row is not hidden behind the bar. Selecting a group through the treemap also expands its
group row.

### D7 Search

Search runs over `c` by case-insensitive substring match on the label. While the field is not
empty, the table of the current grouping is replaced by a result `tbody` list built with the
existing row builder: company rows with factor 1, their Company rank, in rank order, 200 at a time
with the existing "show more" control, and a first row stating the number of matches. Group rows,
category rows and excluded rows are hidden meanwhile. Input is handled on the `input` event without
a timer; filtering 4,000 labels is a few milliseconds. The search text is not written to the
address. Escape in the field clears it.

The field is hidden without the `js` class.

### D8 Explanations

The `title` attributes stay, so the existing requirements about tooltips hold. In addition:

- Summary values with an explanation get a small `<details class="hint">` whose content is the
  explanation text, laid over the page next to the value so it moves nothing.
- Each fund row of the data status gets a detail row like D4, holding the lines that are its
  tooltip today.
- The data status line gets the same `details` treatment as the summary values.
- The coverage legend shows the amount next to the share, which is all its tooltip holds.

All of these work without scripts.

### D9 Staleness notice and generation time

The bar always contains the generation time. The notice replaces the text of that element's
neighbour, which has a reserved minimum width, and gets the warning colours. Nothing is inserted
into the flow.

### D10 No rebuild on load

The script keeps the server-rendered rows and only sets the text of the "show more" control.
`renderCompanies` runs on sorting, "show more", search, and when a selected company lies beyond the
rendered rows.

### D11 Address

`#grouping=<NAME>[&zoom=<key>.<depth>][&chart=bars|overlap]`. Treemap is the default and is not
written. Parts in any order, unknown parts ignored. Parsing and formatting are pure functions.

### D12 Testable script logic

`Script.LAYOUT` grows into the DOM-free part of the script: address parsing and formatting (D11),
level members (D5: the companies of a level from `c`, `g` and `m`), breadcrumb names, and search
(D7). A new `PageLogicTest` runs these with Node.js on a synthetic data block, the same way
`TreemapParityTest` does, and is skipped when Node.js is missing. DOM behavior (sticky pane, column
positions, tabs) is checked by hand on the synthetic large page that `LargePortfolioTest` writes
with `-Dlens.demo=<dir>`, with the checklist in tasks.md.

Alternative: a headless browser in the dev image. Rejected for this change: a large dependency for
one page, and it is not needed to test the logic that can go wrong silently.

## Risks / Trade-offs

- [`:has()` is missing in old browsers] → detail rows are then always shown; the page stays correct
  and only longer.
- [A sticky pane on a low or zoomed viewport hides part of the chart] → the pane scrolls inside
  itself (D2), and below 1100 pixels width it is not sticky at all.
- [A click on a company in a grouped overview no longer selects it directly] → two clicks instead
  of one; search and the table reach a company directly. The tooltip of the group overlay says what
  a click does.
- [Company tooltips disappear in grouped overviews, because the group overlay takes the hover] →
  accepted for the overview, where most company tiles are too small to read; the zoomed level has
  them. If this turns out to be missed, the overlay can cover only the group header and border.
- [DOM behavior has no automated test] → manual checklist in tasks.md on the synthetic page, and
  `PageSmokeTest` asserts the structure that the behavior depends on: one shared `colgroup`, the
  detail rows, the pane elements, the tabs.
- [The bar takes vertical room on small screens] → it wraps to at most two lines and holds no
  summary figures.
- [Wider body on very wide screens] → capped at 1680 pixels.

## Changes during implementation

- D4: the fixed widths are set on the head cells of each table (classes `rank`, `name`, `num`,
  `share`), not on a `colgroup`. Hiding the direct and indirect columns in a narrow table pane then
  works by hiding cells, which `col` elements do not support in every browser. The rule that hides
  them is a container query on the table pane (640 pixels), so it also applies to a narrow pane on a
  wide screen.
- D4: amount cells of the breakdown tables no longer repeat the currency code; the column heads
  name it ("Total (EUR)"). This gives the name column about 100 pixels more in the right pane.
  Contribution lines, tooltips and the summary keep the code.
- D4: rows built by the script get their detail row when they are opened first, not when they are
  built, so expanding a large group creates half as many elements.
- D5: the embedded member lists of merged tiles (`m`) are kept unchanged and are no longer read by
  the script, which derives every level from `c` and `g`. Removing them is a later size reduction.
- D9: the bar has two lines of fixed height (three below 700 pixels); the staleness notice is the
  first element of the second line and shortens the file and date text next to it.

- After the first review of the real page: the visible heading of the bar is the "Breakdown by"
  label with the selector, and the `h1` that names the grouping is kept for assistive technology,
  the page title and the page without scripts. This removes the grouping being named twice.
- Summary figures are cards, and the notices open from a chip as one list. The notice line that
  repeated the chip's text is left out.
- The stylesheet has a small design system (tokens for surfaces, text, accent, radii, control
  height and shadow; controls, cards and chips built from them). `AGENTS.md` names the rule.
- The merged tile of small companies is a muted blue instead of grey.
- The rank column is four digits wide and does not wrap.
- D4: in a narrow table pane the direct and indirect cells are collapsed to zero width instead of
  removed. Removing them left two empty columns from the detail rows' column span, which took the
  width of the name column.

## Migration Plan

No data or configuration migration. The next run that writes the page produces the new layout; a
run that is skipped because nothing changed would keep the old page, but the version is part of
the input fingerprint and this change raises it, so the first run of the new version writes the
page. Rollback is the previous release.

## Open Questions

- The exact breakpoint (1100 pixels) and pane ratio (5:6) are starting values to adjust on the
  synthetic page during implementation. They do not change the specs beyond the number named there;
  if the breakpoint moves, the spec scenario value moves with it.
