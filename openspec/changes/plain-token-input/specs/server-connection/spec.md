# Spec Delta

## ADDED Requirements

### Requirement: Plain token entry
The API token field on the setup screen SHALL be a normal, visible single-line text field, not a password field: it SHALL support ordinary selection, paste and editing, and SHALL NOT autocorrect or capitalise the text. The entered token SHALL only be used for the connection check and encrypted storage.

#### Scenario: Paste a token
- **WHEN** the user pastes a copied `tk_…` token into the token field
- **THEN** the full token is shown in plain text and can be edited before connecting

#### Scenario: No password keyboard
- **WHEN** the user focuses the token field
- **THEN** the keyboard behaves as for normal text input instead of offering a password manager

#### Scenario: Token unchanged by the keyboard
- **WHEN** the user pastes `tk_abc123` and taps Connect
- **THEN** exactly `tk_abc123` is checked against the server and stored, without capitalisation or autocorrection
