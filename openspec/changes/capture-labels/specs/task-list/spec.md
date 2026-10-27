# Spec Delta

## ADDED Requirements

### Requirement: Labels on rows
A task row SHALL show the task's labels as small chips under its title, each filled with the label's Vikunja colour made a little lighter with contrasting text, or the theme's plain chip for a label without a colour or one the app doesn't know (for example deleted on the server meanwhile). A task without labels SHALL show no chips.

#### Scenario: Labelled task
- **WHEN** "Order capacitors" has the labels "errand" (orange) and "15 min" (no colour)
- **THEN** its row shows an orange "errand" chip and a plain "15 min" chip under the title

#### Scenario: Captured offline
- **WHEN** the user captures "Buy filament @errand" offline
- **THEN** the row shows the "errand" chip at once, before the sync
