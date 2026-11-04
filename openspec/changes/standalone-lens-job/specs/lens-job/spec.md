# Spec Delta

## Purpose

Defines the Portfolio Lens command-line job: its configuration, what one run does from reading PP
to writing the page, how repeated runs share state, and how it reports success and failure.

## ADDED Requirements

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

#### Scenario: Price changed
- **WHEN** a holding's valuation changed since the previous run
- **THEN** the run recalculates and rewrites the page without provider requests for funds whose compositions are within their refresh cadence.

### Requirement: State across runs
Request budget usage, provider backoff and suspension, per-fund attempt times and the input fingerprint SHALL be kept in a state file in the cache directory, so that limits hold across runs. The state file SHALL contain no provider payload and no amounts.

#### Scenario: Budget across runs
- **WHEN** runs within 24 hours together used the whole configured request budget
- **THEN** the next run within that window sends no provider request and the page reports the pending lookups.

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
