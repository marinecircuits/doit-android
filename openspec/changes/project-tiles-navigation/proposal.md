# Proposal

## Why

With everything in one list, there is no way to look at a single project or to see how projects are organised (issue #4). Vikunja projects nest, and the user wants to browse them: top-level projects as tiles, and each project's subprojects as tiles beneath its tasks.

## What Changes

- The main view keeps the list of all open tasks in its upper ~2/3 and shows one tile per top-level project in the lower ~1/3.
- Tapping a tile opens a project screen: a header with a back arrow and the project title, the capture box, the project's own open tasks, and, if it has subprojects, their tiles in the lower ~1/3. The same layout applies at every depth.
- Tiles show the project title and the number of open tasks directly in that project. The task list and the tile area scroll independently.
- Back (arrow or system back) goes to the parent project, and from a top-level project to the main view. If the open project disappears on the server, the app falls back to the nearest remaining parent.
- Capturing on a project screen creates the task in that project; the main view, the share sheet and the widget keep using the default project. The widget's + button opens the main view.
- Moving between screens counts as leaving the view, so struck-through completed rows are cleared.
- The app now stores each project's parent (`parent_project_id`); the local database moves to version 2 with a migration that keeps all tasks and unsent changes.

## Capabilities

### New Capabilities
- `project-navigation`: project tiles, project screens, subproject tiles, back navigation and the screen layout.

### Modified Capabilities
- `task-list`: the main view's task list sits above the project tiles; project tags are not shown on project screens.
- `task-capture`: capture on a project screen goes to that project.
- `task-actions`: navigating between screens counts as leaving the view for struck-through rows.
- `home-widget`: the + button opens the main view.

## Impact

- Data: `ProjectDto`, `ProjectEntity` (new column), Room database version 2 with an auto-migration and a new exported schema; `SyncEngine` stores the parent id.
- Code: `TaskRepository.capture` takes a project; new `ProjectTree` helper; `TasksViewModel` navigation state; `TasksScreen` layout and tiles; `MainActivity` widget capture handling; README.
- No server or API permission changes: `parent_project_id` is part of the project list the app already reads.
