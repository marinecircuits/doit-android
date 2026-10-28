# Spec Delta

## ADDED Requirements

### Requirement: Persistent doing shortcut
When at least one open task has the existing doing label, the app SHALL show one doing task and its project at the top of home, project and label screens. It SHALL select the first in normal task order and omit completed and deleted tasks. When more than one task is doing, the shortcut SHALL also show the number of additional tasks. It SHALL disappear when no open task is doing or the label is unavailable.

#### Scenario: One working task
- **WHEN** exactly one open task is doing
- **THEN** its title and project appear at the top and tapping the shortcut opens that task's edit sheet

#### Scenario: Several working tasks
- **WHEN** three open tasks are doing across projects
- **THEN** one appears at the top with "+2 more" and tapping the shortcut opens the doing label's full task list

#### Scenario: Last task stops
- **WHEN** the last doing task is stopped, completed or deleted
- **THEN** the shortcut disappears
