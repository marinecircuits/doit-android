# Proposal

## Why

Shopping-list identity must be shared across Doit clients. Vikunja descriptions provide shared storage without changing project names or task identifiers.

## What Changes

- Cache project descriptions and recognize a standalone visible `doit:type=shopping` line, including rich-text paragraphs.
- Show cart markers and free-text quantity badges, keeping complete task titles for edits and sync.
- Exclude shopping items from working-state controls and warnings; block doing in capture, edits, moves and undo.
- Clean up downloaded doing memberships durably and retain failed removals.
- Migrate Room from version 4 to 5 without losing tasks or pending changes.

## Capabilities

### New Capabilities
- `shopping-projects`: shared marker, quantity display and working-state exclusions.

## Impact

Android reads the shared marker; users change it in Vikunja or Doit Linux. No new API permissions are needed for recognition. Cleanup uses the existing task-label delete permission. Other clients can still add doing until Doit syncs. The Linux client and Android share parser fixtures. Server-enforced validation and an Android marker editor are outside this change.
