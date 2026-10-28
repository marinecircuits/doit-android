# shopping-projects Specification

## Purpose

Recognize shopping lists consistently across devices using the shared Doit project marker and present their quantities without changing task data.

## Requirements

### Requirement: Shared project classification
The app SHALL recognize the exact standalone visible line `doit:type=shopping` in a project's description, including rich-text formatting and HTML entities. It SHALL cache descriptions for offline recognition and show cart markers in project headers and tiles. Subprojects SHALL be classified independently. Removing the marker remotely SHALL restore ordinary behavior after sync. Comments, attributes and inline prose SHALL NOT classify a project.

#### Scenario: Marker from another client
- **WHEN** Linux or the web editor saves `<p>doit:type=shopping</p>` in a project description
- **THEN** Android recognizes that project on its next sync and remembers it offline

#### Scenario: Upgrade
- **WHEN** a user upgrades from a database without project descriptions
- **THEN** existing tasks, labels and pending changes survive, descriptions default empty, and sync fills them

### Requirement: Shopping items exclude working state
Shopping tasks SHALL offer no work controls and SHALL NOT count toward doing warnings or the doing view. Capture and title edits SHALL omit the doing label, and moves or completion undo SHALL NOT restore it. Sync SHALL durably remove doing from cached and downloaded shopping items while preserving other labels, fields and newer intents. Failed required removal SHALL remain queued and report sync failure.

#### Scenario: Capture and move
- **WHEN** a task is captured with doing into a shopping project or a working task moves into one
- **THEN** it has no doing membership locally and sync removes any server membership

#### Scenario: Permission failure
- **WHEN** the token cannot remove a shopping item's doing label
- **THEN** sync reports missing permission, the removal remains queued, and the item does not count as doing locally

### Requirement: Free-text quantities
A shopping title ending in a nonempty, whitespace-separated [quantity] suffix SHALL display its contents as a separate badge in project and label views. No number or unit interpretation SHALL occur. Capture and title edits SHALL treat quantity contents literally. Stored and synced titles SHALL remain complete. Empty or unfinished suffixes and ordinary projects SHALL retain plain titles; Edit SHALL show full text when badges are truncated.

#### Scenario: Literal quantity
- **WHEN** `Milk #Groceries @errand [ask @doing or #Work for two bags]` is captured into a shopping project
- **THEN** the task stays in Groceries with errand and the complete free-text quantity, without interpreting its @/# tokens
