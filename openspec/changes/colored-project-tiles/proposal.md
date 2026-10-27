# Proposal

## Why

All project tiles look the same, so the user has to read every title to find a project. Vikunja projects already have a colour that the user picked in the web UI. Showing it on the tiles makes projects recognisable at a glance and matches what the user sees on the server.

## What Changes

- A project tile takes its project's Vikunja colour, made a little lighter. The text on it is dark or light, whichever is easier to read on that colour.
- Projects without a colour, or with one the app cannot read, keep the current plain tile.
- The app now stores each project's colour (`hex_color`). The local database moves to version 3 with a migration that keeps all tasks and unsent changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `project-navigation`: tiles show the project's colour.

## Impact

- Data: `ProjectDto`, `ProjectEntity` (new column), Room database version 3 with an auto-migration and a new exported schema; `SyncEngine` stores the colour.
- Code: new `ProjectColors` helper; the tile in `TasksScreen`; README.
- No server or API permission changes: `hex_color` is part of the project list the app already reads.
