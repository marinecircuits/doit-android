# Spec Delta

## ADDED Requirements

### Requirement: Optional working-state controls
When an existing cached label is named doing, ignoring case, each open task SHALL offer Start working or Stop working on its row and edit sheet. The control SHALL update only that label and visibly indicate the active state. The app SHALL NOT create the label. Controls SHALL be absent if the label is unavailable, and existing task gestures SHALL remain available.

#### Scenario: Start and stop
- **WHEN** a task has Errand and the user starts and then stops working
- **THEN** Doing is added and removed while Errand remains

#### Scenario: Missing label
- **WHEN** no cached label is named doing
- **THEN** no working controls or conflict warning are shown

### Requirement: Multiple working tasks
When more than one open task has the doing label, the app SHALL show a temporary overlay with the count and Review. Review SHALL open the doing label's tasks across projects. The warning SHALL appear on a new or changed conflicting count, not on unchanged syncs, and SHALL be dismissed when the conflict is resolved. Starting a task SHALL NOT stop others.

#### Scenario: Review a conflict
- **WHEN** the user starts a second task while another is doing
- **THEN** both remain active and an overlay offers Review without shifting the task list

#### Scenario: Conflict resolves
- **WHEN** the count falls to one or zero
- **THEN** any old conflict warning is dismissed

### Requirement: Completion ends work
Completing a working task SHALL remove doing before completion is sent. Undo SHALL restore its previous working state, even after sync. A repeated task's next occurrence SHALL not remain doing. Completed rows SHALL not show an active control or count toward the warning.

#### Scenario: Undo after sync
- **WHEN** a working task is completed, synced and then undone
- **THEN** it reopens with its doing label restored locally and on the server

### Requirement: Durable working-state synchronization
The app SHALL persist the desired doing membership by label ID with the existing pending task changes, including for unsent captures. It SHALL preserve unrelated labels and fields, the newest intent during in-flight requests, and queued deletion. Transient failures and rejected tokens SHALL retain the intent. Refused label permissions SHALL report the affected task and reconcile with server state. Deleted labels SHALL NOT be recreated.

#### Scenario: Stop during creation
- **WHEN** a task captured with `@doing` is stopped while it is being created
- **THEN** the eventual server task is not doing

#### Scenario: Stop during an add
- **WHEN** a doing add is being sent and the user stops working
- **THEN** the latest intent is sent and the server ends without doing

#### Scenario: Permission refused
- **WHEN** the server refuses removal of doing
- **THEN** the task is named in a notice, the server state is restored, and unrelated edits continue to sync
