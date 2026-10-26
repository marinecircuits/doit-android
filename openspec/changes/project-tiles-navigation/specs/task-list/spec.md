# Spec Delta

## MODIFIED Requirements

### Requirement: One list of all open tasks
The main view SHALL show, above the project tiles, every task that is not done, from every project the user can access, excluding tasks in archived projects. Tasks the user completes in the current view SHALL stay listed, struck through, until the user leaves the view; tasks completed anywhere else (on the server or from the widget) SHALL disappear after the next sync or widget update.

#### Scenario: Tasks from several projects
- **WHEN** the user has open tasks in the projects Inbox, Work and Home
- **THEN** all of them appear in the main view's list

#### Scenario: Done tasks are hidden
- **WHEN** a task is marked done on the server or from the widget, or was completed in the app before the user left the list
- **THEN** it no longer appears in the list (after the next sync for server-side changes)

### Requirement: Project tag
On the main view, a task that is not in the user's default project SHALL show its project's title as a small secondary line under the task title; tasks in the default project SHALL show no project tag. On a project screen no task SHALL show a project tag.

#### Scenario: Task in another project
- **WHEN** the default project is Inbox and a task belongs to Work
- **THEN** the row shows "Work" under the task title

#### Scenario: Project screen
- **WHEN** the user opens the Work screen
- **THEN** none of its task rows shows a project tag
