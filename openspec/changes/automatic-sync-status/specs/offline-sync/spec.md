# Spec Delta

## ADDED Requirements

### Requirement: Sync issues in About
The home menu SHALL identify About & server as needing attention when the latest sync is offline, fails, lacks permission, or has a rejected token. The About & server dialog SHALL show the current sync status and available server-error detail. Queued changes alone SHALL not be presented as a failure. A later successful sync SHALL clear the error indicator and error details. The menu SHALL NOT offer a Sync now action.

#### Scenario: Server failure and recovery
- **WHEN** a sync fails with a server error and the user opens the home menu
- **THEN** About & server indicates a sync issue and its dialog shows the failure and any available details
- **WHEN** a later sync succeeds
- **THEN** the menu and dialog no longer show that failure

#### Scenario: Waiting changes
- **WHEN** the last sync succeeded and a new local change is waiting to sync
- **THEN** About & server shows the waiting count without marking a sync failure

#### Scenario: Rejected token
- **WHEN** the server rejects the token
- **THEN** About & server indicates a sync issue and its dialog shows the token-rejection status
