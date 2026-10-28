# Spec Delta

## ADDED Requirements

### Requirement: Local shopping project settings
Each project SHALL offer a locally stored shopping list setting, independent of subprojects and cleared on sign-out. Marked project tiles and headers SHALL show a cart. The settings screen SHALL explain the local scope and quantity syntax.

#### Scenario: Mark a project
- **WHEN** the user marks a project as a shopping list
- **THEN** its tile and header show a cart and the setting survives sync and restart

### Requirement: Shopping items and doing
Shopping tasks SHALL NOT offer working controls or count as doing. Capture and editing SHALL omit doing. Moving a working task to a shopping project or marking its project SHALL queue removal of doing, preserving other labels. Sync SHALL also queue and upload cleanup for downloaded shopping tasks. Failed required removal SHALL remain queued and be reported as a sync error.

#### Scenario: Refused cleanup
- **WHEN** the server refuses permission to remove doing from a shopping item
- **THEN** the removal stays queued and the server information reports the item and permission problem

### Requirement: Shopping quantities
A shopping title ending in a whitespace-separated nonempty [quantity] SHALL display the quantity separately while preserving the full title in edits and on the server. The quantity is free text; capture and editing SHALL treat tokens inside it literally. Other projects and incomplete or empty quantities retain plain titles.

#### Scenario: Capture a quantity
- **WHEN** the user captures `Milk #Groceries [ask @doing or #Work]` into a shopping project
- **THEN** the suffix remains literal and no label or project is inferred from its contents
