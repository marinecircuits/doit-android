# Spec Delta

## ADDED Requirements

### Requirement: Pick a due date while editing a title
Typing `!` at a word boundary outside quoted text, or tapping the `!` key while editing a task title, SHALL open the existing date picker. A chosen date SHALL be saved through the existing reschedule action, preserving an existing due time.

#### Scenario: Set date from title
- **WHEN** the user types `!` while editing a task title and picks 30 October
- **THEN** the task's due date is saved as 30 October and the trigger does not become part of its title
