# Tasks

## 1. Branch and baseline

- [x] 1.1 Create the branch `feature/breakdown-two-pane-layout` from `main` and verify with `git status` that it is clean
- [x] 1.2 Run `scripts/dev.sh test` and verify that all tests pass before any change
- [x] 1.3 Write the synthetic large page with `scripts/dev.sh shell` and `mvn test -Dtest=LargePortfolioTest -Dlens.demo=scratch/demo`, and verify that `scratch/demo/large.html` opens in a browser; keep it as the reference for the checks below

## 2. Page structure by pane (design D1)

- [x] 2.1 Restructure `PageWriter` into bar, summary, coverage, `main.panes` with chart pane and table pane, status and footer, with `data-grouping` and `data-chart` on the figures and table blocks; verify in `PageSmokeTest` that the page has one overlap figure inside the chart pane and one treemap, one bar chart and one table block per grouping
- [x] 2.2 Mark the elements of every grouping except Company `hidden` on the server and verify in `PageSmokeTest` that without scripts the Company treemap, bar chart, overlap matrix and table are not hidden
- [x] 2.3 Add the one-line head script that sets the `js` class and update the script count assertion of `PageSmokeTest` to three script elements
- [x] 2.4 Update the existing assertions of `PageSmokeTest` that name `section.view` and `section.overlap` to the new structure and verify that `scripts/dev.sh test PageSmokeTest` passes

## 3. Layout styles (design D2, D3, D9)

- [x] 3.1 Add the sticky bar with grouping selector, search field, application link, generation time and the reserved staleness slot; verify in `PageSmokeTest` that the application link markup of the existing scenario is unchanged and that no link element exists without the setting
- [x] 3.2 Add the two-pane grid from 1100 pixels, the sticky chart pane under the `js` class with its maximum height and inner scrolling, the wider body and `scrollbar-gutter: stable`; verify on the synthetic page at 1440 and 600 pixels width that the chart stays in view when wide and stacks without horizontal scrollbar when narrow
- [x] 3.3 Draw the bar chart into the 640 x 380 view box with 20 rows, scale the overlap matrix into the same box and give the breadcrumb line and the legend line a fixed height; verify in `PageSmokeTest` that treemap and bar chart of a grouping have the same view box
- [x] 3.4 Check light and dark theme on the synthetic page and verify that bar, panes and tabs keep visible boundaries in both

## 4. Tables with fixed columns (design D4, D10)

- [x] 4.1 Add `table-layout: fixed` and the same head cells with fixed widths to every breakdown table (see design, "Changes during implementation"); verify in `PageSmokeTest` that all breakdown tables have the same sequence of head cell classes
- [x] 4.2 Move company detail and contributions into a `tr.detail` row shown through `:has(details[open])`, with the `@supports` fallback, in `PageWriter` and in the script's row builder; verify in `PageSmokeTest` that each rendered company block has a detail row holding its contributions
- [x] 4.3 Stop rebuilding the server-rendered company rows on load; verify on the synthetic page that the first 200 rows are the server's elements after load (for example by a marker attribute in the browser console) and that "show more" still adds rows
- [x] 4.4 Run `scripts/dev.sh test LargePortfolioTest` and verify that the page stays below 3 MB and 30,000 elements

## 5. Script logic without DOM (design D5, D7, D11, D12)

- [x] 5.1 Add address parsing and formatting to the DOM-free part of the script, covering grouping, zoom key and depth, chart tab, any order, unknown parts and the current address format
- [x] 5.2 Add level members and breadcrumb names for the overview, a group, and merged levels of a group and of the Company view
- [x] 5.3 Add the company search over the embedded companies, case-insensitive, in rank order
- [x] 5.4 Add `PageLogicTest`, which runs 5.1 to 5.3 with Node.js on a synthetic data block and is skipped without Node.js; cover: an address of the current format, `zoom=<n>.0`, a group number that does not exist, a group whose members have a revenue factor below 1, a merged level two deep, a search with one match beyond rank 200, a search without match; verify with `scripts/dev.sh test PageLogicTest`
- [x] 5.5 Verify that `scripts/dev.sh test TreemapParityTest` still passes

## 6. Chart tabs, zoom, selection and search in the page (design D5, D6, D7)

- [x] 6.1 Add the chart tabs with `role="tablist"`, arrow key handling and the tab in the address; verify on the synthetic page that the tab survives a grouping change and a reload and that the chart pane keeps its height
- [x] 6.2 Add the group overlays to grouped treemaps in `PageWriter` with accessible name and tooltip, and remove the mosaic of merged tiles from the script; verify in `PageSmokeTest` that every group of a grouped treemap has one focusable overlay and that the merged tile markup and label are unchanged
- [x] 6.3 Draw group levels and merged levels from the logic of 5.2, with breadcrumb, Back button and Escape; verify on the synthetic page: click a sector, click its merged tile, Escape twice, and a reload on each level
- [x] 6.4 Change selection to open the detail row and scroll with `block: 'nearest'` and a scroll margin of the bar height; verify on the synthetic page that a visible row does not scroll the page and a far row is revealed below the bar with the chart still in view
- [x] 6.5 Wire the search field to the table with match count, "show more" and Escape to clear; verify on the synthetic page in the Company and the Sector grouping, including a company beyond rank 200 and a text without match

## 7. Explanations without hover (design D8)

- [x] 7.1 Add the `details` hints to summary values and the data status line and the amount to the coverage legend; verify in `PageSmokeTest` that the concentration explanation text occurs in the page body outside a `title` attribute
- [x] 7.2 Add the detail row to fund rows of the data status; verify in `PageSmokeTest` that the attempt text of a fund occurs in a detail row and that the `title` attribute is still present
- [x] 7.3 Verify on the synthetic page with keyboard only that a hint and a fund detail open and close and move no other content

## 8. Stability check and documentation

- [x] 8.1 On the synthetic page, check each action of the "Stable layout" requirement (grouping change, sort, open and close a row, expand a group, show more, search, tab change, treemap level) and verify that the amount columns keep their horizontal position, for example by comparing `getBoundingClientRect().left` of the total column header before and after in the browser console
- [x] 8.2 Open the synthetic page with scripts disabled and verify the "Scripts disabled" scenario
- [x] 8.3 Open the synthetic page with a generation time older than one hour and verify that the staleness notice appears in the bar without moving content
- [x] 8.4 Update the README paragraph on the page and the treemap (layout, chart tabs, group zoom, search) and verify that it describes only what the page does
- [ ] 8.5 Run `scripts/dev.sh run` against the local Portfolio Performance and check the real page once; keep the output in `scratch/` only (open: Portfolio Performance was not running when the change was implemented)

## 9. Version, privacy scan and pull request

- [x] 9.1 Set the version in `pom.xml` to `1.1.0-SNAPSHOT`, because this is a `feat` change, and verify that the page footer shows it
- [x] 9.2 Run `scripts/dev.sh test` and verify that all tests pass
- [x] 9.3 Scan the diff for personal data (names, amounts, holdings, ISINs of a real portfolio, keys, tokens) and verify that `scratch/` is not staged
- [x] 9.4 Commit with Conventional Commits (`feat(page): ...`), stating in the body that no setting, exit status, `status.json` field or cache format changes
- [ ] 9.5 After the user authorizes it, push the branch and open the pull request into `main`, naming the changed `breakdown-view` requirements
- [ ] 9.6 Run `scripts/dev.sh clean` and delete `scratch/demo`

## Workflow follow-up

- Archive the change after the pull request is merged.
- The entry point from Portfolio Performance to Lens (web app manifest shortcut or launcher) is set up in the server configuration, outside this repository.
- Data correctness (company identity across share classes, company names, revenue regions, provider cross-checks) is the next change.
