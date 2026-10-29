# Spec Delta

## MODIFIED Requirements

### Requirement: Refresh
Tasks and projects SHALL sync with the server when the app comes to the foreground and through automatic sync. Pulling down SHALL NOT initiate a refresh or show a refresh indicator.

#### Scenario: Pull to refresh
- **WHEN** the user pulls the list down
- **THEN** no manual refresh or refresh indicator is triggered
