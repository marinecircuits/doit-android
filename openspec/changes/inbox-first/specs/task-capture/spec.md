## MODIFIED Requirements

### Requirement: Default capture destination
An unqualified task SHALL be created in the active project named Inbox, including when another project is open. An explicit `#project` SHALL take precedence. If Inbox is unavailable, the existing default-project fallback SHALL apply.

#### Scenario: Capture inside another project
- **WHEN** a user creates a task without `#project` while browsing Work
- **THEN** the task is created in Inbox

### Requirement: Inbox ordering
The Home project grid SHALL show an active top-level Inbox first, preserving the order of other projects.
