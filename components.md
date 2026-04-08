# Components Specification

## Task C1 — CyrodracsTable: Custom Table Widget

**Status:** Pending

**Goal:** Replace Flutter's `DataTable` with a custom `CyrodracsTable` widget that supports
per-column widths, server-side sorting with pagination, and a compact actions column.

### C1.1 Motivation

Flutter's `DataTable` distributes column width equally — there is no per-column width control.
This makes it impossible to have a narrow actions column alongside wider data columns.
Additionally, `DataTable` only supports client-side sorting, but our paginated data requires
server-side sorting (re-query with sort params).

### C1.2 CyrodracsTable Widget

A reusable table widget in `lib/widgets/` that:
- Uses Flutter's `Table` internally (supports `FixedColumnWidth`, `FlexColumnWidth`)
- Replicates header styling and row striping from `AppTheme`
- Accepts a `columns` list with per-column width configuration
- Supports clickable headers that fire `onSort(field, direction)` callback
- The caller handles sorting by re-fetching from the backend with sort params

### C1.3 CyrodracsColumn Model

```dart
class CyrodracsColumn {
  final String key;
  final String header;
  final ColumnWidth width;    // FixedColumnWidth, FlexColumnWidth, IntrinsicColumnWidth
  final bool sortable;
}
```

### C1.4 Backend: Sort Parameters

Add `sort` and `direction` query params to:
- `GET /api/view/{viewNodeCode}/data?page=0&size=10&sort=name&direction=ASC`
- `POST /api/view/grid-data/{dataFormCode}/{elementCode}?page=0&size=10&sort=name&direction=ASC`

The backend applies these to the JPA query, overriding (or supplementing) the configured
`SortField` entries on the EntityProvider.

### C1.5 Migration

Replace all `DataTable` usages (ViewNode lists, GRID tables, pending tables) with
`CyrodracsTable`. Remove the padding-based actions column workaround
(`AppTheme.headerWithActionsOffset`).

### Cross-References

- **GRID element**: `gridElement.md` — uses tables for DB rows and pending rows
- **ViewNode ENTITY_LIST**: `viewIntegration.md` — uses tables for entity lists
- **Pagination**: Already implemented, sorting extends it
