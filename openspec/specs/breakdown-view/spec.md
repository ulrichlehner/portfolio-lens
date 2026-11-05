# breakdown-view Specification

## Purpose

Defines how the look-through result is presented and operated in the Breakdown view: layout, summary, charts,
onboarding states, refresh actions, data status and interaction behavior.

## Requirements

### Requirement: Summary before detail
The page SHALL open with a single header line naming the valuation date and the generation time, followed by a summary of the securities value, the resolved share, the share of the ten largest companies and the number of decomposed funds out of held funds. Explanatory notices SHALL NOT appear as permanent paragraphs; they SHALL be reachable through an indicator with a tooltip or the data status.

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

### Requirement: Onboarding and empty states
The page SHALL show one banner for the most relevant state: no API key configured, local storage not confirmed, provider access suspended, no fund held, or empty portfolio. The key, confirmation and suspended banners SHALL name the configuration setting to change. Holdings SHALL remain listed with their value in every state; without provider data the page SHALL NOT claim that any value is attributed to companies.

#### Scenario: No API key
- **WHEN** the portfolio holds funds or directly held securities and no API key is configured
- **THEN** a banner states that funds and directly held securities are broken down by company only with an API key and names the key setting, while the coverage states that no value is attributed to companies.

#### Scenario: Local storage not confirmed
- **WHEN** an API key is configured and the local storage confirmation is off
- **THEN** a banner at the top of the page, above the summary and not collapsible, states that the API key has no effect and that provider data is used only after confirming that the provider plan allows local storage, and names `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true` as the exact setting to add.

#### Scenario: Authentication failure
- **WHEN** the provider rejected the configured key in an earlier run
- **THEN** the banner states that access is suspended until the key setting changes.

#### Scenario: No funds held
- **WHEN** the portfolio holds only directly held securities and provider access is configured and confirmed
- **THEN** no banner is shown and the page displays the direct breakdown.

### Requirement: Compact data status
The data status SHALL list each held fund and each held security of still unknown classification, with its name, a status icon and short status text, retrieval date and decomposed share. Securities classified as directly held shares SHALL NOT be listed. The row tooltip SHALL keep the attempt, retry, provider refresh, holdings date, freshness, source data and denominator details. One line SHALL show storage state, company data state and assumed-company count, with details in its tooltip.

#### Scenario: Persistent storage and completed company data
- **WHEN** compositions are stored in the cache and company data is loaded
- **THEN** one line names the storage and the company data with the assumed-company count, and the fund rows stay visible below it.

#### Scenario: Session-only storage
- **WHEN** a provider key is configured but local storage is not confirmed
- **THEN** a separate warning line states that provider data is not used until local storage is confirmed, and the summary line keeps the company data state and the assumed-company count.

#### Scenario: Fund with failed refresh and retained data
- **WHEN** the latest refresh failed and an earlier stored composition is used
- **THEN** the row shows a warning icon and the earlier retrieval date, and the tooltip names the failure and the original dates.

#### Scenario: Composition rejected by validation
- **WHEN** the provider returned a composition that validation rejects, for example because of negative weights or a total above 100 percent
- **THEN** the row states that the composition was rejected and names the reason in short text, the decomposed share shows a dash instead of zero, and the tooltip explains that the fund stays unresolved.

#### Scenario: Security without provider credentials
- **WHEN** a held security is unclassified and no API key is configured
- **THEN** the row states that no API key is configured and that the security is not broken down, without any progress text.

#### Scenario: Security not yet classified
- **WHEN** provider access is configured and confirmed and the security has not been looked up because the budget or the run time was used up
- **THEN** the row states that the lookup is pending for a later run, and the row disappears in the first page generated after the security is classified as a directly held share.

### Requirement: Data status summary row
An always-visible status row SHALL separate the table from the data status, SHALL open and close the data status when activated, and SHALL state the outcome of the run that generated the page in plain words.

#### Scenario: Toggle
- **WHEN** the user activates the row
- **THEN** the data status opens or closes, and the row keeps showing the state.

### Requirement: Status row warning
When a lookup failed or a composition was rejected, the row SHALL carry a warning marker, name the problem and, when known, state the time of the next attempt.

#### Scenario: Failure
- **WHEN** at least one lookup failed or a composition was rejected
- **THEN** the row shows a warning marker and names the problem and the next attempt time.

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
Tile areas SHALL stay exactly proportional to value. The treemap SHALL show every company whose tile reaches a minimum area as its own tile, SHALL merge the smaller companies of each group into one tile placed after the others, SHALL separate adjacent tiles visibly, SHALL leave no area unpainted, and its legend SHALL be a scale from directly held to held through funds. Each bar SHALL show its total amount and share.

#### Scenario: Many small companies
- **WHEN** the result contains hundreds of companies with tiny exposure
- **THEN** the larger companies keep their own tiles in size order, the companies below the minimum tile area form one tile per group placed last with the area of their total value, adjacent tiles of the same color are separated by a thin line, and the chart area contains no blank region.

#### Scenario: Bar values
- **WHEN** the bar chart shows the largest companies
- **THEN** each bar is followed by its total amount and share, and the value axis leaves enough room for the longest label.

### Requirement: Table columns
The table SHALL show a rank column first for top-level company or group rows by total and name the second column after the selected grouping. Contributions SHALL show their source in the second column below their company. Rows whose total rounds to zero in the currency SHALL be hidden and not ranked.

#### Scenario: Ranked groups
- **WHEN** the user groups by sector
- **THEN** the first column is the rank, the second reads "Sector / company", sectors carry ranks 1, 2, 3 by total, and Unknown, Pending and coverage category rows carry no rank.

### Requirement: Grouping selector
The page SHALL offer a "Breakdown by" selector with Company, Country, Region, Sector, Industry, Currency and Revenue region. Company SHALL be the default. The selection SHALL be kept in the page address, so that reloading or sharing the address keeps it, and the page heading and title SHALL name the selected grouping.

#### Scenario: Switch to country
- **WHEN** the user selects Country
- **THEN** the table, treemap and bar chart show countries, using the data embedded in the page only, and the heading and the page title read "Breakdown by Country (headquarters)".

#### Scenario: Reload
- **WHEN** the user reloads the page after selecting Sector
- **THEN** the page shows the Sector grouping.

### Requirement: Group rows
For every grouping except Company, the table SHALL show one row per group with direct, indirect and total value and share. A group row SHALL expand into its companies, and the companies SHALL keep their contributions. The group totals SHALL add up to the company exposure of the result.

#### Scenario: Country totals
- **WHEN** the result contains companies headquartered in two countries
- **THEN** each country row shows the sum of its companies, and both rows together equal the company exposure.

### Requirement: Unknown and pending groups
Companies without the selected attribute SHALL be shown in an "Unknown" group, and companies whose enrichment is still pending in a "Pending" group. Both groups SHALL show their count in the label.

#### Scenario: Enrichment running
- **WHEN** the user groups by sector while enrichment of the smaller companies is still pending
- **THEN** the enriched companies appear in their sectors, the others in "Pending", and later runs move them into their sectors as enrichment completes.

### Requirement: Grouped charts
For every grouping except Company, the treemap SHALL show groups as tiles with their companies nested inside, and the bar chart SHALL show the largest groups split into direct and indirect value with amount and share next to each bar. Selecting a group in a chart SHALL select and expand it in the table.

#### Scenario: Select a country tile
- **WHEN** the user clicks the area of a country in the treemap outside any labelled company
- **THEN** the table selects and expands that country row.

### Requirement: Standard classifications
Countries SHALL be named by their ISO 3166 code with English names. Regions SHALL come from a country-to-region table maintained by Lens. Sectors SHALL map provider sector names to the 11 GICS sector names by name or alias; unmatched names SHALL be shown as delivered. The country source SHALL be visible in the country tooltip.

#### Scenario: Country not in template
- **WHEN** a company's country is not listed in the region table
- **THEN** the company appears in the "Unknown" region group and keeps its country in the country grouping.

#### Scenario: Provider alias for a sector
- **WHEN** the provider names a company's sector "Technology"
- **THEN** the company appears in the Information Technology sector.

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
The summary SHALL show the concentration as the approximate number of equally sized companies, computed as one divided by the sum of the squared company shares, with the number of companies it is based on. Its tooltip SHALL explain the calculation with an example, say that smaller means more concentrated, and name the basis. It SHALL also show the largest company and its share. When one company exceeds 10 percent of the portfolio value, the attention indicator SHALL name it with its share.

#### Scenario: Evenly spread
- **WHEN** the company exposure is split evenly across 100 companies
- **THEN** the summary shows a concentration of about 100 companies out of 100 companies.

#### Scenario: Uneven split
- **WHEN** three companies hold 50, 25 and 25 percent of the company exposure
- **THEN** the summary shows a concentration of about 2.67 companies, and the tooltip shows the same calculation next to the evenly split example.

#### Scenario: Dominant company
- **WHEN** one company accounts for 12 percent of the portfolio value
- **THEN** the summary shows it as the largest company and the attention indicator names it with its 12 percent share.

### Requirement: Pending lookup wording
The status row SHALL distinguish lookups pending for a later run from answered ones, and answered results from usable data. It SHALL NOT describe a pending lookup as loaded or available.

#### Scenario: Lookups pending
- **WHEN** the run that generated the page left some funds pending because of budget, backoff or the run time limit
- **THEN** the row states how many funds have data, how many are pending and when the next attempt is due.

#### Scenario: Settled
- **WHEN** no lookup is pending
- **THEN** the row states how many funds have data and how many have no composition.

### Requirement: Separated data status section
The chart and table area SHALL be visually separated from the header above and from the data status below, so that each reads as a distinct section. The data status SHALL start collapsed when the page loads, and a visible status row SHALL remain between the table and the data status in both states.

#### Scenario: Data status below the table
- **WHEN** the data status is open below the chart and table
- **THEN** a visible boundary and spacing separate the two areas in light and dark theme.

#### Scenario: Header above the charts
- **WHEN** the page shows a result
- **THEN** a visible boundary separates the summary and coverage legend from the chart and table.

#### Scenario: Page load
- **WHEN** the page is opened or reloaded
- **THEN** the data status is collapsed and the status row is visible.

### Requirement: Self-contained static page
The Breakdown SHALL be one HTML file with inline styles, server-rendered SVG charts, embedded result data and inline scripts. It SHALL load nothing from the network. Without scripts it SHALL still show the summary, the coverage bar, the Company charts, the largest company rows of the table and the data status.

#### Scenario: Opened offline
- **WHEN** the page is opened from disk without network access
- **THEN** it renders completely and the browser requests no other resource.

#### Scenario: Scripts disabled
- **WHEN** the page is opened with scripts disabled
- **THEN** the Company grouping is shown with its charts and its 200 largest company rows with their contributions, and only grouping, sorting, further rows and linked selection are unavailable.

### Requirement: Page size
The page SHALL hold each company once, in the embedded data, and render the 200 largest company rows and the group rows; further rows SHALL be built from the embedded data when the user asks for them, expands a group or selects a chart element. For a portfolio of about 4,000 companies the page SHALL stay below 3 MB and 30,000 elements.

#### Scenario: Large portfolio
- **WHEN** a page is generated for a synthetic portfolio of 4,000 companies held through ten funds
- **THEN** the file is smaller than 3 MB, has fewer than 30,000 elements, and the company table offers to show the remaining rows.

#### Scenario: Selecting a company in a collapsed group
- **WHEN** the user selects the tile of a company whose group row is collapsed
- **THEN** the group expands, its company rows are built, and the company row is selected and scrolled into view.

### Requirement: Provider budget on the page
When provider data is used, the data status SHALL state how many of the request units of the last 24 hours are used. While the budget is used up, the line SHALL be a warning that names when units free up.

#### Scenario: Budget used up
- **WHEN** the run that generated the page could not make further provider requests because the budget was used up
- **THEN** the data status shows a warning with the used and available units and the time when units free up.

### Requirement: Direct and fund colour scale
Each treemap tile SHALL be coloured by the share of the company held directly, on a continuous scale from green (only directly) to blue (only through funds) in 20 steps, with a gradient legend below the treemap. The bar chart SHALL use the same two colours with two swatches. Tiles that merge smaller companies SHALL be grey, outside the scale.

#### Scenario: Partly direct company
- **WHEN** half of a company's exposure is held directly and half through funds
- **THEN** its tile has the colour halfway between green and blue, and the legend below the treemap shows the gradient from "Held directly" to "Through funds".

#### Scenario: Merged small companies
- **WHEN** the treemap merges the smaller companies of a group into one tile
- **THEN** that tile is grey, not a colour of the scale.

### Requirement: Explorable merged tile
The merged tile SHALL state the number of its companies, their total share and the size limit, SHALL be reachable by keyboard, and SHALL show its companies as small unlabelled cells coloured on the direct-to-fund scale. Activating it SHALL show its companies as a treemap of their own over the whole chart, merging again below the minimum area; a group's merged tile SHALL show the companies of that group only.

#### Scenario: Label of the merged tile
- **WHEN** 3,882 synthetic companies with together 33.1 % of the value are merged, each below 0.05 %
- **THEN** the tile, its tooltip and its accessible name read "3,882 companies, 33.1%, each below 0.05%" and hint that it can be explored.

#### Scenario: Exploring with the keyboard
- **WHEN** the user moves the focus to the merged tile and presses Enter
- **THEN** its companies fill the chart, a nested merged tile holds the companies still too small, and selecting a company there selects its table row.

### Requirement: Treemap level navigation
While a merged tile is explored, a breadcrumb above the treemap SHALL name the levels and lead back to each of them, Escape SHALL return one level, and the page address SHALL keep the level next to the grouping so that a reload shows the same level.

#### Scenario: Back to the overview
- **WHEN** the user explores two levels deep and presses Escape twice
- **THEN** the treemap shows the overview again and the breadcrumb is hidden.

#### Scenario: Reload of an explored level
- **WHEN** the user reloads the page while exploring the smaller companies of a sector
- **THEN** the page shows the same grouping and level, and the breadcrumb names the sector.

### Requirement: Generation time and staleness
The page SHALL show when it was generated and the valuation date of the holdings. When opened more than one hour after generation, it SHALL show a notice that the page is older than expected and that the job may not be running.

#### Scenario: Job stopped
- **WHEN** the page is opened three hours after it was generated
- **THEN** the header shows the generation time and a notice that the page is three hours old.

### Requirement: Light and dark theme
The page SHALL follow the system color scheme and SHALL keep the chart, coverage and status colors distinguishable in both.

#### Scenario: Dark system theme
- **WHEN** the page is opened on a system set to dark mode
- **THEN** the page uses the dark palette, and the direct, indirect and coverage colors stay distinct.

### Requirement: No secrets or paths on the page
The page SHALL NOT contain the PP token, the provider key, the PP file path, the cache path or the configuration file path.

#### Scenario: Rendered page content
- **WHEN** a page is generated with a token, a key and a file path configured
- **THEN** none of these values occurs anywhere in the HTML.

### Requirement: Net cash of funds on the page
The page SHALL show companies at their real values and the net cash of funds as its own line with amount and share wherever totals are shown: the breakdown table, the summary and the data status of the fund. Treemap, bars and coverage bar SHALL NOT contain negative segments or exceed 100%; when net cash exists, a note under the treemap and under the coverage bar SHALL name it. Shares SHALL stay relative to the securities value.

#### Scenario: Fund with negative net cash
- **WHEN** the result contains net cash of funds of -300
- **THEN** the table has a "Net cash of funds" row of -300, the summary names it, the fund's data status row is OK and notes the net cash share, the treemap has no negative tile, its note reads "Not shown: net cash of funds" with the amount, and the coverage bar segments fit within the bar.

#### Scenario: No net cash
- **WHEN** no fund has net cash
- **THEN** no net cash row, summary item or note is shown.
