# Spec Delta

## ADDED Requirements

### Requirement: Recent task history
The app menu SHALL open a view showing recently completed and deleted tasks. Recent completions SHALL include a bounded page from the server and local completions, excluding reopened tasks. Device-local completion and deletion records SHALL be saved alongside the task change, limited to 100 per category, survive restart and work offline. Undoing completion SHALL remove the local completion record. The deleted section SHALL identify that its entries are device-local and start when the feature is installed. Signing out SHALL clear local history. A failed server fetch SHALL leave local history visible with an explanation.

#### Scenario: Recently deleted
- **WHEN** the user deletes a task on this device and opens Recent tasks
- **THEN** the task appears in Recently deleted even offline and after restarting

#### Scenario: Server completion
- **WHEN** a task was completed on another device and the server is reachable
- **THEN** the recent completed section includes it

#### Scenario: Undo completion
- **WHEN** the user unchecks a task completed on this device
- **THEN** its local completion record is removed and an open task is not shown as completed
