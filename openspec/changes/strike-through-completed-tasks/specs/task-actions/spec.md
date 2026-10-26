# Spec Delta

## MODIFIED Requirements

### Requirement: Complete a task
Tapping a task's circle, or swiping its row to the right past the threshold, SHALL mark the task done at once; the completion SHALL be saved and synced like any other change. The row SHALL stay where it is with a checked circle and a struck-through title. No snackbar SHALL be shown.

#### Scenario: Tap the circle
- **WHEN** the user taps the circle of "Call dentist"
- **THEN** the circle shows a check, the title is struck through, the row stays in place and the completion is queued for sync

#### Scenario: Short swipe
- **WHEN** the user swipes a row right but releases before the threshold
- **THEN** the row snaps back and nothing changes

### Requirement: Undo completion
Unchecking the circle of a struck-through row SHALL restore the task exactly as it was before completion, including its due date, and the server SHALL end up with the task open again. The row SHALL lose its strike-through and stay in the list as an open task.

#### Scenario: Undo before sync
- **WHEN** the user completes a task offline and then unchecks it
- **THEN** the task is an ordinary open task in its original place and no completion reaches the server

#### Scenario: Undo after sync
- **WHEN** the completion has already been sent to the server and the user unchecks the row
- **THEN** the task is reopened on the server with its original due date

### Requirement: Repeating tasks
Completing a task that repeats in Vikunja SHALL show it struck through until the server responds; after sync it SHALL appear as an open task again with the next due date the server assigned.

#### Scenario: Weekly task
- **WHEN** the user completes "Water plants" which repeats every 7 days and is due today
- **THEN** after sync the task is open again, no longer struck through, and due in 7 days

## ADDED Requirements

### Requirement: Completed rows stay until the view changes
A task completed in the current view SHALL stay visible there, struck through, while the user remains in that view, including across background syncs and screen rotation. It SHALL be gone the next time the user returns after leaving the view, for example after the app went to the background. Struck-through rows SHALL NOT react to swipes or open the edit sheet; only their circle reacts.

#### Scenario: Sync while the row is shown
- **WHEN** a sync finishes while a struck-through row is visible
- **THEN** the row stays in place, still struck through

#### Scenario: Leave and come back
- **WHEN** the user completes a task, switches to another app and comes back
- **THEN** the completed task is no longer listed

#### Scenario: Completed row ignores swipes and taps
- **WHEN** the user swipes or taps the title of a struck-through row
- **THEN** nothing happens
