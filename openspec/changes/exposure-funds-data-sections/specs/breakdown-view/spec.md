# Spec Delta

## ADDED Requirements

### Requirement: Page sections
The page SHALL present its content in three sections in this order: Exposure, Funds and Data quality. Each section SHALL have a visible heading. The top bar SHALL link to each section, and following a link SHALL show the heading of that section below the bar. Without scripts all three sections SHALL be shown.

#### Scenario: Jump to the funds
- **WHEN** the user activates the Funds link in the top bar while the table of the Exposure section is in view
- **THEN** the page shows the Funds section with its heading visible below the bar.

#### Scenario: Section order
- **WHEN** the page shows a result
- **THEN** the summary, the charts and the table come first, the fund table and the overlap matrix second, and the coverage bar and the data status third.

#### Scenario: No fund held
- **WHEN** the portfolio holds no fund
- **THEN** the Funds section states that no fund is held and shows neither a table nor a matrix.

### Requirement: Fund table
The Funds section SHALL list each held fund once, largest value first, with its name, value, share of the securities value, number of contributed companies, looked-through share, composition date and status. The attempt and source details of a fund SHALL be reachable from its row by click, tap and keyboard without changing the position or width of the columns. The collapse marker of a row SHALL NOT overlap the fund name.

#### Scenario: Two funds
- **WHEN** the portfolio holds a fund worth 6,000 and a fund worth 4,000 in a securities value of 20,000
- **THEN** the table lists the larger fund first with a share of 30.00% and the smaller fund second with a share of 20.00%.

#### Scenario: Fund without composition
- **WHEN** a held fund has no usable composition
- **THEN** its row shows a dash for the number of companies and for the looked-through share, and its status text names the reason.

#### Scenario: Fund with failed refresh and retained data
- **WHEN** the latest refresh of a fund failed and an earlier stored composition is used
- **THEN** the row shows a warning icon and the earlier composition date, and the details name the failure and the original dates.

#### Scenario: Composition rejected by validation
- **WHEN** the provider returned a composition that validation rejects, for example because of negative weights or a total above 100 percent
- **THEN** the row states that the composition was rejected and names the reason in short text, the looked-through share shows a dash instead of zero, and the details explain that the fund stays unresolved.

#### Scenario: Open the details of a fund
- **WHEN** the user opens the details of a fund row
- **THEN** the details appear below the row, the marker and the fund name do not overlap, and every column keeps its position and width.

### Requirement: Data quality line
The Exposure section SHALL show one line below the summary that states the looked-through share, the number of funds looked through out of held funds, and a state. The line SHALL lead to the Data quality section.

#### Scenario: Everything looked through
- **WHEN** every held fund is looked through, no lookup failed and the unresolved value is zero
- **THEN** the line shows the looked-through share and the fund count without a warning marker.

#### Scenario: Follow the line
- **WHEN** the user activates the line
- **THEN** the page shows the Data quality section with its heading visible below the bar.

### Requirement: Data quality warning
The state of the data quality line SHALL be a warning only when a lookup failed, a composition was rejected, a held fund has no usable composition, a held security cannot be valued in the reporting currency, or the unresolved value is not zero. The warning SHALL name the first of these that applies. Compositions whose weights are not validated SHALL NOT raise a warning by themselves.

#### Scenario: Unvalidated weights only
- **WHEN** every held fund is looked through, nothing failed, the unresolved value is zero and the provider weights are not validated
- **THEN** the line carries no warning marker, and the Data quality section explains that the weights are not validated.

#### Scenario: Failed lookup
- **WHEN** the lookup of one fund failed
- **THEN** the line carries a warning marker and names the failed lookup.

#### Scenario: Unresolved value
- **WHEN** no lookup failed and 0.5 percent of the securities value is unresolved
- **THEN** the line carries a warning marker and names the unresolved value.

### Requirement: Plain labels
The page SHALL name the share of the securities value that is assigned to companies, ungrouped equities, cash or other assets "Looked through", the number of decomposed funds "Funds looked through", the value held as shares "Held directly", the value held through funds "Through funds", and the concentration figure "Spread". The same term SHALL be used in the summary, the table heads, the chart legends and the Data quality section.

#### Scenario: Table heads and legend
- **WHEN** the page shows the Company table and the treemap legend
- **THEN** the amount columns read "Held directly", "Through funds" and "Total", and the legend uses "Held directly" and "Through funds".

#### Scenario: Currency of the amount columns
- **WHEN** the reporting currency is EUR
- **THEN** the table names the currency once for all amount columns and the column heads are not truncated at a table pane width of 700 pixels.

## MODIFIED Requirements

### Requirement: Summary before detail
The Exposure section SHALL open with a summary of the securities value, the share of the ten largest companies, the largest company and the spread, followed by the data quality line. The top bar SHALL name the valuation date and the generation time. Explanatory notices SHALL NOT appear as permanent paragraphs; they SHALL be reachable through the Data quality section.

#### Scenario: Partial coverage with unvalidated weights
- **WHEN** the result is partial and contains compositions whose weights are not validated
- **THEN** the data quality line shows the looked-through share, and the Data quality section explains the limitation without additional paragraphs above the table.

#### Scenario: Missing valuation
- **WHEN** a held security cannot be valued in the reporting currency
- **THEN** the summary withholds whole-portfolio percentages and the data quality line carries a warning that names the missing valuation.

### Requirement: Concentration summary
The summary SHALL show the spread as the approximate number of equally sized companies, worded as "like N equal companies", computed as one divided by the sum of the squared company shares, with the number of companies it is based on. Its explanation SHALL describe the calculation with an example, say that smaller means more concentrated, and name the basis. The summary SHALL also show the largest company and its share. When one company exceeds 10 percent of the portfolio value, the figure of the largest company SHALL be marked and SHALL name it with its share.

#### Scenario: Evenly spread
- **WHEN** the company exposure is split evenly across 100 companies
- **THEN** the summary shows a spread like about 100 equal companies out of 100 companies.

#### Scenario: Uneven split
- **WHEN** three companies hold 50, 25 and 25 percent of the company exposure
- **THEN** the summary shows a spread like about 2.67 equal companies, and the explanation shows the same calculation next to the evenly split example.

#### Scenario: Dominant company
- **WHEN** one company accounts for 12 percent of the portfolio value
- **THEN** the summary shows it as the largest company with its 12 percent share and marks that figure.

### Requirement: Overlap chart
The Funds section SHALL show an overlap matrix with each pair of decomposed funds once, as the lower triangle of a fund matrix without diagonal. Each cell SHALL show the overlap percentage and the number of shared companies, shaded by size. A cell tooltip SHALL name both funds, the overlap, the number of shared companies and the five largest shared companies. With fewer than two decomposed funds, the section SHALL show a short message instead of a matrix. The matrix SHALL NOT depend on the selected grouping.

#### Scenario: Two decomposed funds
- **WHEN** the portfolio holds two decomposed funds
- **THEN** the overlap matrix shows one cell for the pair with its overlap and shared company count.

#### Scenario: Single fund
- **WHEN** only one fund is decomposed
- **THEN** the Funds section states that at least two decomposed funds are needed.

#### Scenario: Grouping change
- **WHEN** the user changes the grouping from Company to Sector
- **THEN** the overlap matrix is unchanged.

### Requirement: Chart tabs
The chart pane SHALL offer the chart kinds Treemap and Top 20 as tabs and SHALL show one of them at a time, Treemap first. The selected chart kind SHALL be kept when the grouping changes and SHALL be kept in the page address. The tabs SHALL be operable by keyboard and SHALL expose which one is selected to assistive technology. A page address that names the overlap chart kind SHALL show the Treemap tab.

#### Scenario: Switch to the overlap matrix
- **WHEN** the user looks for the overlap matrix in the chart pane
- **THEN** the chart pane offers no Overlap tab, and the matrix is shown in the Funds section that the top bar links to.

#### Scenario: Grouping change keeps the tab
- **WHEN** the Top 20 tab is selected and the user changes the grouping from Company to Sector
- **THEN** the chart pane shows the bar chart of the largest sectors.

#### Scenario: Reload
- **WHEN** the user reloads the page with the Top 20 tab selected
- **THEN** the page shows the Top 20 tab again.

#### Scenario: Address of an earlier page
- **WHEN** the page is opened with an address that names the overlap chart kind
- **THEN** the chart pane shows the Treemap tab of the grouping in the address.

### Requirement: Top bar
The page SHALL show a bar that stays at the top of the viewport while scrolling. It SHALL contain the "Breakdown by" selector, the company search, the links to the three page sections, the configured application link, the generation time and the staleness notice. The summary SHALL follow below the bar and SHALL scroll with the page.

#### Scenario: Scrolled down
- **WHEN** the user has scrolled to the end of the table
- **THEN** the grouping selector, the search field, the section links and the application link are visible without scrolling back.

#### Scenario: No application link configured
- **WHEN** no application link is configured
- **THEN** the bar contains no application link and no empty placeholder for it.

### Requirement: Stable layout
Changing the grouping, sorting, opening or closing a row, expanding a group, loading further rows, searching, changing the chart tab and changing the treemap level SHALL NOT change the position or width of the table columns and SHALL NOT move content that is above the element the user acted on. Notices that appear after the page has loaded SHALL NOT move other content.

#### Scenario: Opening a company row
- **WHEN** the user opens a company row whose contributions have long source names
- **THEN** the contributions appear below that row over the width of the table and every column keeps its position and width.

#### Scenario: Grouping change
- **WHEN** the user changes the grouping from Company to Country
- **THEN** the amount and share columns are at the same horizontal positions as before.

#### Scenario: Staleness notice
- **WHEN** the page is opened three hours after it was generated
- **THEN** the staleness notice appears in the top bar and the summary, charts and table do not move.

#### Scenario: Chart tab change
- **WHEN** the user switches between the Treemap and Top 20 tabs
- **THEN** the chart pane keeps its height and the table does not move.

### Requirement: Compact data status
The data status in the Data quality section SHALL list each held security of still unknown classification, with its name, a status icon and short status text. Held funds SHALL NOT be listed there; they are listed in the fund table. Securities classified as directly held shares SHALL NOT be listed. One line SHALL show storage state, company data state and assumed-company count, with details reachable from it.

#### Scenario: Persistent storage and completed company data
- **WHEN** compositions are stored in the cache and company data is loaded
- **THEN** one line names the storage and the company data with the assumed-company count.

#### Scenario: Session-only storage
- **WHEN** a provider key is configured but local storage is not confirmed
- **THEN** a separate warning line states that provider data is not used until local storage is confirmed, and the summary line keeps the company data state and the assumed-company count.

#### Scenario: Fund with failed refresh and retained data
- **WHEN** the latest refresh of a fund failed and an earlier stored composition is used
- **THEN** the data status does not list the fund, the status row names the failure, and the fund table shows the warning for that fund.

#### Scenario: Composition rejected by validation
- **WHEN** the provider returned a composition that validation rejects, for example because of negative weights or a total above 100 percent
- **THEN** the data status does not list the fund, the status row names the rejection, and the fund table states the reason for that fund.

#### Scenario: Security without provider credentials
- **WHEN** a held security is unclassified and no API key is configured
- **THEN** the row states that no API key is configured and that the security is not broken down, without any progress text.

#### Scenario: Security not yet classified
- **WHEN** provider access is configured and confirmed and the security has not been looked up because the budget or the run time was used up
- **THEN** the row states that the lookup is pending for a later run, and the row disappears in the first page generated after the security is classified as a directly held share.

### Requirement: Data status summary row
An always-visible status row SHALL head the data status in the Data quality section, SHALL open and close the data status when activated, and SHALL state the outcome of the run that generated the page in plain words.

#### Scenario: Toggle
- **WHEN** the user activates the row
- **THEN** the data status opens or closes, and the row keeps showing the state.

### Requirement: Separated data status section
The three page sections SHALL be visually separated from each other and from the top bar, so that each reads as a distinct section. The coverage bar and its legend SHALL be part of the Data quality section. The data status SHALL start collapsed when the page loads, and its status row SHALL be visible in both states.

#### Scenario: Data status below the table
- **WHEN** the data status is open in the Data quality section
- **THEN** a visible boundary and spacing separate the Exposure, Funds and Data quality sections in light and dark theme.

#### Scenario: Header above the charts
- **WHEN** the page shows a result
- **THEN** a visible boundary separates the summary and the data quality line from the chart and table.

#### Scenario: Coverage bar
- **WHEN** the page shows a result
- **THEN** the coverage bar and its legend are shown in the Data quality section and not between the summary and the charts.

#### Scenario: Page load
- **WHEN** the page is opened or reloaded
- **THEN** the data status is collapsed and the status row is visible.

### Requirement: Explorable merged tile
The merged tile SHALL state the number of its companies, their total share and the size limit, SHALL be reachable by keyboard, and SHALL be drawn as one tile in a colour of its own. Activating it SHALL show its companies as a treemap of their own over the whole chart; a group's merged tile SHALL show the companies of that group only. On such a level the companies still merged SHALL NOT cover more than half of the chart, unless the level already shows 3,000 companies of their own. Companies whose tile is too small for a label SHALL be drawn without label and SHALL keep their tooltip and their selection. Every company SHALL therefore be reachable as a tile of its own after a bounded number of levels.

#### Scenario: Label of the merged tile
- **WHEN** 3,882 synthetic companies with together 33.1 % of the value are merged, each below 0.05 %
- **THEN** the tile, its tooltip and its accessible name read "3,882 companies, 33.1%, each below 0.05%" and hint that it can be explored.

#### Scenario: Exploring with the keyboard
- **WHEN** the user moves the focus to the merged tile and presses Enter
- **THEN** its companies fill the chart, a nested merged tile holds the companies still too small, and selecting a company there selects its table row.

#### Scenario: Merged tile of a zoomed group
- **WHEN** the user has zoomed into a sector and activates its merged tile
- **THEN** the treemap shows the smaller companies of that sector only.

#### Scenario: Many companies of equal size
- **WHEN** the user activates a merged tile that holds 2,800 companies of equal value
- **THEN** the level shows 1,400 of them as tiles of their own without labels and one merged tile covering half of the chart, and activating that merged tile shows the remaining 1,400 companies without a further merged tile.

#### Scenario: Selecting an unlabelled company
- **WHEN** the user activates a tile that is drawn without label
- **THEN** its tooltip names the company and its share, and its table row is selected.

### Requirement: Self-contained static page
The Breakdown SHALL be one HTML file with inline styles, server-rendered SVG charts, embedded result data and inline scripts. It SHALL load nothing from the network. Without scripts it SHALL still show the summary, the Company charts, the largest company rows of the table, the fund table, the overlap matrix, the coverage bar and the data status.

#### Scenario: Opened offline
- **WHEN** the page is opened from disk without network access
- **THEN** it renders completely and the browser requests no other resource.

#### Scenario: Scripts disabled
- **WHEN** the page is opened with scripts disabled
- **THEN** the Company grouping is shown with its treemap and its bar chart below each other and its 200 largest company rows with their contributions, the Funds and Data quality sections are shown below, and only grouping, chart tabs, treemap levels, search, sorting, further rows and linked selection are unavailable.
