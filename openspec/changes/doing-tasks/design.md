# Design

Use the first cached label whose title equals `doing` ignoring case. The row control and edit-sheet action change only that label. Snapshot the task's labels and doing label ID when completing, remove doing locally and queue its removal before completion. Undo uses the snapshot even after the completed task was removed by sync.

Store the desired membership as `_doing: {id, active}` inside the existing per-task pending fields. It is excluded from task create/patch payloads. Push ordinary queued label additions, then the doing intent through the task-label API, then other fields, so removal precedes completion. Retain the intent if it changed while an API call was in flight or a transient error occurred. A refused operation reports the task, clears only the matching intent and pulls the actual server state; a rejected token keeps the intent. An absent association on removal counts as already stopped. When an in-flight label upload finishes, preserve newer labels and queued deletion.

Count open doing tasks globally from cached tasks and labels. A snackbar keyed to the conflicting count overlays content without shifting the list. Review opens the doing label's existing cross-project view; cancellation on a count change clears stale warnings.

The existing task-label schema and queue persist offline and across restarts. An Android device is needed to verify visual styling, touch targets, snackbar interactions and real-server permissions.
