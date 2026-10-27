# task-actions Specification

## Purpose

What the user can do to a task already in the list: complete it (with a safety net), reschedule it quickly, or fix its title, date and project.

## Requirements

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

### Requirement: Quick reschedule
Swiping a row to the left past the threshold SHALL open a bottom sheet with: Today, Tomorrow, Next week (next Monday), Pick a date…, and No date (only when the task has a due date). Choosing an option SHALL set the due date at once; dismissing the sheet SHALL change nothing.

#### Scenario: Move to tomorrow
- **WHEN** the user swipes "Review PR" left and picks Tomorrow
- **THEN** the task's due date becomes tomorrow and it moves to its new position in the list

#### Scenario: Pick a date
- **WHEN** the user picks "Pick a date…" and chooses 14 November
- **THEN** the task's due date becomes 14 November

### Requirement: Due time of day
When a date is chosen for a task that had no due date, its due time SHALL be 12:00 local time. When the task already had a due date, its time of day SHALL be kept and only the date SHALL change.

#### Scenario: Undated task gets a date
- **WHEN** an undated task is set to Tomorrow
- **THEN** it is due tomorrow at 12:00 local time

#### Scenario: Dated task is moved
- **WHEN** a task due today at 09:30 is set to Tomorrow
- **THEN** it is due tomorrow at 09:30

### Requirement: Light edit sheet
Tapping a task row (outside the circle) SHALL open a bottom sheet with the task's title, due date (with a date picker and a clear option) and project (a list of non-archived projects). The title SHALL save when its field loses focus or the sheet closes. A selected or cleared date and a selected project SHALL save immediately. The sheet SHALL have no Save or Close button; it SHALL dismiss through the standard sheet gesture, outside tap, or system back action. A blank title SHALL NOT replace the existing title.

#### Scenario: Move a task to another project
- **WHEN** the user opens "Buy filament" and chooses project "3D printing"
- **THEN** the task belongs to "3D printing" locally at once and on the server after sync

#### Scenario: Title saves on focus loss
- **WHEN** the user changes the title and leaves the title field
- **THEN** the new title appears locally at once and reaches the server after sync

#### Scenario: Closing the sheet saves the title
- **WHEN** the user changes the title and closes the sheet without leaving the field first
- **THEN** the new title is saved

### Requirement: Delete a task
The edit sheet SHALL offer deleting the task. Confirming SHALL remove it from the list at once. A task the server has never seen SHALL drop its unsent create and SHALL NOT be created. Any other task SHALL be deleted on the server at the next successful sync, including when it was already gone there. Cancelling the confirmation SHALL leave the task as it is.

#### Scenario: Delete a synced task
- **WHEN** the user opens "Buy filament", chooses Delete task and confirms
- **THEN** the task leaves the list at once and is gone from the server after sync

#### Scenario: Delete before the first sync
- **WHEN** the user deletes a task captured offline, before it has synced
- **THEN** the task is gone locally and the server never receives it

#### Scenario: Cancel
- **WHEN** the user opens the delete confirmation and cancels
- **THEN** the task is unchanged

### Requirement: Actions work offline
Every action in this capability SHALL apply to the local list immediately, with or without a connection, and SHALL reach the server on the next successful sync.

#### Scenario: Reschedule offline
- **WHEN** the user reschedules a task with no connection
- **THEN** the list shows the new date at once and the server has it after reconnecting

### Requirement: Completed rows stay until the view changes
A task completed in the current view SHALL stay visible there, struck through, while the user remains in that view, including across background syncs and screen rotation. It SHALL be gone the next time the user returns after leaving the view, for example after the app went to the background or after moving to another project screen or back. Struck-through rows SHALL NOT react to swipes or open the edit sheet; only their circle reacts.

#### Scenario: Sync while the row is shown
- **WHEN** a sync finishes while a struck-through row is visible
- **THEN** the row stays in place, still struck through

#### Scenario: Leave and come back
- **WHEN** the user completes a task, switches to another app and comes back
- **THEN** the completed task is no longer listed

#### Scenario: Completed row ignores swipes and taps
- **WHEN** the user swipes or taps the title of a struck-through row
- **THEN** nothing happens

#### Scenario: Navigate away and back
- **WHEN** the user completes a task on the Work screen, opens a subproject and goes back to Work
- **THEN** the completed task is no longer listed on the Work screen
