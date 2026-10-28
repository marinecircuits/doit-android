# Design

Detect a single newly typed `!` at a word boundary in a collapsed selection, outside quoted text. Remove the trigger from the field before showing the picker. A paste, a selected-text replacement and an exclamation mark inside a word remain literal. The keyboard accessory also offers a focus-preserving `!` button which opens the picker directly.

The capture bar and share dialog each hold an optional `LocalDate` and show controls to change or clear it. The date is sent to the repository with the task; the title and shared description are not rewritten to encode it. Capture stores local noon using the existing `DueDates` logic in the same transaction as the task and its pending create, so offline captures appear dated and sync the date together with the create. The edit sheet uses its existing date control and reschedule callback, preserving the time on a task that was already dated. Cancelling the picker leaves any earlier selection unchanged.

Only-`@label` or `#project` navigation remains unchanged, and an empty parsed task title still creates nothing. Date selection is transient just like the existing capture draft.
