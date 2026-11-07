## ADDED Requirements

### Requirement: Web saved filters
The app SHALL list the user's saved filters from Vikunja in a dedicated page. It SHALL retain their server-provided titles and descriptions and SHALL NOT include ordinary projects or the built-in Favorites pseudo-project.

#### Scenario: Open a saved filter
- **WHEN** the user selects a saved filter
- **THEN** the app loads all pages of its tasks using the server's saved query and ordering, including completed tasks when they match
- **AND** task actions target each task's real project, not the filter's pseudo-project

#### Scenario: Unsent local change
- **WHEN** a filter refresh includes a task with an unsent edit or deletion
- **THEN** the local edit remains intact and a locally deleted task is not restored

#### Scenario: Connection failure
- **WHEN** loading or refreshing a filter fails
- **THEN** the app shows a retry action and an appropriate connection or permission message
- **AND** already loaded results remain visible unless the filter no longer exists

### Requirement: Swipe navigation
A right swipe from Home SHALL open Saved filters. A left swipe or Back from its list SHALL return Home. From a filter's task results, Back SHALL return to the filter list. A Home button SHALL return directly Home. Existing configured label screens SHALL remain accessible after the filter list in the swipe sequence.

#### Scenario: No label shortcuts configured
- **WHEN** the user swipes right from Home without any configured label screens
- **THEN** Saved filters still opens

#### Scenario: Vertical scrolling or text selection
- **WHEN** the user scrolls vertically, edits capture text, or browses a project/direct label view
- **THEN** the gesture SHALL NOT open Saved filters
