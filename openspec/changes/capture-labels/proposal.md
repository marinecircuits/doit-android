# Proposal

## Why

The user tags tasks with Vikunja labels and wants to add them while capturing, as doit for Linux now does (its open change `capture-labels`): an `@` prefix with suggestions of their labels. doit for Android ignores labels entirely: it neither shows nor sets them, and the capture box keeps every character verbatim.

## What Changes

- Typing `@` in the capture box (main view, project screens) or in the share dialog's title field shows up to 5 of the user's labels under the field, filtered by what follows the `@`. Tapping one completes it; typing stays in the field. Names with spaces are completed as `@"two words"`.
- When a task is captured, every `@label` token (and `@"two words"`) naming an existing label, ignoring case, is taken out of the title and becomes a label on the task. Any other `@name` stays in the title as plain text: a capture never creates a label. `+`, `!`, `*` and date words stay plain text, and an `@` inside a word (an e-mail address) or followed by a space stays in the title.
- Task rows on project screens show their labels as small chips under the title, in the label's colour made a little lighter (like project tiles), or plain without a colour.
- All of it works offline: suggestions use the labels from the last sync, chips show at once, and labels are added once the task exists on the server.
- The setup screen and README list the label permissions as optional: without them, tasks are still created and the user is told that their labels couldn't be added.

The behaviour matches doit for Linux, except for keyboard handling: Android has no ↑/↓/Tab/Escape navigation of the suggestions, and the keyboard's send action always captures.

Out of scope: changing labels on an existing task (edit sheet), filtering by label, labels in the widget, `+project`, `!priority` or date syntax.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `task-capture`: `@label` syntax and label suggestions in the capture box and the share dialog.
- `task-list`: label chips on rows.
- `offline-sync`: labels are synced; labels from a capture are added after the task has been created.

## Impact

- API: `GET /labels` (paged) and `POST /tasks/{task}/labels`; tasks already carry `labels`.
- Data: new `labels` and `task_labels` tables, Room database version 4 with an auto-migration and a new exported schema.
- Code: a `LabelSyntax` parser, `TaskRepository.capture`, `SyncEngine` (pull labels and task labels, push labels after a create), `TaskListModel` rows, `TasksViewModel`, the capture box, the share dialog, task rows, the setup screen's permission list, README.
- Token: `labels → read_all` and `tasks_labels → create`, both optional.
