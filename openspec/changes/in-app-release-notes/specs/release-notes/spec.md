# Spec Delta

## ADDED Requirements

### Requirement: Release notes in About
The About menu SHALL provide access to the complete release notes for the installed app version. The notes SHALL be available offline and scrollable, and SHALL be sourced from the changelog prepared for that release. When no matching section exists, the view SHALL show an unavailable message.

#### Scenario: Read release notes offline
- **WHEN** the user opens Release notes from About without a network connection
- **THEN** all changelog entries for the installed version are displayed

#### Scenario: Missing notes
- **WHEN** the installed version has no changelog section
- **THEN** the app shows that release notes are unavailable
