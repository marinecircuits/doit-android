# Proposal

## Why

The user wants to select due dates quickly while typing, without leaving capture to edit the new task.

## What Changes

- Typing `!` at the start of a word or tapping the `!` key opens the existing date picker in the capture bar, share title and task edit title.
- Capture and share show the selected date with change and clear controls, then create the task with that date; editing saves the selected date using the existing reschedule behavior.
- For new tasks the date is stored locally and queued with the create, at noon in the device's local time. Existing `@labels` and `#projects` still work in the same capture.
- Ordinary punctuation, pasted text and `!` inside quoted names stay literal. Cancelling the picker preserves the previous selected date.

Out of scope: natural-language dates, priority syntax, and picking a time of day.

## Capabilities

### Modified Capabilities

- `task-capture`: select and see a date before creating a task.
- `task-actions`: invoke the date picker from the edit title.

## Impact

The existing date picker, task creation queue and sync API are reused. A date is passed to the repository at capture; no database migration, server route or permission is needed.
