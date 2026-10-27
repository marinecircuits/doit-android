# Tasks

- [x] 1. Share capture parsing/completion between `@labels` and `#projects`, with active-project filtering and deterministic duplicate handling.
- [x] 2. Apply an explicit project before the current/default destination and persist it in the existing offline create.
- [x] 3. Reuse suggestions in the main capture field and share dialog, including project colors and quoted names.
- [x] 4. Preserve label regression tests and add parser, repository, view-model and offline-sync coverage for projects.
- [x] 5. Document the syntax, precedence and edge cases in README and the change specs.
- [ ] 6. Validate the change specs and pass GitHub Actions unit tests and debug APK build.
- [ ] 7. Check on device: suggestions and keyboard focus in both fields, project colors, and mixed label/project capture offline.

After device verification, reconcile the capture-labels and capture-projects deltas into the canonical specs and archive the changes.
