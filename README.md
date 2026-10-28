# doit

A deliberately small Android app for a self-hosted [Vikunja](https://vikunja.io): one capture box, one list of everything that's still open, project tiles to look at a single project, and simple controls to finish or reschedule. It works offline and syncs when it can.

## Requirements

- Vikunja **2.4 or newer**, reachable over **HTTPS** (the app uses API v2 only).
- Android 8.0 or newer.

## Connecting

1. In Vikunja's web UI open **Settings → API Tokens** and create a token with these permissions:

   | Group | Permissions |
   |---|---|
   | tasks | `read_all`, `create`, `update` |
   | projects | `read_all`, `update` (`update` is only for setting a project colour) |
   | other | `user` |
   | labels | `read_all` (optional, for `@labels`) |
   | tasks_labels | `create`, `delete` (optional, for `@labels` and `@doing`) |

   `other → user` is only used to read your *default project* (where new tasks go). Without it the app falls back to your first project. Without the label permissions tasks are still created, and the app tells you when their labels couldn't be added.
2. Open doit, enter the server address (e.g. `todo.example.com`) and paste the token.

New tasks go to the default project set in Vikunja under **Settings → General**, or to the project whose screen is open. A `#project` in the title overrides that destination.

## Label screens

Configure up to three **Label screens** from the home menu. Swipe right from home to move through them, and left to move back; unconfigured or deleted labels are skipped. Each screen shows matching tasks across projects. The previous context label is kept as screen 1, and the **Home** button returns directly to the main view from a label or project screen.

## Using it

- The main view shows large top-level project tiles only. Tiles take their project's Vikunja colour, a little lighter. Tap a tile to see that project's tasks, with smaller subproject tiles below; back returns to the parent. The open project screen uses that same lightened colour for the whole page, including the task list. A project without a colour keeps the normal background.
- Add labels with **`@`**: typing `@` in the capture box, the edit sheet, or the share dialog's title shows your labels, narrowing as you type; tap one to complete it. `@"two words"` works for names with spaces. Sending only `@errand` or `@"15 min"` opens that label's tasks instead of creating one. The `@label` words are taken out of the title, and an `@name` that isn't one of your labels stays in the title as plain text (doit never creates labels). On an existing task, recognized labels are added to the ones it already has. Labels show as small coloured chips under the task's title. This is the same `@` syntax as doit for Linux.
- Pick a project with **`#`** in those same fields: type `#`, narrow the suggestions, and tap a project to complete its name. For example, `Buy lamp #"Home office" @errand` creates **Buy lamp** in **Home office** with the **errand** label. Sending only `#Work` or `#"Home office"` opens that project, parents included, instead of creating a task. Quotes are only needed when the name contains a space. The same tokens in the edit sheet move the task and add labels. Projects and subprojects from the last sync are available offline; archived projects are excluded. Unknown `#names` stay in the title. If you name several known projects, the last one wins. When projects share a name, the first active one in the cached project order is used and suggested. `C#`, URL fragments, `+` and date words stay as typed, and shared descriptions are never parsed.
- To make a project a shopping list, open it and choose **Menu → Project settings → Shopping list**. The cart appears on its tile and header. This setting is local to this device, and subprojects have their own settings. Shopping items cannot be marked `doing`; existing `doing` labels are removed when synced. Add a free-text quantity at the end, for example `Milk [one carton]`; it appears separately in the list while the full title stays in Vikunja. Put `#project` and `@label` before the quantity, whose contents remain literal.
- If an existing label is named **doing** (ignoring case), use the play/stop button on a task or its edit sheet to start or stop working. The app never creates this label. A doing task stays at the top of the app; tap it to edit it. When several tasks are doing, the header shows one and the number of others; tap it to open the doing list across projects. A temporary **Review** notification also appears. Completing a task stops work; undo restores it. Changes work offline and sync automatically. To stop working, the token needs `tasks_labels → delete`.
- Tap a task's circle to complete it. It stays struck through until you leave the screen; tap the circle again to undo.
- While the capture box, edit sheet, or share title is focused, **`@`**, **`#`**, and **`!`** keys sit above the keyboard. The first two insert a label or project marker. Tapping **`!`**, or typing it at the start of a word, opens a date picker. In capture and sharing, the chosen date is shown until you send or clear it; it is saved with the task even offline. In the edit sheet, the chosen date is saved immediately. A new due date uses noon in your local time. Exclamation marks inside words and pasted text stay literal.
- Tap a task to edit its title, date and project. Changes save as you make them; the title saves when you leave its field. `@` and `#` in that title add labels and move the task.
- In a project, choose **Color** from the three-dot menu and pick one of the eight colours, or no colour. The page and the tile change immediately, and the colour is saved to Vikunja on the next sync. The token needs permission to update projects; without it, doit tells you and the previous colour comes back.
- The app syncs automatically when opened, after local changes, and periodically in the background when a network is available. The main menu's **About & server** shows the latest sync status and any server error details; its menu label marks sync issues. It also shows the app version and configured server address without showing the token. Inside a project the menu only has **Color**.

## Home label shortcuts

Open **Menu → Recent tasks** to see recently completed and deleted tasks. Each section starts with five tasks; choose **Show five more** to browse further. Tap a task to reopen it or restore a deletion. Deleted tasks are recreated from locally saved details, so server-only details such as attachments may not be recovered. Completed tasks include recent server results when connected as well as completions recorded on this phone. Deleted tasks are recorded on this device from the time this feature is installed; the app keeps up to 100 local entries of each kind. Signing out clears the local history.

The home screen lists all cached labels beneath the project tiles as colored `@name` shortcuts. Tap one to see its tasks across projects, even offline. Projects and labels scroll together; labels without tasks are included. If a task loses the current label, it stays in that label view until you navigate away.

## Installing

Release APKs are attached to this repository's GitHub Releases as `doit-<version>.apk`. To get updates automatically, add the repo to [Obtainium](https://github.com/ImranR98/Obtainium). Because the repo is private, Obtainium needs a GitHub personal access token with read access to it.

Every push to `main` also produces a debug build (`doit debug`, package `com.marinecircuits.doit.debug`) as an artifact of the **Build** workflow run. It installs next to the release build.

## Development

- There is no local Android toolchain requirement: the **Build** workflow compiles, runs the unit tests and uploads the debug APK. Locally, `./gradlew testDebugUnitTest assembleDebug` works with JDK 21 and the Android SDK.
- Changes are planned with [OpenSpec](https://github.com/Fission-AI/OpenSpec): proposals live in `openspec/changes/`, accepted behaviour in `openspec/specs/`. In Claude Code use `/opsx:propose`, `/opsx:apply` and `/opsx:archive`.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org); the version is derived from them.

## Releasing

1. Run the **Prepare Release** workflow on `main`; enable *dry run* to just see the next version. It bumps the version and `CHANGELOG.md` with Commitizen and opens a `Release X.Y.Z` pull request.
2. Review and merge that pull request. **Prepare Release** starts **Build** on the release branch immediately: that run tests and produces the signed APK (R8 is the slow part, about three minutes). **Publish Release** reuses that APK, checks the signature, tags the merge commit `X.Y.Z` and publishes the GitHub release. It does not compile or test again.

Release APKs are signed with the shared marinecircuits Android key from the organization secrets `ANDROID_SIGNING_KEYSTORE_BASE64`, `ANDROID_SIGNING_STORE_PASSWORD`, `ANDROID_SIGNING_KEY_ALIAS` and `ANDROID_SIGNING_KEY_PASSWORD`. Its certificate SHA-256 fingerprint is
`06:41:57:C9:52:74:C3:6E:68:EE:48:8D:41:02:61:6A:BA:07:4E:84:7C:3F:F2:ED:63:ED:1A:92:C5:40:0B:09`.
