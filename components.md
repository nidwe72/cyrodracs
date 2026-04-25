# Components Specification

## Task C1 — Table Component Refactor

**Status:** Pending. Implementation **approach undecided** — see C1.0 below.

**Goal:** Replace Flutter's `DataTable` with a table component that supports
per-column widths, sticky header, server-side sorting, per-column filter
inputs (see `columnFilters.md` CF1), and a compact actions column.

**Non-blocking for column filters.** `columnFilters.md` CF1 / CF2 ship first
on the current `DataTable` with known cosmetic limitations (see `columnFilters.md`
CF1.1 interim note). Their widgets re-host onto whichever component C1 lands
without protocol or state-model changes.

### C1.0 Approach — Open Decision

The chosen implementation approach is **pending evaluation**. Candidates:

| Option | Notes |
|---|---|
| **Pure Flutter `Table`** (the earlier proposal, working name `CyrodracsTable`) | Max control, zero dependency risk, more work — sticky header, resizable columns, keyboard handling all built from scratch. |
| **`data_table_2`** | Close drop-in replacement for `DataTable` with per-column widths, fixed headers/rows. Smaller surface, but still missing some features. |
| **`pluto_grid`** | Feature-rich (sorting, filtering, column resize, freeze). Heavier; opinionated styling to reconcile with `AppTheme`. |
| **Syncfusion / other commercial** | Licensing considerations. |
| **Custom on top of `ScrollController` + `Table`** | Same as pure Flutter but with explicit sticky-header + horizontal scroll orchestration. |

**Evaluation criteria:**
- Per-column `FixedColumnWidth` / `FlexColumnWidth` support.
- Sticky column headers while rows scroll vertically.
- Two-row header cells (label+sort / filter) or a generic "header extra row"
  slot — required by `columnFilters.md` CF1.1.
- Sort-state *displayed* on header (not just callback).
- Integration with existing `AppTheme` header + row-striping styling
  (`frontendStyling.md` S1.2, S2).
- Horizontal scrolling for wide tables; actions column visually compact.
- Reasonable accessibility / keyboard behavior.
- Maintenance posture: last release cadence, open-issue count, license.

**Action item:** Evaluate candidates against the criteria, record findings
here, then pick one. Until that decision lands, the working name
`CyrodracsTable` is a placeholder and may or may not survive.

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

Regardless of implementation approach (C1.0), the replacement widget exposes
at minimum:

- A `columns` list with per-column width configuration.
- Header cells supporting a label row + optional filter-input row (see C1.6).
- Sticky header during vertical row scroll (see C1.7).
- A controlled sort-state prop fed *into* the header so the glyph reflects
  current state (see C1.8).
- A click / keyboard handler on the label row that fires an `onSort(field, direction)`
  callback; caller handles re-fetching with the new sort (see C1.4).
- Row striping consistent with `AppTheme.stripeColor(index)`.
- Keyboard / accessibility behavior per C1.9.

### C1.3 Column Model

Working shape (name placeholder per C1.0):

```dart
class TableComponentColumn {
  final String key;
  final String header;
  final ColumnWidth width;            // FixedColumnWidth, FlexColumnWidth, IntrinsicColumnWidth
  final bool sortable;
  final bool filterable;              // renders filter-input row when true
  final Widget Function(BuildContext)? filterInputBuilder;
  //   builds the filter input widget for the header's second row; owner-supplied
  //   so columnFilters.md CF1.2 widgets plug in without the table widget
  //   knowing about filter types.
}
```

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

### C1.5 Migration

Replace all `DataTable` usages once C1's approach is chosen:

- ENTITY_LIST pages (`app_view.dart` → `_buildEntityTable`).
- GRID tables inside DataForm editors (`form_renderer_view.dart` →
  `_buildField` GRID case).
- Pending-children tables (`gridElement.md` G7.6.4).
- **Future:** Selectable GRID — when that track lands, a GRID used as a
  form-field picker source is another C1 consumer (row-click selects
  the row's entity as the field's value). See the `Selectable GRID`
  pending item in `specifications.md`. Note: `ENTITY_SELECT` remains a
  dropdown and is not affected.

Remove the padding-based actions-column workaround
(`AppTheme.headerWithActionsOffset` in `frontendStyling.md`).

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

### Cross-References

- **Column filters**: `columnFilters.md` CF1 / CF2 — consumers of this
  widget's filter-input slot and sort API. The feature ships first on
  the interim `DataTable`; re-hosting onto the C1 widget is a migration,
  not a re-implementation.
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
