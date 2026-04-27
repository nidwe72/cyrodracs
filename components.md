# Components Specification

## Task C1 — Table Component Refactor

**Status:** **Done — shipped 2026-04-28.** Both ENTITY_LIST and GRID
surfaces render via `TrinaGrid`. Material `DataTable` and the interim
`IntrinsicWidth`/`stretch` crutch from the column-filter v1 era are
gone from the codebase.

**Goal (achieved):** Replaced Flutter's `DataTable` with `TrinaGrid`,
gaining per-column flex widths with user-driven resize, native sticky
header, desktop-grade keyboard navigation, and server-side sort
plumbing — used as a presentation layer with deliberate carve-outs for
the project's own filter and editing flows (see C1.10). Cell wrapping
remains a non-feature (see C1.2 note); `data_table_2` was equivalent
on that axis.

**`columnFilters.md` CF1 filter widgets re-hosted unchanged.** They
moved from the prior `DataTable` `DataColumn.label` host into
`TrinaColumn.titleRenderer` via the project's
`SortableFilterableHeader` widget. No protocol or state-model changes;
`TrinaGrid`'s built-in filter UI is bypassed per C1.10.

### C1.0 Decision — `trina_grid`

**Decided 2026-04-27:** adopt [`trina_grid`](https://pub.dev/packages/trina_grid).
It is the actively maintained successor to `pluto_grid` (which had
periods of dormancy and a community fork chain landing on `trina_grid`),
shares its API surface, and provides the more powerful grid features
the project will need over time — column resize / reorder / freeze,
desktop-grade keyboard navigation, server-side pagination modes,
dark mode, RTL.

**Migration tooling.** `trina_grid` ships an automatic migration
script for existing `pluto_grid` code (`flutter pub run trina_grid
--migrate-from-pluto-grid`); we don't have `pluto_grid` code today,
but the script exists if a different team brings it.

**Why over the alternatives:**

| Option | Verdict |
|---|---|
| **`data_table_2`** | Considered. Lighter and closer to Material `DataTable` semantics, with a smaller feature ceiling — proportional widths via `ColumnSize.S/M/L` but no column resize / reorder / freeze, no desktop-grade keyboard model. Cell-wrapping support is a wash (both packages effectively have fixed row heights — see C1.6 note). Rejected in favour of investing once in `trina_grid` to avoid a second migration when those richer features become needed. |
| **`pluto_grid`** (the original by `bosskmk`/`weblaze.dev`) | Rejected — same API as `trina_grid` but maintenance has been sporadic (21-month gap before a Flutter-3.38 catch-up release). Going `pluto_grid` first means a near-term migration to `trina_grid` anyway. |
| **`pluto_grid_plus`** (intermediate community fork) | Rejected — explicitly no longer maintained per its own maintainer; `trina_grid` is the recommended successor. |
| **Pure Flutter `Table`** (working name was `CyrodracsTable`) | Retained as the **fallback** if `trina_grid` ever becomes unmaintained or imposes blocking constraints. The C1.10 carve-outs for filtering/editing keep the project's own logic decoupled from `trina_grid`'s internals, which makes a future swap mechanical. |
| **Syncfusion / other commercial** | Rejected — licensing. |

**Trade-offs accepted:**

- **Third-party dependency, single maintainer (`trinavo.com` /
  doonfrs).** Currently active (v2.2.1 ~53 days ago, ~30 open issues,
  zero open PRs at the time of decision — suggesting responsive
  maintenance). Watched; the pure-Flutter `Table` fallback above
  (C1.0 table) covers a future hand-off. The C1.10 boundary keeps
  domain logic out of the package's internals so a fallback migration
  remains mechanical.
- **Heavier package than `data_table_2`.** We absorb features we
  don't use (in-cell editing, copy/paste, export, row grouping); the
  user-facing surface is constrained by C1.10.
- **Styling reconciliation.** `TrinaGrid` has its own configuration
  system (`TrinaGridConfiguration`, theme objects). Mapping it onto
  `AppTheme` (`frontendStyling.md` S1.2 / S2) is part of C1.5, not a
  follow-up.
- **Mobile UX is secondary.** `trina_grid` (and its predecessor
  `pluto_grid`) optimise for desktop / web; their mobile UX is a
  documented work-in-progress. Acceptable for the current target
  surfaces; if mobile becomes a primary surface a separate evaluation
  is in order.

The placeholder name `CyrodracsTable` is dropped; this spec now refers
directly to `TrinaGrid` / `TrinaColumn` / `TrinaRow` from `trina_grid`.

### C1.1 Motivation

Flutter's `DataTable` distributes column widths equally — no per-column
width control. This makes it impossible to keep a narrow actions column next
to wider data columns. Additionally:

- `DataTable` only supports client-side sorting; paginated data needs
  server-side re-query on sort (see `columnFilters.md` CF2).
- `DataTable` has no native sticky header; filter inputs in `columnFilters.md`
  CF1 need to remain visible while rows scroll.
- `DataTable` has no native two-row header cells; `columnFilters.md` CF1.1
  needs label+sort on one line and a filter input on a second line per
  column.

### C1.2 Table Widget API

The table widget — `TrinaGrid` from `trina_grid` — must, at minimum:

- Accept a `columns` list with per-column width configuration.
  **Realized via** `TrinaColumn.width: double` (initial pixel width)
  combined with `TrinaColumn.minWidth` and the table's resize / reorder
  features. The compact actions column uses an explicit fixed width.
- Render header cells supporting a label row + optional filter-input
  row (see C1.6). **Realized via** `TrinaColumn.titleSpan` / a custom
  title widget rendering both rows; we deliberately bypass
  `trina_grid`'s built-in filter UI (see C1.10).
- Sticky header during vertical row scroll (see C1.7). **Realized
  natively** by `TrinaGrid`.
- Accept a controlled sort-state prop fed *into* the header so the glyph
  reflects the current state (see C1.8). **Realized via**
  `TrinaColumn.sort` plus the project-level sort glyph rendered in the
  custom title widget (we keep the existing `↕ / ↑ / ↓` glyph from
  `columnFilters.md` CF2.1 instead of `trina_grid`'s default arrows,
  for visual consistency).
- Click / keyboard handler on the label row that fires
  `onSort(field, direction)`; caller handles re-fetching with the new
  sort (see C1.4).
- Row striping consistent with `AppTheme.stripeColor(index)`.
- Keyboard / accessibility behavior per C1.9.

**On row heights and cell wrapping.** `trina_grid` (like Material
`DataTable` and `data_table_2`) uses a single fixed row height —
`TrinaGridConfiguration.style.rowHeight`, default ~45 px. Wrapping
multi-line cell content beyond the configured row height is not
supported out of the box; the variable-row-heights claim from
earlier drafts of this spec was not borne out by the docs and is
withdrawn. Mitigations for tables that need wrapping:

- **Truncate-with-tooltip** by default: cells render `Text` with
  `TextOverflow.ellipsis` and a `Tooltip` for the full value. This
  is the industry-standard pattern (Salesforce, Linear, GitHub
  issue lists) and is the v1 default.
- **Per-table `rowHeight` bump** for surfaces that have long content
  (e.g. description-heavy tables). `TrinaGridConfiguration.copyWith`
  produces a tailored configuration; the per-table override is
  isolated to that surface.
- **Detail popover / row-click → editor** as the escape hatch when
  truncation is unacceptable (the GRID flow already does this via
  `gridElement.md` G5).

If a future surface genuinely requires per-row content-driven height,
that's a separate evaluation — `trina_grid`'s `rowWrapper` callback
exists and *might* enable it, but the package's docs do not document
this pattern, and virtualization (`rowsCacheExtent`) suggests the
underlying assumption is uniform row height.

### C1.3 Column Model

`TrinaColumn` / `TrinaRow` / `TrinaCell` from `trina_grid` are used
directly — no project-level wrapper class is introduced. Adapter code
at the call site translates project column metadata (e.g.
`viewIntegration.md` `TableColumn`, `gridElement.md` `GridTableColumn`)
into `TrinaColumn`:

```dart
TrinaColumn(
  title: '',                       // header text rendered via titleSpan below
  field: column.key,
  type: TrinaColumnType.text(),    // display-only; we don't use in-cell editing (C1.10)
  enableEditingMode: false,
  enableFilterMenuItem: false,     // we render our own filter row (CF1)
  enableContextMenu: false,
  width: column.initialWidth ?? 160,
  minWidth: 80,
  titleSpan: WidgetSpan(
    child: _SortableFilterableHeader(
      column: column,
      labelRow: labelRow,          // sortable header row (CF2.1 / C1.8)
      filterInput: filterInput,    // CF1 widget when filterable (C1.6)
    ),
  ),
)
```

**Why no wrapper class.** The earlier draft sketched a
`TableComponentColumn` working shape to abstract over an undecided
backend. With `trina_grid` chosen, the abstraction adds no value;
direct use keeps the call sites simple. If the pure-Flutter `Table`
fallback is ever taken (C1.0), an adapter can be introduced at that
point — call sites already produce a structured per-column descriptor
(`TableColumn` / `GridTableColumn`) that drives whichever target type.

### C1.4 Sort + Filter Protocol

Sort and filter transport use the existing GraphQL data queries —
`viewData` for ENTITY_LIST and the GRID data query — extended per
`columnFilters.md` CF4 with:

- `userSort: [SortFieldInput!]`
- `userFilter: FilterNodeInput`

The table component does not own these protocol definitions. It only
surfaces the UI hooks (sort callback, filter input slot); the caller
translates UI state into the GraphQL args and re-fetches.

(Earlier drafts of this spec proposed REST query params `?sort=...&direction=...`.
Those are superseded by `columnFilters.md` CF4 and removed.)

### C1.5 Migration (historical record)

Done as one migration in 2026-04-28. All `DataTable` call sites
replaced with `TrinaGrid`:

- ENTITY_LIST pages (`app_view.dart` → `_buildEntityTable`).
- GRID tables inside DataForm editors (`form_renderer_view.dart` →
  `_buildField` GRID case).
- Pending-children rows folded into the same GRID `TrinaGrid` via
  metadata + `rowColorCallback` (no separate sub-table).
- **Future consumer:** Selectable GRID — when that track lands, a
  GRID used as a form-field picker source is another consumer
  (row-click selects the row's entity as the field's value). See
  the `Selectable GRID` pending item in `specifications.md`. Note:
  `ENTITY_SELECT` remains a dropdown and is not affected.

Concrete steps applied (kept here as a record):

- **Dropped nested-scrollable wrappers around the table.**
  `TrinaGrid` requires a bounded parent; the prior
  `Expanded → SingleChildScrollView → SizedBox(width: ∞) → DataTable`
  pattern became `Expanded → TrinaGrid` for ENTITY_LIST and
  `SizedBox(height: 320) → TrinaGrid` for the GRID inside a
  scrollable form.
- **Removed the `IntrinsicWidth` + `crossAxisAlignment: stretch`
  crutch** from the prior `sortableDataColumn`. `TrinaGrid` delivers
  bounded constraints to `titleRenderer`, so the workaround was
  no longer needed; the file `widgets/sortable_column_header.dart`
  was deleted, with `SortDirection` + `cycleSortDirection` moved
  to `widgets/grid/column_sort.dart`.
- **Replaced the actions-column workaround** —
  `AppTheme.headerWithActionsOffset`, `actionsOffset(...)`,
  `cellWithActions(...)`, `cellWithTrailingActions(...)`,
  `stripeColor(...)` — all dropped from `AppTheme`. Edit and delete
  icons now live in dedicated narrow `__edit` / `__delete` columns
  built via `buildTrinaActionColumn` (40 px each, suppressed from
  auto-size).
- **Reconciled styling.** `TrinaGridStyleConfig` mapped onto
  `AppTheme` via `lib/widgets/grid/trina_grid_theme.dart`
  (`trinaGridConfigForApp(...)`).
- **Applied C1.10 carve-outs.** `enableEditingMode: false`,
  `enableFilterMenuItem: false`, `enableContextMenu: false`,
  `enableSorting: false`, `enableColumnDrag: false` per column;
  `gridPadding: 0`, `gridBorderWidth: 0`, scrollbar gutter
  suppressed via `columnShowScrollWidth: false`.

### C1.6 Header Layout

Each column header is a two-row cell:

- **Row 1 — label row.** Column label + sort glyph (see C1.8). Clickable
  (or keyboard-activatable) to cycle sort state.
- **Row 2 — filter input row.** Rendered only when `filterable` is true.
  Content comes from `filterInputBuilder`. The widget does not interpret
  the builder's result — it only provides the slot and constrains it to
  the column's width.

When no column in the table has `filterable: true`, row 2 is omitted
entirely (no empty strip).

### C1.7 Sticky Header

The full header region (both rows) stays pinned while rows scroll
vertically. Horizontal scroll, if any, scrolls the header together with
the rows. This is a hard requirement for `columnFilters.md` CF1 — filter
inputs must remain reachable in long lists.

**Realized natively** by `TrinaGrid`'s built-in vertical scroll and
sticky heading row. No extra `ScrollController` orchestration is
required at the call site.

### C1.8 Sort State as Input

The table accepts the current sort state as a prop:

```dart
class SortState {
  final String? field;
  final SortDirection? direction;  // ASC | DESC | null
}
```

The header glyph (`↕` inactive, `↑` ascending, `↓` descending) is derived
from this prop, not from internal widget state. Clicking the label row
invokes `onSort` with the next state in the `none → asc → desc → none`
cycle (`columnFilters.md` CF2.2); the caller updates the prop and passes
it back.

### C1.9 Keyboard & Accessibility

**v1 — minimal:** the table component (including its header rows) is
**excluded from Tab traversal** via `Focus.skipTraversal` (or equivalent)
on the outer container. Mouse / pointer input remains fully functional;
keyboard navigation across the table itself is not provided. This keeps
the feature shippable without the tab-storm problem caused by two tab
stops per column × many columns.

**Later — revisit:** a finer-grained keyboard model (column-by-column
header navigation, `Enter`/`Space` to cycle sort, `Escape` to clear a
filter input, screen-reader labels for sort state) is deferred until
overall keyboard navigation for the app is designed holistically.

`trina_grid` ships its own desktop-grade keyboard model
(arrow-key cell navigation, copy/paste, etc.) which we deliberately
**do not surface** in v1 — see C1.10. When the holistic keyboard
design lands, that built-in model becomes a candidate to enable
selectively rather than building parallel keyboard plumbing.

### C1.10 Integration Boundary — What We Use vs. What We Bypass

`trina_grid` is a feature-rich grid framework. The project's own specs
(`columnFilters.md` CF1 for filter UI, `gridElement.md` G5 for
edit-in-frame editing) collide with several of those features. To
avoid throwing away or fighting recently-shipped work, the integration
is deliberately partial: we use `trina_grid` as a **presentation +
scroll + sticky-header + sort plumbing layer** and bypass features
whose UX is owned elsewhere.

**Used:**
- Display grid (rows / cells / column widths / resize / reorder).
- Sticky header (C1.7) and frozen columns where useful (e.g. an
  identifier column on wide tables).
- **`TrinaColumn.titleRenderer`** as the project's column-header hook.
  The renderer receives a tight `(width, height)` matching the
  column's resolved width (including user-resize updates), so the
  CF1 filter input row inside our header widget fills the column
  width through normal `crossAxisAlignment: stretch` propagation —
  no `IntrinsicWidth` workaround needed. This satisfies
  `columnFilters.md` CF1.1 ("filter input stretches to fill the
  column's available content width") *structurally*, not by
  per-widget width values.
- Sort plumbing — `TrinaColumn.sort` plus a custom title widget
  rendering the project's `↕ / ↑ / ↓` glyph (CF2.1) and dispatching
  `onSort` to the caller, which translates to the GraphQL `userSort`
  arg (`columnFilters.md` CF4 / CF5).
- Scrollbars, virtualization, row striping (mapped to `AppTheme`).
- Empty-state placeholder (`TrinaGrid.noRowsWidget`).
- Per-row background coloring via `TrinaGrid.rowColorCallback`
  (e.g. amber for pending rows in the GRID flow, replacing the
  current `WidgetStateProperty.all(...)` per-`DataRow` styling).

**Bypassed (we override or disable):**

- **In-cell editing.** `enableEditingMode: false` per column and on
  the grid as a whole. Row editing flows through the `EditorFrame`
  on the EditorStack per `gridElement.md` G5; row click pushes a
  child frame, no inline edit. This is non-negotiable — adopting
  `trina_grid`'s in-cell model would invalidate the entire G5 design.
- **Built-in column filter UI.** `enableFilterMenuItem: false`. The
  filter row (label + filter input) is rendered by the project's own
  `_SortableFilterableHeader` widget hosted in `TrinaColumn.titleSpan`,
  using the existing CF1 widgets (`StringFilterInput`,
  `EntityRefFilterInput`, etc.). The filter delegate model in
  `trina_grid` does not match CF1.2's typed-input shape, projected
  list filter (CF3.4.1), or `EntityRenderer.searchFields`/`sortFields`
  driven typeahead (CF3.5).
- **Built-in context menus** (`enableContextMenu: false`) — keep the
  surface clean; project-specific actions live in toolbars / row
  hover icons via `AppTheme.actionIcon`.
- **`trina_grid`'s keyboard model in v1** — surfaced focus at the
  grid level only (Tab in / out), per C1.9. Re-evaluate later.
- **Copy / paste / data export** — not exposed; not in scope for v1.
- **Row grouping** — not used; the project's domain doesn't
  group rows.
- **Multi-column sort UI** — single-column only per CF2; multi-column
  remains scoped to `columnFilters.md` CF7's advanced editor.

**Why the carve-outs matter for the dependency story.** The
project-side filter widgets, sort glyph, and editor flow live in
project code, not inside `trina_grid` configuration. If `trina_grid`
is ever swapped for the pure-Flutter `Table` fallback (C1.0), the
swap moves the **rendering** layer only — filter UX, sort UX,
EditorStack flow, and protocol all keep working unchanged.

### C1.11 Implementation Notes (recorded after migration)

Things learned during the v1 migration that future maintainers
should be aware of. None of these change the contracts above; they
explain the *implementation idioms* future contributors will see in
the code.

- **`TrinaGrid.didUpdateWidget` ignores `columns` and `rows`.**
  Trina captures both props at `initState`, then internal state
  takes over. Replacing the props on rebuild is silently dropped.
  The migration uses a **key-change-on-fetch** strategy: each
  successful fetch bumps `_gridGeneration` which is part of the
  `TrinaGrid.key`, forcing a remount with the fresh row set. Cost:
  user column-resize state resets per fetch — accepted v1 trade-off.
  Imperative `stateManager.removeAllRows + appendRows` was tried
  first but exhibited a subtle "rows in `refRows` but not in the
  visible body" failure that wasn't tracked down; the key-change
  path is deterministic.

- **Reactive header state via `Listenable` + `AnimatedBuilder`.**
  Because columns can't be replaced once mounted (above point), the
  sort glyph + filter widget can't be refreshed by rebuilding the
  column. Project pattern: `GridRebuildTrigger` (a tiny
  `ChangeNotifier` subclass exposing public `bump()`) is captured in
  the column's `titleRenderer` closure, wrapped in `AnimatedBuilder`
  around the header widget, and `bump()`-ed by the host whenever
  sort/filter state changes. The column object stays stable; the
  title widget re-renders.

- **`rowColorCallback` overrides `evenRowColor` / `oddRowColor`.**
  When the GRID surface engages `rowColorCallback` for pending-row
  amber tinting, it short-circuits the style-config zebra striping
  for *every* row — `rowColorCallback` returns a non-nullable
  `Color`, with no "fall through" sentinel. The callback must
  re-implement the alternation itself for committed rows (as the
  GRID's `_rowColor` does). The ENTITY_LIST surface, which doesn't
  use `rowColorCallback`, gets striping straight from style config.

- **`rows: const []` crashes.** Trina wraps the `rows` prop into a
  `FilteredList` whose `addAll` mutates the underlying list — passing
  a `const` (immutable) list throws `Unsupported operation: addAll`
  the moment any row mutation happens (which the key-change strategy
  triggers on every fetch via `stateManager.appendRows`). Always pass
  a fresh mutable `<TrinaRow>[]`-typed list.

- **Custom-`titleRenderer` columns lose Trina's resize affordance
  unless re-hosted.** Trina renders the resize handle (a `Listener`
  wrapping `IconButton` with `MouseCursor.resizeLeftRight`) only when
  using its default header layout. With a custom `titleRenderer`,
  Trina hands the `contextMenuIcon` widget to the renderer via
  `ctx.contextMenuIcon` and expects it to be hosted. The default icon
  is visually intrusive over filter inputs; the project draws its
  own thin invisible 6-px-wide handle (`_ColumnResizeHandle`) at the
  column's right edge, calling `stateManager.resizeColumn` directly.

- **Action icons live in dedicated narrow columns.** Edit / delete
  used to be embedded in the first / last data cell with offset
  padding. The migrated layout has an `__edit` column at the start
  and `__delete` column at the end (`buildTrinaActionColumn`), each
  40 px fixed and `suppressedAutoSize: true` so the data columns
  scale-fill the rest. This makes the first data column's *header*
  align naturally with its cell content (no offset gymnastics) and
  keeps icons out of the data flow.

- **Filter input vertical centring needs `TextAlignVertical(y: ~0.4)`.**
  Flutter's `TextField` has a baseline-vs-bounds asymmetry inside
  `InputDecorator`: at `y: 0` the text reads visibly above centre
  because glyph ascenders take more vertical room than descenders.
  Project standard is `kFilterTextAlignVertical = TextAlignVertical(y: 0.4)`
  (in `lib/widgets/filters/filter_field_style.dart`), the result of
  fine-tuning against the date-picker's `Text`-in-`Row(center)`
  baseline. Plus `floatingLabelBehavior: never` to defeat the global
  `inputDecorationTheme`'s implicit label space.

- **Trina's `_GridContainer` adds inner padding + border.** Defaults
  are `gridPadding: 2` and `gridBorderWidth: 1`, both painted at
  the inside edge of the grid. With a Card wrapper that already
  provides an outer border, both are redundant — the project sets
  both to `0` in `trinaGridConfigForApp` so column headers butt
  against the panel header and the only visible outer border is the
  Card's.

- **`TrinaGridScrollbarConfig.columnShowScrollWidth: false`** —
  default is `true`, which permanently reserves the scrollbar's
  ~8 px width on the right of the column header layout, leaving a
  visible gutter even when no scrolling is happening. Set to `false`
  so columns scale-fill the full table width and the scrollbar
  overlays the rightmost column when needed.

- **Per-column right-border on `titleRenderer`.** Trina's default
  cell border draws between cells, but with a custom `titleRenderer`
  the *header*-to-*header* boundary doesn't pick this up. The
  project's `buildTrinaColumn` and `buildTrinaActionColumn` paint a
  `BorderSide` on the right edge of the header `Container` to make
  data and action column boundaries identically visible.

- **Picker over-approximation in junction-table GRIDs (known
  limitation).** `columnFilters.md` CF3.4.1's projection drops
  COMPARISON nodes whose field doesn't start with `<columnKey>.`,
  which means an inventor-column picker on a
  `CameraLensMount2CameraProducer` GRID (filtered by
  `cameraProducer.id = X`) shows every inventor system-wide, not
  only inventors that have a row with producer `X`. Documented in
  CF3.4.1's correctness characterisation as a "safe over-
  approximation"; future work would either walk the relational path
  schema-aware, or expose a "DISTINCT-from-current-row-set"
  picker mode. Not blocking.

### Cross-References

- **Column filters**: `columnFilters.md` CF1 / CF2 — consumers of
  this widget's filter-input slot and sort API. CF1 v1 originally
  shipped on Material `DataTable`; the C1 migration re-hosted the
  widgets unchanged into `TrinaColumn.titleRenderer`.
- **Selectable GRID**: `specifications.md` pending item. Another future
  consumer of C1 — GRID used as a DataForm field's picker source, with
  row-click selecting that row's entity as the field's value. Column
  filters from CF1 apply inside the selectable GRID. `ENTITY_SELECT`
  stays a dropdown and is not affected.
- **GraphQL**: `graphql.md` and `columnFilters.md` CF4 — sort / filter
  protocol.
- **GRID element**: `gridElement.md` — uses tables for DB rows and
  pending-children rows.
- **ViewNode ENTITY_LIST**: `viewIntegration.md` — uses tables for entity
  lists.
- **Styling**: `frontendStyling.md` S1.2, S2 — header + row-striping
  styling that the C1 widget must honor.
- **Pagination**: already implemented; C1 extends it with server-side sort
  and filter.
