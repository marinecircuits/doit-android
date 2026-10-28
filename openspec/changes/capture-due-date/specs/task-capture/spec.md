# Spec Delta

## MODIFIED Requirements

### Requirement: Capture box
The main screen SHALL have a single-line text field at the top. Submitting it (keyboard action or send button) SHALL create a task from the trimmed title, using a selected due date if present, and SHALL clear the field and selected date while keeping the keyboard open. Blank input SHALL be ignored.

#### Scenario: Add a task
- **WHEN** the user types "Buy filament" and presses the keyboard's send action
- **THEN** a task titled "Buy filament" appears in the list immediately and the field is empty and still focused

#### Scenario: Blank input
- **WHEN** the user submits only spaces
- **THEN** no task is created

### Requirement: Plain-text titles
Other than recognized label and project tokens and a newly typed date trigger, capture text SHALL be kept literal. A pasted `!`, an exclamation mark inside a word, and date words SHALL not themselves select a due date.

#### Scenario: Text that looks like quick-add syntax
- **WHEN** the user pastes "call +Bob tomorrow !3" and captures it without picking a date
- **THEN** the task title is exactly "call +Bob tomorrow !3", with no project, date or priority change

## ADDED Requirements

### Requirement: Pick a due date while capturing
In the capture box and share title, a newly typed `!` at a word boundary outside quoted text or a tap on the `!` key SHALL open a date picker without retaining the trigger in the title. The chosen date SHALL be visible and removable until capture. A new task SHALL be created locally at noon in the device's local time and queued with that date, including offline. Cancelling the picker SHALL preserve any previous date.

#### Scenario: Pick a date with labels and projects
- **WHEN** the user types `Buy milk #Home @errand `, types `!`, chooses 30 October and submits
- **THEN** the task is created in Home with the label errand and a due date of 30 October at local noon

#### Scenario: Cancel or clear
- **WHEN** a date is selected and the picker is cancelled
- **THEN** the selected date remains until the user clears it or captures the task

#### Scenario: Offline capture
- **WHEN** the user chooses a date and captures a task offline
- **THEN** the local task shows that date and the queued create sends it on the next successful sync
