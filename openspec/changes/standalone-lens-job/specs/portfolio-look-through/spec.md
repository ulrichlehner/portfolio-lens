# Spec Delta

## ADDED Requirements

### Requirement: Standalone identity
The analysis SHALL be presented as Portfolio Lens, a standalone companion to Portfolio Performance, on a page titled Breakdown by Company. It SHALL state that it is not part of PP and not supported by the PP maintainers, and SHALL NOT require any change to PP or to the portfolio file.

#### Scenario: Open the page
- **WHEN** a user opens the generated page
- **THEN** the page title reads Breakdown by Company, the header names Portfolio Lens and the analysed PP file label, and the footer carries the independence notice.

## MODIFIED Requirements

### Requirement: Portfolio scope
The analysis SHALL value every instrument holding that PP reports for the configured file, in the reporting currency of the holdings response. It SHALL NOT require a prior selection. Percentages SHALL use the current value of those holdings, excluding account cash. Unsupported negative positions SHALL be disclosed as excluded, and an empty portfolio SHALL show an explicit empty state.

#### Scenario: Open without a prior selection
- **WHEN** a run reads a file with instrument holdings
- **THEN** the analysis covers all instrument holdings and reports the valuation date of the holdings response.

#### Scenario: Accounts and closed positions
- **WHEN** the holdings contain instruments, cash accounts and a holding of a type Lens does not know
- **THEN** only the instruments enter the denominator, the page labels the account-cash exclusion and names the unsupported holding as not analysed.

#### Scenario: Negative position
- **WHEN** a held position is negative
- **THEN** the page identifies the excluded unsupported position and does not claim complete coverage of the entire portfolio.

#### Scenario: Empty portfolio
- **WHEN** the file has no instrument holding
- **THEN** the page states that there is no holding and shows no invented coverage categories.

### Requirement: Visible analysis limitations
The page SHALL state that current valuations are combined with available composition dates. It SHALL show unresolved exposure alongside results, provide per-source reasons and refresh status, and remain usable without manual uploads. Unsupported economic exposure SHALL NOT be substituted with collateral or a similar index fund.

#### Scenario: Known fund has no usable composition
- **WHEN** an instrument is classified as a fund but its composition is unavailable or unusable
- **THEN** its value remains in the not-yet-decomposed category, with a visible reason distinguishing missing composition, unusable composition and a weight shortfall from missing instrument classification.

#### Scenario: Missing identifier is not cash evidence
- **WHEN** a source row has no usable identifier and no verified asset classification
- **THEN** its value remains unclassified rather than being assigned to cash or other assets merely because an identifier is absent.

#### Scenario: Unsupported synthetic fund
- **WHEN** only a synthetic fund's collateral basket is available
- **THEN** its value remains unresolved with an unsupported-exposure reason, while other validated holdings remain visible.

#### Scenario: Session-only composition storage
- **WHEN** persistent caching is not permitted because local storage is not confirmed or the provider policy does not allow it
- **THEN** no provider data is requested, every unclassified position remains in the not-yet-decomposed category with a reason naming the missing confirmation or the policy, and no row or banner presents a retrieval that has not started.

#### Scenario: No provider credentials
- **WHEN** no provider credentials are configured
- **THEN** every unclassified position remains in the not-yet-decomposed category with a reason naming the missing credentials, and no row or banner presents a retrieval or a check that has not started.

### Requirement: Existing portfolio compatibility
Lens SHALL NOT modify transactions, instruments or the portfolio file; it reads PP only. Unavailable or incompatible cached data SHALL be safely discarded and shown as unavailable until retrieved again.

#### Scenario: Cache incompatible after update
- **WHEN** existing cached composition data cannot be read by the current Lens version
- **THEN** the run continues, the incompatible data is discarded, and affected exposure is unresolved until a usable replacement is obtained.

## REMOVED Requirements

### Requirement: Feature identity
**Reason**: Lens is no longer a view inside PP's navigation, and the page is English only in this step.
**Migration**: Replaced by "Standalone identity". German texts can return with a later translation change.
