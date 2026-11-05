## MODIFIED Requirements

### Requirement: Conservative numerical validation
The system SHALL preserve source rows and use decimal weights without general normalization. Malformed, nonfinite, negative or overfull weights SHALL make a snapshot unusable, except for net cash of rows without an identifier within its limit and an evidenced rounding excess of at most one millionth in fractional-weight units. Accepted excess SHALL be removed proportionally with exact reconciliation. Positive shortfalls SHALL remain unresolved. Legitimate duplicate rows SHALL only be combined after validating their semantics.

#### Scenario: Signed or overfull response
- **WHEN** a response contains a negative allocation on a row with a security identifier, a negative net of rows without an identifier beyond the net cash limit, or a total above 100% beyond the rounding tolerance
- **THEN** the system retains diagnostic source information and leaves the fund unresolved rather than dropping rows or rescaling it.

#### Scenario: Provider rounding excess
- **WHEN** published weights sum to 100.00000001% because of decimal rounding
- **THEN** the rows remain eligible for classification after a proportional correction within the tolerance, reconcile exactly to the fund value and keep a zero shortfall, while the original source rows remain unchanged.

#### Scenario: Tiny positive shortfall
- **WHEN** valid nonnegative weights sum to less than 100% by an amount that rounds to zero in the display
- **THEN** the exact shortfall remains unresolved and is not normalized away or treated as complete coverage.

#### Scenario: Legitimate duplicate positions
- **WHEN** two nonnegative positions represent the same verified instrument within a valid NAV composition
- **THEN** both contributions are retained in the calculation rather than discarding one by identifier.

### Requirement: Netting of rows without identifier
Negative rows without a security identifier SHALL follow this requirement instead of the negative-weight rule. When all identified rows are nonnegative, rows without an identifier SHALL be netted against each other. A zero or positive net SHALL stay unresolved as one amount with a non-fatal diagnostic. A negative net of at most 10% of the fund's NAV (a named limit) SHALL be the fund's net cash: the snapshot stays usable, identified rows keep their weights and may sum to more than 100% by exactly that net, and a non-fatal diagnostic is recorded. A negative net below the limit SHALL make the snapshot unusable with its own reason. Netting SHALL NOT classify rows by name, rescale identified rows or change source rows.

#### Scenario: Negative cash and currency rows without identifier
- **WHEN** a composition sums to 100%, all identified rows are nonnegative, and its rows without an identifier are +0.05% and -0.015% of the fund
- **THEN** the identified rows keep their exact weights and are eligible for classification, the identifier-less rows become one unresolved amount of +0.035%, the fund reconciles exactly, the source rows stay unchanged, and a netting diagnostic is recorded.

#### Scenario: Negative net without identifier
- **WHEN** the identified rows of a composition sum to 103% and its rows without an identifier, for example a "USD CASH" row and small "INR/USD" forward rows, net to -3%
- **THEN** the snapshot is usable, the identified rows keep their weights, the rows without an identifier become one net cash amount of -3% that is not classified by name, the fund reconciles exactly, and a net cash diagnostic is recorded.

#### Scenario: Short position with identifier
- **WHEN** a row with a security identifier has a negative weight
- **THEN** the snapshot is unusable even if identifier-less rows could absorb the amount.

## ADDED Requirements

### Requirement: Net cash beyond the limit
A negative net of rows without an identifier below -10% of the fund's NAV SHALL make the snapshot unusable. The rejection SHALL name negative net cash beyond the limit, which likely means leverage, and SHALL be told apart from negative weights of identified rows.

#### Scenario: Large negative net
- **WHEN** the identified rows sum to 112% and the rows without an identifier net to -12%
- **THEN** the fund stays unresolved and its data status says that the negative net cash is beyond the limit.

### Requirement: Re-evaluation after rule changes
When the validation rules change, the first run after the upgrade SHALL evaluate again every fund whose last attempt ended without a usable composition, without waiting for the fund cadence. Funds with a stored usable composition SHALL keep their cadence.

#### Scenario: Fund rejected by the old rule
- **WHEN** a run under the old rules rejected a fund for its negative net cash and the next run uses the new rules on unchanged input
- **THEN** the next run is not skipped, requests that fund's composition once and uses it, and requests no composition of the other funds.
