# Proposal

## Why

The user wants synchronization to happen automatically without a Sync now menu item, and wants sync failures visible in About & server.

## What Changes

- Remove Sync now from the menu. The existing foreground, local-change and periodic background sync triggers continue to run automatically.
- Mark About & server when the latest sync reports an offline connection, server error, missing permission or rejected token. Pending changes alone do not mark an error.
- In About & server, continue showing the current sync status and show server error details when available. A later successful sync clears the error indicator and details.

Out of scope: changing the server sync API, background scheduling policy, or pull-to-refresh.

## Capabilities

### Modified Capabilities

- `offline-sync`: communicate automatic sync failures through the About menu and dialog.

## Impact

Remove a menu callback, derive the failure indicator and details from the existing sync result, and document automatic sync. No new permissions or database changes.
