# Tasks

## 1. Fix on the open pull request of `breakdown-two-pane-layout`

- [x] 1.1 On the branch `feature/breakdown-two-pane-layout`, change the style rule that removes the padding of fund row summaries so that the collapse marker keeps its gutter (design D7); verify on the synthetic page that marker and fund name do not overlap in a closed and an opened fund row, in the Company and a grouped view, at 1440 and 375 pixels width
- [x] 1.2 Run `scripts/dev.sh test`, scan the diff for personal data, commit as `fix(page): ...` and push to that pull request after the user authorizes it

## 2. Branch and baseline

- [x] 2.1 Verify that the pull request of `breakdown-two-pane-layout` is merged and the change is archived, so that `openspec/specs/breakdown-view/spec.md` contains the requirements "Chart tabs", "Top bar" and "Stable layout"; stop and report if not
- [x] 2.2 Create the branch `feature/exposure-funds-data-sections` from `main` and verify with `git status` that it is clean
- [x] 2.3 Run `scripts/dev.sh test` and verify that all tests pass before any change
- [x] 2.4 Write the synthetic large page with `-Dlens.demo=scratch/demo` and keep it as the reference for the checks below

## 3. Explored treemap levels (design D8)

- [x] 3.1 Extend `Lens.merge` with the rule for explored levels (merged rest at most half of the weight, at most 3,000 shown tiles) and use it in `Lens.levelMembers` and in the drawing of explored levels; leave the overview call unchanged
- [x] 3.2 Add cases to `PageLogicTest`: 2,800 members of equal weight give 1,400 shown and 1,400 merged on the first level and no merged rest on the second; 7,000 members of equal weight give 3,000 shown on the first level; a skewed level whose merged rest is below half is unchanged; verify with `scripts/dev.sh test PageLogicTest`
- [x] 3.3 Draw tiles below the minimum tile area without label, with tooltip, link and selection; verify on the synthetic page that such a tile shows its tooltip and selects its table row
- [x] 3.4 Verify with `scripts/dev.sh test TreemapParityTest` that the overview layout is unchanged
- [x] 3.5 On the synthetic page, explore the merged tile of the Company view to the last level and verify that the last level has no merged tile and that opening the largest level takes less than 300 milliseconds

## 4. Presenter: warning rule and fund rows (design D3, D4)

- [x] 4.1 Add the attention flag and the text of the first problem to the presenter model, in the order failed lookup, rejected composition, fund without composition, missing valuation, unresolved value; add presenter tests for each case and for a result with only unvalidated weights, which gives no attention
- [x] 4.2 Add the fund row model (name, value, share of the securities value, number of companies, looked-through share, composition date, status and detail texts), sorted by value; add presenter tests with two synthetic funds worth 6,000 and 4,000 in a securities value of 20,000 and with a fund without composition
- [x] 4.3 Run `scripts/dev.sh test` for the presenter tests and verify that they pass

## 5. Page structure (design D1, D2, D5)

- [x] 5.1 Restructure `PageWriter` into the sections Exposure, Funds and Data quality with ids and headings, and add the section links to the bar; verify in `PageSmokeTest` the order of the three sections and that each link target exists
- [x] 5.2 Reduce the summary to securities value, top ten share, largest company, spread and net cash when present, and add the data quality line with its link; verify in `PageSmokeTest` that the line shows the looked-through share and the fund count, and that it carries the warning marker only in a scenario with a failed lookup
- [x] 5.3 Move the mark for a company above 10 percent to the largest-company card; verify in `PageSmokeTest` with a synthetic portfolio that holds one company at 12 percent
- [x] 5.4 Write the fund table in the Funds section from the fund row model and remove fund rows from the data status; verify in `PageSmokeTest` that each held fund occurs once in the Funds section with its detail row, and that the data status lists only securities of unknown classification
- [x] 5.5 Move the overlap matrix below the fund table and remove the Overlap tab; verify in `PageSmokeTest` that the chart pane has two tabs and no overlap figure, and that the Funds section holds one overlap figure or the message for fewer than two decomposed funds
- [x] 5.6 Move the coverage bar, its legend and the notices into the Data quality section; verify in `PageSmokeTest` that they occur after the Funds section
- [x] 5.7 Map `chart=overlap` to the treemap in `Lens.parseAddress` and handle section links in the script without changing the address; add a `PageLogicTest` case for the address and verify on the synthetic page that a section link scrolls and that a reload keeps grouping and level

## 6. Labels and styles (design D6)

- [x] 6.1 Change the labels in `Texts` and move the currency from the column heads to the caption line of the table; verify in `PageSmokeTest` that the heads read "Held directly", "Through funds", "Total" and "Share" in every breakdown table and that the old terms "Resolved", "Indirect" and "Funds broken down" do not occur in the page
- [x] 6.2 Style the sections, the data quality line, the fund table and the overlap figure with the tokens and blocks of the design system only; verify by reading the diff of `Style.java` that it adds no colour, radius, control height or shadow value outside `:root`
- [x] 6.3 On the synthetic page at 1440, 700 and 375 pixels width, verify that no column head is truncated, that the fund table keeps its column positions when a detail row opens, and that the page has no horizontal scrollbar
- [ ] 6.4 Check light and dark theme in a browser that follows the system colour scheme and verify that the three sections keep visible boundaries in both

## 7. Whole page checks and documentation

- [ ] 7.1 Open the synthetic page with scripts disabled and verify the "Scripts disabled" scenario, including that the section links work
- [x] 7.2 Repeat the "Stable layout" checks of the previous change for grouping change, row opening, tab change and treemap level, and verify that the amount columns keep their horizontal position
- [x] 7.3 Run `scripts/dev.sh test LargePortfolioTest` and verify that the page stays below 3 MB and 30,000 elements
- [x] 7.4 Update the README paragraph on the page (sections, fund table, where the overlap matrix is) and verify that it describes only what the page does
- [x] 7.5 Run `scripts/dev.sh run` against the local Portfolio Performance and check the real page once; keep the output in `scratch/` only

## 8. Version, privacy scan and pull request

- [x] 8.1 Check the version in `pom.xml`: keep `1.1.0-SNAPSHOT` if 1.1.0 is not released, otherwise set the next MINOR snapshot, because this is a `feat` change
- [x] 8.2 Run `scripts/dev.sh test` and verify that all tests pass
- [x] 8.3 Scan the diff for personal data (names, amounts, holdings, ISINs of a real portfolio, keys, tokens) and verify that `scratch/` is not staged
- [x] 8.4 Commit with Conventional Commits (`feat(page): ...`), stating in the body that no setting, exit status, `status.json` field or cache format changes
- [x] 8.5 After the user authorizes it, push the branch and open the pull request into `main`, naming the changed `breakdown-view` requirements
- [x] 8.6 Run `scripts/dev.sh clean` and delete `scratch/demo`

## Workflow follow-up

Open in this change:

- Task 6.4: the dark values of the tokens are not checked, because the preview used for the checks
  always renders light. Check once in a browser that follows the system colour scheme.
- Task 7.1: without scripts the structure is verified from the page source (three sections shown,
  section links are plain anchors, both Company charts shown). The rendered page without scripts
  could not be shown in the preview; check once in a browser with scripts disabled.

- Archive the change after the pull request is merged.
- Next change for the table and treemap: share bars in table rows, a second line under company
  names, a new form of the contribution list, and the Company treemap coloured by sector.
- Later: visible grouping tabs, computed findings at the top, account cash as a coverage segment.
