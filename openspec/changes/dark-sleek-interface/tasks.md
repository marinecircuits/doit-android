- [x] Define ink/silver/blue themes, typography, shapes, wordmark, and matching launcher colors.
- [x] Restyle home/project navigation, capture, labels, task cards, search, setup, and editing sheets.
- [x] Keep project colors recognizable and verify readable title/count contrast at color extremes.
- [x] Run all 302 unit tests successfully and build the signed release APK.
- [x] Verify the release signing certificate and update the installed Waydroid app without replacing its data.
- [x] Inspect home, project, and task-detail screens in Waydroid; verify opening and returning without modifying tasks.

Validation: `testDebugUnitTest assembleRelease` passed with 302 tests, zero failures, and zero skipped tests. The installed build uses the existing marinecircuits signing certificate. Screenshots and the APK are retained in the local checkout's ignored `app/build` directory.
