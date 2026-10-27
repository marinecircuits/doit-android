# doit

A deliberately small Android app for a self-hosted [Vikunja](https://vikunja.io): one capture box, one list of everything that's still open, project tiles to look at a single project, and swipes to finish or reschedule. It works offline and syncs when it can.

## Requirements

- Vikunja **2.4 or newer**, reachable over **HTTPS** (the app uses API v2 only).
- Android 8.0 or newer.

## Connecting

1. In Vikunja's web UI open **Settings → API Tokens** and create a token with these permissions:

   | Group | Permissions |
   |---|---|
   | tasks | `read_all`, `create`, `update` |
   | projects | `read_all` |
   | other | `user` |
   | labels | `read_all` (optional, for `@labels`) |
   | tasks_labels | `create` (optional, for `@labels`) |

   `other → user` is only used to read your *default project* (where new tasks go). Without it the app falls back to your first project. Without the label permissions tasks are still created, and the app tells you when their labels couldn't be added.
2. Open doit, enter the server address (e.g. `todo.example.com`) and paste the token.

New tasks go to the default project set in Vikunja under **Settings → General**, or to the project whose screen is open. A `#project` in the title overrides that destination.

## Using it

- The main view shows large top-level project tiles only. Tiles take their project's Vikunja colour, a little lighter. Tap a tile to see that project's tasks, with smaller subproject tiles below; back returns to the parent.
- Add labels with **`@`**: typing `@` in the capture box, the edit sheet, or the share dialog's title shows your labels, narrowing as you type; tap one to complete it. `@"two words"` works for names with spaces. The `@label` words are taken out of the title, and an `@name` that isn't one of your labels stays in the title as plain text (doit never creates labels). On an existing task, recognized labels are added to the ones it already has. Labels show as small coloured chips under the task's title. This is the same `@` syntax as doit for Linux.
- Pick a project with **`#`** in those same fields: type `#`, narrow the suggestions, and tap a project to complete its name. For example, `Buy lamp #"Home office" @errand` creates **Buy lamp** in **Home office** with the **errand** label. The same tokens in the edit sheet move the task and add labels. Projects and subprojects from the last sync are available offline; archived projects are excluded. Unknown `#names` stay in the title. If you name several known projects, the last one wins. When projects share a name, the first active one in the cached project order is used and suggested. `C#`, URL fragments, `+`, `!` and dates stay as typed, and shared descriptions are never parsed.
- Tap a task's circle or swipe it right to complete it. It stays struck through until you leave the screen; tap the circle again to undo.
- While the capture box, edit sheet, or share title is focused, **`@`** and **`#`** keys sit above the keyboard. Tapping one inserts that sign at the cursor without closing the keyboard.
- Swipe left to reschedule, or tap a task to edit its title, date and project. Changes save as you make them; the title saves when you leave its field. `@` and `#` in that title add labels and move the task.
- Choose **About doit** from the three-dot menu to see the installed app version.

## Installing

Release APKs are attached to this repository's GitHub Releases as `doit-<version>.apk`. To get updates automatically, add the repo to [Obtainium](https://github.com/ImranR98/Obtainium). Because the repo is private, Obtainium needs a GitHub personal access token with read access to it.

Every push to `main` also produces a debug build (`doit debug`, package `com.marinecircuits.doit.debug`) as an artifact of the **Build** workflow run. It installs next to the release build.

## Development

- There is no local Android toolchain requirement: the **Build** workflow compiles, runs the unit tests and uploads the debug APK. Locally, `./gradlew testDebugUnitTest assembleDebug` works with JDK 21 and the Android SDK.
- Changes are planned with [OpenSpec](https://github.com/Fission-AI/OpenSpec): proposals live in `openspec/changes/`, accepted behaviour in `openspec/specs/`. In Claude Code use `/opsx:propose`, `/opsx:apply` and `/opsx:archive`.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org); the version is derived from them.

## Releasing

1. Run the **Prepare Release** workflow on `main`; enable *dry run* to just see the next version. It bumps the version and `CHANGELOG.md` with Commitizen and opens a `Release X.Y.Z` pull request.
2. Review and merge that pull request. **Publish Release** then builds the signed APK, tags the merge commit `X.Y.Z` and publishes the GitHub release.

Release APKs are signed with the shared marinecircuits Android key from the organization secrets `ANDROID_SIGNING_KEYSTORE_BASE64`, `ANDROID_SIGNING_STORE_PASSWORD`, `ANDROID_SIGNING_KEY_ALIAS` and `ANDROID_SIGNING_KEY_PASSWORD`. Its certificate SHA-256 fingerprint is
`06:41:57:C9:52:74:C3:6E:68:EE:48:8D:41:02:61:6A:BA:07:4E:84:7C:3F:F2:ED:63:ED:1A:92:C5:40:0B:09`.
