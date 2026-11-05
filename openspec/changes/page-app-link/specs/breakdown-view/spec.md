## ADDED Requirements

### Requirement: Application link in the header
When an application link is configured, the page header SHALL show it as a plain link with its label, opening in the same tab with `rel="noopener"`, with URL and label escaped, working without JavaScript and loading nothing. Without a configured link the header SHALL be unchanged.

#### Scenario: Link configured
- **WHEN** the link is `https://example.org/` with the label `PP & Co <test>`
- **THEN** the header contains `<a href="https://example.org/" rel="noopener">PP &amp; Co &lt;test&gt;</a>`, no `target` attribute, and the page makes no request to the URL.

#### Scenario: No link
- **WHEN** no application link is configured
- **THEN** the page contains no application link and its header is the same as before this change.
