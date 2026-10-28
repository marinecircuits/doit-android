# Design

Derive a display address from the saved API base, remove the `/api/v2/` suffix, retain any reverse-proxy subpath and port, and remove URL user information, query and fragment. Observe this address separately from credentials so the view does not need the token. Reuse the existing sync state rather than making a new request: an unavailable server is still identifiable offline.

The existing About menu item is renamed About & server and continues to show the app version. Show the current sync status, including rejected-token and pending-change states, or indicate that no sync has run. If no address is available, show Unavailable.
