# company-enrichment Specification

## Purpose
Resolves descriptive attributes of the companies in a Breakdown result, such as headquarters country,
currency, sector, industry and size, from provider profiles and public registers, and keeps them cached.

## Requirements

### Requirement: Company attributes with source
The system SHALL provide for each company of a Breakdown result its headquarters country, currency, sector, industry and market capitalization where available. Each attribute SHALL carry its source and retrieval date. A missing attribute SHALL be reported as unknown and SHALL NOT be guessed.

#### Scenario: Profile available
- **WHEN** the provider profile of a company returns sector and industry
- **THEN** the company carries both with the provider as source and the retrieval date.

#### Scenario: No data
- **WHEN** neither the profile nor the register knows a company
- **THEN** its attributes are unknown, except a country derived from its ISIN prefix as described below.

### Requirement: Country resolution order
The headquarters country SHALL be taken from the public legal entity register when a record exists for the company's ISIN, otherwise from the provider profile, otherwise from the ISIN country prefix. The chosen source SHALL be kept with the country.

#### Scenario: Register differs from ISIN
- **WHEN** a company's ISIN starts with KY and its register record names Hong Kong as headquarters country
- **THEN** the company's country is Hong Kong with the register as source.

#### Scenario: Only the ISIN is known
- **WHEN** neither register nor profile provides a country
- **THEN** the country is taken from the ISIN prefix and marked as derived from the ISIN.

### Requirement: Value-ordered enrichment within budget
Provider profile requests SHALL be scheduled in descending order of company value and SHALL count against the existing provider request budget. Register lookups SHALL be batched. Enrichment SHALL NOT block or change the look-through calculation, and the number of companies still pending SHALL be available.

#### Scenario: Budget exhausted
- **WHEN** the request budget is exhausted before all companies are enriched
- **THEN** the largest companies are enriched, the rest are reported as pending, and enrichment continues when budget is available again.

### Requirement: Enrichment cache
Register data SHALL be stored on disk. Provider profiles SHALL be stored on disk only when the account's persistence policy and preference permit it, and otherwise kept for the session. Stored attributes older than 30 days SHALL be refreshed when the company appears in a result.

#### Scenario: Restart with stored data
- **WHEN** PP restarts and the enrichment of all current companies is stored and younger than 30 days
- **THEN** the attributes are available without provider or register requests.

#### Scenario: Persistence withdrawn
- **WHEN** the user withdraws local storage for the provider account
- **THEN** stored provider profiles are deleted, while register data remains.

### Requirement: Register lookup privacy and opt-out
Register requests SHALL contain only instrument identifiers, no portfolio names, amounts or credentials. The user SHALL be able to switch register lookups off; the country then comes from the profile or the ISIN prefix.

#### Scenario: Register switched off
- **WHEN** the user switches register lookups off
- **THEN** no register requests are made and countries come from profiles or ISIN prefixes.
