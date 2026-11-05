# Spec Delta

## ADDED Requirements

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

## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Session-only operation
**Reason**: Each run of the job is a new process, so there is no session in which in-memory data could be reused. Session-only retrieval would request every composition on every run, which breaks the 24-hour cadence and the budget, and the page itself persists derived results.
**Migration**: Confirm local storage in the configuration to use provider data, or leave it off to get the holdings-only page without provider requests. See "Local storage confirmation" and "Lens cache directory".

### Requirement: Last valid data and retention
**Reason**: Its session-only scenario no longer applies, because session-only operation is removed.
**Migration**: Replaced by "Last valid data across runs", which keeps the other rules unchanged.
