# Spec Delta

## ADDED Requirements

### Requirement: Two-pane layout
On a viewport at least 1100 CSS pixels wide, the page SHALL show the charts in a left pane and the breakdown table in a right pane. With scripts, the chart pane SHALL stay in view while the table scrolls with the page, and the page SHALL keep one vertical scrollbar. On a narrower viewport the charts SHALL be shown above the table in one column.

#### Scenario: Scrolling a long table
- **WHEN** the user scrolls to the 150th company row on a viewport 1440 pixels wide
- **THEN** the chart pane is still fully visible next to the table.

#### Scenario: Narrow viewport
- **WHEN** the page is opened on a viewport 600 pixels wide
- **THEN** the chart is shown above the table in one column and the page has no horizontal scrollbar.

#### Scenario: Chart pane taller than the viewport
- **WHEN** the viewport is too low to show the whole chart pane
- **THEN** every part of the chart pane can still be reached by scrolling.

### Requirement: Top bar
The page SHALL show a bar that stays at the top of the viewport while scrolling. It SHALL contain the "Breakdown by" selector, the company search, the configured application link, the generation time and the staleness notice. The summary and the coverage bar SHALL follow below the bar and SHALL scroll with the page.

#### Scenario: Scrolled down
- **WHEN** the user has scrolled to the end of the table
- **THEN** the grouping selector, the search field and the application link are visible without scrolling back.

#### Scenario: No application link configured
- **WHEN** no application link is configured
- **THEN** the bar contains no application link and no empty placeholder for it.

### Requirement: Chart tabs
The chart pane SHALL offer the chart kinds Treemap, Top 20 and Overlap as tabs and SHALL show one of them at a time, Treemap first. The selected chart kind SHALL be kept when the grouping changes and SHALL be kept in the page address. The tabs SHALL be operable by keyboard and SHALL expose which one is selected to assistive technology.

#### Scenario: Switch to the overlap matrix
- **WHEN** the user selects the Overlap tab
- **THEN** the chart pane shows the overlap matrix in place of the treemap and the table stays where it is.

#### Scenario: Grouping change keeps the tab
- **WHEN** the Top 20 tab is selected and the user changes the grouping from Company to Sector
- **THEN** the chart pane shows the bar chart of the largest sectors.

#### Scenario: Reload
- **WHEN** the user reloads the page with the Overlap tab selected
- **THEN** the page shows the Overlap tab again.

### Requirement: Treemap group zoom
In every grouping except Company, activating a group in the treemap SHALL show the companies of that group as a treemap of their own over the whole chart, with the companies below the minimum tile area merged into one tile. Activating a company tile on that level SHALL select the company. Tile areas on a zoomed level SHALL stay proportional to the values within that group.

#### Scenario: Zoom into a sector
- **WHEN** the user clicks anywhere inside the Information Technology area of the Sector treemap
- **THEN** the treemap shows only the companies of Information Technology, the largest ones labelled, and the breadcrumb names the sector.

#### Scenario: Company on a zoomed level
- **WHEN** the user clicks a company tile after zooming into a country
- **THEN** that company's row below the country row is selected in the table.

#### Scenario: Keyboard
- **WHEN** the user moves the focus to a group in the treemap and presses Enter
- **THEN** the treemap zooms into that group as it does on a click.

### Requirement: Company search
The top bar SHALL offer a search field that finds companies by name, ignoring case, among all companies of the result. While a search text is entered, the table SHALL list the matching companies by total value with their rank in the Company grouping and their contributions, in every grouping, and SHALL state how many companies match. Clearing the field SHALL restore the table of the selected grouping.

#### Scenario: Company outside the rendered rows
- **WHEN** the result has 4,000 companies and the user enters the name of the company ranked 3,500
- **THEN** the table lists that company with rank 3,500 and its values.

#### Scenario: No match
- **WHEN** no company name contains the search text
- **THEN** the table states that no company matches and shows no company row.

#### Scenario: Clear
- **WHEN** the user clears the search field while the Sector grouping is selected
- **THEN** the table shows the sector rows again.

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
- **WHEN** the user switches between the Treemap, Top 20 and Overlap tabs
- **THEN** the chart pane keeps its height and the table does not move.

### Requirement: Explanations without hover
Every explanation that the page offers as a tooltip of a summary value, the coverage legend, the data status line or a data status row SHALL also be reachable by click, tap and keyboard, and SHALL be shown as text on the page when opened.

#### Scenario: Fund row on a touch screen
- **WHEN** the user taps the data status row of a fund on a device without hover
- **THEN** the attempt, retry, provider refresh, holdings date, freshness, source data and denominator details of that fund are shown as text.

#### Scenario: Concentration
- **WHEN** the user moves the focus to the concentration value with the keyboard and activates its explanation
- **THEN** the explanation of the calculation is shown as text.

## MODIFIED Requirements

### Requirement: Linked chart and table selection
Selecting a company in a chart SHALL select, expand and reveal the same company in the table. Selecting a company in the table SHALL highlight it in the visible chart. Revealing a row SHALL scroll the page only when the row is not fully visible, and only as far as needed; the chart SHALL remain visible on a viewport that shows two panes.

#### Scenario: Select a treemap tile
- **WHEN** the user clicks the tile of a company whose row is collapsed
- **THEN** the table selects that company row, expands it to show its contributions and scrolls it into view.

#### Scenario: Row already visible
- **WHEN** the user clicks the tile of a company whose row is fully visible
- **THEN** the row is selected and expanded and the page does not scroll.

#### Scenario: Chart stays in view
- **WHEN** the user clicks the tile of a company whose row is far below on a viewport 1440 pixels wide
- **THEN** the row is scrolled into view and the treemap with the highlighted tile is still visible.

### Requirement: Grouped charts
For every grouping except Company, the treemap SHALL show groups as tiles with their companies nested inside, and the bar chart SHALL show the largest groups split into direct and indirect value with amount and share next to each bar. Selecting a group in the treemap SHALL zoom into it and SHALL select and expand it in the table. Selecting a group in the bar chart SHALL select and expand it in the table.

#### Scenario: Select a country tile
- **WHEN** the user clicks the area of a country in the treemap
- **THEN** the treemap zooms into that country and the table selects and expands that country row.

#### Scenario: Select a country bar
- **WHEN** the user clicks the bar of a country in the bar chart
- **THEN** the table selects and expands that country row and the bar chart stays as it is.

### Requirement: Explorable merged tile
The merged tile SHALL state the number of its companies, their total share and the size limit, SHALL be reachable by keyboard, and SHALL be drawn as one grey tile. Activating it SHALL show its companies as a treemap of their own over the whole chart, merging again below the minimum area; a group's merged tile SHALL show the companies of that group only.

#### Scenario: Label of the merged tile
- **WHEN** 3,882 synthetic companies with together 33.1 % of the value are merged, each below 0.05 %
- **THEN** the tile, its tooltip and its accessible name read "3,882 companies, 33.1%, each below 0.05%" and hint that it can be explored.

#### Scenario: Exploring with the keyboard
- **WHEN** the user moves the focus to the merged tile and presses Enter
- **THEN** its companies fill the chart, a nested merged tile holds the companies still too small, and selecting a company there selects its table row.

#### Scenario: Merged tile of a zoomed group
- **WHEN** the user has zoomed into a sector and activates its merged tile
- **THEN** the treemap shows the smaller companies of that sector only.

### Requirement: Treemap level navigation
While the treemap shows a group or the companies of a merged tile, a breadcrumb above the treemap SHALL name the levels and lead back to each of them, a visible control SHALL return one level, Escape SHALL return one level, and the page address SHALL keep the level next to the grouping so that a reload shows the same level. An address whose level does not exist in the page SHALL show the overview of its grouping.

#### Scenario: Back to the overview
- **WHEN** the user explores two levels deep and presses Escape twice
- **THEN** the treemap shows the overview again and the breadcrumb is hidden.

#### Scenario: Reload of an explored level
- **WHEN** the user reloads the page while exploring the smaller companies of a sector
- **THEN** the page shows the same grouping and level, and the breadcrumb names the sector.

#### Scenario: Group and merged tile in the breadcrumb
- **WHEN** the user zooms into a sector and then into its merged tile
- **THEN** the breadcrumb reads the overview, the sector and the smaller companies, and each of the first two leads back to its level.

#### Scenario: Address of a level that no longer exists
- **WHEN** the page is opened with an address naming a group number that the page does not have
- **THEN** the treemap shows the overview of the grouping named in the address.

### Requirement: Self-contained static page
The Breakdown SHALL be one HTML file with inline styles, server-rendered SVG charts, embedded result data and inline scripts. It SHALL load nothing from the network. Without scripts it SHALL still show the summary, the coverage bar, the Company charts, the overlap matrix, the largest company rows of the table and the data status.

#### Scenario: Opened offline
- **WHEN** the page is opened from disk without network access
- **THEN** it renders completely and the browser requests no other resource.

#### Scenario: Scripts disabled
- **WHEN** the page is opened with scripts disabled
- **THEN** the Company grouping is shown with its treemap, its bar chart and the overlap matrix below each other and its 200 largest company rows with their contributions, and only grouping, chart tabs, treemap levels, search, sorting, further rows and linked selection are unavailable.
