# Spec Delta

## MODIFIED Requirements

### Requirement: Destination project
Tasks captured on the main view or from the share sheet SHALL be created in the default project configured in the user's Vikunja settings. If no default project is set or the token cannot read user settings, the app SHALL use the first non-archived project it knows. Tasks captured on a project screen SHALL be created in that project.

#### Scenario: Default project configured
- **WHEN** the user's Vikunja default project is Inbox
- **THEN** tasks captured on the main view are created in Inbox

#### Scenario: Capture on a project screen
- **WHEN** the user captures "Order capacitors" on the Firmware screen
- **THEN** the task is created in Firmware and appears in Firmware's list
