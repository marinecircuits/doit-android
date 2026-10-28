# Proposal

## Why

The user wants the existing context-label screen reached by swiping in the opposite direction, three configurable label screens, and a direct way home.

## What Changes

- Configure three independently selectable label-screen slots from one home menu item, preserving the existing context-label setting as the first slot.
- Swipe right from home and between configured screens; swipe left to go back. Skip disabled or deleted labels.
- Show a Home button on label and project headers, while retaining Back and task-row swipe actions.

## Capabilities

### New Capabilities

- `label-screens`: configurable label screens and their navigation.

## Impact

Add two preferences, extend the existing task view-model and screen, and reuse the existing label-filtered list. No database migration or API change is needed.
