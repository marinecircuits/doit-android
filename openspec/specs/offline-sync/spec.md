# offline-sync Specification

## Purpose

Keeps the app fully usable without a connection and reconciles the device with the Vikunja server without losing or overwriting either side's changes unnecessarily.

## Requirements

### Requirement: Local data survives restarts
The app SHALL keep the last synced tasks and projects, plus all unsent changes, on the device, and SHALL show them immediately on launch without waiting for the network.

#### Scenario: Launch offline
- **WHEN** the user opens the app with no connection
- **THEN** the list shows the tasks from the last sync plus any local changes

### Requirement: Ordered change queue
Every local change (create, complete, reopen, reschedule, edit) SHALL be recorded as a pending change and sent to the server in the order it was made. A pending change SHALL be removed only after the server has accepted it.

#### Scenario: Create then complete offline
- **WHEN** the user captures a task and then completes it, both offline
- **THEN** on reconnect the server first creates the task and then marks that same task done

### Requirement: Field-level updates
Updates to existing tasks SHALL send only the fields the user changed, so changes made elsewhere to other fields of the same task are preserved.

#### Scenario: Concurrent edits to different fields
- **WHEN** the user reschedules a task offline while the title of the same task is edited in the web UI
- **THEN** after sync the server has both the new due date and the new title

### Requirement: Local changes win on the same field
When the same field was changed both locally and on the server since the last sync, the local value SHALL be sent and SHALL be the final value.

#### Scenario: Both sides reschedule
- **WHEN** a task is moved to Friday in the web UI and to Monday in the app while offline
- **THEN** after sync the task is due Monday on both sides

### Requirement: Server changes are applied
A sync SHALL first send pending changes and then replace local data with the server's current open tasks and projects, except for tasks that still have unsent changes. Tasks that are done, deleted or no longer visible on the server SHALL be removed locally.

#### Scenario: Task deleted in the web UI
- **WHEN** a task is deleted on the server and has no pending local change
- **THEN** it disappears from the app after the next sync

#### Scenario: Pending change for a deleted task
- **WHEN** the user reschedules a task offline that was meanwhile deleted on the server
- **THEN** the pending change is discarded on sync and the task disappears

### Requirement: Sync triggers
The app SHALL sync when it comes to the foreground, on pull-to-refresh, shortly after any local change while a network is available, and in the background about every 30 minutes while a network is available.

#### Scenario: Change while online
- **WHEN** the user completes a task with a working connection
- **THEN** the completion reaches the server within a few seconds without further user action

### Requirement: Failure handling
Network errors, timeouts and server errors (5xx, 429) SHALL leave pending changes in place to be retried later with backoff. A change the server rejects as invalid (other 4xx except 401) SHALL be dropped, and the user SHALL be told once which task it affected.

#### Scenario: Server down
- **WHEN** a sync fails because the server returns 503
- **THEN** all pending changes are kept and retried later

#### Scenario: Rejected change
- **WHEN** the server answers 422 to an update
- **THEN** that change is discarded, the task is refreshed from the server, and a message names the task

### Requirement: Single sync at a time
At most one sync SHALL run at a time; a sync requested while one is running SHALL run after it finishes, so no change is sent twice in parallel.

#### Scenario: Pull to refresh during background sync
- **WHEN** the user pulls to refresh while a background sync is running
- **THEN** a second sync starts after the first finishes
