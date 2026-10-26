# task-capture Specification

## Purpose

Getting a new task into Vikunja as fast as possible, from the app itself or from any other app via Android's share sheet.

## Requirements

### Requirement: Capture box
The main screen SHALL have a single-line text field at the top. Submitting it (keyboard action or send button) SHALL create a task whose title is the trimmed text, with no due date, and SHALL clear the field while keeping the keyboard open for the next task. Blank input SHALL be ignored.

#### Scenario: Add a task
- **WHEN** the user types "Buy filament" and presses the keyboard's send action
- **THEN** a task titled "Buy filament" appears in the list immediately and the field is empty and still focused

#### Scenario: Blank input
- **WHEN** the user submits only spaces
- **THEN** no task is created

### Requirement: Plain-text titles
The capture box SHALL NOT interpret any syntax: characters such as `*`, `+`, `!` and date words SHALL be kept verbatim in the title.

#### Scenario: Text that looks like quick-add syntax
- **WHEN** the user captures "call +Bob tomorrow !3"
- **THEN** the task title is exactly "call +Bob tomorrow !3", with no project, date or priority change

### Requirement: Destination project
Tasks captured on the main view or from the share sheet SHALL be created in the default project configured in the user's Vikunja settings. If no default project is set or the token cannot read user settings, the app SHALL use the first non-archived project it knows. Tasks captured on a project screen SHALL be created in that project.

#### Scenario: Default project configured
- **WHEN** the user's Vikunja default project is Inbox
- **THEN** tasks captured on the main view are created in Inbox

#### Scenario: Capture on a project screen
- **WHEN** the user captures "Order capacitors" on the Firmware screen
- **THEN** the task is created in Firmware and appears in Firmware's list

### Requirement: Capture works offline
Capturing SHALL work without a connection: the task SHALL appear in the list at once and SHALL be created on the server on the next successful sync.

#### Scenario: Offline capture
- **WHEN** the user captures a task in flight mode
- **THEN** the task appears in the list immediately and exists on the server after the device reconnects and syncs

### Requirement: New task stays visible
After a capture, the list SHALL scroll so the new task is visible.

#### Scenario: Long list
- **WHEN** the list has 40 dated tasks and the user captures an undated task
- **THEN** the list scrolls to show the new task, which is the first of the undated tasks after the 40 dated ones

### Requirement: Share into app
The app SHALL appear as a target in Android's share sheet for plain text. Sharing SHALL open a small dialog over the source app, pre-filled with a proposed title, that the user can edit and confirm with "Add" or dismiss with "Cancel".

#### Scenario: Share a web page
- **WHEN** the user shares a page from the browser (subject "Rust book", text "https://doc.rust-lang.org/book/")
- **THEN** the dialog proposes the title "Rust book" and, on Add, creates the task with the URL as its description

#### Scenario: Share plain text
- **WHEN** the user shares the text "Pick up parcel at post office" without a subject
- **THEN** the dialog proposes that text as the title and the task has no description

#### Scenario: Share while signed out
- **WHEN** the user shares text before the app has been set up
- **THEN** the dialog explains that the app must be connected to a server first and offers to open the app
