# Proposal

## Why

The official Vikunja Android app is still an alpha Flutter build, and the most complete third-party client (Vicu) is a full Things-style planner. What's missing is a deliberately small, native app for a self-hosted Vikunja: capture a task in a second, see everything that is still open in one list, tick things off, and have it work without a connection.

## What Changes

- New native Android app **doit** (Kotlin + Jetpack Compose, Material 3 with Material You dynamic colour), package `com.marinecircuits.doit`, min Android 8.0.
- One-time setup with server URL + Vikunja API token; the app talks only to `/api/v2`.
- A single screen: capture box on top, then one list of every open task across all projects, ordered overdue → dated → undated.
- Plain-text capture into the user's Vikunja default project.
- Task actions: tap the circle or swipe right to complete (vanishes, Undo snackbar); swipe left for a quick due-date sheet; tap the task for a light edit sheet (title, due date, project).
- Offline-first: a local database is the source of truth, changes are queued and pushed in order, sync runs in the background and on demand.
- Android share target: text or a link shared from any app becomes a new task.
- Home-screen widget listing open tasks with tap-to-complete and a + button that opens capture.
- GitHub Actions: debug APK on every push and pull request; Commitizen-driven Prepare Release / Publish Release workflows that publish a signed release APK on GitHub Releases.

Out of scope for this change: descriptions, labels, priorities, reminders/notifications, kanban or other views, multiple accounts, password/OIDC login, Play Store distribution.

## Capabilities

### New Capabilities
- `server-connection`: connecting the app to a Vikunja server with an API token, validating it, and signing out.
- `task-list`: the single list of open tasks, its ordering, due-date labels and refresh behaviour.
- `task-capture`: creating tasks from the capture box and from Android's share sheet.
- `task-actions`: completing (with undo), rescheduling and lightly editing existing tasks.
- `offline-sync`: local storage, the queue of pending changes, and how local and server state are reconciled.
- `home-widget`: the home-screen widget.
- `build-release`: CI builds, versioning, release preparation/publication and APK signing.

### Modified Capabilities
<!-- none: this is the first change in the project -->

## Impact

- New Android project (Gradle 9.7.1 wrapper, AGP 9.3.2, Kotlin 2.3.0, Room with KSP, WorkManager, Glance, OkHttp, kotlinx.serialization).
- New GitHub Actions workflows: `build.yml`, `prepare-release.yml`, `publish-release.yml`; Commitizen config `.cz.toml`.
- Depends on organization secrets `ANDROID_SIGNING_KEYSTORE_BASE64`, `ANDROID_SIGNING_STORE_PASSWORD`, `ANDROID_SIGNING_KEY_ALIAS`, `ANDROID_SIGNING_KEY_PASSWORD` (added manually by the user).
- Server requirement: Vikunja ≥ 2.4 and an API token with permissions `tasks: read_all, create, update`, `projects: read_all`, `other: user`.
