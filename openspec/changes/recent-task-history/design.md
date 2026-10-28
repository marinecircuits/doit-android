# Design

Save local actions transactionally with their task changes, bounded to 100 per category and cleared at sign-out. Fetch a bounded page of recently completed tasks from the server on opening the view, merging it with local history and suppressing reopened tasks. The server does not generally expose deleted task history, so label the deleted list as device-local and explain its starting point. When offline, keep the locally recorded history visible.
