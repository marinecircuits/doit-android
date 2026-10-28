# Design

Use a confirmation dialog for each history row. Reopen a completed task by queuing only its done state, retaining server fields. Recreate deleted tasks using a locally saved snapshot, because existing Vikunja servers do not provide a general restore endpoint. Queue the original deletion and the replacement independently to avoid races with in-flight sync. Older history has only title and project. Describe the limitations before recreation. Extend the database with backward-compatible snapshot columns, keep up to 100 local records, and show rows in increments of five.
