# Spec Delta

## MODIFIED Requirements

### Requirement: Release notes in About
The About menu SHALL provide access to the complete release history from the bundled changelog. It SHALL be available offline and scrollable, with release headings and entries preserved. If the changelog is unavailable, the view SHALL show an unavailable message.

#### Scenario: Browse past releases offline
- **WHEN** the user opens Release notes from About without a network connection
- **THEN** the current and earlier release notes are displayed in a scrollable view

#### Scenario: Missing notes
- **WHEN** the bundled changelog is unavailable or empty
- **THEN** the app shows that release notes are unavailable
