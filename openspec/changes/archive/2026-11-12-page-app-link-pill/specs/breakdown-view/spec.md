## MODIFIED Requirements

### Requirement: Application link in the header
When an application link is configured, the page SHALL show it as a plain link with its label, fixed at the bottom right of the viewport and visible while scrolling, opening in the same tab with `rel="noopener"`, with URL and label escaped, working without JavaScript and loading nothing. The link SHALL be a small pill with a dark translucent surface and light text in both color schemes, less opaque until hovered or focused, and SHALL show a visible focus ring on keyboard focus. The page SHALL leave room below its last line so that the link does not cover content. Without a configured link the page SHALL contain no link and no placeholder.

#### Scenario: Link configured
- **WHEN** the link is `https://example.org/` with the label `PP & Co <test>`
- **THEN** the page contains `<a class="app-link" href="https://example.org/" rel="noopener">PP &amp; Co &lt;test&gt;</a>` outside the top bar, no `target` attribute, and the page makes no request to the URL.

#### Scenario: Scrolled to the end
- **WHEN** a link is configured and the user has scrolled to the end of the page
- **THEN** the link is visible at the bottom right and the last line of the page is not covered by it.

#### Scenario: No link
- **WHEN** no application link is configured
- **THEN** the page contains no application link and no placeholder for it.

### Requirement: Top bar
The page SHALL show a bar that stays at the top of the viewport while scrolling. It SHALL contain the "Breakdown by" selector, the company search, the links to the three page sections, the generation time and the staleness notice. The summary SHALL follow below the bar and SHALL scroll with the page.

#### Scenario: Scrolled down
- **WHEN** the user has scrolled to the end of the table
- **THEN** the grouping selector, the search field and the section links are visible without scrolling back.

#### Scenario: No application link configured
- **WHEN** an application link is configured or not
- **THEN** the bar contains no application link and no placeholder for it.
