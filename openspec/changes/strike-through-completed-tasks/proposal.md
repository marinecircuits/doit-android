# Proposal

## Why

When a task is completed it vanishes at once, so the list shifts under the user's finger and there is little feedback beyond a snackbar (issue #2). Keeping the row in place, struck through, gives clear feedback and a stable list.

## What Changes

- Completing a task (tapping its circle or swiping right) marks it done and saves and syncs it immediately as before, but the row stays in place with a checked circle and a struck-through title.
- Unchecking a struck-through row undoes the completion; the row becomes an ordinary open task again.
- **BREAKING (UI):** the "Completed “…”" snackbar with Undo is removed; unchecking the row replaces it.
- Completed rows leave the list the next time the user leaves the view (for now: the app goes to the background; later also navigating between project screens).
- Struck-through rows can't be swiped or opened for editing; only their circle reacts.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `task-actions`: completing keeps the row struck through instead of removing it; undo is unchecking; repeating tasks stay struck through until the server reopens them; new requirement for when completed rows leave.
- `task-list`: the list keeps tasks completed in the current view until the user leaves it; the empty state appears only after that.

## Impact

- `ui/tasks/TaskListModel.kt`, `TasksViewModel.kt`, `TasksScreen.kt`, `SwipeRow.kt`; two snackbar strings removed.
- No data, sync, API or widget changes: completion is stored and queued exactly as before.
