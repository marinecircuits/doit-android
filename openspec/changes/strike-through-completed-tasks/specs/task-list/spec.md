# Spec Delta

## MODIFIED Requirements

### Requirement: One list of all open tasks
The main screen SHALL show every task that is not done, from every project the user can access, excluding tasks in archived projects. Tasks the user completes in the current view SHALL stay listed, struck through, until the user leaves the view; tasks completed anywhere else (on the server or from the widget) SHALL disappear after the next sync or widget update.

#### Scenario: Tasks from several projects
- **WHEN** the user has open tasks in the projects Inbox, Work and Home
- **THEN** all of them appear in the one list

#### Scenario: Done tasks are hidden
- **WHEN** a task is marked done on the server or from the widget, or was completed in the app before the user left the list
- **THEN** it no longer appears in the list (after the next sync for server-side changes)

### Requirement: Empty state
When there are no open tasks and no struck-through rows left in the view, the list area SHALL show a short "Nothing to do" message instead of an empty screen.

#### Scenario: Everything done
- **WHEN** the user completes the last open task and later comes back to the list
- **THEN** the list area shows "Nothing to do"
