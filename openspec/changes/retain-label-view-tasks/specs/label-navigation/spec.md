# Spec Delta

## ADDED Requirements

### Requirement: Retain tasks after label removal
A label view SHALL continue displaying tasks that matched the label during the current visit when that label is removed, including changes received through synchronization. The rows SHALL reflect current task data and labels, and actual label membership SHALL determine counts. Deleted tasks SHALL disappear. Once the user changes views, the next visit SHALL show tasks according to their current labels. Backgrounding or reopening the same view SHALL NOT reset the retained membership.

#### Scenario: Stop a doing task
- **WHEN** the user stops a task in the doing label view
- **THEN** the task remains visible without the doing label and the doing count decreases
- **AND** after leaving and returning, the task is absent unless it has the label again

#### Scenario: Synced label change
- **WHEN** synchronization removes the current label from a visible task
- **THEN** the task remains visible until the view changes

#### Scenario: Switch configured label screens
- **WHEN** the user navigates from one label screen to another and back
- **THEN** the first screen reflects current label membership
