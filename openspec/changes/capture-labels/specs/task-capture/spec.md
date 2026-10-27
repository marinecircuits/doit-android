# Spec Delta

## MODIFIED Requirements

### Requirement: Plain-text titles
Apart from `@label` tokens (see "Labels from capture"), the capture box and the share dialog SHALL NOT interpret any syntax: characters such as `*`, `+`, `!` and date words SHALL be kept verbatim in the title.

#### Scenario: Text that looks like quick-add syntax
- **WHEN** the user captures "call +Bob tomorrow !3"
- **THEN** the task title is exactly "call +Bob tomorrow !3", with no project, date or priority change

#### Scenario: At signs that are not labels
- **WHEN** the user captures "mail bob@example.com about the meeting @ 5pm"
- **THEN** the title is kept exactly and the task gets no label

## ADDED Requirements

### Requirement: Labels from capture
In the capture box and the share dialog, every `@name` or `@"two words"` that starts a word and names one of the user's labels, ignoring case, SHALL be removed from the title and added to the task as that label. Any other `@name` SHALL stay in the title as plain text: a capture never creates a label. Only the title is parsed, never a shared description. A text that is blank once its labels are removed SHALL capture nothing.

#### Scenario: Existing label
- **WHEN** the user captures "Order capacitors @errand" and the label "Errand" exists
- **THEN** a task "Order capacitors" is created with the label "Errand"

#### Scenario: Unknown name stays text
- **WHEN** the user captures `Call bank @"15 min" @phone`, the label "15 min" exists and "phone" doesn't
- **THEN** the task "Call bank @phone" is created with the label "15 min", and no label is created

#### Scenario: Only labels
- **WHEN** the user captures "@errand" and the label "errand" exists
- **THEN** no task is created

### Requirement: Label suggestions
While the text before the cursor in the capture box or the share dialog's title field ends in `@` and a partial name, the app SHALL show up to 5 of the user's labels under the field, names starting with the typed part first, then names containing it, ignoring case. Tapping one SHALL complete the name, quoted if it has a space, followed by a space, with the cursor after it. Typing SHALL stay in the field, and the send action SHALL capture as usual.

#### Scenario: Pick a label
- **WHEN** the user types "Buy filament @er", taps "errand" in the suggestions, then types "!" and sends
- **THEN** the field read "Buy filament @errand " after the tap, and the task "Buy filament !" is created with the label "errand"

#### Scenario: Name with a space
- **WHEN** the user types "Call bank @15" and taps "15 min"
- **THEN** the field reads `Call bank @"15 min" `

#### Scenario: No match
- **WHEN** the user types "Ask @zz" and no label contains "zz"
- **THEN** no suggestions are shown
