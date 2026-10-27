# Design

## Parsing and completion

`CaptureSyntax` handles both word-starting prefixes in a single pass. It consumes quoted names as whole tokens, even when unknown or unfinished, so an `@` inside a quoted project name and a `#` inside a quoted label name are never interpreted independently. Recognized names are removed, remaining whitespace is collapsed as before, labels accumulate, and the last recognized project sets the destination.

Suggestions use the active token before the cursor to choose the label or project collection. They keep the existing limit of five and prefix-before-substring ranking. Completing a suggestion uses the active prefix, quotes names containing whitespace, appends a space, and preserves text after the cursor. A closed quote or trailing space ends suggestions. The keyboard send action still captures.

Archived projects are excluded before parsing and suggesting. Subprojects are eligible regardless of the open screen. Duplicate project names, ignoring case, resolve to the first active project in the cached order (`position`, then `title`); suggestions expose that same first project only. Path-qualified or ID-qualified syntax would be a separate feature.

## Destination and sync

The repository resolves `namedProjectId ?: projectId ?: defaultProjectId() ?: 0`. Both capture interfaces already call this method. The existing task row and pending create persist the destination ID, so offline capture uses the existing create endpoint and requires no changes to sync or storage. Shared descriptions remain untouched. An empty title after removing recognized labels and projects returns without writing or scheduling a sync.

The main field uses `TasksUiState.projects`; the share dialog observes `repository.projects`. Both reuse the suggestion overlay/list with project colors and retain focus. Capturing into another project keeps the current screen open.

## Validation

Retain the existing label tests, then test project parsing/completion, mixed prefixes, quoted/unknown/archived names, duplicate names, URL fragments and `C#`, no-title input, destination precedence, share descriptions, view navigation, and an offline queued capture syncing into the named project with its labels. GitHub Actions runs the Android tests and debug build because the developer machine has no Android toolchain.
