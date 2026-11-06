# Spec Delta

## MODIFIED Requirements

### Requirement: Sync triggers
The app SHALL sync when it comes to the foreground, shortly after any local change while a network is available, and in the background about every 30 minutes while a network is available. Pulling down SHALL NOT trigger a manual refresh.

#### Scenario: Change while online
- **WHEN** the user completes a task with a working connection
- **THEN** the completion reaches the server within a few seconds without further user action

### Requirement: Single sync at a time
At most one sync SHALL run at a time; a sync requested while one is running SHALL run after it finishes, so no change is sent twice in parallel.

#### Scenario: Pull to refresh during background sync
- **WHEN** an automatic sync is requested while another sync is running
- **THEN** the second sync starts after the first finishes
