# Design

## Context

See proposal.md for the motivation. The current state that shapes the approach, as of the branch
`feature/breakdown-two-pane-layout`:

- `PageWriter.page()` writes: top bar, summary with the notices, coverage section, `main.panes`
  with chart pane and table pane, status section with the data status, footer.
- The overlap matrix is one `figure[data-chart=overlap]` in the chart pane and a third chart tab.
  The address part `chart=overlap` selects it.
- Fund rows are part of the data status (`table.funds`, written by `fundRow` from
  `LookThroughDataStatus.FundStatus`). They hold name, status, retrieval date and looked-through
  share. The value of a fund is known to the overlap model (`FundOverlap.Fund.value`), and the
  companies of a fund follow from the contributions of the result.
- The summary's attention marker comes from `LookThroughViewPresenter` and is set for every
  partial result. A result is partial whenever provider weights are not validated, which is
  always.
- Labels are constants in `Texts`. The amount column heads carry the currency ("Total (EUR)") and
  are 12 characters wide.
- The stylesheet has a design system (tokens, controls, cards, chips), described in `Style.java`
  and named in `AGENTS.md`.
- Company and group rows show the collapse marker in a 16 pixel gutter left of the name. The rule
  `.status table summary{padding:0}` removes that gutter for fund rows, so the marker lies on the
  first letter.
- Explored treemap levels are computed by `Lens.levelMembers` and drawn with `Lens.merge`, which
  merges every member whose tile would be smaller than the minimum tile area (140 square units of
  a 640 x 380 chart). When the members of a level are of similar size, almost all of them fall
  below that area again, so each level shows a few tiles and one merged tile covering nearly the
  whole chart.

## Goals / Non-Goals

**Goals:**

- The section structure, labels, warning rule and treemap level rule of the spec delta.
- No new data source: every value comes from the look-through result that the page already gets.
- The overview treemap that the server renders stays as it is, so `TreemapParityTest` is not
  affected.

**Non-Goals:**

- Changing how completeness is computed or stored. Only the rule for the warning marker on the
  page changes.
- A new chart kind, or changes to the overview treemap.
- A change of the embedded data format beyond additions.

## Decisions

### D1 Page structure

```
<header class="topbar">        selector, search, section links, app link, time, stale notice
<section id="exposure">        h2 (visually hidden under .js, the bar names the view)
  summary cards
  data quality line
  <main class="panes">         chart pane (Treemap, Top 20), table pane
<section id="funds">           h2 "Funds"
  fund table
  overlap matrix
<section id="quality">         h2 "Data quality"
  coverage bar and legend
  notices
  data status (status row, storage and provider lines, unclassified securities)
<footer>
```

Section links are plain anchors (`#funds`, `#quality`), so they work without scripts. The script
already uses the fragment for the page state (`#grouping=...`), so it handles a click on a section
link itself: it scrolls to the section and leaves the address unchanged. Without scripts the
browser follows the anchor. Sections get `scroll-margin-top` of the bar height.

Alternative: tabs that show one section at a time. Rejected: the page without scripts would need
all three anyway, and find-in-page and printing work better on one page.

### D2 Summary and data quality line

The summary keeps four cards: securities value, top ten share, largest company, spread. The
figures "Resolved" and "Funds broken down" and the notices chip leave the summary.

The data quality line is one chip-like link below the cards: "Looked through 99.50% · 7 of 7
funds" and, in the warning state, the warning marker with the first problem. It links to
`#quality`. The net cash figure, when present, stays a card, because it is a figure about the
portfolio.

### D3 Warning rule

The presenter gets a second, narrower flag next to the existing completeness: `needsAttention`,
true when a lookup failed, a composition was rejected, a held fund has no usable composition, a
held security has no valuation, or the unresolved value is not zero. It also yields the short text
of the first problem in that order. The existing completeness and its text stay and are shown in
the Data quality section as an explanation.

The mark for a company above 10 percent moves from the notices to the largest-company card, since
it is a statement about the portfolio and not about data quality.

Alternative: change the completeness itself so that unvalidated weights no longer make a result
partial. Rejected: completeness is part of the calculation and of `status.json`, which is public
interface.

### D4 Fund table

One table in the Funds section replaces the fund rows of the data status. Columns: fund, value,
share of the securities value, companies, looked through, composition date, status. Rows are
sorted by value, largest first. The detail row keeps the attempt texts as today.

The presenter builds one row model per held fund from what exists: `FundStatus` (status, dates,
texts), the fund's value and share from the valuation, and the number of distinct companies among
the contributions whose source is that fund. A fund without usable composition shows a dash for
companies and looked-through share.

The table uses the fixed layout and the head cell classes of the breakdown tables, with the name
column taking the remaining width. On a narrow screen value and composition date are collapsed the
same way as the direct and indirect columns of the breakdown tables.

Securities of unknown classification stay in the data status, in a table of their own with name
and status.

### D5 Overlap matrix in the Funds section

The figure moves below the fund table and is no longer bound to a chart tab. It keeps its 640 x
380 view box but gets a maximum width of 720 pixels, so it does not grow with the page.

`Lens.parseAddress` keeps accepting `chart=overlap` and maps it to the treemap, and
`formatAddress` never writes it. `setTab` handles two tabs.

### D6 Labels and currency

`Texts` changes: "Looked through", "Funds looked through", "Held directly", "Through funds",
"Spread". The spread value reads "like 83 equal companies" with "of 4,176 companies" as the
second line of the card.

"Through funds (EUR)" does not fit a 12 character column. The currency moves out of the column
heads into the caption line above the table ("Amounts in EUR"), and the heads read "Held
directly", "Through funds", "Total", "Share". Column widths stay.

The share column tooltip keeps "Share of selected value".

### D7 Collapse marker in fund rows

The rule `.status table summary{padding:0}` is changed so that it resets only the vertical
padding. The marker rule for `td>details>summary` then applies to fund rows as to company rows.
This is a fix of the open pull request of `breakdown-two-pane-layout` and is committed there, not
on the branch of this change.

### D8 Explored treemap levels always make progress

`Lens.merge` gets an optional fourth argument used only for explored levels:

- Members at or above the minimum tile area are shown, as today.
- If the remaining members cover more than half of the level's weight, further members are shown
  in order of size until the rest covers at most half, or until the level shows 3,000 tiles.
- Members shown this way are drawn as tiles without label. They keep tooltip, link and selection.

With members sorted by weight this is one pass. The overview (server-rendered, and drawn by the
script after a return) calls `merge` without the argument and is unchanged, so the Java layout and
`TreemapParityTest` are not affected. `levelMembers` uses the same call, so the members of level
n+1 are the rest of level n under the new rule.

Depth is bounded: each level halves the merged weight or shows 3,000 companies. For 4,000
companies of equal size that is two levels. Drawing up to 3,000 rectangles happens only when the
user opens such a level.

The merged label keeps its form. Its "each below" value is computed from the threshold that the
level actually used, which is the weight of its smallest shown member when the rule above applied.

Alternative: paging through fixed blocks of companies with tiles scaled per page. Rejected: tile
areas would no longer be comparable with the merged tile on the same level.

Alternative: lower the minimum tile area on explored levels. Rejected: it does not bound the
depth when members are of equal size.

Addresses with a depth that no longer exists fall back to the overview, as specified.

## Risks / Trade-offs

- [The reader looks for the overlap matrix among the chart tabs] → the Funds link in the bar is
  always visible, and the README names the section.
- [A page without warning looks more certain than the data is] → the data quality line always
  shows the looked-through share, and the Data quality section keeps the explanation of
  unvalidated weights.
- [Tiles of 6 to 9 pixels are hard to hit] → they keep their tooltip, the table and the search
  reach every company, and one more level doubles their size.
- [The fund table duplicates what the breakdown shows for a directly comparable fund] → it holds
  only fund-level figures, no company rows.
- [Changed labels differ from terms used in Portfolio Performance] → the explanations name the
  calculation as before.

## Changes during implementation

- Branch: created from `chore/archive-breakdown-two-pane-layout` and not from `main`, because the
  archive commit that syncs the specs was not merged yet. The pull request therefore contains that
  commit unless the archive branch is merged first. No baseline run was made on the new branch; the
  last full run on the same code was the one of the previous pull request.
- D3: the warning rule is a class of its own, `DataQuality`, built from the status rows and the
  summary. The summary got `largestDominant`, `valuationProblem`, `unresolvedValue` and
  `unresolvedText`. A lookup counts as failed for the statuses failed, malformed, retained after
  failure, key rejected and no access; rate limited and checking do not count.
- D3: for an unconfirmed provider the status rows are not passed, so stale statuses raise no
  warning; held funds without composition still do.
- D4: a held fund is a security whose contributions have a fund as origin, the rule the fund
  counts and the overlap already use. Every other listed row is a security of unknown
  classification and stays in the data status.
- D4: on a narrow screen value, companies and composition date are collapsed, not only value and
  date.
- D2: the data quality line is a chip that links to the Data quality section. The design system
  got the token `--r-pill` for chips.
- D8: tiles below the label size were already drawn without label and with tooltip and link, so
  no drawing code changed.
- Tests: the synthetic smoke portfolio always has unresolved value, so the page test checks the
  warning there and in a scenario with failed lookups; the case without warning is covered by
  `DataQualityTest`. The mark of the largest company is checked on the smoke portfolio, whose
  largest company is far above 10 percent; the 12 percent and 8 percent cases are covered by the
  presenter test.
- On a real portfolio the explored levels of the Company view ended after seven levels. The rule
  bounds the depth; it does not make it small for a long tail.

## Migration Plan

No data or configuration migration. Addresses that name the overlap tab show the treemap. Rollback
is the previous release.

## Open Questions

- Whether the fund table should also show the fund's largest company. It needs no new data and can
  be added later without changing the structure.
