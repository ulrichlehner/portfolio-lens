# composition-provider-settings Specification

## Purpose

Defines how a user configures a fund composition provider in the preferences: entering, replacing and removing
an API key, seeing its state, and choosing the persistence preference.

## Requirements

### Requirement: Single API key field
The provider settings page SHALL show one masked API key field, a link to request a key and a one-line key status. It SHALL NOT show a stored key. Entering a key and confirming the preferences dialog with Apply or OK SHALL store it in secure storage. Cancelling the dialog SHALL NOT change the stored key.

#### Scenario: First key entered
- **WHEN** a user without a key enters a valid key and presses OK
- **THEN** the key is stored securely, the field is cleared and the next opening of the page shows the status "key stored" without the key.

#### Scenario: Dialog cancelled
- **WHEN** a user types a key and cancels the dialog
- **THEN** no key is stored or replaced.

#### Scenario: Empty field with stored key
- **WHEN** a key is stored and the user confirms the dialog without typing in the field
- **THEN** the stored key remains unchanged.

### Requirement: Key validation feedback
The page SHALL reject keys that contain whitespace or line breaks before storing them, showing the reason on the page and keeping the dialog open.

#### Scenario: Key with line break
- **WHEN** a user pastes a key that contains a line break and presses Apply
- **THEN** the page shows that the key is invalid, nothing is stored, and the dialog stays open.

### Requirement: Key removal
The page SHALL offer one control that removes the key from the session and from secure storage, enabled only while a key is configured.

#### Scenario: Remove stored key
- **WHEN** a user removes a stored key
- **THEN** the key is deleted from secure storage and the session, the status shows "no key", and retrievals that need the key stop.

### Requirement: Session-only fallback
Session-only key entry SHALL be offered only when secure storage is unavailable. The page SHALL then state that the key is kept until PP exits and SHALL NOT write it to plaintext settings.

#### Scenario: Secure storage unavailable
- **WHEN** secure storage cannot be used and a user enters a key and presses OK
- **THEN** the key is used for the current session only, the status states this, and no key is written to disk.

### Requirement: Compact persistence preference
The page SHALL show a confirmation that the user's provider plan allows local storage, the persistence preference as one checkbox, and a single status line. The confirmation SHALL be off by default and SHALL be stored only with Apply or OK. While it is off and provider policy is not verified, the persistence checkbox SHALL be disabled and the status line SHALL name the reason in one sentence. Once it is on, the checkbox SHALL be enabled and default to on.

#### Scenario: Persistence unverified
- **WHEN** the provider's persistence permission is unverified and the confirmation is off
- **THEN** the checkbox is disabled and the status line states that compositions are kept for the session only until local storage is confirmed.

#### Scenario: Local storage confirmed
- **WHEN** the user ticks the confirmation and presses OK
- **THEN** the persistence checkbox is enabled and on, and the status line states that provider data is stored on this computer.

#### Scenario: Confirmation withdrawn
- **WHEN** the user clears the confirmation and presses OK
- **THEN** the account's stored provider data is deleted, the checkbox is disabled, and analysis continues with session-only data.
