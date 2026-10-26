# Design

## Context

Greenfield repo; see proposal.md for motivation and specs/ for behaviour. Constraints that shape the approach:

- Target server is Vikunja ≥ 2.4 with API v2. v2 is generated with Huma: list endpoints return `{items,total,page,per_page,total_pages}`, create is `POST` (201), update is `PUT`, and every GET+PUT resource gets a synthesised `PATCH` (Huma AutoPatch: server-side GET → JSON merge patch → PUT). Request bodies are validated, but the server sets `FieldsOptionalByDefault = true`, so partial create bodies are accepted.
- API token scopes are shared between v1 and v2 (`tasks.read_all` covers `GET /api/v2/tasks`, `tasks.update` covers `PUT` and `PATCH /api/v2/tasks/{id}`). `GET /api/v2/user` falls under `other.user`.
- Page size is capped by the server (`max_items_per_page`, default 50), so lists must be paged.
- Unset dates are serialised as `0001-01-01T00:00:00Z`.
- There is no JDK or Android SDK on the developer machine, so every compile happens on GitHub Actions. Each compile error costs a CI round trip, which pushes the design towards stable, well-known APIs.

API surface used (all under `/api/v2`):

| Purpose | Call | Token scope |
|---|---|---|
| Check server | `GET /info` (public) | none |
| Default project | `GET /user` → `settings.default_project_id` | `other.user` |
| Projects | `GET /projects?page=N` | `projects.read_all` |
| Open tasks | `GET /tasks?filter=done = false&page=N` | `tasks.read_all` |
| Create | `POST /projects/{id}/tasks?format=markdown` | `tasks.create` |
| Update | `PATCH /tasks/{id}` (`application/merge-patch+json`) | `tasks.update` |

## Goals / Non-Goals

**Goals:**
- A small codebase (single Gradle module, no DI framework) that one person can keep in their head.
- Offline-first with a correctness story for the edge cases spelled out in the offline-sync spec.
- A toolchain combination known to build today.

**Non-Goals:**
- Reusing code with an iOS app (no Kotlin Multiplatform).
- Delta sync by `updated` timestamp; a full refresh of open tasks is cheap at personal scale.
- Exactly-once creation under connection loss (see Risks).

## Decisions

**Toolchain pinned to Now in Android's current set.** AGP 9.3.2 (built-in Kotlin, no `kotlin-android` plugin), Kotlin 2.3.0, KSP 2.3.4, Gradle 9.7.1, compileSdk 37, targetSdk 36. minSdk 26 (Android 8.0), so `java.time` is available without desugaring and adaptive icons need no fallbacks. *Alternative:* newest everything (AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12). Rejected for the first build because each incompatibility costs a CI round trip; the versions can be bumped later.

**Single activity, state-driven screens.** `MainActivity` shows Setup or Tasks depending on whether credentials exist, with no navigation library. The light edit sheet and quick-date sheet are Material 3 `ModalBottomSheet`s. Sharing uses a separate translucent `ShareActivity` that shows a dialog over the source app.

**Manual dependency container.** An `AppContainer` built in `Application.onCreate` holds the database, settings, API client, repository and sync engine. *Alternative:* Hilt, which adds kapt/KSP processing and plugin version coupling for a graph of about 6 objects.

**Thin hand-written HTTP client (OkHttp + kotlinx.serialization).** Six calls do not justify Retrofit, and PATCH needs the `application/merge-patch+json` content type anyway. JSON is decoded with `ignoreUnknownKeys` so server additions don't break the app. Errors become a typed exception carrying the HTTP status and the RFC 9457 `title`/`detail`.

**Room as the source of truth.** It has three tables:
- `tasks`: id, title, dueAt (epoch ms or null), projectId, done, createdAt, syncGeneration. Tasks created offline get negative temporary ids.
- `projects`: id, title, archived, position. Ids ≤ 0 (pseudo projects such as Favorites) are ignored.
- `pending_changes`: seq, taskId, kind (`create` | `update`), fields (JSON object), revision.

The UI and the widget only ever read Room, through Flows.

**One pending change per task, merged.** A new local change to a task that already has a pending change merges its fields into that row (later value wins per field) and increments `revision`. Merging keeps the queue short and makes undo before sync cancel out naturally: `done=true` then `done=false` merge into one harmless update. The order of creates relative to updates is kept by `seq`.

**Push with revision check.** For each pending change, in `seq` order:
1. Send it. A create is a POST with the merged fields; an update is a PATCH with exactly the merged fields.
2. In one transaction, re-read the row. If `revision` is unchanged, delete it. If the user changed the task meanwhile, keep the row with the new fields. A create that succeeded becomes an update for the real id.
3. A successful create rewrites the temporary id in `tasks` and in `pending_changes` to the server id.
4. The task row is overwritten with the server's response unless a newer local change exists.

A 404 drops the change and the local task. Other 4xx (except 401 and 429) drop the change and record a one-time message for the user. A 401, a 429, a 5xx or an IO error stops the push and keeps the queue.

**Pull with mark-and-sweep.** The sync fetches every page of projects and open tasks and stamps every task it upserts with a new `syncGeneration`. Tasks that have pending changes are skipped. Afterwards it deletes `id > 0 AND syncGeneration != current AND id NOT IN pending`. This avoids a `NOT IN (…all ids…)` query, which would hit SQLite's 999-parameter limit on older Android versions.

**Sync scheduling.** `SyncEngine.sync()` is guarded by a process-wide `Mutex`, so a second request waits for the first. Pull-to-refresh and app foreground call it directly, which gives an immediate spinner and error message. Local changes enqueue a unique one-time WorkManager job (`sync-now`, `APPEND_OR_REPLACE`, network-connected constraint). A unique periodic job runs every 30 minutes. Expedited work is avoided, because before Android 12 it needs a foreground-service notification.

**Dates.** Due dates are stored as instants. "Today" and the overdue logic use the device time zone. Labels and grouping are date-based, so a task due today at 12:00 is not shown as overdue at 13:00. A newly dated task gets 12:00 local time; an existing time of day is kept (task-actions spec). On the wire, "no date" is omitted on create and sent as JSON `null` in a merge patch. Removing the key makes the server's PUT see a zero time, which clears the date.

**Undo.** The UI keeps the task's previous `dueAt` in the snackbar callback. Undo queues an update with `done=false` and the original `due_date`, which also covers repeating tasks that the server already moved forward.

**Credentials.** An AES-256-GCM key lives in the Android Keystore. The token is stored encrypted, with its IV, in a DataStore together with the server URL and the cached default-project id. The manifest sets `allowBackup=false`, and `dataExtractionRules` excludes everything. A network security config forbids cleartext traffic.

**Swipe gestures without `SwipeToDismissBox`.** Rows use a small custom swipe built from `detectHorizontalDragGestures` and an `Animatable` offset. The threshold is 35% of the row width, and passing it gives haptic feedback. The Material 3 swipe API has changed signature across releases, while the foundation gesture APIs are stable. Icons are local vector drawables instead of the `material-icons` artifact, which is no longer updated.

**Widget (Glance).** `provideGlance` reads the open tasks from Room once per update. A `CheckBox` is bound to an `ActionCallback` that calls the same repository method as the app. Every repository write and every sync ends with `DoitWidget().updateAll(context)`. The + button starts `MainActivity` with an extra that focuses the capture box.

**Build and release.**
- `gradle.properties` holds `doit.versionName`. Commitizen keeps it in step through `version_files`, and Gradle derives `versionCode`.
- The release signing config is created only when `ANDROID_SIGNING_KEYSTORE` (a file path) is set. If it isn't set, a `release`-variant check task fails the build, so an unsigned release can't be produced.
- The workflows mirror `marinecircuits/weekly-planner`: Commitizen pinned in `requirements-release.txt`, tags without a prefix, release PRs labelled `release`, and an idempotent publish step.
- The publish job builds and signs before tagging, so a signing failure leaves no tag or release behind.
- The keystore is PKCS#12. `gradle/actions/setup-gradle` provides caching.

## Risks / Trade-offs

- [No local compiler; every mistake costs a CI round] → pinned, proven toolchain; stable APIs only; small unit-tested pure functions (ordering, labels, URL normalisation, change merging).
- [Duplicate task if the connection drops after the server accepted a create but before the response arrived] → accepted for a personal app; it is rare and the duplicate is visible and easy to complete. A future change could dedupe by checking for an identical recent title before retrying.
- [Full refresh of all open tasks on every sync] → fine at hundreds of tasks; revisit with `updated`-based filters if it becomes slow.
- [Server-side AutoPatch is GET+PUT, so a write landing between them on the server can be overwritten] → the window is milliseconds; accepted.
- [Keystore generated with OpenSSL 3 (AES-256/PBKDF2 PKCS#12)] → readable by JDK 17+, which CI uses; the first release build confirms it.
- [Token expiry: API tokens can have an expiry date] → a 401 keeps data and asks for a new token (server-connection spec).
- [Glance and WorkManager background limits on aggressive OEM ROMs] → periodic sync is best-effort; foreground and pull-to-refresh always sync.

## Migration Plan

This is the first version, so there is nothing to migrate. Rollout:
1. Merge to `main` and install the CI debug APK.
2. Once it works against the real server, run Prepare Release to produce `0.1.0`.
3. Merge the release PR and point Obtainium at the private repo's releases.

Rollback means installing the previous release APK. The same signing key allows downgrade by reinstalling.

## Open Questions

- App icon: this change ships a simple placeholder (a check mark in a circle). It can be redesigned later without affecting behaviour.
