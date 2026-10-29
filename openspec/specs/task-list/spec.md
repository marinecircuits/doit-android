# task-list Specification

## Purpose

The task list shown inside each project, in one predictable order.

## Requirements

### Requirement: Project task list
Each project screen SHALL show its own open tasks, excluding tasks in subprojects. The main view SHALL show no task list. Tasks the user completes in the current project view SHALL stay listed, struck through, until the user leaves the view; tasks completed anywhere else (on the server or from the widget) SHALL disappear after the next sync or widget update.

#### Scenario: Tasks from several projects
- **WHEN** the user has open tasks in the projects Inbox, Work and Home
- **THEN** the main view shows only project tiles, and opening Work shows only Work's tasks

#### Scenario: Done tasks are hidden
- **WHEN** a task is marked done on the server or from the widget, or was completed in the app before the user left the list
- **THEN** it no longer appears in the list (after the next sync for server-side changes)

### Requirement: List ordering
Tasks with the locally selected next-actions label SHALL appear first in lists and the widget. The default is a label named `next`, if present; the user may choose another label or none. This setting SHALL remain on the device. Within each group the order SHALL be overdue tasks, tasks due today or later, then undated tasks. Dated tasks SHALL be sorted by due date and time ascending; undated tasks SHALL be sorted newest first.

#### Scenario: Mixed tasks
- **WHEN** the list contains a task due yesterday, one due Friday, one due today and one without a date
- **THEN** the order is: yesterday's task, today's task, Friday's task, the undated task

### Requirement: Due-date label
Each dated task SHALL show a compact due label on the right: "today", "tomorrow", "yesterday", the weekday name for dates within the next 6 days, otherwise day and month, adding the year when it differs from the current year. Overdue labels SHALL use the theme's error colour. Undated tasks SHALL show no label.

#### Scenario: Task due in three days
- **WHEN** today is Monday 26 October and a task is due Thursday 29 October
- **THEN** its label reads "Thu"

#### Scenario: Overdue task
- **WHEN** a task was due on 20 October and today is 26 October
- **THEN** its label reads "20 Oct" in the error colour

### Requirement: Project tag
On a project screen no task SHALL show a project tag, because the header identifies the project.

#### Scenario: Project screen
- **WHEN** the user opens the Work screen
- **THEN** none of its task rows shows a project tag

### Requirement: Refresh
Tasks and projects SHALL sync with the server when the app comes to the foreground and when the user pulls down on the main view or a project list, and SHALL show a refresh indicator only for a user-initiated pull.

#### Scenario: Pull to refresh
- **WHEN** the user pulls the list down
- **THEN** a refresh indicator shows until the sync finishes, and the list reflects the server state

### Requirement: Sync status visibility
When changes are waiting to be sent or the last sync failed, the main screen SHALL show a short status line (for example "Offline · 2 changes waiting"); when everything is synced it SHALL show nothing.

#### Scenario: Offline with pending changes
- **WHEN** the device is offline and the user has completed two tasks
- **THEN** the status line reads "Offline · 2 changes waiting"

### Requirement: Empty state
When there are no open tasks and no struck-through rows left in a project view, the list area SHALL show a short "Nothing to do" message instead of an empty screen.

#### Scenario: Everything done
- **WHEN** the user completes the last open task and later comes back to the list
- **THEN** the list area shows "Nothing to do"
