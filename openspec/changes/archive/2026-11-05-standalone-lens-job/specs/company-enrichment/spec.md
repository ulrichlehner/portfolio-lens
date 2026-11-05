# Spec Delta

## MODIFIED Requirements

### Requirement: Value-ordered enrichment within budget
Provider profile requests SHALL be scheduled in descending order of company value and SHALL count against the provider request budget shared across runs. Register lookups SHALL be batched. Enrichment SHALL NOT change the look-through calculation, and the number of companies still pending SHALL be shown on the page.

#### Scenario: Budget exhausted
- **WHEN** the request budget or the run time limit is exhausted before all companies are enriched
- **THEN** the largest companies are enriched, the rest are reported as pending, and a later run continues when budget is available again.

### Requirement: Enrichment cache
Register data SHALL be stored in the cache directory. Provider profiles SHALL be stored there only when the local storage confirmation and the provider policy permit it; otherwise no profile is requested. Stored attributes older than 30 days SHALL be refreshed when the company appears in a result.

#### Scenario: Restart with stored data
- **WHEN** a run starts and the enrichment of all current companies is stored and younger than 30 days
- **THEN** the attributes are available without provider or register requests.

#### Scenario: Persistence withdrawn
- **WHEN** the user turns the local storage confirmation off
- **THEN** the next run deletes stored provider profiles, while register data remains.

### Requirement: Register lookup privacy and opt-out
Register requests SHALL contain only instrument identifiers, no portfolio names, amounts or credentials. A configuration setting SHALL switch register lookups off; the country then comes from the profile or the ISIN prefix.

#### Scenario: Register switched off
- **WHEN** the register lookup setting is off
- **THEN** no register requests are made and countries come from profiles or ISIN prefixes.
