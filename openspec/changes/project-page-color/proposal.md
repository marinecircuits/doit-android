# Proposal

## Why

Opening a project should make that screen recognisable as the project, not only its tile.

## What Changes

- When a project with a colour is open, paint the whole page, including the task list, with the same lightened colour used on its tile.
- Choose text and status-bar icons from that colour's contrast. A project without a colour keeps the normal background.
- Leave subproject tiles on their own colours.

## Impact

- `TasksScreen` overrides the colour scheme for the project page only. No data or sync change.
