# Spec Delta

## ADDED Requirements

### Requirement: Home label shortcuts
Home SHALL show all cached labels alphabetically below the project tiles, separated by a divider and a Labels heading. They SHALL appear as compact wrapping `@name` pills using their label colors and share the project area's vertical scroll. Tapping a pill SHALL open that label's existing cross-project task view without creating a task, including offline. The project and label sections SHALL have independent empty messages.

#### Scenario: Browse every label
- **WHEN** the user opens home with labels that have open tasks and labels that have none
- **THEN** all cached labels appear below the projects, including unused labels

#### Scenario: Open a label offline
- **WHEN** the user taps Errand's shortcut while offline
- **THEN** Errand's tasks open across projects, no task is created, and Back returns home

#### Scenario: No projects
- **WHEN** no projects exist but cached labels exist
- **THEN** home shows "No projects yet" and the label shortcuts remain accessible

#### Scenario: Labels change
- **WHEN** the cached label list is renamed, recolored or deleted by sync
- **THEN** the shortcuts reflect the current titles, colors and alphabetical order
