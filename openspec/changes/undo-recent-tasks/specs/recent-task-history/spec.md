# Spec Delta

## ADDED Requirements

### Requirement: Undo recent actions
Tapping a recent task SHALL offer to undo the action. Completed tasks SHALL be reopened without overwriting unrelated server fields. Deleted tasks SHALL be recreated from their locally stored snapshot, preserving cached due date and labels. The interface SHALL explain that server-only details may not be restored. Older entries SHALL restore their available title and project.

#### Scenario: Reopen a completed task
- **WHEN** the user chooses to undo completion
- **THEN** the task is reopened and queued for sync without clearing its due date

#### Scenario: Restore a deleted task
- **WHEN** the user restores a locally recorded deletion
- **THEN** the app creates an open task from its cached details and queues it for sync

### Requirement: Incremental history display
Each recent task section SHALL initially show up to five items, with a control that shows the next five when more are available.

#### Scenario: Browse further
- **WHEN** a section has more than five entries and the user chooses Show five more
- **THEN** that section displays up to five additional entries
