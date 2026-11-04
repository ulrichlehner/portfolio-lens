# portfolio-look-through Specification

## Purpose

Explain current company exposure across direct securities and fund constituents while preserving traceable contributions and the objective of complete validated coverage.

## Requirements

### Requirement: Feature identity
The analysis SHALL be presented as Breakdown (German: Aufschlüsselung) within Portfolio Performance, as a sub-item of Statement of Assets (German: Vermögensaufstellung), with the view title Breakdown by Company (German: Aufschlüsselung nach Unternehmen). It SHALL retain the existing PP application identity and portfolio compatibility rather than requiring a separately branded application.

#### Scenario: Open the analysis
- **WHEN** a user navigates to the look-through analysis
- **THEN** the feature is listed as Breakdown under Statement of Assets within the existing PP application, and the view is titled Breakdown by Company.

#### Scenario: German user interface
- **WHEN** PP runs with the German language
- **THEN** the sidebar shows Aufschlüsselung under Vermögensaufstellung and the view is titled Aufschlüsselung nach Unternehmen.

### Requirement: Portfolio scope
The analysis SHALL value every security with a nonzero net position across all portfolios of the open client in the reporting currency. It SHALL NOT require a prior selection. Percentages SHALL use the current value of those held securities, excluding account cash. Zero positions SHALL contribute nothing. Unsupported negative positions SHALL be disclosed as excluded, and an empty portfolio SHALL show an explicit empty state.

#### Scenario: Open without a prior selection
- **WHEN** a user opens the Breakdown view in a client with holdings and no securities view is open
- **THEN** the analysis covers all held securities and reports the valuation date.

#### Scenario: Accounts and closed positions
- **WHEN** the portfolio contains positive securities, an account cash balance and a zero position
- **THEN** only positive securities enter the denominator, and the view labels the account-cash exclusion.

#### Scenario: Negative position
- **WHEN** a held position is negative
- **THEN** the view identifies the excluded unsupported position and does not claim complete coverage of the entire portfolio.

#### Scenario: Empty portfolio
- **WHEN** the portfolio has no current holding
- **THEN** the view states that there is no holding and shows no invented coverage categories.

### Requirement: Missing valuation visibility
Missing valuation or currency conversion SHALL make the analysis explicitly incomplete. The system SHALL NOT substitute zero or show a whole-selection coverage percentage when its denominator cannot be established.

#### Scenario: Missing exchange rate
- **WHEN** a selected security cannot be valued in the reporting currency
- **THEN** the system identifies the missing valuation and withholds whole-selection percentages while allowing clearly labelled valued-subset results.

### Requirement: Company aggregation and provenance
The system SHALL aggregate verified direct and indirect contributions by company and show their separate and combined values and percentages. Indirect contributions SHALL equal parent value multiplied by validated weights along the contribution path. Users SHALL be able to inspect originating holdings and composition provenance without creating owned securities for constituents.

#### Scenario: Direct share and fund overlap
- **WHEN** a verified company is held directly and through two funds
- **THEN** its combined exposure includes all three contributions, with each source inspectable and no additional parent-fund exposure counted.

### Requirement: Unresolved exposure conservation
Unavailable or unusable compositions SHALL leave the entire affected fund unresolved. Usable partial compositions SHALL retain their unresolved remainder. Unknown identity or classification SHALL leave the affected contribution unresolved. The system SHALL NOT normalize resolved contributions to conceal missing exposure.

#### Scenario: Mixed coverage
- **WHEN** a synthetic selection consists of 60% fully resolved direct equity and 40% a fund with only half its NAV validated and grouped
- **THEN** resolved company exposure is 80% and unresolved exposure is 20% of the full securities denominator.

### Requirement: Separate coverage categories
The view SHALL reconcile portfolio value across company/instrument contributions, identified equities with unverified company grouping, cash/other assets and unresolved portions. Unknown asset types SHALL remain unresolved. Coverage and composition freshness SHALL be reported separately; rounding SHALL NOT turn a nonzero unresolved remainder into a complete-coverage claim.

#### Scenario: Fund cash and ungrouped shares
- **WHEN** a fund contains verified cash and an identified equity whose company grouping is unknown
- **THEN** cash remains cash/other assets, the equity remains ungrouped, and neither is reported as fully grouped company exposure.

### Requirement: Provider classification of constituents
A held instrument and each constituent of a received composition SHALL be classified from the provider's own security data where available: equities grouped by security identifier, funds traversed, bonds and other non-equities shown as other assets. A composition whose weights are arithmetically usable but whose allocation semantics are not validated SHALL still expand into these classified constituents, with coverage kept partial and a visible notice.

#### Scenario: Fund constituents classified into issuers
- **WHEN** a held fund returns nonnegative weights that sum to 100 percent and the provider classifies its constituents as equities and other assets
- **THEN** the constituents appear as company/instrument rows and other assets, the result is labelled partial coverage, and the notice states that the weights are not validated.

#### Scenario: Unclassifiable or invalid weights
- **WHEN** a provider answer contains a constituent whose name indicates a fund, cash or derivative without classification, or a missing, malformed, negative or overfull weight
- **THEN** the affected value remains unresolved instead of being guessed or partially displayed.

### Requirement: Assumed companies for unclassified listings
A constituent with a security identifier for which the provider returns no classification SHALL be assumed to be a company unless its name indicates a fund, cash, money market, bond or derivative. Assumed companies SHALL be marked as not confirmed and counted in the data status. Constituents without identifier and with a non-company name SHALL remain unresolved.

#### Scenario: Listed share unknown to the provider
- **WHEN** a constituent has an ISIN, the provider classification finds nothing, and its name does not indicate a fund, cash or derivative
- **THEN** it is shown as a company marked "assumed, not confirmed", and the data status counts the assumed positions and their share.

### Requirement: Bounded nested look-through
Nested funds SHALL be decomposed only with usable compositions. Cycles and branches beyond eight composition edges SHALL stop at the affected branch and preserve its value as unresolved. Repeated legitimate paths SHALL remain distinct contributions before company aggregation.

#### Scenario: Cyclic fund branch
- **WHEN** a nested composition refers to a fund already present on its current path
- **THEN** only that branch becomes unresolved with a cycle reason and all other validated branches remain usable.

### Requirement: Visible analysis limitations
The view SHALL state that current valuations are combined with available composition dates. It SHALL show unresolved exposure alongside results, provide per-source reasons and refresh status, and remain usable without manual uploads. Unsupported economic exposure SHALL NOT be substituted with collateral or a similar index fund.

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
- **WHEN** persistent caching is disabled by preference or provider policy
- **THEN** the same validated compositions produce the same exposure results, while the data-status UI explains that reopening PP needs retrieval and offline fund analysis may be unavailable after restart.

#### Scenario: No provider credentials
- **WHEN** no provider credentials are configured
- **THEN** every unclassified position remains in the not-yet-decomposed category with a reason naming the missing credentials, and no row or banner presents a retrieval or a check that has not started.

### Requirement: Durable complete-coverage objective
The ultimate objective SHALL remain 100% validated coverage of the declared scope. A partial first release SHALL NOT close it. Every outstanding coverage gap SHALL retain its category, missing evidence, proposed remedy and verification criterion in a maintained register. Release and archive review SHALL carry unresolved gaps into linked follow-up records and preserve this requirement in durable specs.

#### Scenario: Partial release accepted
- **WHEN** a first release passes its partial-coverage acceptance checks with unresolved funds or unverified company grouping
- **THEN** the release can be accepted as partial only, with the gaps retained and linked to follow-up records before archive.

#### Scenario: Complete coverage claimed
- **WHEN** all selected value is numerically allocated but some identities, classifications or exposure bases remain unverified
- **THEN** the system does not claim 100% validated coverage.

### Requirement: Existing portfolio compatibility
Enabling, disabling or removing look-through SHALL NOT modify transactions, owned securities or the portfolio file format. Unavailable or incompatible derived data SHALL be safely discarded and shown as unavailable until retrieved again.

#### Scenario: Cache incompatible after update
- **WHEN** existing derived composition data cannot be read by the current version
- **THEN** the portfolio remains usable, the incompatible data is discarded, and affected exposure is unresolved until a usable replacement is obtained.
