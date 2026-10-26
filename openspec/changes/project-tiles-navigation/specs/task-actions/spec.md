# Spec Delta

## MODIFIED Requirements

### Requirement: Completed rows stay until the view changes
A task completed in the current view SHALL stay visible there, struck through, while the user remains in that view, including across background syncs and screen rotation. It SHALL be gone the next time the user returns after leaving the view, for example after the app went to the background or after moving to another project screen or back. Struck-through rows SHALL NOT react to swipes or open the edit sheet; only their circle reacts.

#### Scenario: Sync while the row is shown
- **WHEN** a sync finishes while a struck-through row is visible
- **THEN** the row stays in place, still struck through

#### Scenario: Leave and come back
- **WHEN** the user completes a task, switches to another app and comes back
- **THEN** the completed task is no longer listed

#### Scenario: Completed row ignores swipes and taps
- **WHEN** the user swipes or taps the title of a struck-through row
- **THEN** nothing happens

#### Scenario: Navigate away and back
- **WHEN** the user completes a task on the Work screen, opens a subproject and goes back to Work
- **THEN** the completed task is no longer listed on the Work screen
