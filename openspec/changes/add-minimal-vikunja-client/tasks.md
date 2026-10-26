# Tasks

## 1. Scaffold and continuous build

- [x] 1.1 Add the Gradle 9.7.1 wrapper (jar checked against Gradle's published SHA-256), `settings.gradle.kts`, `gradle/libs.versions.toml`, root and `app` build scripts with the pinned toolchain from design.md, and an empty Compose `MainActivity`; verify `./gradlew assembleDebug` succeeds on GitHub Actions
- [x] 1.2 Configure the debug build type (`.debug` id suffix, label "doit debug") and derive `versionCode` from `doit.versionName`; verify the CI debug APK's badging shows `com.marinecircuits.doit.debug`
- [x] 1.3 Add `.github/workflows/build.yml` (push to `main` and pull requests: unit tests + debug APK artifact `doit-debug-<short sha>`); verify a run uploads the artifact
- [x] 1.4 Add `.gitignore` and a README covering setup, the required token permissions and how releases work; verify the README's permission list matches design.md
- [x] 1.5 Create the private repo `marinecircuits/doit` and push; verify `gh repo view --json visibility` reports PRIVATE and the first build run is green

## 2. Vikunja API v2 client

- [x] 2.1 Add DTOs (task, project, user settings, paginated envelope, problem details) and date conversion treating `0001-01-01T00:00:00Z` as no date; verify with unit tests for parsing, offsets and zero dates
- [x] 2.2 Implement server URL normalisation (scheme default, trailing slash, `/api/v1`/`/api/v2` suffix, reject `http://`); verify with unit tests for every scenario in the server-connection spec
- [x] 2.3 Implement the HTTP client (info, user, paged projects, paged open tasks, create, merge-patch update) with typed errors carrying status and problem detail; verify with MockWebServer tests for paging, PATCH content type, null-clearing a due date and 401/403/422 mapping

## 3. Local storage and credentials

- [x] 3.1 Add the Room database (`tasks`, `projects`, `pending_changes`) with DAOs and exported schema; verify with Robolectric DAO tests for the open-task query and the mark-and-sweep delete
- [x] 3.2 Add the settings DataStore with Keystore-encrypted token, backup and data-extraction rules excluding all data, and a network security config forbidding cleartext; verify the merged manifest in the CI build has `allowBackup=false` and the config attached

## 4. Offline sync

- [x] 4.1 Implement merging of pending changes per task (field-wise last-wins, revision bump); verify with unit tests including complete-then-undo collapsing into one update
- [x] 4.2 Implement repository writes (capture with temporary id, complete, undo, reschedule keeping time of day or defaulting to 12:00, edit) that update Room and the queue atomically; verify with Robolectric tests per action
- [x] 4.3 Implement the sync engine: ordered push with revision check and temporary-id remapping, error classification, then paged pull with mark-and-sweep, all under one mutex; verify with Robolectric + MockWebServer tests for create-then-complete offline, deleted-on-server, 422 rejection, 503 retry and edit-during-push
- [x] 4.4 Add the WorkManager worker, unique one-time `sync-now` job after local changes, 30-minute periodic job and foreground sync; verify with a WorkManager test-helper test that a local change enqueues `sync-now` with a network constraint

## 5. Main screen

- [x] 5.1 Add the Material You theme (dynamic colour on Android 12+, light/dark from system), placeholder adaptive icon and string resources; verify the CI APK builds with the icon and no hard-coded UI strings in code
- [x] 5.2 Build the setup screen with validation and the exact error messages from the server-connection spec; verify with unit tests of the setup view model against MockWebServer
- [x] 5.3 Build the task list (ordering, due labels, project tag, status line, empty state, pull-to-refresh); verify with unit tests for ordering and label formatting covering the task-list spec scenarios
- [ ] 5.4 Add the capture box (send action, blank ignored, keeps focus, scrolls to the new task); verify on device against the task-capture scenarios
- [ ] 5.5 Add the swipe row (35% threshold, snap back, haptic) with complete on the circle or a right swipe and the 4-second Undo snackbar; verify on device against the task-actions completion and undo scenarios
- [ ] 5.6 Add the quick reschedule sheet (Today, Tomorrow, Next week, Pick a date…, No date) opened by a left swipe; verify on device, and with unit tests for the date choices
- [ ] 5.7 Add the light edit sheet (title, date with clear, project list; Save disabled for a blank title); verify on device against the edit scenarios
- [x] 5.8 Add the overflow menu (sync now, sign out with pending-changes confirmation) and the re-enter-token flow on 401; verify with view-model unit tests

## 6. Share target

- [ ] 6.1 Add `ShareActivity` (translucent, dialog, signed-out message) with the `text/plain` share intent filter, and a pure function proposing title and description from subject/text; verify with unit tests for the share scenarios and on device from a browser

## 7. Home-screen widget

- [ ] 7.1 Add the Glance widget (ordered list with due labels, checkbox complete action, + button focusing capture, signed-out message) and call `updateAll` after every write and sync; verify on device against the home-widget scenarios

## 8. Release workflows

- [x] 8.1 Add `.cz.toml` (version 0.0.0, tags without prefix, `version_files = ["gradle.properties:doit.versionName"]`) and `requirements-release.txt`; verify `cz bump --dry-run --files-only --changelog` locally proposes 0.1.0
- [ ] 8.2 Add `prepare-release.yml` mirroring weekly-planner (dry run, files-only bump, unit tests, `release/<version>` PR with `release` label); verify a dry-run dispatch on GitHub prints 0.1.0
- [ ] 8.3 Add release signing from `ANDROID_SIGNING_*` environment variables, with a check that fails the release build without them, and `publish-release.yml` (validate, test, build signed APK, tag, release with notes and `doit-<version>.apk`, idempotent); verify by publishing 0.1.0 and checking the APK signer's SHA-256 is `06:41:57:C9:…:0B:09`

## 9. End-to-end checks

- [ ] 9.1 Install the release APK, connect to the real server and walk through every spec scenario that needs a device (offline capture/complete, concurrent web edit, widget, share); record any gaps as follow-up changes

## Workflow follow-up

- Archive the change with `/opsx:archive` once 0.1.0 is verified on the phone.
