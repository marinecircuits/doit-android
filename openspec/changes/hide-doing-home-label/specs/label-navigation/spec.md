# Spec Delta

## ADDED Requirements

### Requirement: Exclude doing from home shortcuts
The home label shortcut list SHALL exclude labels named doing, case-insensitively. Other label functionality, including the doing shortcut and label view, SHALL remain available. If no other labels exist, the normal empty label message SHALL appear.

#### Scenario: Doing and other labels
- **WHEN** cached labels include Doing and Errand
- **THEN** home shows Errand but not Doing as a label shortcut

#### Scenario: Only doing exists
- **WHEN** Doing is the only cached label
- **THEN** the home label section shows its empty message
