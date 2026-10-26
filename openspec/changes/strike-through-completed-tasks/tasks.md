# Tasks

## 1. Struck-through completed rows

- [ ] 1.1 Let `TaskListModel` place completed tasks among the open ones by the usual order, marked completed and never overdue; verify with unit tests for ordering and flags
- [ ] 1.2 Keep pre-completion snapshots of tasks completed in the current view in `TasksViewModel` (complete, uncheck, clear on leaving the view, open version wins for reopened repeating tasks); verify with view-model tests for completing, a sync removing the task, unchecking and leaving
- [ ] 1.3 Show completed rows with a checked circle and struck-through title, unchecking via the circle, no swipe or edit on them, swipe right returning the row to place, and remove the Undo snackbar and its strings; verify the CI build and tests pass
- [ ] 1.4 Verify on device: complete by tap and by swipe, uncheck, background sync keeps the row, leaving and returning removes it

## Workflow follow-up

- Close issue #2 with the pull request; sync specs into `openspec/specs/` after merging and archive after the on-device check.
