## ADDED Requirements

### Requirement: Net cash of funds on the page
The page SHALL show companies at their real values and the net cash of funds as its own line with amount and share wherever totals are shown: the breakdown table, the summary and the data status of the fund. Treemap, bars and coverage bar SHALL NOT contain negative segments or exceed 100%; when net cash exists, a note under the treemap and under the coverage bar SHALL name it. Shares SHALL stay relative to the securities value.

#### Scenario: Fund with negative net cash
- **WHEN** the result contains net cash of funds of -300
- **THEN** the table has a "Net cash of funds" row of -300, the summary names it, the fund's data status row is OK and notes the net cash share, the treemap has no negative tile, its note reads "Not shown: net cash of funds" with the amount, and the coverage bar segments fit within the bar.

#### Scenario: No net cash
- **WHEN** no fund has net cash
- **THEN** no net cash row, summary item or note is shown.
