# recurring-tasks Specification

## Purpose

Configure recurring tasks explicitly, without interpreting ordinary title words as dates or schedules.

## Requirements

### Requirement: Explicit shortcuts
Capture and title editing SHALL recognize only exact word-starting `!daily`, `!weekly`, `!monthly`, and `!Nd` / `!Nw` shortcuts, ignoring case, with N from 1 to 9999. Plain words, unknown shortcuts, quoted text, quantity brackets, and embedded punctuation SHALL remain literal. All recognized repeat tokens SHALL be removed, with the last one choosing the recurrence.

#### Scenario: Explicit repeat and literal words
- **WHEN** the user enters `Write weekly report !weekly`
- **THEN** the title becomes `Write weekly report` with a weekly repeat chip
- **AND** `!weelky` only offers an explicit correction and is not silently interpreted

### Requirement: Scheduling suggestions and chips
Typing `!` SHALL offer Choose date, Daily, Weekly, and Monthly without taking focus from the title. Continued typing SHALL filter choices; primary activation, Enter or Tab SHALL accept. Arrow keys SHALL select; Escape SHALL dismiss suggestions. Accepted repeats SHALL be shown as editable, removable chips. Pasted exact tokens SHALL be previewed before capture.

### Requirement: First date and repeat settings
A repeating task SHALL require an explicit first due date before it is saved. Cancelling selection SHALL preserve the title and prior schedule. New dates SHALL use local noon; existing times SHALL be retained. Repeat settings SHALL support fixed intervals from the scheduled date (Vikunja mode 0), calendar monthly repeats (mode 1), and fixed intervals after completion (mode 2). Removing repeat SHALL preserve the due date and clear both repeat fields. Date-only edits SHALL NOT overwrite recurrence.

#### Scenario: Missing first date
- **WHEN** a repeat is chosen on an undated task
- **THEN** the picker asks for the first due date before committing the repeat
- **AND** cancelling leaves the original title and settings intact

### Requirement: Durable synchronization
The task cache and pending changes SHALL persist repeat_after and repeat_mode. Upgrading SHALL preserve cached tasks and queued work. Newer edits SHALL survive in-flight sync; downloading tasks SHALL retain repeat settings from other clients. Restoring deleted tasks SHALL restore cached recurrence. Completion SHALL let Vikunja advance the task's schedule.

### Requirement: Repeat indicator in task lists
Recurring tasks SHALL show a small repeat-arrows icon immediately before the due date in task lists and the home-screen widget. The icon SHALL also appear for recurring tasks without a due date and for calendar monthly repeats with a zero interval. Its accessible description SHALL describe the recurrence, including after-completion mode. Ordinary tasks SHALL show no repeat icon.
