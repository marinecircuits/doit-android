# Spec Delta

## ADDED Requirements

### Requirement: Configurable label screens
The home menu SHALL let the user independently select or disable up to three ordered label screens. Each screen SHALL show tasks with its selected label across projects. An existing context-label setting SHALL remain selected in the first slot after upgrading. Disabled or deleted labels SHALL be skipped during navigation.

#### Scenario: Upgrade
- **WHEN** an existing installation has context label Errand
- **THEN** screen 1 is configured as Errand and screens 2 and 3 are unconfigured

#### Scenario: Missing label
- **WHEN** screen 2 refers to a deleted label and screens 1 and 3 are configured
- **THEN** navigation moves directly between screens 1 and 3

### Requirement: Label screen navigation
Swiping right on the page SHALL move from home to the first available label screen and then through subsequent available screens. Swiping left SHALL return through them to home. Project screens and label screens opened directly from capture SHALL not be changed by these page swipes. Task-row swipes SHALL retain their existing actions.

#### Scenario: Navigate three labels
- **WHEN** three labels are configured and the user swipes right three times from home
- **THEN** their task lists appear in configured order

#### Scenario: Return to home
- **WHEN** the user swipes left from the first label screen
- **THEN** the home screen is shown

### Requirement: Home button
Every project or label header SHALL have a Home button that returns directly to the home screen, while Back retains its existing behavior.

#### Scenario: Return from a nested project
- **WHEN** the user taps Home inside a nested project
- **THEN** the main view is shown directly
