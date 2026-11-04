# breakdown-view Specification

## Purpose

Defines how the look-through result is presented and operated in the Breakdown view: layout, summary, charts,
onboarding states, refresh actions, data status and interaction behavior.

## Requirements

### Requirement: Summary before detail
The view SHALL open with a single header line naming the valuation date, followed by a summary of the securities value, the resolved share, the share of the ten largest companies and the number of decomposed funds out of held funds. Explanatory notices SHALL NOT appear as permanent paragraphs; they SHALL be reachable through an indicator with a tooltip or the information pane.

#### Scenario: Partial coverage with unvalidated weights
- **WHEN** the result is partial and contains compositions whose weights are not validated
- **THEN** the summary shows the resolved share, an indicator marks the unvalidated weights, and its tooltip explains the limitation without additional paragraphs above the table.

#### Scenario: Missing valuation
- **WHEN** a held security cannot be valued in the reporting currency
- **THEN** the summary withholds whole-portfolio percentages and the indicator names the missing valuation.

### Requirement: Coverage bar
The view SHALL show a horizontal bar that splits the securities value into company exposure, ungrouped equities, cash and other assets, and unresolved value. Segments SHALL be proportional to value, SHALL be labelled in a tooltip with amount and percentage, and SHALL NOT hide a nonzero unresolved remainder through rounding.

#### Scenario: Small unresolved remainder
- **WHEN** the unresolved value is nonzero but below one tenth of a percent
- **THEN** the unresolved segment remains visible with a minimum width and its tooltip shows the exact amount.

#### Scenario: Missing denominator
- **WHEN** the securities denominator cannot be established
- **THEN** the bar is replaced by a short notice that coverage cannot be computed.

### Requirement: Company charts
The view SHALL offer a treemap of companies sized by total exposure and a bar chart of the largest companies stacked by direct and indirect exposure. Charts SHALL show only company exposure; unresolved and other categories SHALL stay in the coverage bar. Each chart element SHALL show the company, amount and share in a tooltip.

#### Scenario: Company held directly and through funds
- **WHEN** a company is held directly and through two funds
- **THEN** its treemap tile shows the combined exposure, and its bar shows separate direct and indirect segments.

#### Scenario: No company exposure
- **WHEN** the result contains no company exposure
- **THEN** the chart area shows an empty-state message instead of empty axes.

### Requirement: Linked chart and table selection
Selecting a company in a chart SHALL select, expand and reveal the same company in the table. Selecting a company in the table SHALL highlight it in the visible chart.

#### Scenario: Select a treemap tile
- **WHEN** the user clicks the tile of a company whose row is collapsed
- **THEN** the table selects that company row, expands it to show its contributions and scrolls it into view.

### Requirement: Stable selection across updates
Status updates, automatic refresh checks and recalculations SHALL preserve the selected rows, expanded rows and scroll position in the company table and in the data status table, as long as the selected items still exist.

#### Scenario: Automatic refresh check with no new data
- **WHEN** the periodic refresh check runs and no composition changed
- **THEN** neither table is rebuilt and the selection stays unchanged.

#### Scenario: New composition arrives
- **WHEN** a refreshed composition changes the result while a company row is selected and expanded
- **THEN** the same company stays selected and expanded after the update.

#### Scenario: Selected item disappears
- **WHEN** a selected company no longer appears in the new result
- **THEN** the selection is cleared without an error.

### Requirement: Responsive recalculation
Recalculation SHALL happen only when its inputs change: client data, valuation date, retained compositions or classifications. It SHALL run without blocking user interaction, and several changes in quick succession SHALL produce a single recalculation. A view that opens with a shown earlier result SHALL run one calculation in the background to bring that result up to date.

#### Scenario: Many fund results during one refresh
- **WHEN** a refresh delivers results for many funds in quick succession
- **THEN** the view stays responsive to scrolling and selection and recalculates once per burst instead of once per fund.

#### Scenario: Return with an earlier result
- **WHEN** the view opens with a result from an earlier opening
- **THEN** it shows that result and runs exactly one background calculation for the current inputs.

### Requirement: Single refresh action with inline progress
The view SHALL offer one refresh action. While a refresh runs, an inline progress indicator SHALL show completed and total lookups and offer a stop control. Starting or ending a refresh SHALL NOT move the summary, charts or table. The view SHALL NOT show a permanently visible cancel action.

#### Scenario: Refresh in progress
- **WHEN** the user starts a refresh for funds that need constituent lookups
- **THEN** the refresh action is disabled, the progress line shows the counts, and stopping it ends waiting without discarding results already applied.

#### Scenario: No layout shift
- **WHEN** the user starts a refresh and it later completes
- **THEN** the progress appears and disappears in place of the valuation date in the summary row, and no other element of the view changes position.

#### Scenario: Nothing to refresh
- **WHEN** no held fund needs a composition
- **THEN** the refresh action is disabled and no progress line is shown.

### Requirement: Onboarding and empty states
The view SHALL show one banner for the most relevant state: no API key configured, provider access suspended, no fund held, or empty portfolio. The no-key and suspended banners SHALL offer to open the provider's settings page. Holdings SHALL remain listed with their value in every state; without provider credentials the view SHALL NOT claim that any value is attributed to companies.

#### Scenario: No API key
- **WHEN** the portfolio holds funds or directly held securities and no API key is configured
- **THEN** a banner states that funds and directly held securities are broken down by company only with an API key and opens the provider settings page on request, while the coverage states that no value is attributed to companies.

#### Scenario: Authentication failure
- **WHEN** the provider rejected the configured key
- **THEN** the banner states that access is suspended and offers to open the provider settings page.

#### Scenario: No funds held
- **WHEN** the portfolio holds only directly held securities and an API key is configured
- **THEN** no banner is shown and the view displays the direct breakdown.

### Requirement: Compact data status
The data status pane SHALL list each held fund, and each held security whose classification is still unknown, with its name, a status icon with a short status text, retrieval date and decomposed share. Securities classified as directly held shares SHALL NOT be listed. Attempt, retry, provider refresh, holdings date, freshness, source data and denominator details SHALL remain available in the row tooltip. When storage is persistent, the pane SHALL show the storage mode, the company data state and the assumed-company count in one line, and the details of that line SHALL stay in its tooltip. A limitation of persistence SHALL remain a separate, visible warning line.

#### Scenario: Persistent storage and completed company data

- **WHEN** data is stored on this computer and company data is loaded
- **THEN** one line names the storage and the company data with the assumed-company count, and the pane keeps its height with the fund rows visible.

#### Scenario: Session-only storage

- **WHEN** compositions are kept for this session only
- **THEN** a separate warning line names the limitation and the summary line keeps the company data state and the assumed-company count.

#### Scenario: Fund with failed refresh and retained data
- **WHEN** the latest refresh failed and an earlier composition from this session is used
- **THEN** the row shows a warning icon and the earlier retrieval date, and the tooltip names the failure and the original dates.

#### Scenario: Composition rejected by validation
- **WHEN** the provider returned a composition that validation rejects, for example because of negative weights or a total above 100 percent
- **THEN** the row states that the composition was rejected and names the reason in short text, the decomposed share shows a dash instead of zero, and the tooltip explains that the fund stays unresolved.

#### Scenario: Security without provider credentials
- **WHEN** a held security is unclassified and no API key is configured
- **THEN** the row states that no API key is configured and that the security is not broken down, without any progress text, and the row disappears once the security is classified as a directly held share.

#### Scenario: Security not yet classified
- **WHEN** an API key is configured and the first pass has not reached the security yet
- **THEN** the row states that it was not retrieved yet, and the row disappears once the security is classified as a directly held share.

### Requirement: Immediate restore from the last result

Within one session, when the view opens for a portfolio whose earlier opening produced a result, the view SHALL show that result immediately instead of the calculating state, also when the portfolio has unsaved changes. It SHALL replace the result when the background calculation finishes. The slot of the valuation date SHALL indicate that an update runs. Without an earlier result, the calculating state stays.

#### Scenario: Return to the view

- **WHEN** the user leaves the view and returns to it without changing the portfolio
- **THEN** the previous result, charts and summary appear at once, the date slot shows that an update runs, and the table is not replaced by the calculating message.

#### Scenario: Updated result arrives

- **WHEN** the background calculation finishes after a return
- **THEN** the new result replaces the shown one and the update indicator disappears.

#### Scenario: Unsaved changes

- **WHEN** the portfolio has unsaved changes and an earlier result exists
- **THEN** the earlier result is shown and replaced when the calculation finishes.

#### Scenario: Other portfolio

- **WHEN** the view opens for a portfolio without an earlier result in this session
- **THEN** the view shows the calculating state.

### Requirement: Stable header controls

The refresh action SHALL stay visible when the grouping or chart selection changes and when the window is narrow. Changing a selection SHALL NOT move the refresh action; only the selectors may collapse into the overflow menu. The grouping selector SHALL name the current grouping in its label and menu.

#### Scenario: Long grouping name

- **WHEN** the user selects a grouping with a long label in a narrow window
- **THEN** the refresh action stays visible and only the selectors move or collapse.

#### Scenario: Switching grouping

- **WHEN** the user switches the grouping back and forth
- **THEN** the grouping selector shows the new grouping, the refresh action keeps its position and no overflow marker appears for the refresh action.

### Requirement: Calculating state
Until the first result for the open portfolio is available, the view SHALL show a calculating state that is distinct from every empty state: placeholders in the summary and a calculating message in the chart area and the table. Empty-state messages SHALL appear only for a calculated result.

#### Scenario: Opening the view
- **WHEN** the user opens the view and the first calculation is still running
- **THEN** the summary shows placeholders and the chart area and table state that the breakdown is being calculated, without the "no company exposure" message.

### Requirement: Data status summary row
An always-visible status row SHALL separate the table from the data status pane, SHALL open and close the pane when activated, and SHALL state the current refresh situation in plain words.

#### Scenario: Toggle
- **WHEN** the user activates the row
- **THEN** the data status pane opens or closes, and the row keeps showing the current state.

### Requirement: Status row wording
The row SHALL distinguish running lookups from waiting ones and answered results from usable data. It SHALL NOT describe a running or waiting lookup as loaded or available.

#### Scenario: Refresh running
- **WHEN** a refresh is running and some funds wait for the rate limit
- **THEN** the row states how many lookups run, how many wait and how many are answered, and the pane stays closed if the user closed it.

#### Scenario: Settled
- **WHEN** no lookup is running
- **THEN** the row states how many funds have data and how many have no composition.

### Requirement: Status row warning
When a lookup failed or a composition was rejected, the row SHALL carry a warning marker, name the problem and, when known, state the time of the next attempt.

#### Scenario: Failure
- **WHEN** at least one lookup failed or a composition was rejected
- **THEN** the row shows a warning marker and names the problem and the next attempt time.

### Requirement: Separated data status pane
The chart and table area SHALL be visually separated from the header above and from the data status pane below, so that each reads as a distinct section. The pane SHALL start collapsed unless the user has opened or closed it before; an explicit user choice SHALL be remembered per view and SHALL NOT be overridden by the view. A visible status row SHALL remain between the table and the pane in both states.

#### Scenario: Pane below the table
- **WHEN** the data status pane is open below the chart and table
- **THEN** a visible boundary and spacing separate the two areas in light and dark theme.

#### Scenario: Header above the charts
- **WHEN** the view shows a result
- **THEN** a visible boundary separates the summary and coverage legend from the chart and table.

#### Scenario: First opening
- **WHEN** the view is opened in a workspace that has no stored state for the pane
- **THEN** the pane is collapsed and the status row is visible.

#### Scenario: Stored state
- **WHEN** the user opened or closed the pane earlier
- **THEN** the view restores that state and the status row stays visible.

### Requirement: Percent signs outside percent columns
Shares SHALL carry a percent sign wherever they are shown: in the summary, the coverage legend and tooltips, the chart labels, treemap group headers and tooltips, and the share column of the table.

#### Scenario: Summary and legend
- **WHEN** the summary shows the assigned share and the top ten share and the coverage legend shows its categories
- **THEN** each share is followed by a percent sign, as are the values in the table's share column.

### Requirement: Coverage legend
The coverage legend SHALL carry a caption naming it as coverage, SHALL list only nonzero categories and SHALL NOT reuse the colors that the charts use for direct and indirect holdings.

#### Scenario: Zero categories
- **WHEN** ungrouped equities and cash or other assets are zero
- **THEN** the coverage legend lists only companies and the remaining nonzero categories.

### Requirement: Treemap tiles and bar values
The treemap SHALL show every company as its own tile, SHALL leave no area unpainted, and its legend SHALL be a labelled scale from directly held to held through funds. Each bar SHALL show its total amount and share next to it.

#### Scenario: Many small companies
- **WHEN** the result contains hundreds of companies with tiny exposure
- **THEN** every company keeps its own tile in size order, tiles too small for a gap are drawn without one, the area left by tiles too small to draw is filled in the tile color for fund holdings, and the chart area contains no blank region.

#### Scenario: Bar values
- **WHEN** the bar chart shows the largest companies
- **THEN** each bar is followed by its total amount and share, and the value axis leaves enough room for the longest label.

### Requirement: Table columns
The table SHALL show a rank column first for top-level company or group rows by total and name the second column after the selected grouping. Contributions SHALL show their source in the second column below their company. Rows whose total rounds to zero in the currency SHALL be hidden and not ranked.

#### Scenario: Ranked groups
- **WHEN** the user groups by sector
- **THEN** the first column is the rank, the second reads "Sector / company", sectors carry ranks 1, 2, 3 by total, and Unknown, Pending and coverage category rows carry no rank.

### Requirement: Grouping selector
The view SHALL offer a "Breakdown by" selector with Company, Country, Region, Market classification, Sector, Industry and Currency. Company SHALL be the default. The selection SHALL be remembered per view and SHALL NOT trigger provider requests.

#### Scenario: Switch to country
- **WHEN** the user selects Country
- **THEN** the table, treemap and bar chart show countries, using the current result and stored attributes only.

### Requirement: Group rows
For every grouping except Company, the table SHALL show one row per group with direct, indirect and total value and share. A group row SHALL expand into its companies, and the companies SHALL keep their contributions. The group totals SHALL add up to the company exposure of the result.

#### Scenario: Country totals
- **WHEN** the result contains companies headquartered in two countries
- **THEN** each country row shows the sum of its companies, and both rows together equal the company exposure.

### Requirement: Unknown and pending groups
Companies without the selected attribute SHALL be shown in an "Unknown" group, and companies whose enrichment is still pending in a "Pending" group. Both groups SHALL show their count in the label.

#### Scenario: Enrichment running
- **WHEN** the user groups by sector while enrichment of the smaller companies is still running
- **THEN** the enriched companies appear in their sectors, the others in "Pending", and the groups update as enrichment completes without losing the selection.

### Requirement: Grouped charts
For every grouping except Company, the treemap SHALL show groups as tiles with their companies nested inside, and the bar chart SHALL show the largest groups split into direct and indirect value with amount and share next to each bar. Selecting a group in a chart SHALL select and expand it in the table.

#### Scenario: Select a country tile
- **WHEN** the user clicks the area of a country in the treemap outside any labelled company
- **THEN** the table selects and expands that country row.

### Requirement: Standard classifications
Regions and market classification SHALL use PP's region taxonomy templates, mapping each country code to its template category. Sectors SHALL map provider sector names to PP's GICS sector template by name; unmatched names SHALL be shown as delivered. The country source SHALL be visible in the country tooltip.

#### Scenario: Country not in template
- **WHEN** a company's country is not listed in the region template
- **THEN** the company appears in the "Unknown" region group and keeps its country in the country grouping.

### Requirement: Revenue region grouping
The "Breakdown by" selector SHALL offer "Revenue region". It SHALL show the exposure allocated by revenue per country or region, companies without revenue data in their headquarters country marked as fallback, a "No revenue data" group for companies with neither, and a notice with the shares of company exposure allocated by revenue, by headquarters fallback and without data, plus the range of reporting periods.

#### Scenario: Partial revenue data
- **WHEN** companies covering 80 percent of the company exposure have revenue data
- **THEN** the remaining 20 percent are allocated to headquarters countries as marked fallback where known, and the notice states 80 percent by revenue, the fallback share and the share without data.

#### Scenario: Headquarters and revenue differ
- **WHEN** the user switches between Country and Revenue region
- **THEN** both groupings keep the same total, and the labels make clear that one shows headquarters and the other revenue.

### Requirement: Overlap chart
The chart pane SHALL offer an "Overlap" chart kind showing each pair of decomposed funds once, as the lower triangle of a fund matrix without diagonal. Each cell SHALL show the overlap percentage and the number of shared companies, shaded by size. A cell tooltip SHALL name both funds, the overlap, the number of shared companies and the five largest shared companies. With fewer than two decomposed funds, the chart SHALL show a short message instead of a matrix.

#### Scenario: Two decomposed funds
- **WHEN** the portfolio holds two decomposed funds
- **THEN** the overlap chart shows one cell for the pair with its overlap and shared company count.

#### Scenario: Single fund
- **WHEN** only one fund is decomposed
- **THEN** the overlap chart states that at least two decomposed funds are needed.

### Requirement: Concentration summary
The summary SHALL show the concentration as the approximate number of equally sized companies, computed as one divided by the sum of squared shares of each company in the company exposure, together with the number of companies that the value is based on. Its tooltip SHALL explain the calculation with a worked example, state that a smaller value means higher concentration, and name the basis. The summary SHALL also show the largest company with its share of the portfolio value. When a single company exceeds 10 percent of the portfolio value, the attention indicator SHALL name that company and its share.

#### Scenario: Evenly spread
- **WHEN** the company exposure is split evenly across 100 companies
- **THEN** the summary shows a concentration of about 100 companies out of 100 companies.

#### Scenario: Uneven split
- **WHEN** three companies hold 50, 25 and 25 percent of the company exposure
- **THEN** the summary shows a concentration of about 2.67 companies, and the tooltip shows the same calculation next to the evenly split example.

#### Scenario: Dominant company
- **WHEN** one company accounts for 12 percent of the portfolio value
- **THEN** the summary shows it as the largest company and the attention indicator names it with its 12 percent share.
