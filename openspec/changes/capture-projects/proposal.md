# Proposal

## Why

Labels can now be added with `@` during capture. The user wants the same interaction for choosing an existing project with `#`, including when sharing text into the app.

## What Changes

- Show up to five cached, non-archived project suggestions after `#` in the capture box and share title. Include subprojects and rank prefix matches before substring matches, ignoring case.
- Complete a picked project as `#name ` or `#"two words" ` without moving focus from the field.
- Remove recognized project tokens from the captured title and use the last recognized project as the destination, overriding the current/default project. Unknown and archived names stay literal; no project is created.
- Parse projects and labels together so both work in the same title and prefixes inside quoted names remain literal. Only tokens with no remaining task title create nothing.
- Preserve offline behavior: the local task and queued create hold the selected project ID immediately. No new endpoint, permission or database migration is needed.

Out of scope: project creation, editing syntax on existing tasks, project paths or ID syntax to distinguish duplicate names, priority/date syntax, changes to the Linux app, and automatic navigation after capture.

## Capabilities

### Modified Capabilities

- `task-capture`: project syntax, suggestions, and explicit destination precedence alongside `@labels`.

## Impact

- Replace `LabelSyntax` with shared `CaptureSyntax` and reuse the suggestion list for labels and projects.
- Update `TaskRepository.capture`, both capture interfaces, README, and parser/repository/view-model/sync tests.
