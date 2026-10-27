# Design

## Context

See proposal.md for motivation and the specs for behaviour. Relevant current state:

- Vikunja returns `hex_color` on every project in the list the app already pulls. Its documentation says the value has no leading `#`, but the field allows seven characters, so a `#` may appear. An empty string means no colour.
- Projects are replaced on every pull, and the local database is at version 2 with exported schemas for versions 1 and 2.
- Tiles are `OutlinedCard`s in `TasksScreen`; the "N open" line uses `onSurfaceVariant`.

## Goals / Non-Goals

**Goals:**
- Tiles in the project's colour, with readable text in both light and dark theme.
- A database upgrade that keeps every task and pending change.

**Non-Goals:**
- Colours anywhere else: project tags in the list, the project header, the edit sheet or the widget.
- Choosing or changing a project's colour from the app.

## Decisions

**Colour as a column, added by an auto-migration.** `ProjectEntity.hexColor` gets `@ColumnInfo(defaultValue = "")`, and the database moves to version 3 with `AutoMigration(from = 2, to = 3)`, the same approach as the parent id in version 2. The next pull fills in the real colours.

**Store the raw value, read it when drawing.** The entity keeps the string exactly as Vikunja sends it. A pure `ProjectColors.tile(hex)` turns it into tile colours, or `null` when there is nothing usable. It accepts six or three hex digits, with or without `#`. Keeping the parsing out of the sync means a parsing change never needs a migration.

**Lighter means 30 % of the way to white.** Each RGB channel is blended with white: `c + (255 − c) × 0.3`. That keeps the hue recognisable (a dark blue stays blue) while making dark colours less heavy behind text. The same fill is used in light and dark theme, so a project looks the same everywhere. *Alternative:* blending towards the theme surface, which would make tiles darker in dark theme, contrary to "lighter".

**Text colour by contrast ratio.** The text is a near-black (`#1D1B20`, Material's neutral 10) or white, whichever has the higher WCAG contrast ratio with the fill. Both lines use it; the "N open" line at 80 % opacity, so it stays secondary as on plain tiles.

**Plain tiles stay as they are.** Without a usable colour the tile is the current `OutlinedCard` with theme colours. Coloured tiles keep the outline too, so light fills still have an edge on a light background.

## Risks / Trade-offs

- [A very light project colour gives a tile close to the background] → The outline keeps the tile's edge visible.
- [Schema 3 must be committed from CI] → As for version 2: the build warns when `app/schemas` changed, and the file comes from the `room-schemas` artifact.
