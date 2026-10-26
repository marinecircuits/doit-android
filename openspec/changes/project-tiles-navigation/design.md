# Design

## Context

See proposal.md for motivation and the specs for behaviour. Relevant current state:

- Projects are stored without their parent. Vikunja returns `parent_project_id` on every project (`0` for top-level) in the list the app already pulls.
- `TasksViewModel` drives a single screen. It already keeps "completed in this view" snapshots (struck-through rows) that must clear when the view changes.
- The local database is at version 1 with an exported schema, and it holds unsent changes that must survive any upgrade.

## Goals / Non-Goals

**Goals:**
- Project browsing without adding a navigation library or a second view model.
- A database upgrade that keeps every task and pending change.

**Non-Goals:**
- Showing the hierarchy elsewhere, for example as paths in the edit sheet's project list or in the widget.
- Counting subprojects' tasks on a tile, creating or editing projects, and project colours or icons.
- Remembering the open project across process death.

## Decisions

**Parent id as a column, added by an auto-migration.** `ProjectEntity.parentProjectId` gets `@ColumnInfo(defaultValue = "0")`, and the database moves to version 2 with `AutoMigration(from = 1, to = 2)`. Room generates the `ALTER TABLE` from the committed version 1 and 2 schemas. The next pull fills the real parent ids anyway, because projects are replaced on every sync. *Alternative:* destructive migration, which would lose unsent changes. *Alternative:* a hand-written `Migration`, which is equivalent but easier to get out of step with the schema.

**Navigation is a stack of project ids in `TasksViewModel`.** An empty stack is the main view. `openProject` pushes, and `navigateUp` and `navigateHome` pop; each also clears the struck-through snapshots, which satisfies "moving between screens is leaving the view". The stack is checked against the current project list on every emission: it is cut at the first id that is no longer a visible project, or no longer a child of the one before it. That covers projects deleted or archived elsewhere. *Alternative:* navigation-compose with a route per project, which brings a new dependency and per-destination view models whose shared sync and status state would need lifting out.

**One screen composable with two regions.** The header (back arrow and title, project screens only) and the capture bar sit on top. The content `Column` holds the task list with `weight(2f)` and, if there are tiles, the tile grid with `weight(1f)`; with no tiles the list takes `weight(1f)` alone. Each region is its own lazy container, so they scroll independently. The tiles are a `LazyVerticalGrid` with adaptive columns of at least 140 dp, each a card showing the title and "N open". Every view gets a fresh `LazyListState`, keyed by the project id, so opening a project starts at the top.

**Tree logic in a pure `ProjectTree` helper.** It holds `topLevel`, `children`, `validPath` and `openCounts`, operating on non-archived projects. A project counts as top-level when its parent is 0, itself, or not among the visible projects. That keeps subprojects of archived or unshared projects reachable.

**Capture destination follows the view.** `TaskRepository.capture` takes an optional project id: the open project on a project screen, null (the default project) on the main view and in the share sheet. The widget's + action calls `navigateHome()` before focusing capture.

**Project tags on project screens.** The tag logic is reused by passing the open project as the "default" to `TaskListModel.rows`. Every task on that screen is in that project, so none gets a tag.

## Risks / Trade-offs

- [Auto-migration depends on the committed `1.json`] → it is in the repo; a Robolectric test opens a real version-1 database built from that schema with the version-2 code and checks that the data survives.
- [A project shared with the user whose parent isn't shared appears at the top level] → intended, so it stays reachable.
- [Tile counts only include a project's own tasks, so a parent whose work sits in subprojects shows a low count] → matches what its screen lists; it can be revisited later.
- [On short screens the lower third leaves little room for tiles] → the grid scrolls; the 2:1 split is what the issue asks for.

## Migration Plan

The upgrade is automatic on first launch of the new version, and the next sync fills parent ids. Downgrading to 0.1.0 isn't supported by Room (the version-2 database won't open in version 1); reinstalling clears the local cache, and unsent changes would be lost. That is acceptable for a personal app; avoid downgrading with unsent changes.
