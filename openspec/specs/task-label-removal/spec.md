# Task label removal

The task editor shows each assigned label with a removal control. Removal changes only that task's membership, works offline, and does not delete the label itself. Other labels are preserved. Removing doing uses the existing working-state flow.

Pending removal survives restart and refused requests. Adding a label again while removal is in flight wins after synchronization. Labels removed from an unsent task are not added during its creation.
