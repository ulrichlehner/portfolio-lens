# fund-compositions Specification

## Purpose

Provide automatic, replaceable sources of fund compositions with validated semantics, provenance and visible limitations for portfolio exposure analysis.

## Requirements

### Requirement: Automatic provider access
The system SHALL retrieve compositions automatically after individual credential configuration, initially through Eulerpool. Provider substitution SHALL preserve the exposure contract. Routine use SHALL NOT require file uploads, manual constituent mapping or a shared application key.

#### Scenario: Configured fund retrieval
- **WHEN** a run with a configured key and confirmed local storage finds an eligible fund
- **THEN** the system schedules eligible retrieval and reports its status on the page without requesting a holdings file.

### Requirement: Licensed local use
Published Lens binaries or container images SHALL require verified runtime and adapter-distribution permissions of the provider. The source repository MAY be published with a notice of unverified rights and data quality; the notice SHALL NOT claim to grant rights or transfer legal responsibility.

#### Scenario: Free cache permission unresolved
- **WHEN** API access succeeds, local caching permission is unverified and the user has not confirmed local storage for the account
- **THEN** no provider data is requested or stored, and binary or image releases still require the separate permission and data-quality gates.

### Requirement: Local storage confirmation
Real-data persistence SHALL require either verified permission or the user's explicit confirmation in the configuration that local storage is allowed. Without either, persistence SHALL remain disabled and no provider data SHALL be requested.

#### Scenario: User confirms local storage
- **WHEN** the user confirms in the configuration that local storage is allowed
- **THEN** validated compositions and classifications are stored in the cache directory and are available in later runs without new provider requests.

### Requirement: Development permission assumption
Implementation and local development MAY proceed under an explicitly recorded development-only assumption of runtime/display and adapter distribution permission. That assumption SHALL NOT establish verified permission or satisfy the pre-publish permission checklist. Real-data disk persistence SHALL follow the user's confirmation, not the assumption itself.

#### Scenario: Development permission assumed
- **WHEN** development proceeds under the recorded permission assumption and the user has not confirmed local storage
- **THEN** no real provider data is requested or stored, and binary or image releases remain blocked until runtime/display, adapter distribution and applicable attribution obligations are verified.

#### Scenario: Authorized experimental source fork
- **WHEN** the Lens source repository is published before provider clarification
- **THEN** it includes the notice of unverified rights, distributes no provider payloads or credentials, and leaves permission and data-quality gates open; the notice does not assert that provider rights are verified or responsibility has legally transferred.

### Requirement: Provider-specific persistence preference
Effective persistence SHALL require the user's confirmation and provider policy approval. Policies SHALL distinguish permitted, prohibited and unverified persistence, including retention limits. Restrictions SHALL override the confirmation without changing other providers' settings.

#### Scenario: User opts out of caching
- **WHEN** the user turns the local storage confirmation off
- **THEN** the provider's data is neither requested nor stored, and the page shows the direct analysis without provider data, with no restart of PP needed.

#### Scenario: Provider prohibits persistence
- **WHEN** a provider's policy prohibits persistence although the user confirmed local storage
- **THEN** no data of that provider is requested or stored, and the page names the policy as the reason.

#### Scenario: Different provider permissions
- **WHEN** one provider permits persistence and another prohibits it or has unverified permission
- **THEN** the first uses its cache, and the second makes no requests, independently of each other.

### Requirement: Persistence withdrawal and cleanup
Withdrawing the confirmation or a policy permission SHALL make the next run delete the affected provider's retained data in the cache directory before any other work and rewrite the page without results derived from it. Reduced retention SHALL delete expired data. Other providers' caches, register data and the portfolio SHALL remain untouched.

#### Scenario: Confirmation withdrawn
- **WHEN** the user turns the confirmation setting off and the next run starts
- **THEN** the provider's compositions, classifications, profiles and revenue splits are deleted from the cache, register data remains, and the new page shows no provider-derived exposure.

#### Scenario: Permission withdrawn during refresh
- **WHEN** the confirmation is withdrawn while a run that started with it is still working
- **THEN** that run finishes with the configuration it read at start, and the next run deletes the provider's cached data before any other work, so no later run can recreate it from the earlier configuration.

#### Scenario: Persistence is re-enabled
- **WHEN** the user turns the confirmation back on
- **THEN** later runs retrieve data again within budget, without changing analysis calculations.

### Requirement: Credential and portfolio privacy
The system SHALL keep credentials outside the cache, the state file, the page, the status file and logs, reading them only from configuration. Requests SHALL contain only identifiers needed for retrieval or bounded enrichment, never quantities, balances or transactions.

#### Scenario: Credential storage unavailable
- **WHEN** the key file setting names a file that cannot be read
- **THEN** the run stops with a configuration error naming the setting, and no key is written anywhere as a substitute.

#### Scenario: Provider request content
- **WHEN** a run requests a fund composition
- **THEN** the request contains the fund identifier and the key header only, and no amount, quantity or holding name.

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
Automatic refresh SHALL occur at most once per rolling 24 hours per fund across all runs. Runs SHALL respect provider quotas and coalesce duplicate identifiers. Rewriting the page SHALL NOT by itself trigger retrieval. Rate limits SHALL cause backoff across runs, and authentication or entitlement failures SHALL suspend requests until the key configuration changes.

#### Scenario: Manual refresh during rate limiting
- **WHEN** a run, scheduled or started by hand, begins before a provider retry deadline recorded by an earlier run
- **THEN** the run sends no request to that provider and the page reports the deferred status with the next attempt time.

#### Scenario: Application closed
- **WHEN** PP is not running
- **THEN** the run makes no provider request.

#### Scenario: Bounded parallel classification
- **WHEN** constituent classifications run concurrently
- **THEN** admission respects the shared budget and lookup cap, reserves bounded fallback costs, coalesces duplicate identifiers, preserves source order and rechecks suspension/backoff before queued requests execute.

#### Scenario: Classification failure is not negative evidence
- **WHEN** classification fails because of transport, authentication, entitlement or rate limiting
- **THEN** the failure is not cached as a definitive missing classification, relevant backoff/suspension is preserved, and directly held instruments cannot bypass the shared request budget.

#### Scenario: Key replaced after suspension
- **WHEN** a run was suspended by an authentication failure and the user configures a different key
- **THEN** the next run lifts the suspension and retries within budget.

### Requirement: Last valid data across runs
Failed retrieval or validation SHALL NOT replace an existing usable snapshot with empty or invalid data. Licensed retained data SHALL remain available with visible age and failure status only within permitted retention. Different sources or vintages SHALL NOT be silently blended.

#### Scenario: Failed refresh with retained data
- **WHEN** refresh fails and a previous valid snapshot is within permitted retention
- **THEN** the system uses that snapshot with a failed-refresh status and its original dates.

#### Scenario: Retention expires
- **WHEN** retained data reaches its permitted retention limit
- **THEN** the system deletes it and marks affected exposure unresolved until usable data is retrieved.

### Requirement: No loss of earlier resolutions
A run SHALL NOT resolve fewer instruments than the stored data allows because of an exhausted budget, a failed refresh or the run time limit. A retained composition SHALL be used with the stored classifications of its constituents until a successful refresh replaces them.

#### Scenario: Failed refresh after the cadence
- **WHEN** a fund is refreshed after 24 hours and the provider answers with errors
- **THEN** the run resolves at least as many instruments as the run before, using the stored composition and classifications.

#### Scenario: Budget used up on a later run
- **WHEN** a later run within the same day finds the budget used up
- **THEN** it resolves at least as many instruments as the earlier run.

#### Scenario: Held share with a deferred refresh
- **WHEN** a directly held share that no fund holds was classified in an earlier run and this run defers its refresh
- **THEN** the run uses the stored classification of the share and resolves it as before.

### Requirement: Catch-up of missing classifications
A run SHALL look up the constituents of retained compositions, and the held instruments without a retained composition, that have no stored classification, without requesting the composition again. A failed lookup SHALL wait before its retry, from one hour growing to at most a week. Classifications used by a result SHALL be stored for later runs.

#### Scenario: Lookup failed during the first fill
- **WHEN** some constituent lookups of the first run fail and the provider answers again later
- **THEN** a later run looks up only those constituents, and the result reaches the same resolutions as a run without failures.

#### Scenario: Instrument that keeps failing
- **WHEN** the lookup of one constituent fails in every run
- **THEN** it is retried after one hour, then after four, sixteen and more hours, and the runs between make no request for it.

#### Scenario: Company profile that cannot be used
- **WHEN** a company profile or revenue answer fails for one company in every run
- **THEN** the run state keeps the failure, the company is retried after one hour, then after four, sixteen and more hours, and the runs between make no request for it. A profile field of an unexpected type or length counts as unknown and does not make the answer fail.

### Requirement: Failure scope
A single failed provider answer SHALL put only that instrument on hold. The whole provider account SHALL back off only on a rate limit, an authentication or entitlement failure, or three failures in a row. A lookup that a limit stopped SHALL name that limit: backoff, the lookup cap of a refresh, the request budget, or no answer stored yet.

#### Scenario: One failing company
- **WHEN** the provider answers with an error for one company and normally for all others
- **THEN** the other lookups of the run continue, and the failed company is retried in a later run.

#### Scenario: Lookups on hold
- **WHEN** lookups are stopped because the provider failed several times in a row while the budget is not used up
- **THEN** the classification diagnostics show them as on hold by backoff, not as an exhausted budget.

### Requirement: Lens cache directory
Provider compositions, classifications, company profiles and revenue splits SHALL be stored only in the configured Lens cache directory, never in the PP workspace, the portfolio file, the output directory or the repository. The output page MAY contain results derived from them.

#### Scenario: Stored composition location
- **WHEN** a run stores a validated composition
- **THEN** the data is written below the cache directory and nowhere else.
