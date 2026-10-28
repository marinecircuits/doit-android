# Design

Persist marked project IDs in device preferences, separately from server projects. The app renders a cart and parses a final quantity suffix only for marked projects. Label cleanup uses durable pending changes and retries server refusals; it also runs after downloads, so other clients' doing changes are cleaned up. Subprojects are independent.
