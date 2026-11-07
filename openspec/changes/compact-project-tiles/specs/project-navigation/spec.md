## MODIFIED Requirements

### Requirement: Top-level project tiles
The home screen SHALL show compact tiles for its existing top-level projects. Home tiles SHALL have a minimum height of 92 dp, and subproject tiles SHALL have a minimum height of 60 dp. Tiles SHALL grow vertically when enlarged text requires more room and retain their existing navigation.

#### Scenario: Compact home grid
- **WHEN** the user opens Home
- **THEN** project tiles use reduced padding and icon sizes, and more project rows fit on screen

### Requirement: Project tile content
Home and subproject tiles SHALL show the project's title without an open-task count. Existing color accents and shopping indicators SHALL remain available.

#### Scenario: Project with open tasks
- **WHEN** a project contains open tasks
- **THEN** its tile shows its title without a task-count subtitle

#### Scenario: Enlarged title
- **WHEN** the user enlarges text or a project name wraps
- **THEN** the tile accommodates a two-line title without a fixed-height crop
