# Spec Delta

## ADDED Requirements

### Requirement: Swipe-down label shortcut
The label-screen settings SHALL offer a separate configurable label for a downward gesture. Pulling down at the top of a task or project list SHALL open that label across projects. Disabled or deleted selections SHALL do nothing. Ordinary list scrolling, the three sideways screens, and Back and Home navigation SHALL remain available.

#### Scenario: Open configured label
- **WHEN** the user pulls down at the top of a list with Errand configured
- **THEN** the Errand label view opens across projects

#### Scenario: No valid selection
- **WHEN** the selected label is disabled or no longer exists
- **THEN** pulling down does not change the view or start a manual refresh
