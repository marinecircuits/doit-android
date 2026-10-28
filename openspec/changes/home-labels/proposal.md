# Proposal

## Why

The user wants the available labels at the bottom of the home screen, as in the Linux app.

## What Changes

- Add a Labels section below the project tiles with all cached labels, alphabetically ordered, including labels with no tasks.
- Show compact, wrapping, colored `@name` shortcuts in the same scrolling area as projects. Tapping one opens its existing cross-project label view, even offline.
- Show separate empty states when projects or labels are absent, without hiding the other section.

## Capabilities

### New Capabilities

- `label-navigation`: home shortcuts to cached labels.

## Impact

The home grid and task view-model navigation only; no schema, API or permissions change.
