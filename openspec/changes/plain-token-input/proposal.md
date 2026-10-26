# Proposal

## Why

Pasting a Vikunja API token on the Connect to Vikunja screen is awkward: the token field is a password field, so the keyboard offers a password manager instead of a plain paste, and the masked text can't be checked or corrected (issue #3).

## What Changes

- The API token field on the setup screen (first run and "Enter a new token") becomes a normal, visible single-line text field that supports ordinary selection, paste and editing.
- The field turns off autocorrect and capitalisation so a pasted `tk_…` token is not altered.
- Storage and use of the token are unchanged: it is still encrypted with the Android Keystore and only sent to the configured server.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `server-connection`: adds a requirement that the token field is a plain, editable text field.

## Impact

- `ui/setup/SetupScreen.kt` only. No data, API or build changes.
