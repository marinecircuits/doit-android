# Proposal

## Why

The user wants to see a task marked doing at the top of the application and open either that task or the full doing list.

## What Changes

- Show a persistent doing shortcut above the capture bar on home, project and label screens whenever an open task has the existing doing label.
- Show the first task in normal task order and its project. If several are doing, show how many others there are.
- Tapping the shortcut opens the task's edit sheet when there is one; when there are several it opens the doing label's cross-project task list.
- Keep the existing temporary conflict warning and exclude completed or deleted tasks.

## Capabilities

### Modified Capabilities

- `doing-tasks`: persistent shortcut and contextual navigation for working tasks.

## Impact

Task-screen state and a compact header. No sync, API or database changes.
