# Design

Append full-width divider, heading and label-shortcut items to the existing home `LazyVerticalGrid`, so projects and labels share one vertical scroll and pull-to-refresh. Label pills wrap and reuse the existing lightened label colors; uncolored labels use the theme's surface color. The cached label list includes unused labels and is sorted case-insensitively.

An explicit `openLabel(id)` reuses standalone `@label` navigation: it clears the project/context path, retains normal task actions, and opens the label by ID. If a label disappears, the state falls back to home and context navigation remains available. Empty projects do not prevent cached labels being displayed.
