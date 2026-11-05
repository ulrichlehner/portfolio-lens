# pp-rest-source Specification

## Purpose
Defines how Portfolio Lens reads the portfolio from the local Portfolio Performance REST API v1:
connection, compatibility checks, file selection and the mapping of holdings and instruments.

## Requirements

### Requirement: Read-only access
Lens SHALL send only GET requests to PP. It SHALL NOT call any endpoint that changes data, SHALL NOT start a pairing request, and SHALL NOT send an `Origin` header.

#### Scenario: Complete run
- **WHEN** a run reads the version, the files, the holdings and the instruments
- **THEN** every request sent to PP uses the GET method and none carries an `Origin` header.

### Requirement: Loopback connection with bearer token
Lens SHALL accept only a PP base URL with the `http` scheme whose host is a loopback IP literal or `localhost`, and SHALL send the configured token as a bearer token on every request except the version check.

#### Scenario: Non-loopback URL
- **WHEN** the configured PP URL names the host `pp.example.com`
- **THEN** the run stops with a configuration error before any request is sent.

#### Scenario: Token rejected
- **WHEN** PP answers 401
- **THEN** the run stops with a message that the token was rejected and should be replaced in PP, without printing the token.

### Requirement: Version check
Each run SHALL first request `/v1/version` and SHALL continue only when the `apiVersion` major version is 1. A missing optional `applicationVersion` SHALL be tolerated.

#### Scenario: Compatible newer minor version
- **WHEN** PP reports `apiVersion` 1.4.0
- **THEN** the run continues.

#### Scenario: PP not reachable
- **WHEN** no connection to the PP URL can be established
- **THEN** the run stops with a message that PP is not running or the REST API is disabled, sends no provider request and leaves the existing page unchanged.

### Requirement: File selection
Lens SHALL address the file configured by id or alias. Without a configured file it SHALL use the only enabled file and SHALL stop with an error that lists the ids and aliases when several or none are enabled.

#### Scenario: One enabled file
- **WHEN** no file is configured and `/v1/files` lists exactly one file
- **THEN** that file is used.

#### Scenario: Ambiguous alias
- **WHEN** PP answers 409 with the problem type for an ambiguous alias
- **THEN** the run stops with a message to configure the file id instead.

#### Scenario: File not open
- **WHEN** PP answers 409 because the file is not open
- **THEN** the run stops with a message to open the file in PP and leaves the existing page unchanged.

### Requirement: Holdings mapping
Lens SHALL read holdings at the default valuation date, in the configured reporting currency when set. Instrument holdings SHALL join to instruments by `uuid`. Cash account holdings SHALL count as account cash. Holdings of an unknown type SHALL be excluded from the analysis and reported as such.

#### Scenario: Instrument and cash account
- **WHEN** holdings contain one instrument and one cash account
- **THEN** the instrument enters the securities scope with its valuation and the cash account is labelled as excluded account cash.

#### Scenario: Unknown holding type
- **WHEN** a holding has the type `crypto-wallet`, which Lens does not know
- **THEN** it is not analysed, the data status names one holding of an unsupported type, and the run succeeds.

### Requirement: Valuation completeness from REST data
An instrument holding SHALL be treated as missing valuation when it has no price, a zero price or a zero valuation, and as missing conversion when its price currency differs from the reporting currency and its local valuation is absent or numerically equal to its valuation. Negative shares or valuations SHALL be unsupported negative positions.

#### Scenario: Silent one-to-one conversion
- **WHEN** an instrument priced in USD has a valuation of 1105 EUR and a local valuation of 1105 USD
- **THEN** the instrument is reported as missing conversion and whole-portfolio percentages are withheld.

#### Scenario: Converted holding
- **WHEN** an instrument priced in USD has a valuation of 1000 EUR and a local valuation of 1105 USD
- **THEN** it enters the securities scope with 1000 EUR.

### Requirement: Instrument identifiers
Lens SHALL use the instrument's ISIN from `/instruments` as the only identifier in provider requests; WKN and ticker symbol SHALL be kept for display only. Absent optional fields and a null currency code SHALL be treated as unknown rather than as errors.

#### Scenario: Instrument without ISIN
- **WHEN** a held instrument has no ISIN
- **THEN** it stays unresolved with a reason naming the missing identifier, and the run continues.

### Requirement: Tolerant parsing
Lens SHALL ignore unknown response fields and SHALL accept unknown values of open sets such as holding types and problem types. A response that lacks a required field SHALL stop the run with a message naming the endpoint and field.

#### Scenario: Additional field
- **WHEN** the holdings response contains a field that Lens does not know
- **THEN** the field is ignored and the run produces the same result as without it.

#### Scenario: Missing required field
- **WHEN** a holding lacks `valuation`
- **THEN** the run stops with a message naming the holdings endpoint and the missing field, and the existing page stays unchanged.
