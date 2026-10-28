# Proposal

## Why

The user wants to see which Vikunja server is connected without adding another item to the crowded home menu.

## What Changes

Combine server information with the existing About dialog. Show the app version, configured server address, and current sync status, including token rejection and waiting changes. The address is available offline and must not expose the token or URL credentials. No network request is required.

## Capabilities

### Modified Capabilities

- `server-connection`: expose the configured server address and sync status in About & server.

## Impact

Settings exposes a display-only address, the task view model observes it, and the existing About dialog shows it alongside sync state. No API, storage migration or new permission is needed.
