# Spec Delta

## MODIFIED Requirements

### Requirement: Complete a task
Tapping a task's circle SHALL mark the task done at once; the completion SHALL be saved and synced like any other change. The row SHALL stay where it is with a checked circle and a struck-through title. No snackbar SHALL be shown. Horizontal task-row gestures SHALL NOT complete or reschedule the task.

#### Scenario: Tap the circle
- **WHEN** the user taps the circle of "Call dentist"
- **THEN** the circle shows a check, the title is struck through, the row stays in place and the completion is queued for sync

#### Scenario: Short swipe
- **WHEN** the user swipes across a task row
- **THEN** the task is not completed or rescheduled by that gesture

## REMOVED Requirements

### Requirement: Quick reschedule
**Reason**: Task-row swipe actions are disabled. Due dates remain editable from the task sheet.
**Migration**: Open the task and use its due-date picker.
