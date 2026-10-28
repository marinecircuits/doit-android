# Design

Keep `context_label_id` as the first of three preference keys so upgrades preserve the configured screen. The settings dialog lists the three slots and uses the existing label picker for each, including a disable option. Invalid or deleted label IDs are skipped until the user reconfigures them.

The view model holds the current slot index, derives the open label using the existing filtered task list, and skips missing or disabled slots. A rightward page swipe moves from home to the next configured slot; a leftward page swipe moves to the previous one or home. Swipes from project screens and ad-hoc label screens do not change the context slot. Back from a label screen goes home. A Home button on the existing header calls `navigateHome` from any project or label screen.

Task-row swipe actions remain on their existing child gesture handler. The page gesture is available on screen background; check gesture arbitration on an Android device.
