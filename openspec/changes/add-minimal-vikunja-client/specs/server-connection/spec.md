# Spec Delta

## Purpose

Connects the app to exactly one self-hosted Vikunja server using an API token, keeps that credential safe on the device, and lets the user disconnect.

## ADDED Requirements

### Requirement: First-run setup
When no server is configured, the app SHALL show a setup screen asking for the server URL and an API token, and SHALL show no task data until setup succeeds. The screen SHALL state which token permissions the app needs.

#### Scenario: Fresh install
- **WHEN** the user opens the app for the first time
- **THEN** the setup screen is shown with empty server URL and API token fields and a list of the required token permissions

### Requirement: Server URL normalisation
The app SHALL accept the server URL with or without a trailing slash and with or without a trailing `/api/v1` or `/api/v2` segment, and SHALL default to `https://` when no scheme is given.

#### Scenario: URL pasted with API suffix
- **WHEN** the user enters `https://todo.example.com/api/v1/`
- **THEN** the app uses `https://todo.example.com/api/v2/` as its API base

#### Scenario: URL without scheme
- **WHEN** the user enters `todo.example.com`
- **THEN** the app uses `https://todo.example.com/api/v2/` as its API base

### Requirement: Connection validation
Before saving, the app SHALL verify that the server answers on API v2 and that the token can list projects, and SHALL keep the user on the setup screen with a specific error message when either check fails.

#### Scenario: Valid server and token
- **WHEN** the user submits a reachable Vikunja 2.4+ URL and a valid token
- **THEN** the configuration is saved, an initial sync runs and the task list is shown

#### Scenario: Server unreachable or not Vikunja v2
- **WHEN** the URL cannot be reached or does not serve `/api/v2/info`
- **THEN** the app shows "Can't reach a Vikunja 2.4+ server at this address" and saves nothing

#### Scenario: Token rejected
- **WHEN** the server rejects the token itself (its token check answers 401)
- **THEN** the app shows that the token is invalid or expired and saves nothing

#### Scenario: Token lacks permissions
- **WHEN** the token passes the token check but the server refuses to list projects (401 or 403)
- **THEN** the app shows that the token is missing permissions and lists the required ones

### Requirement: Credential storage
The API token MUST be stored only on the device, encrypted with a key held in the Android Keystore, and MUST be excluded from Android cloud backup and device-to-device transfer.

#### Scenario: Backup excludes credentials
- **WHEN** Android performs a cloud backup or device transfer of the app
- **THEN** neither the token nor the local task database is included

### Requirement: Encrypted transport
The app SHALL only connect over HTTPS and MUST NOT allow cleartext HTTP traffic.

#### Scenario: HTTP URL entered
- **WHEN** the user enters a URL starting with `http://`
- **THEN** validation fails with a message that HTTPS is required

### Requirement: Sign out
The app SHALL offer a sign-out action that removes the token, server URL, cached tasks, cached projects and unsent changes from the device and returns to the setup screen. If unsent changes exist, the app SHALL ask for confirmation first.

#### Scenario: Sign out with pending changes
- **WHEN** the user chooses sign out while 2 changes are still unsent
- **THEN** the app warns that 2 unsent changes will be lost and signs out only after confirmation

### Requirement: Re-entering a rejected token
When the server rejects a previously working token, the app SHALL keep all local data and unsent changes and SHALL let the user enter a new token without losing them.

#### Scenario: Token revoked on the server
- **WHEN** a sync receives 401 for a previously working token
- **THEN** the app keeps local data and pending changes, stops syncing and shows a message offering to re-enter the token
