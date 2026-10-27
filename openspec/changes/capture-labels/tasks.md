# Tasks

## 1. Data and sync

- [ ] 1.1 `LabelSyntax` (parse, typing, suggest, token; port of the Linux rule); unit tests incl. e-mail addresses, a lone `@`, quoted names, unknown names, a title that is only labels
- [ ] 1.2 `labels` and `task_labels` tables, database version 4 with an auto-migration, commit the generated `4.json`; `TaskRepository.capture` parses labels, stores them and queues them; verify with repository tests and a version-3 upgrade test
- [ ] 1.3 API `labels()` (paged) and `addLabel`; sync pulls labels (keeping them on 401/403) and task labels, moves task labels with a new id, cleans orphans, and pushes labels after a create (resolve with one refetch, add, already-added counts as done, deleted ones skipped with a notice, missing permissions dropped with a notice); verify with fake-server tests

## 2. Interface

- [ ] 2.1 Label chips on task rows, coloured like project tiles, dimmed on struck-through rows; verify with list-model tests
- [ ] 2.2 Label suggestions under the capture box and the share dialog's title field: filter, tap to complete with the cursor after the name, keyboard stays up, send captures
- [ ] 2.3 Setup screen permission list and README; verify the CI build and tests pass
- [ ] 2.4 Verify on device against the real server: suggestions and completion, an existing label online and offline, an unknown `@name` staying in the title, chips in light and dark theme, a token without label permissions, upgrade from 0.6.0 keeping data

## Workflow follow-up

- After the on-device check, sync the specs into `openspec/specs/` and archive with `openspec archive capture-labels --skip-specs`.
