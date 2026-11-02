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
   | projects | `read_all`; `create` for subprojects; `update` for colours; `read_one`, `update` for shopping settings |
   | other | `user` |
   | labels | `read_all` (optional, for `@labels`) |
   | tasks_labels | `create`, `delete` (optional, for `@labels` and `@doing`) |

   `other → user` is only used to read your *default project* (where new tasks go). Without it the app falls back to your first project. Without the label permissions tasks are still created, and the app tells you when their labels couldn't be added.
2. Open doit, enter the server address (e.g. `todo.example.com`) and paste the token.

On the main screen, and when something is shared into doit, new tasks go to the project titled **Inbox**. Inside a project they go to that project. A `#project` in the title overrides either. If there is no Inbox, they go to the default project set in Vikunja under **Settings → General**, or the first project.

## Label screens

Configure up to three **Label screens** from the home menu, plus a separate label to open by swiping down at the top of a list. Pulling down no longer triggers a refresh; syncing remains automatic. Swipe right from home to move through them, and left to move back; unconfigured or deleted labels are skipped. Each screen shows matching tasks across projects. The previous context label is kept as screen 1, and the **Home** button returns directly to the main view from a label or project screen.

## Using it

- Long-press the app icon and choose **Add task** to open the home capture box. New tasks go to **Inbox** unless you specify a `#project`.
- If an older pinned **Add task** icon says “App isn't installed”, remove it, open the updated app once, then long-press its app icon and drag a new **Add task** shortcut onto the home screen.
- Share text or a link from another app and choose **Add to doit** to review and save it as a task. A shared page title becomes the task title, and its link becomes the description.
- Choose **Menu → Next actions label** on the main screen to put tasks with that label first in lists and the widget. The default is `@next` when it exists; choose **None** to disable this. The choice belongs to this device and does not change settings on other devices. Within each group, the usual date order applies.
- The main view shows large top-level project tiles only. Tiles take their project's Vikunja colour, a little lighter. Tap a tile to see that project's tasks, with smaller subproject tiles below; back returns to the parent. The open project screen uses that same lightened colour for the whole page, including the task list. A project without a colour keeps the normal background.
- Add labels with **`@`**: typing `@` in the capture box, the edit sheet, or the share dialog's title shows your labels, narrowing as you type; tap one to complete it. `@"two words"` works for names with spaces. Sending only `@errand` or `@"15 min"` opens that label's tasks instead of creating one. The `@label` words are taken out of the title, and an `@name` that isn't one of your labels stays in the title as plain text (doit never creates labels). On an existing task, recognized labels are added to the ones it already has. Labels show as small coloured chips under the task's title. This is the same `@` syntax as doit for Linux.
- Pick a project with **`#`** in those same fields: type `#`, narrow the suggestions, and tap a project to complete its name. For example, `Buy lamp #"Home office" @errand` creates **Buy lamp** in **Home office** with the **errand** label. Sending only `#Work` or `#"Home office"` opens that project, parents included, instead of creating a task. Quotes are only needed when the name contains a space. The same tokens in the edit sheet move the task and add labels. Projects and subprojects from the last sync are available offline; archived projects are excluded. Unknown `#names` stay in the title. If you name several known projects, the last one wins. When projects share a name, the first active one in the cached project order is used and suggested. `C#`, URL fragments, `+` and date words stay as typed, and shared descriptions are never parsed.
- To add a subproject, open its parent and choose **Menu → Create subproject**, enter a name, and confirm. The new tile appears beneath the parent’s tasks. Creation needs a connection and the token’s `projects → create` permission; it is not queued offline. Inbox cannot have a subproject: that is the default project, and also any project still titled Inbox. The menu item is hidden there.
- To archive the open project, choose **Menu → Archive project** and confirm. It leaves the app immediately, including its tasks, and Vikunja is updated on the next sync. Subprojects are not archived; a child of an archived project shows on its own. Restoring it is done in Vikunja. The token needs permission to update projects. A refused archive is reported and the project comes back on the next sync.
- To rename the open project, choose **Menu → Project settings**, edit the name, and tap OK. A blank name is ignored. The new name shows immediately and is sent to Vikunja on the next sync. The token needs permission to update projects. A refused rename is reported and the previous name comes back.
- To make a project a shopping list, open it and choose **Menu → Project settings → Shopping list**. The cart appears on its tile and header. This setting syncs through the project description, and subprojects have their own settings. Shopping items cannot be marked `doing`; existing `doing` labels are removed when synced. Add a free-text quantity at the end, for example `Milk [one carton]`; it appears separately in the list while the full title stays in Vikunja. Put `#project` and `@label` before the quantity, whose contents remain literal.
- If an existing label is named **doing** (ignoring case), use the play/stop button on a task to start or stop working. The app never creates this label. A doing task stays at the top of the app; tap it to edit it. When several tasks are doing, the header shows one and the number of others; tap it to open the doing list across projects. A temporary **Review** notification also appears. Completing a task stops work; undo restores it. Changes work offline and sync automatically. To stop working, the token needs `tasks_labels → delete`.
- Tap a task's circle to complete it. It stays struck through until you leave the screen; tap the circle again to undo.
- While the capture box, edit sheet, or share title is focused, **`@`**, **`#`**, and **`!`** keys sit above the keyboard. The first two insert a label or project marker. The capture box also has **`/`**, which inserts a slash the same way. A leading slash searches tasks, projects, and labels instead of creating a task; type the query after it, or tap a result. Tapping **`!`**, or typing it at the start of a word, opens a date picker. In capture and sharing, the chosen date is shown until you send or clear it; it is saved with the task even offline. In the edit sheet, the chosen date is saved immediately. A new due date uses noon in your local time. Exclamation marks inside words and pasted text stay literal.
- A task's Vikunja description is shown under its title, up to four lines. In the edit sheet it is a **Details** field. It saves when you leave the field or close the sheet, including clearing it. Words in the description are not links. Each web address gets a globe at the side of the row; tap the globe to open that address.
- Tap a task to edit its title, date and project. The project name in the edit sheet opens that project; a chevron next to it still changes the project. In the project picker, subprojects sit directly beneath their parent, indented for each nesting level. Changes save as you make them; the title saves when you leave its field. `@` and `#` in that title add labels and move the task. **Promote to project** in the edit sheet creates a subproject under the task's current project and copies its description. The task is removed only after that copy is confirmed. Its due date and labels are not kept. This is never done automatically. It needs a connection and permission to create projects.
- In a project, choose **Color** from the three-dot menu and pick one of the eight colours, or no colour. The page and the tile change immediately, and the colour is saved to Vikunja on the next sync. The token needs permission to update projects; without it, doit tells you and the previous colour comes back.
- The app syncs automatically when opened, after local changes, and periodically in the background when a network is available. A short sync note, such as changes waiting or a failed sync, sits at the bottom of the screen so the list does not jump. The main menu's **About & server** shows the latest sync status and any server error details; its menu label marks sync issues. It also shows the app version and configured server address without showing the token. Inside a project the menu has **Color** and **Project settings**.

## Shopping lists

Projects whose description contains **`doit:type=shopping` on its own line** are shopping lists. Add or remove the line in Vikunja's project editor or with the Shopping list switch in Doit Android or Linux. The marker is shared through the server, cached for offline use, and applies only to that project's tasks; subprojects are independent. A cart marks shopping tiles and headers. The switch preserves other description text and formatting. Changes made offline are queued, and previous local shopping choices migrate into queued marker updates. Already-synced markers survive signing out. Changing the marker requires `projects → read_one` and `update`, plus write access to the project; recognizing it needs no extra permission. A refused update stays queued and is reported in **About & server**.

Shopping items cannot be marked doing: work controls disappear, capture and title edits omit `@doing`, moves and completion undo cannot restore it, and sync removes doing downloaded from another client. Other labels are preserved. Refused cleanup remains queued and is reported as a sync failure. Vikunja's web app can still add the label; Doit removes it on its next sync.

A title such as **`Milk [one carton]`** shows Milk with a separate quantity badge in shopping project and label views. Quantities are free text, with no unit or number interpretation. Put capture tokens before the final brackets: `Milk #Groceries @errand [one carton]`. `@` and `#` inside the quantity stay literal. Editing and syncing retain the entire title. Long badges are abbreviated; Edit shows the full text. Ordinary projects display titles unchanged.

## Home label shortcuts

Open **Menu → Recent tasks** to see recently completed and deleted tasks. Each section starts with five tasks; choose **Show five more** to browse further. Tap a task to reopen it or restore a deletion. Deleted tasks are recreated from locally saved details, so server-only details such as attachments may not be recovered. Completed tasks include recent server results when connected as well as completions recorded on this phone. Deleted tasks are recorded on this device from the time this feature is installed; the app keeps up to 100 local entries of each kind. Signing out clears the local history.

The home screen lists cached labels other than `@doing` beneath the project tiles as colored `@name` shortcuts. Tap one to see its tasks across projects, even offline. Projects and labels scroll together; labels without tasks are included. If a task loses the current label, it stays in that label view until you navigate away.

## Installing

Release APKs are attached to this repository's GitHub Releases as `doit-<version>.apk`. To get updates automatically, add the repo to [Obtainium](https://github.com/ImranR98/Obtainium). Because the repo is private, Obtainium needs a GitHub personal access token with read access to it.

Every push to `main` also produces a debug build (`doit debug`, package `com.marinecircuits.doit.debug`) as an artifact of the **Build** workflow run. It installs next to the release build.

## Development

- There is no local Android toolchain requirement: the **Build** workflow compiles, runs the unit tests and uploads the debug APK. Locally, `./gradlew testDebugUnitTest assembleDebug` works with JDK 21 and the Android SDK.
- Changes are planned with [OpenSpec](https://github.com/Fission-AI/OpenSpec): proposals live in `openspec/changes/`, accepted behaviour in `openspec/specs/`. In Claude Code use `/opsx:propose`, `/opsx:apply` and `/opsx:archive`.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org); the version is derived from them.
- **Merge pull requests with a regular merge commit** (the default “Create a merge commit” option), the same way previous merges in this repository were done. Do not squash or rebase. This keeps the individual Conventional Commits from the branch in history so Commitizen can see them and produce the next version bump.
- **Wait for the pull request’s workflows to succeed before merging.** Do not merge while Build (or any other required workflow on the PR) is still running or has failed.

## Releasing

1. Run the **Prepare Release** workflow on `main`; enable *dry run* to just see the next version. It bumps the version and `CHANGELOG.md` with Commitizen and opens a `Release X.Y.Z` pull request.
2. Review and merge that pull request. **Prepare Release** starts **Build** on the release branch immediately: that run tests and produces the signed APK (R8 is the slow part, about three minutes). **Publish Release** reuses that APK, checks the signature, tags the merge commit `X.Y.Z` and publishes the GitHub release. It does not compile or test again.

Release APKs are signed with the shared marinecircuits Android key from the organization secrets `ANDROID_SIGNING_KEYSTORE_BASE64`, `ANDROID_SIGNING_STORE_PASSWORD`, `ANDROID_SIGNING_KEY_ALIAS` and `ANDROID_SIGNING_KEY_PASSWORD`. Its certificate SHA-256 fingerprint is
`06:41:57:C9:52:74:C3:6E:68:EE:48:8D:41:02:61:6A:BA:07:4E:84:7C:3F:F2:ED:63:ED:1A:92:C5:40:0B:09`.

The **About → Release notes** view shows the complete release history, available offline.
