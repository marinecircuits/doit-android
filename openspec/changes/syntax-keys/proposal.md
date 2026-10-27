# Proposal

## Why

`@` and `#` are how doit adds a label and chooses a project, but both signs are buried on a phone keyboard. They also did nothing in the edit sheet, so an existing task could not be labelled or moved that way.

## What Changes

- While the capture box, edit sheet title, or share title is focused, show `@` and `#` keys that insert the sign at the cursor and leave the keyboard open.
- Keep a space between the sign and a neighbouring word so the token is not glued to the title.
- When an edited title is saved, apply the same rules as capture: add recognized labels to the ones already on the task, move it to a recognized project, and keep the previous title when nothing but tokens remains.

## Capabilities

### Modified Capabilities

- `task-capture`: the same `@label` and `#project` tokens work in the edit sheet, and focused task fields offer keys for both signs.

## Impact

- `SyntaxKeys` is shared by the three fields. `TaskRepository.applyEditedTitle` records the label and project changes on the existing pending update, including an unsent capture.
