## ADDED Requirements

### Requirement: Net cash of funds
The net cash of a fund's composition SHALL contribute the holding value times its negative weight in its own category, net cash of funds. It SHALL NOT be a company, cash or other assets, or unresolved value, and the contributions of a holding SHALL still sum to its value.

#### Scenario: Fund with negative net cash
- **WHEN** a holding of 10,000 is a fund whose identified rows sum to 103% and whose net cash is -3%
- **THEN** its companies receive 10,300 in total, net cash of funds receives -300, the contributions sum to 10,000, and no company named after a cash row exists.
