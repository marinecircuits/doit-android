## MODIFIED Requirements

### Requirement: Project tile content
Each tile SHALL show the project's title and the number of open tasks directly in that project. In dark mode, a server color SHALL appear as an almost-neutral dark tint with silver text and a colored icon accent. In light mode, it SHALL retain the established lightened fill and contrasting text. Missing or unreadable colors SHALL use the theme's neutral surface.

#### Scenario: Dark project color
- **WHEN** the device uses dark mode and Work has a blue server color
- **THEN** Work has a nearly neutral tile with a blue hint, a blue icon, and readable silver title and count
- **AND** its project page has an ink background with a trace of blue with separate task cards

#### Scenario: Light mode
- **WHEN** the device uses light mode
- **THEN** the established lightened project colors and contrasting text remain available

### Requirement: Top-level project tiles
The home screen SHALL continue to show non-archived top-level projects, with taller tiles than the subproject grid. Both tile types SHALL keep title and open count readable, including when text is enlarged. Titles may wrap and tiles may grow vertically.

#### Scenario: Long project name
- **WHEN** a project has a long name or the user enlarges text
- **THEN** the tile accommodates a two-line title and task count without a fixed-height crop
