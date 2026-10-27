# Spec Delta

## MODIFIED Requirements

### Requirement: Plain-text titles
Apart from recognized `@label` and `#project` tokens, the capture box and share dialog SHALL keep other syntax as text. Prefixes inside words or URLs, or followed by whitespace, SHALL remain literal. Prefixes inside a quoted label or project name SHALL not be parsed independently. Shared descriptions SHALL never be parsed.

#### Scenario: Text that looks like quick-add syntax
- **WHEN** the user captures "call +Bob tomorrow !3"
- **THEN** the task title is exactly "call +Bob tomorrow !3", with no project, date or priority change

#### Scenario: At signs that are not labels
- **WHEN** the user captures "mail bob@example.com about the meeting @ 5pm"
- **THEN** the title is kept exactly and the task gets no label

#### Scenario: Literal hashes
- **WHEN** the user captures "Read C# at https://example.com/#Work # 5 tomorrow !3"
- **THEN** that text remains the title, with no project, date or priority override

#### Scenario: Prefix inside a quoted name
- **WHEN** the label "Review #Work" and project "Work" exist and the user captures `Call @"Review #Work"`
- **THEN** the task title is "Call", the label is applied, and the default/current project remains the destination

### Requirement: Destination project
A recognized `#project` in the captured title SHALL override the open project and the configured default project. Without one, project-screen captures SHALL use the open project and main-view/share captures SHALL use the configured default, falling back to the first non-archived project. Choosing another project SHALL not navigate away from the current screen.

#### Scenario: Default project configured
- **WHEN** the user's Vikunja default project is Inbox
- **THEN** tasks captured on the main view without a recognized `#project` are created in Inbox

#### Scenario: Capture on a project screen
- **WHEN** the user captures "Order capacitors" on the Firmware screen
- **THEN** the task is created in Firmware and appears in Firmware's list

#### Scenario: Override the open project
- **WHEN** the user captures "Order capacitors #Firmware" on the Work screen and Firmware exists
- **THEN** the task "Order capacitors" is created in Firmware and the Work screen stays open

#### Scenario: Unknown name preserves the destination
- **WHEN** the user captures "Order capacitors #missing" on the Work screen and no active project has that name
- **THEN** "Order capacitors #missing" is created in Work

## ADDED Requirements

### Requirement: Projects from capture
Both capture interfaces SHALL remove word-starting `#name` or `#"two words"` tokens matching a cached active project title, ignoring case, and use the last match as the destination. Subprojects SHALL be eligible. Unknown, archived or unfinished names SHALL stay literal. Duplicate titles SHALL resolve to the first active project in cached order. `@labels` SHALL work in the same input. If no title remains, no task SHALL be created.

#### Scenario: Project and label offline
- **WHEN** the user captures `Buy lamp #"Home office" @errand` offline and both names are cached
- **THEN** "Buy lamp" is queued in Home office with errand, and sync creates it in that project with that label

#### Scenario: Last known project wins
- **WHEN** the user captures "Buy lamp #Work #Home #missing", Work and Home exist, and missing does not
- **THEN** "Buy lamp #missing" is created in Home

#### Scenario: Archived project stays literal
- **WHEN** the user captures "Buy lamp #Old" and Old is archived
- **THEN** "Buy lamp #Old" is created in the default/current project

#### Scenario: Only capture tokens
- **WHEN** the user submits "#Work @errand" with both names known
- **THEN** no task is created or queued

### Requirement: Project suggestions
After `#` and a partial name before the cursor, both capture interfaces SHALL suggest up to five cached active projects, including subprojects, ranking prefix matches before substring matches, ignoring case. Duplicate names SHALL use the same first active project as parsing. Tapping SHALL complete the name (quoted for whitespace) plus a space, move the cursor after it, and retain focus. Each suggestion SHALL show its project color. Send SHALL capture as usual.

#### Scenario: Complete a project with spaces
- **WHEN** the user types "Buy lamp #ho" and taps Home office
- **THEN** the field reads `Buy lamp #"Home office" ` and the cursor follows the trailing space

#### Scenario: Switch between project and label suggestions
- **WHEN** the user types "Buy #Home @er"
- **THEN** suggestions are labels matching "er", and moving the cursor immediately after "#Home" shows project matches

#### Scenario: No matching project
- **WHEN** the user types "Buy #zz" and no active cached project contains "zz"
- **THEN** no suggestions are shown and sending keeps "#zz" in the task title
