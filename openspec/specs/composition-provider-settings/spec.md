# composition-provider-settings Specification

## Purpose

Defines how a user configures a fund composition provider in the preferences: entering, replacing and removing
an API key, seeing its state, and choosing the persistence preference.

## Requirements

### Requirement: Key validation feedback
Lens SHALL reject a configured key that contains whitespace, line breaks or characters outside the accepted token alphabet before any provider request, naming the setting in the error and not printing the key. A single trailing line break in a key file SHALL be removed before validation.

#### Scenario: Key with line break
- **WHEN** the configured key contains a line break inside the value
- **THEN** the run stops with a configuration error that names the key setting, and no provider request is made.

### Requirement: Key removal
Removing the key from the configuration SHALL stop all provider requests from the next run on. Provider data already in the cache SHALL remain usable within its retention as long as the local storage confirmation stays on.

#### Scenario: Remove stored key
- **WHEN** the user removes the key setting and the next run starts
- **THEN** no provider request is made, cached compositions within retention are still used, and the page states that no API key is configured.

### Requirement: API key from configuration
Lens SHALL read the provider API key from an environment variable or from a file named by a separate setting. It SHALL NOT store, copy or rewrite the key. Without a key, Lens SHALL make no provider request.

#### Scenario: Key in a secret file
- **WHEN** the key file setting names a readable file with a valid key
- **THEN** the key is used for provider requests of this run and is not written anywhere.

#### Scenario: No key configured
- **WHEN** neither the key nor the key file is set
- **THEN** the run makes no provider request and the page states that funds are broken down only with an API key and names the setting.

### Requirement: Local storage confirmation setting
Lens SHALL offer one setting with which the user confirms that their provider plan allows local storage of provider data. It SHALL be off by default. Only when it is on SHALL provider data be requested and stored.

#### Scenario: Confirmation off
- **WHEN** a key is configured and the confirmation setting is off
- **THEN** no provider request is made, cached provider data is deleted, the page shows only the holdings with a banner naming `LENS_EULERPOOL_LOCAL_STORAGE_CONFIRMED=true`, `check` fails and every run logs a warning (see `lens-job`, "Warning for an unconfirmed provider key").

#### Scenario: Confirmation on
- **WHEN** a key is configured and the confirmation setting is on
- **THEN** compositions and company data are requested within budget and stored in the cache directory.
