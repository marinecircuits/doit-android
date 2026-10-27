# Spec Delta

## ADDED Requirements

### Requirement: Labels sync
A sync SHALL fetch the user's labels and each open task's labels. If the token may not read labels, the app SHALL keep the labels it knows and the sync SHALL still succeed.

#### Scenario: Label added in the web UI
- **WHEN** the user adds the label "errand" to a task in the web UI
- **THEN** after the next sync the task's row shows the "errand" chip

#### Scenario: Token without label read permission
- **WHEN** the token may not read labels
- **THEN** syncs still succeed and the status line shows no permission problem

### Requirement: Labels added after the create
Labels from a capture SHALL be added after the task has been created; a label already on the task counts as added, and one deleted on the server meanwhile SHALL be skipped and named in a message. If the server refuses to add labels because the token lacks permissions, the labels SHALL be dropped, the user told once in a message naming the task, and the sync SHALL continue.

#### Scenario: Labels captured offline
- **WHEN** the user captures "Buy filament @errand" in flight mode and later reconnects
- **THEN** the sync creates the task and then adds the label "errand" to it

#### Scenario: Token without label permissions
- **WHEN** the token may create tasks but not add labels, and the user captures "Buy milk @errand"
- **THEN** the task "Buy milk" is created without the label and a message reads "Couldn't add labels to “Buy milk”: the token isn't allowed to change labels"

#### Scenario: Label deleted meanwhile
- **WHEN** the user captures "Buy milk @errand" offline and "errand" is deleted on the server before the sync
- **THEN** the task "Buy milk" is created without labels and a message reads "Couldn't add labels to “Buy milk”: “errand” no longer exists"
