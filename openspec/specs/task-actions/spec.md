# task-actions Specification

## Purpose

What the user can do to a task already in the list: complete it (with a safety net), reschedule it quickly, or fix its title, date and project.

## Requirements

### Requirement: Complete a task
Tapping a task's circle, or swiping its row to the right past the threshold, SHALL mark the task done. The task SHALL disappear from the list immediately and a snackbar "Completed “<title>”" with an Undo action SHALL show for about 4 seconds.

#### Scenario: Tap the circle
- **WHEN** the user taps the circle of "Call dentist"
- **THEN** the task disappears and a snackbar offers Undo

#### Scenario: Short swipe
- **WHEN** the user swipes a row right but releases before the threshold
- **THEN** the row snaps back and nothing changes

### Requirement: Undo completion
Choosing Undo SHALL restore the task to the list exactly as it was before completion, including its due date, and the server SHALL end up with the task open again.

#### Scenario: Undo before sync
- **WHEN** the user completes a task offline and taps Undo
- **THEN** the task is back in its original place and no completion reaches the server

#### Scenario: Undo after sync
- **WHEN** the completion has already been sent to the server and the user taps Undo
- **THEN** the task is reopened on the server with its original due date

### Requirement: Repeating tasks
Completing a task that repeats in Vikunja SHALL hide it until the server responds; after sync it SHALL reappear with the next due date the server assigned.

#### Scenario: Weekly task
- **WHEN** the user completes "Water plants" which repeats every 7 days and is due today
- **THEN** after sync the task is open again and due in 7 days

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
Tapping a task row (outside the circle) SHALL open a bottom sheet with the task's title, due date (with a date picker and a clear option) and project (a list of non-archived projects). Save SHALL apply all changes at once; Cancel or dismissing SHALL discard them. Save SHALL be disabled while the title is blank.

#### Scenario: Move a task to another project
- **WHEN** the user opens "Buy filament", chooses project "3D printing" and taps Save
- **THEN** the task belongs to "3D printing" locally at once and on the server after sync

#### Scenario: Discard edits
- **WHEN** the user changes the title and then dismisses the sheet
- **THEN** the task keeps its original title

### Requirement: Actions work offline
Every action in this capability SHALL apply to the local list immediately, with or without a connection, and SHALL reach the server on the next successful sync.

#### Scenario: Reschedule offline
- **WHEN** the user reschedules a task with no connection
- **THEN** the list shows the new date at once and the server has it after reconnecting
