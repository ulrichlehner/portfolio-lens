# lens-job Specification

## Purpose
Defines the Portfolio Lens command-line job: its configuration, what one run does from reading PP
to writing the page, how repeated runs share state, and how it reports success and failure.

## Requirements

### Requirement: Run command
Lens SHALL provide a `run` command that performs one complete pass: read configuration, read PP, resolve compositions and company data within budget, calculate, write the page and exit. It SHALL NOT keep running or schedule itself; repetition is left to an external scheduler.

#### Scenario: Successful run
- **WHEN** the configuration is valid and PP serves the configured file
- **THEN** the run writes the page to the output directory and exits with status 0.

### Requirement: Check command
Lens SHALL provide a `check` command that validates the configuration and PP access, reports the PP API version, the selected file and whether a provider key and the local storage confirmation are set, and SHALL make no provider request and write no page.

#### Scenario: Check with missing token
- **WHEN** `check` runs without a configured PP token
- **THEN** it reports the missing setting by name and exits with a nonzero status.

#### Scenario: Check with key but without confirmation
- **WHEN** `check` runs with a provider key configured and `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED` not set to `true`
- **THEN** it states that the key has no effect until `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true` is set and exits with the configuration error status.

### Requirement: Warning for an unconfirmed provider key
While a provider key is configured and the local storage confirmation is not `true`, every `run`, including a skipped one, SHALL log a warning naming `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true`, and the status file SHALL mark provider data as unconfirmed. A confirmation value other than `true` or `false` SHALL be a configuration error.

#### Scenario: Unconfirmed key on a skipped run
- **WHEN** a run with a key and without the confirmation finds its inputs unchanged
- **THEN** it skips the calculation but still logs the warning and keeps the unconfirmed marker in the status file.

#### Scenario: Misspelled confirmation value
- **WHEN** the confirmation setting is `yes`
- **THEN** the run stops with a configuration error naming the setting and its accepted values `true` and `false`.

### Requirement: Configuration sources
Configuration SHALL come from environment variables and optionally from a properties file named on the command line; environment variables SHALL take precedence. Each secret SHALL also be readable from a file named by a separate setting. Secrets SHALL NOT be accepted as command-line arguments.

#### Scenario: Secret from file
- **WHEN** the token file setting names a file that contains a token followed by a line break
- **THEN** the token without the line break is used.

#### Scenario: Both value and file
- **WHEN** a secret is set directly and through a file
- **THEN** the run stops with a configuration error naming both settings.

### Requirement: Secrets never in output
The PP token and the provider key SHALL NOT appear in logs, error messages, the page, the status file, the cache or the state file. Error messages SHALL name the setting, not its value.

#### Scenario: Invalid key format
- **WHEN** the configured provider key contains whitespace
- **THEN** the error names the provider key setting and does not print any part of the key.

### Requirement: Skip unchanged runs
A run SHALL compare a fingerprint of its inputs with the one stored by the previous successful run. When they match and no provider work is due, it SHALL NOT recalculate or rewrite the page, and SHALL only record the time of the check.

#### Scenario: Nothing changed
- **WHEN** two runs five minutes apart see identical holdings, instruments, configuration and cache, and no fund is due for refresh
- **THEN** the second run sends no provider request, leaves `index.html` unchanged and updates the last check time in the status file.

#### Scenario: Unchanged input after the first fill
- **WHEN** three runs ten minutes apart follow a completed first fill and nothing changed in PP
- **THEN** each of them makes at most a handful of provider or register requests.

#### Scenario: Price changed
- **WHEN** a holding's valuation changed since the previous run
- **THEN** the run recalculates and rewrites the page without provider requests for funds whose compositions are within their refresh cadence.

### Requirement: State across runs
Request budget usage, provider backoff and suspension, per-fund attempt times and the input fingerprint SHALL be kept in a state file in the cache directory, so that limits hold across runs. The state file SHALL contain no provider payload and no amounts.

#### Scenario: Budget across runs
- **WHEN** runs within 24 hours together used the whole configured request budget
- **THEN** the next run within that window sends no provider request and the page reports the pending lookups.

#### Scenario: Attempt cut off by the time limit
- **WHEN** a run ends while a fund request is still running
- **THEN** that attempt is not recorded, and the next run requests the fund again instead of waiting for the 24-hour cadence.

#### Scenario: State file lost
- **WHEN** the state file is missing or unreadable
- **THEN** the run starts with an empty state, logs that it did, and completes.

### Requirement: Bounded run time
Provider work in one run SHALL stop at the configured run time limit. Lookups left over SHALL remain pending for the next run, and the page SHALL be written with the results available at that point.

#### Scenario: Many new funds
- **WHEN** a portfolio gains twenty funds without compositions and the time limit is reached after twelve
- **THEN** the page shows twelve decomposed funds and eight pending, and the next run continues with the remaining eight.

### Requirement: No overlapping runs
Only one run SHALL work on a cache directory at a time. A run that finds another run active SHALL exit with status 0 without changes.

#### Scenario: Scheduler overlap
- **WHEN** a run starts while the previous run still holds the lock
- **THEN** the new run logs that another run is active and exits without reading PP.

### Requirement: Atomic output
The page and the status file SHALL be replaced atomically, so that a reader never sees a partial file. A failed run SHALL leave the previous page in place and SHALL record the failure and its time in the status file.

#### Scenario: PP closed during the day
- **WHEN** a run fails because PP is not reachable
- **THEN** the previous page remains, and the status file records the failure, its time and the time of the last successful run.

### Requirement: Exit status
The job SHALL exit with 0 after a successful or skipped run, and with distinct nonzero statuses for configuration errors, PP access or compatibility errors, and output or cache write errors.

#### Scenario: Unwritable output directory
- **WHEN** the output directory cannot be written
- **THEN** the run exits with the output error status and the message names the directory setting.

### Requirement: Logging
The job SHALL log to standard error with one line per event: run start, skip, counts of requests and pending work, warnings and the result. Logs SHALL NOT contain amounts, quantities or holdings names; instrument identifiers MAY appear at debug level only.

#### Scenario: Normal run log
- **WHEN** a run completes at the default log level
- **THEN** the log names the number of funds, provider requests, pending lookups and the output file, and no amount or holding name.

#### Scenario: Requests by type
- **WHEN** a run made provider and register requests
- **THEN** one INFO line counts them by type (fund composition, company profile, classification search, revenue split, GLEIF register) with found, not found and failed answers, without identifiers.

#### Scenario: Debug level
- **WHEN** `LENS_LOG_LEVEL` is FINE
- **THEN** only the Lens loggers log at FINE; the JDK's TLS and HTTP client loggers stay at INFO. Each provider and register request logs one FINE line with its type, outcome, HTTP status and the ISIN it asked for, and each company lookup put on hold logs one FINE line with the next attempt; no URL, header or other query value is logged.

### Requirement: Budget reporting
Each run SHALL record in the status file the request units used in the rolling 24-hour window, the capacity and whether the budget is used up, and SHALL log a warning while it is used up. Funds SHALL be refreshed in descending value, so that a first fill that spreads over several runs covers the largest holdings first.

#### Scenario: First fill of a large portfolio
- **WHEN** a synthetic portfolio of 4,000 companies is analysed for the first time with the default budget
- **THEN** the run completes without pending lookups, and the status file reports the used units below the capacity.

#### Scenario: Budget used up
- **WHEN** the configured budget is used up
- **THEN** the status file reports the budget as used up and the log contains a warning naming the budget setting.
