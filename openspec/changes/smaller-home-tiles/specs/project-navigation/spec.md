# Spec Delta

## MODIFIED Requirements

### Requirement: Top-level project tiles
The main view SHALL show one tile for every non-archived top-level project, using the full content area and showing no task list. A project whose parent is archived or not accessible SHALL count as top-level. Nested projects SHALL NOT have tiles on the main view. Home tiles SHALL have a 104dp minimum height, retain the adaptive width and text styles, and remain taller than subproject tiles.

#### Scenario: Only top-level projects
- **WHEN** the user has the projects Work (with subproject Firmware) and Home
- **THEN** the main view shows tiles for Work and Home and none for Firmware

#### Scenario: Subproject of an archived project
- **WHEN** the project Old is archived and its subproject Notes is not
- **THEN** the main view shows a tile for Notes

#### Scenario: No projects
- **WHEN** no projects are available
- **THEN** the main view shows a short "No projects yet" message

#### Scenario: Slightly smaller home tiles
- **WHEN** the user opens the main view
- **THEN** home tiles have a 104dp minimum height and subproject tiles retain their existing size
