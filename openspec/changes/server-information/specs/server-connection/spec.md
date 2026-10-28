# Spec Delta

## ADDED Requirements

### Requirement: Connected server information
The home menu SHALL provide an About & server dialog with the app version, configured server address and latest sync state. The address SHALL be available offline, retain a reverse-proxy path and port, and omit the API suffix and URL credentials. Neither the API token nor URL credentials SHALL be displayed. The dialog SHALL use existing sync state without initiating a network request.

#### Scenario: Server behind a reverse proxy
- **WHEN** the configured API base is `https://todo.example.com:8443/vikunja/api/v2/`
- **THEN** About & server displays `https://todo.example.com:8443/vikunja/` alongside the app version and sync status

#### Scenario: Offline or rejected token
- **WHEN** the app is offline or the token is rejected
- **THEN** About & server still shows the configured address and the relevant status without exposing the token
