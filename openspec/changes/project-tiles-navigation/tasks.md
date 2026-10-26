# Tasks

## 1. Project hierarchy data

- [ ] 1.1 Store `parent_project_id` (DTO, `ProjectEntity` column with default 0, sync mapping) and move the database to version 2 with an auto-migration; commit the generated `2.json`; verify with a Robolectric test that a version-1 database built from `1.json` keeps its tasks, projects and pending changes after upgrading, and a sync test that parent ids are stored
- [ ] 1.2 Let `TaskRepository.capture` take a destination project (default project when none); verify with a repository test

## 2. Navigation model

- [ ] 2.1 Add `ProjectTree` (top-level including orphans and children of archived projects, immediate children, path validation, own open counts); verify with unit tests
- [ ] 2.2 Add navigation to `TasksViewModel` (project stack, rows and tiles per view, no tags on project screens, capture into the open project, clearing struck-through rows on navigation, falling back when the open project disappears, `navigateHome`); verify with view-model tests

## 3. Screens

- [ ] 3.1 Build the layout: project header with back arrow and title, capture placeholder naming the project, 2:1 task and tile split (full height without tiles), tile grid with counts, independent scrolling, per-view empty state, system back, and widget + returning to the main view; verify the CI build and tests pass
- [ ] 3.2 Update the README for project navigation; verify it matches the specs
- [ ] 3.3 Verify on device: tiles on the main view, opening projects and subprojects, back at every level, capture into the open project, struck-through rows clearing on navigation, upgrade from 0.1.0 keeping data

## Workflow follow-up

- Close issue #4 with the pull request. Sync the specs into `openspec/specs/` before merging; after the on-device check archive it with `openspec archive project-tiles-navigation --skip-specs`.
