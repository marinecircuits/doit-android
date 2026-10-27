# Design

## Context

See proposal.md for motivation and the specs for behaviour. doit for Linux implements the same feature in its open change `capture-labels`; this design follows it and notes where Android differs.

- Vikunja API v2 has `GET /labels` (paged, token permission `labels → read_all`) and `POST /tasks/{task}/labels` with `{"label_id": …}` (`tasks_labels → create`). Adding a label that is already on the task answers 400. Every task in the task list already carries `labels`.
- The capture box is a `String` state in `TasksScreen`; the share dialog has its own title field. Both end in `TaskRepository.capture`.
- The local database is at version 3. Pending changes are one merged row per task with a revision counter.

## Goals / Non-Goals

**Goals:**
- The Linux app's `@label` rule, suggestions and chips, working offline.
- No new failure for tokens without label permissions.

**Non-Goals:**
- Editing labels on existing tasks, label filters, labels in the widget, creating labels.
- Hardware-keyboard navigation of the suggestions.

## Decisions

**Parsing is a pure `LabelSyntax` port of the Linux `labels.py`.** On `" " + text + " "`, repeatedly take the first match of `\s@("([^"]+)"|\S+)` from the current position. If the name is a known label (ignoring case), collect the label's own title once and replace the match with a space; otherwise move on one character, leaving it as text. The title is what remains with whitespace collapsed. `typing(textBeforeCursor)` finds a label being typed with `(?:^|\s)@(?:"([^"]*)|(\S*))$`, `suggest` ranks prefix matches before substring matches (at most 5), and `token(name)` writes `@name ` or `@"two words" `. Parsing happens in `TaskRepository.capture`, so the capture box and the share dialog behave the same; the shared description is never parsed.

**Labels stored by title per task.** New tables: `labels (id, title, hexColor)` with the server's labels from the last sync, and `task_labels (taskId, title, position)` with each task's label names in order (primary key `taskId, title`). Storing names rather than ids lets a task captured offline show its chips before any id is known; chip colours come from `labels` by case-insensitive title. The database moves to version 4 with `AutoMigration(from = 3, to = 4)`, which only creates the two tables.

**The capture queues the label names with the create.** `capture` writes the task, its `task_labels` and a pending create whose fields include `labels: ["Errand", …]`. Later merges never touch `labels`, because only a capture sets it.

**Push: labels go after the task exists.**
- A create sends the task without `labels` and `done`; whatever of the two is left becomes an update, as `done` already does.
- An update carrying `labels` is handled on its own:
  - Each name is resolved to an id from `labels`, refetching the label list once per sync if a name is missing.
  - Each label is added with `POST /tasks/{task}/labels`. A 4xx other than 401/403/404 means the label is already there and counts as done.
  - A 404 refetches the labels: if the label is gone it is skipped and named in a notice; otherwise the task is gone and the change is dropped as any 404 is.
  - Then `labels` is removed from the current pending row, re-read by `seq` so edits made meanwhile survive; any other fields go out in the next push turn as a normal merge patch.
- A 401/403 on the label route runs the token test. If that fails, the usual rejected-token path applies. If it passes, the token lacks label permissions: the labels are dropped with a notice naming the task, and the sync continues.

**Pull: labels are optional.** The label list is fetched after projects and tasks. A 401/403 keeps the old list, so tokens without `labels → read_all` sync as before. For every task it upserts, the pull replaces that task's `task_labels` from the task's `labels`. Tasks with unsent changes keep their local labels. Orphaned `task_labels` rows, whose task is gone, are deleted in the same transaction. When a create gets its server id, its `task_labels` move to the new id together with the task and its pending change.

**Rows carry chips.** `TaskRow` gains `labels: List<LabelChip(title, hexColor)>`. `TaskListModel.rows` resolves them from the task's label names and the known labels, and the chips reuse `ProjectColors.tile` for fill and text. A chip without a usable colour uses `surfaceVariant`/`onSurfaceVariant`. On a struck-through row the chips are dimmed like the title.

**Suggestions are an overlay under the field, not a popup.** The capture field holds a `TextFieldValue`, so the cursor position is known. While `typing` finds a label and `suggest` returns any, a `Surface` with up to 5 rows (colour dot and name) is drawn over the top of the content below the capture bar, so the list doesn't jump. A tap replaces the range from the `@` to the cursor with `token(name)` and puts the cursor after it. Nothing in the overlay takes focus, so the keyboard stays up. In the share dialog the same list sits inline under the title field. *Alternative:* `DropdownMenu`/`ExposedDropdownMenuBox`, whose popup focus handling fights with an editable field and closes on outside taps. *Alternative:* a horizontal chip row, which is compact but hides longer names.

**Send always captures.** The Linux app completes on Enter unless the name is already typed out. On a phone the send key is the only way to submit, so it always captures; an unfinished `@er` stays in the title as text.

## Risks / Trade-offs

- [Two labels with the same title] → They count as one name; the first one in the server's list is used. Acceptable for a personal server.
- [A label renamed on the server between capture and sync] → One refetch per sync; if the old name no longer exists it is skipped with a notice, as in Linux.
- [Schema 4 must be committed from CI] → As before: the build warns when `app/schemas` changed, and the file comes from the `room-schemas` artifact.
