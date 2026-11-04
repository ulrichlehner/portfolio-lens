# fund-compositions Specification

## Purpose

Provide automatic, replaceable sources of fund compositions with validated semantics, provenance and visible limitations for portfolio exposure analysis.

## Requirements

### Requirement: Automatic provider access
The system SHALL retrieve compositions automatically after individual credential configuration, initially through Eulerpool. Provider substitution SHALL preserve the exposure contract. Routine use SHALL NOT require file uploads, manual constituent mapping or a shared application key.

#### Scenario: Configured fund retrieval
- **WHEN** a configured user opens an analysis containing an eligible fund
- **THEN** the system schedules eligible retrieval and reports its status without requesting a holdings file.

### Requirement: Licensed local use
Official upstream integration and binary releases SHALL require verified desktop-use and adapter-distribution permissions. An explicitly authorized experimental source-only development fork MAY be published with a notice of unverified rights and data quality; the notice SHALL NOT claim to grant rights or transfer legal responsibility.

#### Scenario: Free cache permission unresolved
- **WHEN** API access succeeds, local caching permission is unverified and the user has not confirmed local storage for the account
- **THEN** persistence remains disabled, session-only operation is available under the recorded development assumption, and official integration or binary release still requires the separate permission and data-quality gates.

### Requirement: Local storage confirmation
Real-data persistence SHALL require either verified permission or the user's explicit confirmation for the provider account that local storage is allowed. Without either, persistence SHALL remain disabled.

#### Scenario: User confirms local storage
- **WHEN** the user confirms for a provider account that local storage is allowed and keeps the persistence preference on
- **THEN** validated compositions and classifications of that account are stored on disk and are available after a restart without new provider requests.

### Requirement: Development permission assumption
Implementation and local development MAY proceed under an explicitly recorded development-only assumption of runtime/display and adapter distribution permission. That assumption SHALL NOT establish verified permission or satisfy the pre-publish permission checklist. Real-data disk persistence SHALL follow the user's per-account confirmation, not the assumption itself.

#### Scenario: Development permission assumed
- **WHEN** development proceeds under the recorded permission assumption and the user has not confirmed local storage
- **THEN** real provider data remains session-only, and official integration or binary release remains blocked until runtime/display, adapter distribution and applicable attribution obligations are verified.

#### Scenario: Authorized experimental source fork
- **WHEN** the user explicitly authorizes source-only publication of the development fork before provider clarification
- **THEN** the fork includes an experimental-use notice, distributes no provider payloads or credentials, and leaves permission and data-quality gates open; the notice does not assert that provider rights are verified or responsibility has legally transferred.

### Requirement: Provider-specific persistence preference
The system SHALL offer a local persistence preference per provider/account, defaulting on where permitted. Effective persistence SHALL require both user preference and provider policy approval. Policies SHALL distinguish permitted, prohibited and unverified persistence, including retention limits. Restrictions SHALL override the preference without disabling permitted session-only analysis or changing other providers' settings.

#### Scenario: User opts out of caching
- **WHEN** a user disables persistence for a provider
- **THEN** retrieval, validation, look-through and refresh remain available using session-only results, with no requirement to restart PP.

#### Scenario: Different provider permissions
- **WHEN** one provider permits persistence and another prohibits it or has unverified permission
- **THEN** the first can use its approved disk cache and the second uses session-only results, independently of each other.

### Requirement: Session-only operation
Session-only mode SHALL keep provider compositions, enrichment mappings and derived exposure results out of persistent storage, including portable settings and diagnostic payloads. It SHALL reuse permitted in-memory data while PP runs and discard it at exit. Portfolio, credential and preference storage SHALL remain independent of this choice. Analytical semantics and validation SHALL be identical in both storage modes.

#### Scenario: Restart without persistent data
- **WHEN** PP restarts in session-only mode with network access
- **THEN** it schedules quota-aware initial retrieval and restores available fund analysis without requiring manual uploads or enabling disk caching.

#### Scenario: Restart offline in session-only mode
- **WHEN** PP starts without network access and no permitted retained composition is available
- **THEN** direct analysis remains available and affected fund exposure is explicitly unresolved until retrieval succeeds.

### Requirement: Persistence withdrawal and cleanup
Disabling persistence or withdrawing its permission SHALL stop disk reads and writes for the affected provider/account and delete its retained provider data. Pending jobs SHALL NOT recreate it. Reduced retention SHALL delete expired data. Other providers' permitted caches and the portfolio SHALL remain untouched. Permitted in-memory analysis MAY continue; persistence SHALL NOT resume until both user preference and policy permit it.

#### Scenario: Permission withdrawn during refresh
- **WHEN** persistence permission is withdrawn while a refresh is pending
- **THEN** the provider's disk cache is removed, the completed job cannot recreate it, and permitted results remain usable only in memory.

#### Scenario: Persistence is re-enabled
- **WHEN** a user re-enables persistence and the applicable policy permits it
- **THEN** only eligible validated results within the permitted retention can populate the provider's cache, without changing analysis calculations.

### Requirement: Credential and portfolio privacy
The system SHALL keep credentials outside portfolio files, exports and logs, using secure local storage or session-only entry. Requests SHALL contain only identifiers needed for retrieval or bounded enrichment, never quantities, balances or transactions.

#### Scenario: Credential storage unavailable
- **WHEN** secure storage cannot be used
- **THEN** the system offers session-only entry without silently persisting the key in plaintext settings.

### Requirement: Independent provenance dates
Every composition SHALL identify its source, fund, retrieval time, available provider refresh time and available holdings-effective date. Missing dates SHALL remain unknown. Retrieval or provider refresh time SHALL NOT stand in for the holdings-effective date.

#### Scenario: Effective date absent
- **WHEN** a recently fetched response lacks a holdings-effective date
- **THEN** the result shows holdings date unavailable separately from retrieval and provider refresh times.

### Requirement: Verified allocation semantics
Allocation semantics SHALL be validated either by an authoritative provider declaration or by documented repeated independent agreement. Neither a 100% sum nor a single comparison SHALL suffice. The validated scope SHALL name what it covers; known omissions SHALL stay outside it. An arithmetically usable but unvalidated source MAY expand into provider-classified constituents for an issuer-level display, but SHALL NOT enter validated coverage totals.

#### Scenario: Top holdings normalized to 100 percent
- **WHEN** returned weights sum to 100% but their total-fund denominator is unverified
- **THEN** the entire fund is not counted as validated coverage, even though a separate source preview and unverified display may be available.

#### Scenario: Arithmetic usable, semantics unverified
- **WHEN** a source has valid nonnegative weights but declares an unknown denominator or exposure basis
- **THEN** the result expands into provider-classified issuers for display, keeps coverage partial and does not claim validated company exposure.

#### Scenario: Valid partial NAV fractions
- **WHEN** verified nonnegative rows account for 80% of total fund NAV
- **THEN** those rows are eligible for classification and the remaining 20% stays unresolved without normalization.

#### Scenario: Documented evidence-based acceptance
- **WHEN** a provider declares no denominator but complete dated independent issuer snapshots agree with its weights for at least two public physical funds, within published rounding
- **THEN** the weights are accepted as fractions of total fund NAV for the validated scope, the scope is recorded with sources, date and method, and missing derivative or other known exposure stays outside it.

#### Scenario: Evidence-based acceptance revoked
- **WHEN** a later comparison contradicts the accepted weights or the documented rule no longer holds
- **THEN** the acceptance record is withdrawn and affected funds return to unvalidated rather than keeping the earlier claim.

### Requirement: Conservative numerical validation
The system SHALL preserve source rows and use decimal weights without general normalization. Malformed, nonfinite, negative or overfull weights SHALL make a snapshot unusable, except for an evidenced rounding excess of at most one millionth in fractional-weight units. Accepted excess SHALL be removed proportionally with exact reconciliation. Positive shortfalls SHALL remain unresolved. Legitimate duplicate rows SHALL only be combined after validating their semantics.

#### Scenario: Signed or overfull response
- **WHEN** a response contains a negative allocation on a row with a security identifier, a negative net of rows without an identifier, or a total above 100% beyond the rounding tolerance
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
Negative rows without a security identifier SHALL follow this requirement instead of the negative-weight rule. When all identified rows are nonnegative, rows without an identifier SHALL be netted against each other. A zero or positive net SHALL stay unresolved as one amount with a non-fatal diagnostic; a negative net SHALL make the snapshot unusable. Netting SHALL NOT classify rows by name, rescale identified rows or change source rows.

#### Scenario: Negative cash and currency rows without identifier
- **WHEN** a composition sums to 100%, all identified rows are nonnegative, and its rows without an identifier are +0.05% and -0.015% of the fund
- **THEN** the identified rows keep their exact weights and are eligible for classification, the identifier-less rows become one unresolved amount of +0.035%, the fund reconciles exactly, the source rows stay unchanged, and a netting diagnostic is recorded.

#### Scenario: Negative net without identifier
- **WHEN** the rows without an identifier sum to a negative amount
- **THEN** the snapshot is unusable and the fund stays unresolved with the negative-weight reason.

#### Scenario: Short position with identifier
- **WHEN** a row with a security identifier has a negative weight
- **THEN** the snapshot is unusable even if identifier-less rows could absorb the amount.

### Requirement: Bounded automatic identity resolution
The system SHALL attempt automatic classification and identity resolution within provider budgets. Missing or ambiguous identity SHALL remain explicit. Names alone SHALL NOT establish instrument equivalence or company grouping, and an ISIN alone SHALL NOT establish equity classification.

#### Scenario: Identity cannot be established
- **WHEN** permitted automatic resolution cannot establish a constituent's identity or asset type
- **THEN** its contribution remains unresolved with its source label and reason, without requiring user mapping.

#### Scenario: Search returns another instrument
- **WHEN** a search returns a single hit or matching ticker with a different primary ISIN
- **THEN** that hit does not establish identity or classification for the source ISIN.

#### Scenario: The same instrument has different source tickers
- **WHEN** two funds report the same ISIN with different ticker suffixes
- **THEN** an exact-ISIN classification is shared within the account without asserting those source tickers are verified aliases, and both original contribution rows remain inspectable.

### Requirement: Refresh scheduling and quota handling
Automatic refresh SHALL occur at most once per rolling 24 hours per fund while PP runs. Manual refresh SHALL respect provider quotas and coalesce concurrent requests. Redrawing a view SHALL NOT trigger retrieval. Rate limits SHALL cause backoff, and authentication or entitlement failures SHALL suspend requests until configuration changes.

#### Scenario: Manual refresh during rate limiting
- **WHEN** a user requests refresh before a provider retry deadline
- **THEN** the system reports the deferred status without sending another request or buying quota.

#### Scenario: Application closed
- **WHEN** PP is not running
- **THEN** no automatic refresh is performed by this feature.

#### Scenario: Bounded parallel classification
- **WHEN** constituent classifications run concurrently
- **THEN** admission respects the shared budget and lookup cap, reserves bounded fallback costs, coalesces duplicate identifiers, preserves source order and rechecks suspension/backoff before queued requests execute.

#### Scenario: Classification failure is not negative evidence
- **WHEN** classification fails because of transport, authentication, entitlement or rate limiting
- **THEN** the failure is not cached as a definitive missing classification, relevant backoff/suspension is preserved, and directly held instruments cannot bypass the shared request budget.

### Requirement: Last valid data and retention
Failed retrieval or validation SHALL NOT replace an existing usable snapshot with empty or invalid data. Licensed retained data SHALL remain available with visible age and failure status only within permitted retention. Different sources or vintages SHALL NOT be silently blended.

#### Scenario: Failed refresh with retained data
- **WHEN** refresh fails and a previous valid snapshot is within permitted retention
- **THEN** the system uses that snapshot with a failed-refresh status and its original dates.

#### Scenario: Failed refresh in session-only mode
- **WHEN** refresh fails after a usable composition was retrieved during the current session
- **THEN** the system retains that permitted in-memory snapshot with its original dates and a visible failed-refresh status, without writing it to disk.

#### Scenario: Retention expires
- **WHEN** retained data reaches its permitted retention limit
- **THEN** the system deletes it and marks affected exposure unresolved until usable data is retrieved.
