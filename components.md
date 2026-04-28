# Components Specification

## Task C1 — Table Component

**Status:** Done — shipped 2026-04-28. Both ENTITY_LIST and GRID
surfaces render via [`trina_grid`](https://pub.dev/packages/trina_grid)
behind a project-side adapter layer.

### C1.1 Decision

Tables use `trina_grid`. Reasons:

- Actively maintained successor to `pluto_grid` (its predecessors had
  dormancy gaps; `pluto_grid_plus` is abandoned upstream).
- Built-in features the project needs: per-column flex widths, column
  resize / reorder / freeze, native sticky header, desktop-grade
  keyboard navigation, server-side sort plumbing.
- Better fit than the alternatives we evaluated — `data_table_2`'s
  feature ceiling is below what we'll need over time, and a custom
  pure-Flutter `Table` would be too much from-scratch work for no
  concrete benefit.

### C1.2 Adapter Layer

`trina_grid` is wrapped behind project-side adapters in
`lib/widgets/grid/`:

- `trina_grid_adapter.dart` — `buildTrinaColumn`,
  `buildTrinaActionColumn`, `buildTrinaRow`, cell renderers,
  `GridRebuildTrigger`, `_ColumnResizeHandle`.
- `trina_grid_header.dart` — `SortableFilterableHeader` widget hosting
  the CF1 filter input + sort glyph.
- `trina_grid_theme.dart` — `trinaGridConfigForApp(...)` mapping
  `AppTheme` tokens to `TrinaGridConfiguration`.
- `column_sort.dart` — `SortDirection` + `cycleSortDirection`.

The adapter pattern keeps project-side logic — filter widgets
(`columnFilters.md` CF1), sort glyph + cycle (CF2), edit-in-frame
editing (`gridElement.md` G5), action icons, pending rows — out of
`trina_grid`'s own configuration. Two consequences:

- Consumer surfaces (`app_view.dart` ENTITY_LIST, `form_renderer_view.dart`
  GRID) interact with `trina_grid` *only* through the adapter. New
  surfaces should follow the same pattern.
- If `trina_grid` ever needs replacement, only the rendering layer
  moves; the adapter's API contract and all project-side logic stay
  intact.

### C1.3 Carve-outs (what we use, what we bypass)

`trina_grid` is a feature-rich grid framework. The project uses it as
a **presentation layer** with deliberate carve-outs where its features
would collide with project-spec'd UX.

**Used:**

- Display grid (rows / cells / column widths / resize / reorder).
- Sticky header.
- Sort plumbing via `TrinaColumn.sort`, with the project's own glyph
  + click cycle (CF2.1) rendered in the custom `titleRenderer` and
  dispatched to the caller as `userSort` (CF4 / CF5).
- Scrollbars, virtualization, row striping (mapped from `AppTheme`
  via `trinaGridConfigForApp(...)`).
- Per-row background colouring via `TrinaGrid.rowColorCallback`
  (e.g. amber for pending GRID rows).
- Empty-state placeholder via `TrinaGrid.noRowsWidget`.

**Bypassed:**

- **In-cell editing** (`enableEditingMode: false`). Row editing goes
  through `EditorFrame` per `gridElement.md` G5.
- **Built-in column filter UI** (`enableFilterMenuItem: false`). The
  CF1 filter widgets are hosted inside `TrinaColumn.titleRenderer`
  via `SortableFilterableHeader`.
- **Default sort cycle on header click** (`enableSorting: false`).
  Project owns the `none → asc → desc → none` cycle (CF2.2).
- **Context menus** (`enableContextMenu: false`).
- **Trina's desktop keyboard model** — surface focus only; revisit
  alongside the holistic keyboard-design pass.
- **Copy/paste, data export, row grouping, multi-column sort UI** —
  not surfaced.

### Cross-References

- **Column filters**: `columnFilters.md` CF1 / CF2 — widgets and
  sort cycle hosted via the adapter's `titleRenderer`.
- **Editor flow**: `gridElement.md` G5 — row interaction pushes an
  `EditorFrame`; in-cell editing is bypassed.
- **GraphQL**: `graphql.md` and `columnFilters.md` CF4 — sort + filter
  protocol.
- **Styling**: `frontendStyling.md` S1.2 — `TrinaGridStyleConfig`
  mapping to `AppTheme` via `trinaGridConfigForApp(...)`.
- **Selectable GRID** (`specifications.md` pending): future consumer
  of the same adapter.
- **Entity table unification** (`entityTableUnification.md`): future
  refactor that collapses the adapter's two consumer surfaces into
  one.
