# Design

Sync already runs on foreground, after every local write via network-constrained WorkManager, and periodically in the background. Keep those triggers and the existing retry/backoff behavior. Remove the menu entry; pull-to-refresh remains as already specified.

The task view model derives optional server-error details from `SyncEngine.lastResult`, so they correspond to the most recent sync and clear with a later success. Reuse the existing status model to mark About & server for offline, server error, missing permission or rejected token, but not simply for queued changes. The dialog displays the existing status and nonblank server-error details. Token-rejection messaging takes precedence.

The existing in-process sync result is intentionally not described as permanent history; after restart the foreground sync supplies a fresh result.
