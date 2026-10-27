# Spec Delta

## MODIFIED Requirements

### Requirement: Project tile content
Each tile SHALL show the project's title and the number of open tasks directly in that project. A tile whose project has a colour set in Vikunja SHALL be filled with that colour made a little lighter, and its text SHALL be dark or light, whichever contrasts more with the fill. A tile whose project has no colour, or a colour the app cannot read, SHALL keep the theme's plain tile.

#### Scenario: Tile with open tasks
- **WHEN** Work contains 7 open tasks and its subproject Firmware contains 2
- **THEN** the Work tile reads "Work" and "7 open"

#### Scenario: Coloured project
- **WHEN** the project Work has the colour dark blue in Vikunja
- **THEN** the Work tile is a lighter blue with light text

#### Scenario: Light colour
- **WHEN** the project Home has the colour yellow in Vikunja
- **THEN** the Home tile is a lighter yellow with dark text

#### Scenario: No colour
- **WHEN** the project Inbox has no colour in Vikunja
- **THEN** the Inbox tile looks like a plain tile of the app's theme

#### Scenario: Colour changed on the server
- **WHEN** the user changes Work's colour in the web UI
- **THEN** the Work tile shows the new colour after the next sync
