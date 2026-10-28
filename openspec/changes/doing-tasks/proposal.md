# Proposal

## Why

The user wants the Android app to match the `doing` task controls in the Linux app under development on this machine.

## What Changes

- If an existing cached label is named doing, ignoring case, open tasks offer a play/stop button and an edit-sheet action. A working task is visibly active; other labels are untouched. The app never creates the label.
- When more than one open task is marked doing across projects, show a temporary overlay with Review that opens the doing-label task list. Notify only when the conflicting count changes; cancel stale warnings when resolved.
- Completing a working task removes doing before sending completion; undo restores the earlier working state, including after sync. Repeating tasks reopen without doing.
- Store a durable label-ID membership intent in the existing pending-change queue. Preserve newer changes during in-flight create, label and delete operations; retry transient failures and reconcile refused label operations.
- Document the additional optional `tasks_labels → delete` permission.

Out of scope: creating the label, automatically stopping other tasks, modifying Linux, and the Linux checkout's separate in-progress shopping-list feature.

## Capabilities

### New Capabilities

- `doing-tasks`: optional working-state controls, conflict review, completion behavior and durable sync.

## Impact

Task rows, edit sheet, snackbar, task repository, task-label API, sync queue and tests. No database migration is required.
