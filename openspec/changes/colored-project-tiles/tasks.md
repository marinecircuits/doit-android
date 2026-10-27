# Tasks

## 1. Project colour data

- [x] 1.1 Store `hex_color` (DTO, `ProjectEntity` column with default "", sync mapping) and move the database to version 3 with an auto-migration; commit the generated `3.json`; verify with Robolectric tests that version-1 and version-2 databases keep their tasks, projects and pending changes after upgrading, and a sync test that colours are stored

## 2. Tiles

- [x] 2.1 Add `ProjectColors` (parse six or three hex digits with or without `#`, lighten 30 % towards white, dark or light text by contrast ratio, `null` for no or unreadable colour); verify with unit tests
- [x] 2.2 Fill tiles with the project colour and contrasting text, keeping plain tiles for projects without a colour; update the README; verify the CI build and tests pass
- [ ] 2.3 Verify on device: coloured and plain tiles in light and dark theme, readable text on light and dark colours, a colour changed in the web UI showing after a sync, upgrade from 0.5.1 keeping data

## Workflow follow-up

- Its specs are already synced into `openspec/specs/`; after the on-device check archive it with `openspec archive colored-project-tiles --skip-specs`.
