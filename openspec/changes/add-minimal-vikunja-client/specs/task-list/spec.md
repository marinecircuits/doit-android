# Spec Delta

## Purpose

The app's single screen of work: every open task the user can see, across all projects, in one predictable order.

## ADDED Requirements

### Requirement: One list of all open tasks
The main screen SHALL show every task that is not done, from every project the user can access, excluding tasks in archived projects. Tasks completed locally SHALL disappear immediately, even before the change is synced.

#### Scenario: Tasks from several projects
- **WHEN** the user has open tasks in the projects Inbox, Work and Home
- **THEN** all of them appear in the one list

#### Scenario: Done tasks are hidden
- **WHEN** a task is marked done, in the app or on the server
- **THEN** it no longer appears in the list (after the next sync for server-side changes)

### Requirement: List ordering
The list SHALL be ordered in three groups: overdue tasks (due date before today), then tasks due today or later, then tasks without a due date. Dated tasks SHALL be sorted by due date and time ascending; undated tasks SHALL be sorted newest first.

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
A task that is not in the user's default project SHALL show its project's title as a small secondary line under the task title; tasks in the default project SHALL show no project tag.

#### Scenario: Task in another project
- **WHEN** the default project is Inbox and a task belongs to Work
- **THEN** the row shows "Work" under the task title

### Requirement: Refresh
The list SHALL sync with the server when the app comes to the foreground and when the user pulls down on the list, and SHALL show a refresh indicator only for a user-initiated pull.

#### Scenario: Pull to refresh
- **WHEN** the user pulls the list down
- **THEN** a refresh indicator shows until the sync finishes, and the list reflects the server state

### Requirement: Sync status visibility
When changes are waiting to be sent or the last sync failed, the main screen SHALL show a short status line (for example "Offline · 2 changes waiting"); when everything is synced it SHALL show nothing.

#### Scenario: Offline with pending changes
- **WHEN** the device is offline and the user has completed two tasks
- **THEN** the status line reads "Offline · 2 changes waiting"

### Requirement: Empty state
When there are no open tasks, the list area SHALL show a short "Nothing to do" message instead of an empty screen.

#### Scenario: Everything done
- **WHEN** the user completes the last open task
- **THEN** the list area shows "Nothing to do"
