# project-navigation Specification

## Purpose

Lets the user browse Vikunja's project hierarchy: top-level projects as tiles on the main view, and each project's own tasks with its subprojects as tiles beneath them.

## Requirements

### Requirement: Top-level project tiles
The main view SHALL show one tile for every non-archived top-level project, using the full content area and showing no task list. A project whose parent is archived or not accessible SHALL count as top-level. Nested projects SHALL NOT have tiles on the main view.
Main-view tiles SHALL be roughly twice as tall as subproject tiles, while keeping the same width and text styles.

#### Scenario: Only top-level projects
- **WHEN** the user has the projects Work (with subproject Firmware) and Home
- **THEN** the main view shows tiles for Work and Home and none for Firmware

#### Scenario: Subproject of an archived project
- **WHEN** the project Old is archived and its subproject Notes is not
- **THEN** the main view shows a tile for Notes

#### Scenario: No projects
- **WHEN** no projects are available
- **THEN** the main view shows a short "No projects yet" message

### Requirement: Project tile content
Each tile SHALL show the project's title and the number of open tasks directly in that project. A tile whose project has a colour set in Vikunja SHALL be filled with that colour washed well towards white, and its text SHALL be dark or light, whichever contrasts more with the fill. A tile whose project has no colour, or a colour the app cannot read, SHALL keep the theme's plain tile.

#### Scenario: Tile with open tasks
- **WHEN** Work contains 7 open tasks and its subproject Firmware contains 2
- **THEN** the Work tile reads "Work" and "7 open"

#### Scenario: Coloured project
- **WHEN** the project Work has the colour dark blue in Vikunja
- **THEN** the Work tile is a pale blue, with text that contrasts with the fill

#### Scenario: Light colour
- **WHEN** the project Home has the colour yellow in Vikunja
- **THEN** the Home tile is a lighter yellow with dark text

#### Scenario: No colour
- **WHEN** the project Inbox has no colour in Vikunja
- **THEN** the Inbox tile looks like a plain tile of the app's theme

#### Scenario: Colour changed on the server
- **WHEN** the user changes Work's colour in the web UI
- **THEN** the Work tile shows the new colour after the next sync

### Requirement: Project screen
Tapping a tile SHALL open that project's screen with a header showing a back arrow and the project title, the capture box, and the project's own open tasks, excluding tasks of its subprojects. Tasks behave as in the main list.

#### Scenario: Open a project
- **WHEN** the user taps the Work tile
- **THEN** the Work screen shows Work's open tasks and none from other projects

### Requirement: Subproject area
On a project screen whose project has non-archived immediate subprojects, the upper roughly two thirds of the content area SHALL show the task list and the lower roughly one third SHALL show one tile per immediate subproject. Without subprojects the task list SHALL use the whole content area.

#### Scenario: Project with subprojects
- **WHEN** the user opens Work, which has the subprojects Firmware and Hardware
- **THEN** Work's tasks fill the upper two thirds and tiles for Firmware and Hardware fill the lower third

#### Scenario: Project without subprojects
- **WHEN** the user opens Home, which has no subprojects
- **THEN** Home's tasks use the whole content area

### Requirement: Independent scrolling
Wherever a task list and a tile area are shown together, each SHALL scroll on its own when its content does not fit.

#### Scenario: Many tasks and many tiles
- **WHEN** a project has 40 tasks and 12 subprojects
- **THEN** the task list and the tile area can each be scrolled without moving the other

### Requirement: Deeper levels and back
Tapping a subproject tile SHALL open that subproject's screen with the same layout. The back arrow and the system back action SHALL go to the parent project's screen, and from a top-level project to the main view.
The back arrow SHALL have a larger visual and touch target than a standard icon button.

#### Scenario: Two levels down and back
- **WHEN** the user opens Work, then Firmware, then goes back twice
- **THEN** the app shows Work after the first back and the main view after the second

### Requirement: Open project removed
If the open project or one of its ancestors is deleted or archived on the server, the app SHALL show the nearest remaining ancestor's screen, or the main view if none remains, after the next sync.

#### Scenario: Project deleted elsewhere
- **WHEN** the user is on the Firmware screen and Firmware is deleted in the web UI
- **THEN** after the next sync the app shows the Work screen

### Requirement: Create a subproject
A project screen's overflow menu SHALL offer "Create subproject". A dialog SHALL show the current parent and request a non-blank project name. The app SHALL trim surrounding whitespace and create the project through API v2 with that parent's ID. This operation requires a connection; it SHALL NOT enqueue an offline project or a task.

#### Scenario: Create within a nested project
- **WHEN** the user chooses Create subproject in Work → Firmware and submits "Drivers"
- **THEN** Drivers is created with Firmware as its parent and appears as a tile on Firmware's screen without leaving that screen

#### Scenario: Submitting and dismissing
- **WHEN** creation is in progress
- **THEN** repeat submissions, editing and dismissal are disabled until the request finishes
- **WHEN** the name is blank or the user cancels before submitting
- **THEN** no project is created

#### Scenario: Creation fails
- **WHEN** the connection or server request fails
- **THEN** the dialog retains the name and parent, shows an error, and does not create a local placeholder or retry automatically
- **AND** an uncertain result advises syncing before a retry to avoid duplicates

#### Scenario: Concurrent sync
- **WHEN** a sync is already fetching projects as the user submits a subproject
- **THEN** creation waits for that sync before posting and caching the new project, preventing a stale pull from erasing the new tile
