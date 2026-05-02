# Grid Element Specification

## Overview

This specification introduces the `GRID` DataFormElementType — an embedded table within a DataForm
editor that displays related entities. The GRID element uses an EntityProvider to fetch its rows,
but unlike standalone ENTITY_LIST ViewNodes, it operates within the context of the entity currently
being edited, enabling dynamic filtering.

**Motivating example:** When editing CameraProducer "Fuji", a GRID element shows all
`CameraLensMount2CameraProducer` rows where `cameraProducer` is Fuji. This includes both
proprietary mounts (X-Mount, created by Fuji) and adopted foreign mounts (M42, created by ZeissIkon).

---

## Implementation Status

| Component | Status |
|---|---|
| `GRID` enum value in `DataFormElementType` | Done |
| `DataFormElement.tableColumns` field + seeder + tree builder | Done |
| `GridDataService` + `GridDataController` (POST endpoint) | Done |
| `FilterInjectable` base class + `InjectableExecutor` (Janino) | Done |
| `EditorEntityBuilder` (form state → typed transient entity) | Done |
| `FilterExecutor` merge logic (static + injectable) | Done |
| `ExpressionResolver.resolveFilter()` | Done |
| Config seed data (Expression, EntityProvider, GRID element on cameraProducer form) | Done |
| Domain entities (CameraLensMount.producer, CameraLensMount2CameraProducer) | Done |
| Test data (Fuji, ZeissIkon, mounts, mappings) | Done |
| Frontend: AppConfig editor — Expression fields (type, baseClass, body, desc) | Done |
| Frontend: AppConfig editor — filterInjectableRef on EntityProvider | Done |
| Shared `ColumnRenderer` utility (dot-path getProperty, Mustache rendering) | Done (G3) |
| Parameterized `buildTableColumn` in tree builder (DRY) | Done (G4) |
| Frontend: GRID table rendering in DataForm editor | Done (G1.6) |
| Frontend: GRID pagination | Done (G1.6) — superseded by G1.6.8 (embedded GRIDs no longer paginate); pagination widget to be removed alongside the vertical-sizing implementation |
| Frontend: GRID add/edit/delete actions | Done (G5–G7) |
| Pending children for new parent entities | Done (G7.6) |
| Generic constraint violation error messages | Done (G7.7) |
| `AddAction` + `ContextBinding` models, seeder, tree builder | Done (G6) |
| `EditorStack` + stack path tree (frontend) | Done (G5) |
| Standalone ViewNode for CameraLensMount2CameraProducer | Done (G8) |
| `EntitySelectService` nested relationship rendering | Done (G6.8 fix) |
| Application-level cascade delete (`ViewDataService`) | Done (SQLite fix) |
| Application-level unique constraint check (`DataFormPersistenceService`) | Done (SQLite fix) |
| `ColumnRenderer.buildEntityContext` nested relationship support | Done (Mustache fix) |
| Embedded-GRID vertical hug-content sizing (G1.6.8) | Pending — next implementation target |
| Embedded-GRID row-count badge `(N)` / `(N of M)` (G1.6.9) | Pending — ships with G1.6.8 |

---

## Status Quo

Currently, the DataForm editor supports only scalar and single-entity-reference fields:
`INPUT_STRING`, `DATE_PICKER`, `ENTITY_SELECT`, etc. There is no way to display a collection
of related entities inline. The EntityProvider's FilterNode values are static strings — there is
no mechanism to bind a filter value to the entity currently being edited.

### Current Gaps

1. **No embedded table element.** DataFormElementType has no collection/table type.
2. **Static filter values.** FilterNode.value is always a literal string, making it impossible
   to filter by "the entity I'm currently editing".
3. **No runtime context for EntityProvider.** The filter executor has no access to the editor's
   current entity — it only sees the static AppConfig tree.

---

## Task G1 — GRID DataFormElementType

**Goal:** Add a `GRID` element type that renders as an embedded table within the DataForm editor,
sourced by an EntityProvider.

### G1.1 DataFormElementType Update

```java
public enum DataFormElementType {
    // ... existing types ...
    GRID      // Embedded table showing related entities
}
```

### G1.2 DataFormElement Extensions

The GRID element reuses the existing `entityProviderRef` to point at the EntityProvider that
fetches its rows, and the existing `entityRendererRef` pattern for column rendering. It adds
a reference to TableColumn definitions (reusing the existing `TableColumn` model from
viewIntegration.md).

```java
public class DataFormElement implements Coded {
    // ... existing fields ...

    /** GRID: column definitions for the embedded table. */
    List<TableColumn> tableColumns;
}
```

### G1.3 AppConfigType Rows (Seeder)

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `GridTableColumn` | `DataFormElement` | `tableColumns` | true | false | `...appconfig.TableColumn` |

The existing `TableColumn`, `TableColumnKey`, `TableColumnHeader`, and `TableColumnRendererRef`
types are reused — they are already registered with parent `ViewNode`, but now also appear under
`DataFormElement` for GRID elements. (The seeder may need a second `TableColumn` type entry with
parent `DataFormElement`, or the tree builder handles both parents.)

### G1.4 AppConfig Tree Example

```
AppConfig
└── dataForms:
    └── "cameraProducerForm" (DataForm)
        ├── entity: CAMERA_PRODUCER
        └── elements:
            ├── "name" (DataFormElement)
            │   ├── type: INPUT_STRING
            │   ├── dataBinding: "name"
            │   └── reloadOnChange: true      ← changing name triggers GRID reload
            ├── "foundationYear" (DataFormElement)
            │   ├── type: DATE_PICKER__YEAR_MONTH
            │   └── dataBinding: "foundationYear"
            └── "lensMountMappings" (DataFormElement)
                ├── type: GRID
                ├── entityProviderRef: "mountsForCurrentProducer"
                └── tableColumns:
                    ├── "col_mount"
                    │   ├── key: "cameraLensMount"
                    │   ├── header: "Lens Mount"
                    │   └── entityRendererRef: "lensMountCaption"
                    └── "col_mountProducer"
                        ├── key: "cameraLensMount.producer"
                        ├── header: "Original Creator"
                        └── entityRendererRef: "producerCaption"
```

### G1.5 REST API

```
POST /api/view/grid-data/{dataFormCode}/{elementCode}?page=0&size=10

Body:
{
    "entityId": 4,
    "formState": {
        "name": "Fuji",
        "foundationYear": "1934-01",
        "shutdownYear": null
    }
}
```

Returns a `PagedResponse` (same shape as V2's view data endpoint) for the GRID element's
EntityProvider. The request body carries the **current form state** so that the injectable
sees unsaved edits, not just the persisted entity. The server builds a transient typed entity
via `EditorEntityBuilder` (see `expressions.md` Task E7.7, E2.7).

### G1.6 Frontend Rendering

**Status:** Backend endpoint is fully implemented and returns correct filtered/rendered data.
The frontend currently shows a placeholder card. This section specifies the full rendering.

**Implementation location:** `form_renderer_view.dart` — the `DataFormElementType.grid` case
in the `_buildField()` switch.

#### G1.6.1 Widget Structure

```
Card
  └── Column
        ├── Header row: element label (e.g., "Lens Mounts") + row count badge
        ├── DataTable
        │     ├── columns: from tableColumns[].header
        │     └── rows: all matching rows from GRID endpoint response items[]
        │           (no pagination on embedded GRIDs — see G1.6.8)
        └── (Future) Action row: Add / Edit / Delete buttons
```

#### G1.6.2 Data Fetching

1. The GRID widget receives the current `entityId` and `formState` from the parent
   DataForm editor.
2. On mount (and on reload triggers), it POSTs to:
   ```
   POST /api/view/grid-data/{dataFormCode}/{elementCode}?page=0
   Body: { "entityId": <id>, "formState": { ... } }
   ```
   The `size` query parameter is **omitted** (or sent as `Integer.MAX_VALUE` /
   equivalent) — embedded GRIDs fetch all rows of the effective filter in one
   shot per G1.6.8. The endpoint signature is unchanged; only this caller's
   parameter use changes.
3. The `dataFormCode` comes from the parent DataForm's code (e.g., `"cameraProducer"`).
4. The `elementCode` comes from the GRID DataFormElement's code (e.g., `"lensMountMappings"`).
5. The response is a `PagedResponse` with `items` (list of row maps), `totalCount`, `page`,
   `pageSize`, `totalPages`. The pagination metadata in the response is **ignored** by
   embedded GRIDs (rendered count = `items.length` = `totalCount`); ENTITY_LIST
   surfaces continue to use it.

#### G1.6.3 Column Rendering

Each `tableColumns` entry defines a column:
- **header** → `DataColumn` label text.
- **key** → the key to look up in each row map. Supports dot-separated paths (e.g.,
  `"cameraLensMount"`, `"cameraLensMount.producer"`) — the backend resolves these by walking
  the getter chain (see Task G3). The final value is either rendered via EntityRenderer
  (if the value is a JPA entity and `entityRendererRef` is set) or used directly (primitives).
- The response already contains rendered values (Mustache-applied for relationship columns),
  so the frontend simply displays `row[key].toString()`.

#### G1.6.4 Reload Triggers

The GRID re-fetches data when:
- **Initial load** — when the DataForm editor opens in edit mode (entityId is known).
- **`reloadOnChange` sibling** — when any sibling DataFormElement with `reloadOnChange: true`
  fires an `onChange` event. The parent DataForm editor debounces these (300ms) and passes
  the updated `formState` to all GRID elements for re-fetch.
- **Filter / sort change** — any per-column filter input change (debounced per
  `columnFilters.md` CF1.4) or sort glyph cycle (CF2) re-issues the fetch.

The GRID does NOT fetch in "create new" mode (entityId is null) unless the injectable explicitly
handles null and returns data.

**No pagination — fetch all rows in one shot.** Embedded GRIDs render every matching row
of the effective filter (no page splitting, no pagination bar — see G1.6.8). The
frontend **omits** the `size` query parameter; the backend treats a missing or `null`
`size` as "all rows" — i.e. the contract is *self-describing*, not driven by a magic
sentinel like `Integer.MAX_VALUE`. ENTITY_LIST callers continue to send an explicit
`size` and receive the existing paged behaviour. There is no per-GRID size limit
specified at this stage; should a real use case surface a producer-entity GRID with a
problematic row count, a cap (with a "showing first N of M" note) is the natural
follow-up and is not pre-spec'd here.

#### G1.6.5 Loading / Empty States

| State | Display |
|---|---|
| First-load (no rows yet) | Centered `CircularProgressIndicator` inside the Card body |
| Reload (rows already on screen) | Previous rows stay rendered; the toolbar's Reload icon swaps for a same-sized `CircularProgressIndicator` until the response lands. Layout does not reflow. |
| Empty (0 rows) | "No entries" message in place of the data-row block |
| Error (endpoint fails) | Error message with retry button |
| Create-new mode (no entityId) | "Save the record first to see related entries" |

#### G1.6.6 AppConfig JSON Contract

The GRID element's `tableColumns` are serialized in the AppConfig JSON response. The frontend
needs to read them from the DataFormElement:

```json
{
  "code": "lensMountMappings",
  "type": "GRID",
  "entityProviderRef": "mountsForCurrentProducer",
  "tableColumns": [
    { "code": "col_mount", "key": "cameraLensMount", "header": "Lens Mount", "entityRendererRef": "lensMountCaption" },
    { "code": "col_mountProducer", "key": "cameraProducer", "header": "Producer", "entityRendererRef": "producerCaption" }
  ]
}
```

The `tableColumns` field is already populated by the backend (`AppConfigTreeBuilder` builds
`GridTableColumn` children into `DataFormElement.tableColumns`). The frontend DataFormElement
model needs to parse this array.

#### G1.6.7 Frontend Model Update (DataFormElement)

The Dart `DataFormElement` model (`models/data_form_element.dart`) needs a `tableColumns` field:

```dart
class DataFormElement {
  // ... existing fields ...
  final List<GridTableColumn> tableColumns;
}

class GridTableColumn {
  final String key;
  final String header;
  final String? entityRendererRef;

  const GridTableColumn({required this.key, required this.header, this.entityRendererRef});

  factory GridTableColumn.fromJson(Map<String, dynamic> json) {
    return GridTableColumn(
      key: json['key'] as String,
      header: json['header'] as String,
      entityRendererRef: json['entityRendererRef'] as String?,
    );
  }
}
```

#### G1.6.8 Vertical Sizing

> **🔜 Next implementation target.** Closes a layout gap noticed during
> the C1 / TrinaGrid migration: embedded GRIDs currently expand to fill
> the parent's available vertical space, which makes the parent
> DataForm scroll oddly when the GRID sits among scalar fields. The
> embedded `lensMountMappings` GRID inside the CameraProducer editor
> is the canonical example.

**Goal.** The embedded GRID **hugs its content vertically** — its
height is whatever the toolbar + header + actual rendered rows add up
to, with **no hard-coded dimensions**. The parent DataForm decides
its own scroll boundary; the GRID never adds an internal vertical
scrollbar.

**Effective height.** Sum of:

- Toolbar (Add / Reload / Clear Filters per `columnFilters.md` CF1.6,
  when present).
- Header rows: label row (column titles + sort glyph) + filter input
  row (CF1.1's two-row header).
- Data-row block — exactly `rowCount × rowHeight`. No `pageSize`
  reservation, no padding-up to a target row count: just the rows
  the fetch returned (see *No pagination* below).

There is no pagination bar — embedded GRIDs do not paginate.

**No pagination.** Embedded GRIDs fetch all matching rows of the
effective filter and render them all (G1.6.4). This means the GRID's
height tracks the actual data: 3 rows on Fuji's mount mappings →
3-row-tall data block, 30 rows → 30-row-tall data block. The parent
DataForm scrolls if the resulting GRID exceeds its viewport, which is
the conventional behaviour for a tall element inside a scrollable
form — far more predictable than internal pagination on an embedded
control.

**State variants.**

- **Empty (0 rows)**: data-row block collapses to a single
  empty-state row (G1.6.5). The GRID is at its minimum height
  (toolbar + header + one row).
- **First-load (no rows yet on screen)**: data-row block shows a
  centred `CircularProgressIndicator` at the same single-row height.
  No `pageSize`-tall reservation — the height grows when data lands.
- **Reload (rows already on screen)**: previous rows stay rendered;
  the toolbar's Reload icon swaps for a same-sized
  `CircularProgressIndicator`. Layout does not reflow during the
  reload window. This is the steady-state filter / sort experience
  (G1.6.5).
- **Create-new-mode placeholder** (G1.6.5 *"Save the record first…"*):
  one row tall.

**No magic numbers.** The data-row height is `rowCount × rowHeight`,
where `rowHeight` is the TrinaGrid row metric defined in
`trina_grid_theme.dart` (currently 44, derived from one line of cell
text + cell padding — content-derived, not arbitrary). The header
height is the same metric pair (`columnHeight = 88` with the filter
row, `44` without — same content-derived basis). The GRID host
introduces no hard-coded heights of its own. The legacy
`SizedBox(height: 320)` wrapper around the host TrinaGrid (today at
`form_renderer_view.dart` ~ line 2032) is replaced with the computed
height; that magic number disappears.

**`rowCount` is the count of effective rows on screen.** In code:
`rowCount = _effectiveRows().length`. The `noRowsWidget` already only
fires when `_effectiveRows()` is empty, so the same source of truth
drives the empty-state message and the height — no new branching
introduced.

`_effectiveRows()` is filter-aware on both sides: backend-filtered
committed rows for edit mode, *plus* client-side-filtered pending
rows from the stacked editor (per G7.6) — see G1.6.9's *Pending rows*
note and `columnFilters.md` CF1.5 for how the client-side pending
filter mirrors server semantics.

**Today's reality:** committed and pending rows are *mutually
exclusive* on a given GRID instance — `_effectiveRows()` returns
pending in create-new mode (`entityId == null`) and committed in
edit mode (`entityId != null`). So every concrete row-count today
falls under exactly one of the two row-source columns below. The
formula `rowCount = visibleCommitted + visiblePending` works either
way (one term is always 0); if the model ever changes to allow
coexistence, the height computation needs no revision.

| Mode | visibleCommitted | visiblePending | GRID body |
|---|---|---|---|
| Create-new, no pending | 0 | 0 | one-row-tall (empty-state message) |
| Create-new, 2 pending, no filter | 0 | 2 | two-row-tall (pending rows render with their pending colouring per S3.3) |
| Create-new, 2 pending, filter narrows to 1 | 0 | 1 | one-row-tall (the matching pending row) |
| Edit, no committed (genuinely empty) | 0 | 0 | one-row-tall (empty-state message) |
| Edit Fuji (3 committed), no filter | 3 | 0 | three-row-tall |
| Edit Fuji, filter narrows to 1 | 1 | 0 | one-row-tall |
| Edit producer, 30 committed | 30 | 0 | thirty-row-tall (parent DataForm scrolls if needed) |

**No internal vertical scroll.** The GRID never introduces its own
vertical scrollbar. Horizontal scroll within the GRID remains as
today (TrinaGrid handles column overflow when the table is narrower
than the sum of column widths).

**TrinaGrid integration.** TrinaGrid is a virtualised list and
*requires* a bounded height from its parent — it cannot shrink-wrap
to its rows. The host wraps the TrinaGrid widget in a sized box
whose height is computed per the rules above; the rest of the GRID
card (toolbar, header, optional empty/loading state) is laid out by
a `Column` whose intrinsic height equals the sum of its children.
A small pure helper (e.g. `computeGridBodyHeight(rowCount,
columnHeight, rowHeight, ...)`) belongs in
`trina_grid_adapter.dart` so the host call site reads as content,
not arithmetic.

**Surface scope.** This rule covers the **embedded GRID** inside a
DataForm only. ENTITY_LIST ViewNodes occupy a top-level scaffold body,
keep their existing pagination (per the same paged endpoint), and
continue to fill the available vertical space the conventional way —
they are not in scope here.

#### G1.6.9 Row-Count Badge

The GRID's panel header (S3.2) shows a small count badge after the
label, using one of two formats:

| Situation | Badge | Example |
|---|---|---|
| Result set is not narrowed by any column filter | `(N)` | `(10)` |
| A column filter narrows the result set | `(N of M)` | `(2 of 10)` |

**Uniform formula.** The badge is computed from four counts, two
per row source:

```
N = visibleCommitted + visiblePending     // rows actually rendered
M = totalCommitted   + totalPending       // rows that exist regardless of filter
```

Where:
- `visibleCommitted` = backend-filtered count of committed rows
  (today: `_trinaRows.length` after the server applied `userFilter`).
- `totalCommitted` = baseline count of committed rows — same effective
  filter as the row fetch but with `userFilter` stripped out (the
  new `baselineTotal` field on `PagedResponse`).
- `visiblePending` = client-side-filtered count of pending rows
  (the same column-filter predicates that drive the backend query,
  evaluated locally — see *Pending rows* below and `columnFilters.md`
  CF1.5).
- `totalPending` = `pendingRows.length` (all pending rows, regardless
  of any column filter).

In create-new mode (`entityId == null`) `visibleCommitted` and
`totalCommitted` are both 0; in edit mode `visiblePending` and
`totalPending` are both 0 (today's mutual-exclusivity per G1.6.8 *Today's
reality*). The formula degenerates to the right answer in either
case without mode branching.

**Display rule.** Show `(N of M)` only when `N != M`; otherwise the
plain `(N)` form, to avoid noise (`(10 of 10)` says nothing). When
both are 0 the empty-state message owns the slot — no badge.

**Worked examples** (matching G1.6.8's table — every scenario you can
hit today):

| Scenario | visC | visP | N | totC | totP | M | Badge |
|---|---|---|---|---|---|---|---|
| Create-new "Acme", 0 pending | 0 | 0 | 0 | 0 | 0 | 0 | *no badge* |
| Create-new "Acme", 2 pending, no filter | 0 | 2 | 2 | 0 | 2 | 2 | `(2)` |
| Create-new "Acme", 2 pending, filter narrows to 1 | 0 | 1 | 1 | 0 | 2 | 2 | `(1 of 2)` |
| Edit producer, no committed rows | 0 | 0 | 0 | 0 | 0 | 0 | *no badge* |
| Edit Fuji (3 committed), no filter | 3 | 0 | 3 | 3 | 0 | 3 | `(3)` |
| Edit Fuji, filter narrows to 1 | 1 | 0 | 1 | 3 | 0 | 3 | `(1 of 3)` |
| Edit Fuji, filter excludes everything | 0 | 0 | 0 | 3 | 0 | 3 | `(0 of 3)` |

`(0 of 3)` is intentional — it tells the user the empty state is
filter-induced rather than genuine; clearing the filter restores rows.

**Backend contract.** `PagedResponse` gains a new optional
`baselineTotal: Long` field. For embedded-GRID responses it is
populated by an extra count query: same effective filter as the row
fetch but with `userFilter` stripped out. ENTITY_LIST responses leave
the field `null`; the field is opt-in per caller. Implementation:
`GridDataService` runs the existing count query (with `userFilter`,
gives `totalCount`), then a second count query (without `userFilter`,
gives `baselineTotal`). Two counts per fetch — negligible cost
relative to the row fetch itself.

**Pending rows.** Pending rows from the stacked editor (per G7.6)
**are subject to the same column filters as committed rows**,
evaluated client-side because the backend has no knowledge of
pending state. The client predicate matches server semantics per
`columnFilters.md` CF1.5: STRING `LIKE %v%` (case-insensitive),
NUMBER / DATE / YEAR_MONTH range, BOOLEAN / ENUM / ENTITY_REF
equality. Both `visiblePending` (filtered) and `totalPending`
(unfiltered) feed the badge formula above so a pending row that's
hidden by the active filter is correctly reflected in
`N != M` → `(N of M)`.

---

## Task G2 — Example: Fuji Lens Mounts

**Goal:** Demonstrate the GRID element with the CameraProducer "Fuji" use case.

### G2.1 Test Data

| Entity | Data |
|---|---|
| CameraProducer | id=4, name="Fuji", foundationYear=1934-01 |
| CameraLensMount | id=3, name="X-Mount", producer_id=4 (Fuji) |
| CameraLensMount | id=1, name="M42", producer_id=1 (ZeissIkon) |
| CameraLensMount2CameraProducer | id=3, camera_lens_mount_id=3 (X-Mount), camera_producer_id=4 (Fuji) |
| CameraLensMount2CameraProducer | id=4, camera_lens_mount_id=1 (M42), camera_producer_id=4 (Fuji) |

**Note:** Both X-Mount and M42 get a `CameraLensMount2CameraProducer` row linking them to Fuji.
X-Mount's original creator is Fuji, but the mapping table captures "Fuji uses this mount" regardless
of who created it. This gives a complete picture: every mount a producer uses, in one table.

### G2.2 EntityProvider Configuration

No static filter — the entire restriction is produced by a `FilterInjectable`:

```
expressions:
  └── "producerMountFilter" (Expression)
      ├── type: INJECTABLE_CLASS
      ├── baseClass: FILTER
      └── expression: |
              @Override
              public void execute() {
                  CameraProducer p = getInjectionContext()
                      .getEditorEntity(CameraProducer.class);
                  if (p == null) {
                      setResult(null);
                      return;
                  }
                  setResult(
                      comparison("cameraProducer.id", FilterOperator.EQUALS, p.getId())
                  );
              }

entityProviders:
  └── "mountsForCurrentProducer" (EntityProvider)
      ├── entityType: CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER
      ├── filter: (none)
      ├── filterInjectableRef: "producerMountFilter"
      └── sortFields:
          └── (SortField)
              ├── field: "cameraLensMount.name"
              └── direction: ASC
```

The injectable builds the filter dynamically from the editor entity — see `expressions.md`
Task E2.8 for the full runtime flow.

### G2.3 Expected GRID Output (editing Fuji)

| Lens Mount | Original Creator |
|---|---|
| M42 | ZeissIkon |
| X-Mount | Fuji |

---

## Task G3 — Shared Column Renderer with Dot-Path Support

**Goal:** Extract the duplicated column rendering logic from `ViewDataService` and `GridDataService`
into a shared `ColumnRenderer` utility, and add dot-path traversal for column `key` values.

### G3.1 Motivation

Both `ViewDataService` (ENTITY_LIST tables) and `GridDataService` (GRID elements) contain
near-identical private methods for column value resolution:

- `getProperty(entity, fieldName)` — single-level getter invocation
- `buildEntityContext(entity)` — JPA metamodel introspection for Mustache context
- `isJpaEntity(clazz)` — class hierarchy walk for `@Entity` annotation
- `getId(entity)` — reflective `getId()` call
- `resolveEntityClass(entity)` — Hibernate proxy resolution

This violates DRY and means bug fixes or enhancements (like dot-path support) must be applied
in two places.

### G3.2 `ColumnRenderer` Utility

A new shared Spring `@Component` class `ColumnRenderer` in the `appconfig.service` package that
encapsulates all column value resolution logic.

```java
@Component
public class ColumnRenderer {

    private final EntityManager entityManager;

    /**
     * Resolves a single column value from an entity using a dot-separated key path.
     *
     * Examples:
     *   "name"                    → entity.getName()
     *   "cameraLensMount"         → entity.getCameraLensMount()  (returns JPA entity)
     *   "cameraLensMount.name"    → entity.getCameraLensMount().getName()
     *   "cameraLensMount.producer"→ entity.getCameraLensMount().getProducer()
     */
    public Object resolveValue(Object entity, String dotPath) { ... }

    /**
     * Resolves a column value and applies EntityRenderer if the final value is a JPA entity.
     * Returns a display-ready Object (String for rendered entities, raw value for primitives).
     */
    public Object resolveAndRender(Object entity, String dotPath, Template rendererTemplate) { ... }

    /**
     * Builds a Mustache context map from a JPA entity's basic (non-relationship) attributes.
     */
    public Map<String, Object> buildEntityContext(Object entity) { ... }

    public Long getId(Object entity) { ... }
    public boolean isJpaEntity(Class<?> clazz) { ... }
    public Class<?> resolveEntityClass(Object entity) { ... }
}
```

### G3.3 Dot-Path `resolveValue`

The `resolveValue` method walks the dot-separated path by calling successive getters:

```java
public Object resolveValue(Object entity, String dotPath) {
    String[] segments = dotPath.split("\\.");
    Object current = entity;
    for (String segment : segments) {
        if (current == null) return null;
        current = getProperty(current, segment);
    }
    return current;
}
```

This follows the same convention as `FilterExecutor.walkPath()` (which does dot-path traversal
for JPA Criteria paths) and `DataFormElement.dataBinding` (which uses dot-paths for entity
attribute binding).

### G3.4 Migration of Existing Services

Both `ViewDataService` and `GridDataService` inject `ColumnRenderer` and delegate to it:

```java
// Before (in both services):
Object value = getProperty(entity, key);
if (value != null && isJpaEntity(value.getClass())) { ... }

// After:
Object rendered = columnRenderer.resolveAndRender(entity, col.getKey(), rendererTemplate);
row.put(col.getKey(), rendered);
```

The private `getProperty`, `buildEntityContext`, `isJpaEntity`, `getId`, and `resolveEntityClass`
methods are removed from both services.

### G3.5 Impact on Existing Column Keys

Existing single-segment keys (e.g., `"name"`, `"cameraLensMount"`) continue to work unchanged —
they are simply one-segment dot-paths. No config migration is needed.

---

## Task G4 — Parameterized Tree Builder for TableColumn

**Goal:** Eliminate the duplicated `buildTableColumn` / `buildGridTableColumn` methods in
`AppConfigTreeBuilder` by parameterizing the child type code strings.

### G4.1 Current State

`AppConfigTreeBuilder` has two near-identical methods:

- `buildTableColumn(entity, childrenByParentId)` — matches `"TableColumnKey"`,
  `"TableColumnHeader"`, `"TableColumnRendererRef"`
- `buildGridTableColumn(entity, childrenByParentId)` — matches `"GridTableColumnKey"`,
  `"GridTableColumnHeader"`, `"GridTableColumnRendererRef"`

Both produce the same `TableColumn` object with the same logic; only the child type code
strings differ.

### G4.2 Refactored Method

Replace both methods with a single parameterized method:

```java
private TableColumn buildTableColumn(AppConfigObjectEntity entity,
                                      Map<Long, List<AppConfigObjectEntity>> childrenByParentId,
                                      String keyTypeCode, String headerTypeCode,
                                      String rendererRefTypeCode) {
    TableColumn column = new TableColumn();
    column.setId(entity.getId());
    column.setCode(entity.getCode());

    for (AppConfigObjectEntity child : childrenOf(entity.getId(), childrenByParentId)) {
        String childTypeCode = child.getType().getCode();
        if (keyTypeCode.equals(childTypeCode)) {
            column.setKey(child.getCode());
            column.setKeyNodeId(child.getId());
        } else if (headerTypeCode.equals(childTypeCode)) {
            column.setHeader(child.getCode());
            column.setHeaderNodeId(child.getId());
        } else if (rendererRefTypeCode.equals(childTypeCode)) {
            column.setEntityRendererRef(child.getCode());
            column.setEntityRendererRefNodeId(child.getId());
        }
    }
    return column;
}
```

### G4.3 Call Sites

```java
// In buildViewNode():
} else if ("TableColumn".equals(childTypeCode)) {
    node.getTableColumns().add(buildTableColumn(child, childrenByParentId,
            "TableColumnKey", "TableColumnHeader", "TableColumnRendererRef"));
}

// In buildDataFormElement():
} else if ("GridTableColumn".equals(childTypeCode)) {
    element.getTableColumns().add(buildTableColumn(child, childrenByParentId,
            "GridTableColumnKey", "GridTableColumnHeader", "GridTableColumnRendererRef"));
}
```

### G4.4 AppConfig Type System

The separate type entries in the seeder (`TableColumn` / `GridTableColumn` with their
respective children) remain as-is. Each type entry has a single parent (`ViewNode` or
`DataFormElement`), which is a fundamental constraint of the `AppConfigTypeEntity` model
(single `@ManyToOne parentType`). The duplication at the type level is small (4 rows each)
and unavoidable without a multi-parent schema change, which would add complexity
disproportionate to the benefit.

---

## Task G5 — EditorStack: Stacked Navigation for Nested Editing

**Goal:** Introduce an EditorStack navigation model that replaces modal dialogs with a composable,
recursive stack of full-width editor views, connected by a breadcrumb trail.

### G5.1 Motivation

The GRID element currently displays related entities but offers no way to add or edit them.
The naive approach — a modal dialog — breaks down at recursion: if the child editor itself
contains a GRID that needs an "add" action, modals stack awkwardly. A stacked full-width editor
model is inherently recursive, composable, and gives each editor proper screen space.

### G5.2 Concept: EditorStack

The frontend maintains an **EditorStack** — an ordered list of **EditorFrame** objects. Each
frame represents one active editor context.

```
EditorStack
├── Frame 0: CameraProducer "Fuji" (dataForm: cameraProducerForm, entityId: 4)
└── Frame 1: New CameraLensMount2CameraProducer (dataForm: lensMountMappingForm, entityId: null)
              contextBindings: { cameraProducer: 4 }
```

**Rules:**
- Only the **topmost frame** (highest index) is visible and interactive.
- Parent frames are **preserved in memory** — unsaved edits, scroll position, form state remain
  intact while a child frame is on top.
- Parent frames are **non-editable** while a child frame is on top. They are frozen — no user
  interaction, no programmatic mutation. This prevents side effects: the child's contextBindings
  were resolved from the parent's state at push time, and that state must not change while the
  child is active.
- **Push** adds a new frame on top (triggered by GRID add/edit actions).
- **Pop** removes the topmost frame, revealing the parent. The parent GRID reloads if the child
  performed a save.

### G5.3 Stack Path Tree

The stack path is rendered as a **vertical tree** above the editor area, mirroring the
visual language of the left-side navigation tree. The root is the ViewNode that started
the flow, followed by each EditorFrame as an indented child. The active (current) node
is highlighted.

**At stack depth 2 (editing within a child editor):**
```
▸ Camera Producers                          ← ViewNode (clickable → back to list)
  ▸ CameraProducer: Fuji                   ← Frame 0 (clickable → pops to here)
    ● New LensMount Assignment              ← Frame 1 (active, highlighted)
```

**At stack depth 1 (normal editing, no child pushed):**
```
▸ Camera Producers                          ← ViewNode (clickable → back to list)
  ● CameraProducer: Fuji                   ← Frame 0 (active, highlighted)
```

**At stack depth 0 (list view, no entity opened):**
```
● Camera Producers                          ← ViewNode label (active, highlighted)
```

**Rendering rules:**
- Each line is icon + label, indented by level. Compact, left-aligned above the editor.
- `▸` for non-active nodes (clickable). `●` for the active node (not clickable).
- Clicking a non-active node pops all frames above it. If any popped frame has unsaved
  changes, a **single** confirmation dialog is shown (not per-frame).
- The ViewNode root is always present — clicking it pops the entire stack and returns to
  the list table view.

**Label resolution:**
- **ViewNode root:** Uses `ViewNode.label` (e.g., "Camera Producers").
- **Existing entity frame:** `{DataForm.entity.label}: {entityRendererRef output}` — e.g., "CameraProducer: Fuji".
- **New entity frame:** `"New {DataForm.entity.label}"` — e.g., "New LensMount Assignment".
- The label can also be overridden via `addAction.childLabel` (see G6).

### G5.4 EditorFrame Model (Frontend)

```dart
class EditorFrame {
  final String dataFormCode;
  final Long? entityId;              // null for create-new
  final Map<String, dynamic> contextBindings;  // pre-seeded field values
  final String? breadcrumbLabel;     // override label, or null for auto
  final String? sourceElementCode;   // which element triggered this push (for reload on pop)

  // Preserved state (set while frame is active, restored when frame becomes top again)
  Map<String, dynamic>? formState;
  double? scrollOffset;

  // Pending children: child entities "saved" while this frame has no entityId.
  // Stored here until this frame is persisted, then sent to backend together.
  List<PendingChild> pendingChildren = [];
}

class PendingChild {
  final String dataFormCode;           // child DataForm
  final String contextBindingTarget;   // which field in child receives parent ID
  final Map<String, dynamic> values;   // child form values (without parent reference)
  final String? sourceElementCode;     // which element triggered (for GRID display)
  final List<PendingChild> pendingChildren;  // recursive: grandchildren
}
```

### G5.5 EditorStack State Management

The EditorStack lives at the **view level** (the detail panel), not globally. Each ENTITY_LIST
ViewNode activation starts with a fresh stack containing a single frame (or zero frames if
showing the list table). Navigating to a different ViewNode discards the stack entirely (with
unsaved-changes warning if applicable).

**State transitions:**

| Action | Effect |
|---|---|
| User opens entity from list table | Push frame 0: `{dataFormCode, entityId}` |
| User clicks "Add" on an element | Push frame N+1: `{targetDataFormCode, null, contextBindings}` |
| User clicks "Edit" on a GRID row | Push frame N+1: `{targetDataFormCode, rowEntityId, contextBindings}` |
| User saves in child frame (parent has ID) | Persist child immediately, pop frame, reload parent element |
| User saves in child frame (parent has no ID) | Store as pendingChild on parent frame, pop frame, update parent element display |
| User cancels / clicks stack path parent | Pop frame(s), no reload, discard pending data from popped frame |
| User navigates to different ViewNode | Discard entire stack (with unsaved warning) |

### G5.6 Navigation Tree Interaction

The left-side navigation tree remains **fully interactive** while the editor stack is deep.
Clicking a different ViewNode triggers the standard unsaved-changes warning if any frame in
the stack has pending edits. On confirmation, the entire stack is discarded and the new
ViewNode is activated. Locking or dimming the navigation tree would feel restrictive and
is not necessary — the unsaved-changes warning is sufficient protection.

### G5.7 Unsaved Changes Handling

When an action would pop one or more frames with unsaved changes (clicking a stack path
ancestor, navigating away, or cancelling):
- A **single** confirmation dialog is shown: "You have unsaved changes. Discard and go back?"
- Not one dialog per frame — that would be tedious and confusing.
- The dialog does not enumerate which frames have changes; it simply warns that changes exist.

### G5.8 Stack Depth

No artificial limit on stack depth. The EditorStack is technically recursive and unbounded.
However, deep stacking (3+ levels) is questionable UX — it is not actively encouraged or
designed for. The practical depth for current use cases is 2 (parent editor + child creation
from GRID). The architecture allows deeper stacking if a future use case demands it, but
no special effort is spent optimizing the UX for depth > 2.

### G5.9 Backend Implications

The EditorStack navigation (push/pop, stack path tree, state preservation) is a **purely
frontend concern**. The backend change is limited to one extension:

- `DataFormPersistenceService.save()` and `.load()` work with any DataForm code and entity ID.
- Context bindings are applied client-side before the save request — the backend receives a
  normal `DataFormData` with pre-filled values.
- No new endpoints are required for the stack navigation itself.
- **Pending children:** The existing save endpoint is extended to accept an optional
  `pendingChildren` list. See G7.6 for the full specification of this mechanism.

---

## Task G6 — AddAction and Context Bindings

**Goal:** Define how a DataFormElement declares its "Add" (and "Edit") action, including which
DataForm to open and which field values to pre-seed from the parent context. While the first
use case is the GRID's [+] button, the `AddAction` and `ContextBinding` models are generic
and reusable by other element types (e.g., a future "create new" option on ENTITY_SELECT).

### G6.1 Design Principle: One DataForm, Dynamic Behavior

A DataForm defines **structure** — what fields exist, their types, their data bindings.
Context bindings define **runtime behavior** — which fields are pre-filled and locked in a
given invocation. These are two separate concerns and must not be conflated.

This means: there is always **one** DataForm per entity type (e.g., `lensMountMappingForm`
for `CameraLensMount2CameraProducer`). The same DataForm is used regardless of how it is
opened — standalone from the main tree, or pushed from a parent GRID. The difference lies
entirely in the **caller's context bindings**, not in the form definition.

**Analogy — partial function application:**

| Invocation | Context bindings | Editable fields |
|---|---|---|
| Standalone (from ViewNode) | `{}` (none) | cameraProducer, cameraLensMount |
| From CameraProducer GRID | `{ cameraProducer: ENTITY }` | cameraLensMount |
| From CameraLensMount GRID (future) | `{ cameraLensMount: ENTITY }` | cameraProducer |

The DataForm is the function signature. The contextBindings are partial arguments. This
scales naturally: any number of parent contexts can reuse the same form, each binding
different fields.

**Consequences:**
- **No duplicate DataForms.** Never create a "standalone variant" and a "stacked variant"
  of the same form. The form is always the same.
- **Read-only is runtime, not config.** A DataFormElement does NOT have a static `readOnly`
  flag for this purpose. The read-only state is determined dynamically by the frontend:
  "is this element's code present in the active contextBindings?" If yes → read-only.
  If no → editable.
- **Pre-fill is runtime, not config.** The initial value of a context-bound field comes from
  the resolved binding expression, not from a default value in the DataForm.
- **The backend is unaware.** `DataFormPersistenceService.save()` receives a flat
  `DataFormData` with all field values — it does not know or care which were context-bound.
  The frontend includes the bound values in the save request like any other field value.

### G6.2 AddAction on DataFormElement

The DataFormElement gains a new optional sub-object `addAction` that configures the "Add"
button behavior. Currently used by GRID elements; the model is generic and can be reused
by other element types in the future.

```java
public class AddAction implements Coded {
    Long id;
    String code;
    String targetDataFormRef;             // which DataForm to push onto the EditorStack
    Long targetDataFormRefNodeId;
    List<ContextBinding> contextBindings; // injected values from parent
    String childLabel;                    // optional stack path label override
    Long childLabelNodeId;
}

public class ContextBinding implements Coded {
    Long id;
    String code;
    String target;        // child DataForm element code (e.g., "cameraProducer")
    Long targetNodeId;
    String source;        // ENTITY, ENTITY.fieldPath, etc.
    Long sourceNodeId;
}
```

### G6.3 AppConfig Tree Example

```
DataFormElement "lensMountMappings"
├── type: GRID
├── entityProviderRef: "mountsForCurrentProducer"
├── tableColumns: [...]
└── addAction:
    ├── targetDataFormRef: "lensMountMappingForm"
    ├── childLabel: "LensMount Assignment"
    └── contextBindings:
        └── "cameraProducer" → ENTITY
```

### G6.4 Context Binding: Target and Source

A contextBinding entry maps a **target** (child DataForm element code) to a **source**
(expression referencing the parent context). The parent's `addAction` defines the injection
context into the child.

**Target side** — the key of each contextBinding entry:
- A field code in the child DataForm (e.g., `"cameraProducer"`).
- This is conceptually the same as `DataFormElement.dataBinding` — a path on the child entity.
- Auto-proposals are derived from the child entity type (resolved via
  `addAction.targetDataFormRef` → child DataForm → `entity.fqcn` → JPA metadata).

**Source side** — the value of each contextBinding entry:
- Uses the `ENTITY` system keyword (uppercase to distinguish from field names).
- `ENTITY` alone refers to the parent editor's entity (resolved as its ID at runtime).
- `ENTITY.fieldPath` navigates into the parent entity's attributes using dot-path syntax.
- Auto-proposals for the source side are derived from the parent entity type (resolved via
  the GRID's parent DataForm → `entity.fqcn` → JPA metadata), prefixed with `ENTITY.`.

**Source expression reference:**

| Source expression | Resolves to | Example (parent = CameraProducer "Fuji") |
|---|---|---|
| `ENTITY` | Parent entity ID (Long) | `4` |
| `ENTITY.name` | Scalar field value | `"Fuji"` |
| `ENTITY.foundationYear` | Scalar field value | `"1934-01"` |
| `ENTITY.someRelation` | Related entity ID (if @ManyToOne) | `7` (Long) |
| `ENTITY.someRelation.name` | Transitive dot-path navigation | `"SomeValue"` |

For the CameraLensMount2CameraProducer use case, only `ENTITY` is needed:
- `"cameraProducer" → ENTITY` means: in the child form, the `cameraProducer` field is
  pre-filled with the ID of the CameraProducer currently being edited.

### G6.4.1 Auto-Proposals in Admin Editor

When configuring a contextBinding in the AppConfig admin editor, both sides offer
auto-proposals using the existing `DataBindingService` mechanism:

```
┌─ Context Binding ─────────────────────────────────────────────────┐
│                                                                    │
│  Target: [cameraProducer         ▼]   ← proposals from child      │
│          ┌─────────────────────────┐     entity (CameraLensMount2  │
│          │ cameraProducer          │     CameraProducer):          │
│          │ cameraLensMount         │     field codes derived from  │
│          └─────────────────────────┘     JPA metadata              │
│                                                                    │
│  Source: [ENTITY                  ▼]   ← ENTITY keyword +         │
│          ┌─────────────────────────┐     proposals from parent     │
│          │ ENTITY                  │     entity (CameraProducer):  │
│          │ ENTITY.name             │     ENTITY prefix + field     │
│          │ ENTITY.foundationYear   │     paths from JPA metadata   │
│          │ ENTITY.shutdownYear     │                               │
│          └─────────────────────────┘                               │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

The proposal resolution:
- **Target proposals:** Resolve `addAction.targetDataFormRef` → child DataForm →
  `entity.fqcn` → pass to `DataBindingService` → get attribute paths.
- **Source proposals:** Walk up from contextBinding → addAction → GRID DataFormElement →
  parent DataForm → `entity.fqcn` → pass to `DataBindingService` → prefix each result
  with `ENTITY.`, plus `ENTITY` itself as the first proposal.

### G6.5 Context Binding Application in Child Form (Frontend Logic)

When a child EditorFrame is pushed with contextBindings, the frontend applies them
dynamically. The DataForm itself is rendered unchanged — all elements are present. The
runtime overlay is:

```
for each DataFormElement in the DataForm:
  if element.code is a key in EditorFrame.contextBindings:
    → resolve the binding expression to a concrete value
    → set element's initial value to that resolved value
    → render element as READ-ONLY (disabled input, muted style, optional lock icon)
  else:
    → render element normally (editable, default initial value from entity or empty)
```

**Visual treatment of context-bound fields:**
- **Option A — Visible but locked:** Show the field with its resolved display value
  (e.g., ENTITY_SELECT showing "Fuji"), but greyed out / disabled. The user sees which
  producer is bound and understands the context.
- **Option B — Hidden:** Omit the field entirely. Appropriate when the binding is
  self-evident from the breadcrumb (e.g., breadcrumb says "CameraProducer: Fuji > ...").
- **Recommendation:** Start with Option A (visible but locked). It's more transparent
  and avoids confusion about "where did the producer value come from?" Hidden can be
  offered later as an optional flag on the contextBinding.

**No backend changes needed.** When the form is saved, the frontend includes the
context-bound values in the `DataFormData.values` map alongside user-edited values.
`DataFormPersistenceService` processes them identically — it has no concept of which
values were user-supplied vs. context-bound.

### G6.6 AppConfigType Rows (Seeder)

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `AddAction` | `DataFormElement` | `addAction` | false | false | `...appconfig.AddAction` |
| `AddActionTarget` | `AddAction` | `targetDataFormRef` | false | false | `java.lang.String` |
| `AddActionLabel` | `AddAction` | `childLabel` | false | false | `java.lang.String` |
| `ContextBinding` | `AddAction` | `contextBindings` | true | false | `...appconfig.ContextBinding` |
| `ContextBindingTarget` | `ContextBinding` | `target` | false | false | `java.lang.String` |
| `ContextBindingSource` | `ContextBinding` | `source` | false | false | `java.lang.String` |

**Storage example for `"cameraProducer" → ENTITY`:**

```
AddAction (parent: DataFormElement "lensMountMappings")
├── AddActionTarget  code="lensMountMappingForm"
├── AddActionLabel   code="LensMount Assignment"
└── ContextBinding   code="cameraProducerBinding"
    ├── ContextBindingTarget  code="cameraProducer"
    └── ContextBindingSource  code="ENTITY"
```

Each `ContextBinding` is an AppConfigObject with children for target and source. This
follows the same structural pattern as `SortField` (which has children `SortFieldField`
and `SortDirection`) and `TableColumn` (which has children for key, header, rendererRef).
The `ENTITY` keyword and `ENTITY.fieldPath` expressions are stored as literal strings in
the source child; the frontend resolves them at runtime against the parent editor's entity.

### G6.7 The Target DataForm: `lensMountMappingForm`

A single DataForm for CameraLensMount2CameraProducer, used in all contexts:

```
dataForms:
  └── "lensMountMappingForm" (DataForm)
      ├── entity: CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER
      └── elements:
          ├── "cameraProducer" (DataFormElement)
          │   ├── type: ENTITY_SELECT
          │   ├── dataBinding: "cameraProducer"
          │   ├── entityProviderRef: "allCameraProducers"
          │   └── entityRendererRef: "producerCaption"
          └── "cameraLensMount" (DataFormElement)
              ├── type: ENTITY_SELECT
              ├── dataBinding: "cameraLensMount"
              ├── entityProviderRef: "allCameraLensMounts"
              └── entityRendererRef: "lensMountWithProducerCaption"
```

**Same DataForm, different invocations:**

| Context | contextBindings | Result |
|---|---|---|
| Standalone (ViewNode "Lens Mount Assignments") | `{}` | Both fields editable |
| From CameraProducer GRID (addAction) | `{ cameraProducer: ENTITY }` | cameraProducer=locked, cameraLensMount=editable |
| From CameraLensMount GRID (future) | `{ cameraLensMount: ENTITY }` | cameraLensMount=locked, cameraProducer=editable |

### G6.8 EntityRenderer for LensMount Selection

A new EntityRenderer is needed to display lens mounts with their producer:

```
entityRenderers:
  └── "lensMountWithProducerCaption" (EntityRenderer)
      ├── entityType: CAMERA_LENS_MOUNT
      └── template: "{{name}} ({{producer.name}})"
```

This renders as e.g., `"X-Mount (Fuji)"`, `"M42 (ZeissIkon)"`.

---

## Task G7 — Post-Save Behavior and GRID Reload

**Goal:** Define the lifecycle after a child entity is saved from within a stacked editor frame.

### G7.1 Save Flow

The save behavior depends on whether the parent entity already exists (has an ID) or is
new (no ID). The EditorStack handles this generically — the logic is trigger-agnostic and
works the same regardless of what element triggered the child frame.

#### G7.1.1 Parent has ID (existing entity)

1. User fills in the child form (e.g., selects a CameraLensMount).
2. User clicks **Save**.
3. `DataFormPersistenceService.save()` is called with the child DataForm's data, including
   the context-bound values (cameraProducer ID) in the values map.
4. On success:
   - The child EditorFrame is **popped** from the stack.
   - The parent frame becomes active again, with its preserved form state.
   - The parent element identified by `sourceElementCode` is **reloaded**.
5. On failure:
   - The child frame stays active, error is displayed, user can fix and retry.

**GRID reload mechanism (step 4 detail):**

The parent form is NOT re-loaded from the backend. All parent form fields (name,
foundationYear, etc.) remain exactly as they were — the frozen form state is simply
restored. The **only** change is that the GRID widget re-fetches its data:

```
POST /api/view/grid-data/{dataFormCode}/{elementCode}?page=0&size=10
Body: { "entityId": <parentEntityId>, "formState": { <preserved parent form state> } }
```

Only the GRID table widget re-renders with the updated row list. This is the same
mechanism already used for `reloadOnChange` triggers (e.g., when changing the name field
causes the GRID to re-fetch). The new CameraLensMount2CameraProducer row appears
immediately in the table.

#### G7.1.2 Parent has no ID (new entity — Pending Additions)

1. User is creating a new CameraProducer (no ID yet).
2. User clicks [+] on the GRID → child frame is pushed.
3. User fills in the child form, clicks **Save**.
4. The EditorStack detects: parent frame has no `entityId`.
5. Instead of persisting, the child's data is stored as a **PendingChild** on the parent
   frame:
   ```
   parentFrame.pendingChildren.add(PendingChild {
     dataFormCode: "lensMountMappingForm",
     contextBindingTarget: "cameraProducer",
     values: { cameraLensMount: 3 },
     sourceElementCode: "lensMountMappings",
     pendingChildren: []
   })
   ```
6. The child frame is **popped**.
7. The parent GRID renders the pending addition alongside any DB rows, with a visual
   indicator (e.g., italic text, "pending" badge, or muted row styling) to distinguish
   it from persisted rows.
8. The pending addition can be **removed** by the user (delete icon on the pending row)
   before the parent is saved — this simply removes it from the `pendingChildren` list.

**When the parent is finally saved:**

All pending children are included in the save request and persisted atomically with the
parent in one transaction. See G7.6 for the backend contract.

### G7.2 Cancel Flow

1. User clicks **Cancel** or a stack path ancestor node.
2. If the child form has unsaved changes: show single confirmation dialog (per G5.7).
3. On confirm (or if no changes): pop the child frame, no GRID reload.

### G7.3 "Add Another" Option (Future Enhancement)

For workflows where the user typically adds multiple entries in sequence (e.g., assigning
several lens mounts to a producer), an optional "Save & Add Another" button could:
1. Save the current child entity.
2. Reset the child form to a fresh state (keeping contextBindings pre-filled).
3. NOT pop the stack frame.

This is deferred as a future enhancement — the initial implementation uses the simple
save-then-pop flow. The `addAction` could later gain a `allowAddAnother: true` flag.

### G7.4 Delete from GRID

For completeness, the GRID also needs a delete action on existing rows:
1. User clicks a delete icon/button on a GRID row.
2. Confirmation dialog: "Remove this lens mount assignment?"
3. On confirm: `DELETE /api/view/grid-data/{dataFormCode}/{elementCode}/{entityId}`
   The backend resolves the entity class from the GRID's entityProvider (same resolution
   as the data query endpoint), finds the entity by ID, and removes it.
4. GRID reloads (same mechanism as G7.1 — re-fetch and re-render only the table widget).

Delete does NOT use the EditorStack — it's a direct action on the list.

### G7.5 Edit from GRID

Clicking an existing GRID row (or an edit icon) pushes an EditorFrame with:
- `dataFormCode` = the same `addAction.targetDataFormRef`
- `entityId` = the clicked row's ID
- `contextBindings` = same as addAction (parent fields still read-only)

The edit flow reuses the same DataForm, same context bindings, same save/cancel lifecycle.

### G7.6 Pending Children: Backend Contract

**Goal:** When a parent entity is saved together with pending children (collected while
the parent had no ID), everything is persisted atomically in one transaction. This
mechanism is generic — it works for any parent/child DataForm combination, regardless
of what element triggered the child creation.

#### G7.6.1 Extended Save Request

The existing save endpoint accepts an optional `pendingChildren` list:

```json
POST /api/dataform/save
{
  "dataFormCode": "cameraProducer",
  "entityId": null,
  "values": {
    "name": "Fuji",
    "foundationYear": "1934-01"
  },
  "pendingChildren": [
    {
      "dataFormCode": "lensMountMappingForm",
      "contextBindingTarget": "cameraProducer",
      "values": { "cameraLensMount": 3 },
      "pendingChildren": []
    },
    {
      "dataFormCode": "lensMountMappingForm",
      "contextBindingTarget": "cameraProducer",
      "values": { "cameraLensMount": 1 },
      "pendingChildren": []
    }
  ]
}
```

**Fields per pending child:**

| Field | Description |
|---|---|
| `dataFormCode` | Which DataForm to use for persisting the child |
| `contextBindingTarget` | Which field in the child's values receives the parent's ID |
| `values` | The child form's field values (without the parent reference) |
| `pendingChildren` | Recursive: grandchildren pending on this child |

#### G7.6.2 Backend Processing

```java
@Transactional
public DataFormData saveWithChildren(DataFormData data) {
    // 1. Persist parent entity
    DataFormData saved = save(data);
    Long parentId = saved.getEntityId();

    // 2. For each pending child: inject parent ID, persist recursively
    for (PendingChild child : data.getPendingChildren()) {
        DataFormData childData = new DataFormData();
        childData.setDataFormCode(child.getDataFormCode());
        childData.setValues(child.getValues());
        childData.getValues().put(child.getContextBindingTarget(), parentId);
        childData.setPendingChildren(child.getPendingChildren());

        saveWithChildren(childData);  // recursive — handles grandchildren
    }

    return saved;
}
```

**Key properties:**
- **Atomic:** The entire tree (parent + children + grandchildren) is persisted in one
  `@Transactional` method. If any child fails (e.g., unique constraint violation), the
  whole transaction rolls back — nothing is saved.
- **Recursive:** `pendingChildren` can themselves have `pendingChildren`, supporting
  arbitrary depth. In practice, depth > 2 is rare (G5.8), but the mechanism handles it.
- **Generic:** The method doesn't know about CameraProducers or lens mounts. It works
  with any DataForm codes and any `contextBindingTarget` field. Adding a new parent/child
  relationship requires only configuration (AddAction + ContextBinding), no code changes.
- **Trigger-agnostic:** The pending children mechanism is owned by the EditorStack (G5.4),
  not by any specific element type. Whether the child was triggered by a GRID [+] button,
  an ENTITY_SELECT "create new" option, or any future trigger — the save contract is the
  same.

#### G7.6.3 When `pendingChildren` is Empty or Absent

If the save request has no `pendingChildren` (or an empty list), the behavior is identical
to the current `DataFormPersistenceService.save()` — no change to the existing flow for
simple saves without children.

#### G7.6.4 Frontend: GRID Display of Pending Rows

When the parent has no ID, the GRID widget renders two sources:

| Source | Display |
|---|---|
| DB rows | Normal rendering (but likely 0 rows for a new entity, since the injectable filter has no parent ID to match) |
| Pending additions | Rendered with a visual indicator: italic text, "pending" badge, or muted row style |

Pending rows support:
- **Delete:** Remove from `pendingChildren` list (no backend call).
- **Edit:** Re-open the child frame, pre-filled with the pending data (replaces the
  pending entry on save).

Once the parent is saved and pending children are persisted, the GRID switches to
normal mode — all rows come from the DB, no more pending state.

### G7.7 Generic Constraint Violation Error Messages

**Goal:** When `saveWithChildren` fails due to a DB constraint violation, produce a
user-friendly error message by generic means — using JPA metadata and DataForm
configuration, without per-entity error message configuration.

#### G7.7.1 Stack-Aware Error Context

Every error produced by `saveWithChildren` includes the **stack context** — which
DataForm at which level failed:

```
Error saving LensMount Assignment (child of CameraProducer):
  A Lens Mount Assignment for Fuji with X-Mount already exists.
```

The stack context is derived from:
- `dataFormCode` → DataForm label or entity type name
- Parent chain → "child of {parent DataForm label}"

This is fully generic — it works for any DataForm at any stack depth.

#### G7.7.2 `ConstraintViolationResolver` Service

A generic `@Component` that translates `DataIntegrityViolationException` into
user-friendly messages by combining the DB exception with JPA metadata and DataForm
configuration:

```java
@Component
public class ConstraintViolationResolver {

    private final EntityManager entityManager;
    private final AppConfigStore appConfigStore;

    /**
     * Translates a DataIntegrityViolationException into a user-friendly message.
     * Returns null if the exception cannot be resolved generically (fallback to raw).
     */
    public String resolve(DataIntegrityViolationException ex, String dataFormCode) {
        // 1. Extract constraint name / column names from root cause
        //    (Hibernate ConstraintViolationException → constraint name, SQL message)
        // 2. Resolve entity class from DataForm → entity.fqcn
        // 3. Match against JPA annotations to identify violation type
        // 4. Map column names → entity field names → DataFormElement codes
        // 5. Produce human-readable message
    }
}
```

#### G7.7.3 Resolution per Violation Type

| Violation | Detection | Generic message |
|---|---|---|
| **Unique constraint** | `@UniqueConstraint(columnNames)` on entity class; match constraint name or column names from exception | "An entry with the same {field1} and {field2} already exists" |
| **NOT NULL** | `@Column(nullable=false)` or `@JoinColumn(nullable=false)` | "{fieldLabel} is required" |
| **Foreign key** (dangling reference) | FK violation in exception root cause | "The referenced {entityType} does not exist" |
| **String too long** | `@Column(length=N)` and data truncation exception | "{fieldLabel} exceeds maximum length of {N}" |
| **Exotic / unrecognized** | Fallback | "Save failed: {sanitized DB message}" |

**Resolution chain for unique constraint (our use case):**

```
DataIntegrityViolationException
  → root cause: ConstraintViolationException, constraint="UK_..."
  → columns: camera_lens_mount_id, camera_producer_id
  → JPA metamodel: CameraLensMount2CameraProducer
      @UniqueConstraint(columnNames = {"camera_lens_mount_id", "camera_producer_id"})
  → entity fields: cameraLensMount, cameraProducer
  → DataForm "lensMountMappingForm" elements: "cameraProducer", "cameraLensMount"
  → resolve current values through EntityRenderers: "Fuji", "X-Mount"
  → message: "A Lens Mount Assignment for Fuji with X-Mount already exists"
```

#### G7.7.4 Column-to-Field Mapping

The mapping from DB column names to entity field names uses the JPA metamodel:

```java
Metamodel metamodel = entityManager.getMetamodel();
EntityType<?> entityType = metamodel.entity(entityClass);
// Walk attributes, match @Column(name=...) or @JoinColumn(name=...) to column name
// → returns the Java field name (e.g., "cameraProducer")
```

The mapping from entity field names to user-facing labels uses the DataForm:

```java
DataForm form = appConfigStore.getAppConfig().getDataForms().get(dataFormCode);
DataFormElement element = form.getElements().get(fieldName);
// element code serves as label, or resolve current value via EntityRenderer
```

If the field has an `entityRendererRef` and the current value is available from the
failed save data, the rendered value is included in the message (e.g., "Fuji" instead
of just "cameraProducer").

#### G7.7.5 Integration with `saveWithChildren`

```java
@Transactional
public DataFormData saveWithChildren(DataFormData data) {
    try {
        DataFormData saved = save(data);
        Long parentId = saved.getEntityId();

        for (PendingChild child : data.getPendingChildren()) {
            child.getValues().put(child.getContextBindingTarget(), parentId);
            saveWithChildren(child);
        }
        return saved;
    } catch (DataIntegrityViolationException ex) {
        String message = constraintViolationResolver.resolve(ex, data.getDataFormCode());
        if (message == null) {
            message = "Save failed: " + sanitize(ex.getMostSpecificCause().getMessage());
        }
        throw new UserFacingPersistenceException(message, ex);
    }
}
```

The `UserFacingPersistenceException` carries the resolved message to the REST
controller, which returns it in the error response. The frontend displays it in the
child form (if the stack is still active) or in the parent form (if the error occurred
during the batched save of pending children).

#### G7.7.6 No Per-Entity Configuration Required

The resolver works generically because:
- `@UniqueConstraint`, `@Column`, `@JoinColumn` are already on the entity classes
- The JPA metamodel exposes column-to-field mappings
- The DataForm maps fields to element codes
- EntityRenderers provide human-readable values

Adding a new entity with constraints requires **no error message configuration** — the
resolver introspects the existing annotations and config automatically.

---

## Task G8 — Missing ViewNode: CameraLensMount2CameraProducer

**Goal:** Add a standalone ENTITY_LIST ViewNode for CameraLensMount2CameraProducer in the
app main tree, independent of the stacked editor feature.

### G8.1 Motivation

Currently there is no ViewNode for CameraLensMount2CameraProducer. While the stacked editor
will be the primary way users create these mappings (from within a CameraProducer editor),
a standalone list view is still needed for:
- Administrative overview of all lens mount assignments across all producers.
- Bulk inspection / cleanup.
- Consistency — every entity type should have a basic CRUD view.

### G8.2 Required Seed Data

**EntityProvider:**
```
entityProviders:
  └── "allLensMountMappings" (EntityProvider)
      ├── entityType: CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER
      └── sortFields:
          └── (SortField) field: "cameraProducer.name", direction: ASC
```

**DataForm:** `"lensMountMappingForm"` — same as defined in G6.6 (shared between standalone
and stacked usage).

**ViewNode:**
```
viewTree:
  └── "equipment" (GROUP)
      └── children:
          └── "lensMountMappings" (ViewNode)
              ├── type: ENTITY_LIST
              ├── label: "Lens Mount Assignments"
              ├── entityProviderRef: "allLensMountMappings"
              ├── dataFormRef: "lensMountMappingForm"
              └── tableColumns:
                  ├── "col_producer"
                  │   ├── key: "cameraProducer"
                  │   ├── header: "Producer"
                  │   └── entityRendererRef: "producerCaption"
                  └── "col_mount"
                      ├── key: "cameraLensMount"
                      ├── header: "Lens Mount"
                      └── entityRendererRef: "lensMountWithProducerCaption"
```

### G8.3 Implementation Note

This is a straightforward seed-data addition — no new code is needed. The existing
`ViewDataService`, `DataFormPersistenceService`, and frontend dynamic AppView already handle
ENTITY_LIST ViewNodes generically. The only prerequisite is that `lensMountMappingForm` (G6.6)
and `lensMountWithProducerCaption` EntityRenderer (G6.7) exist.

---

## Task G5–G8 Dependency Order

```
G8 (ViewNode for CameraLensMount2CameraProducer)    ← Independent, can be done first
  └── Requires: lensMountMappingForm (G6.6), lensMountWithProducerCaption renderer (G6.7)

G5 (EditorStack navigation)                          ← Foundation for GRID actions
  └── Pure frontend: stack path tree, push/pop, state preservation

G6 (AddAction + ContextBinding)                      ← Depends on G5
  └── Generic models, seeder types, tree builder, frontend binding logic

G7 (Post-save behavior + pending children)            ← Depends on G5, G6
  └── Save/cancel/reload lifecycle, pending additions for new parent entities
```

---

## Cross-References

- **Expression system** and FilterInjectable: `expressions.md` (Task E2.8 for this use case, E7.5 for FilterInjectable base class)
- **TableColumn model**: `viewIntegration.md` Task V1
- **EntityProvider / FilterNode**: `dataBinding.md` Task 2 and Task 6
- **CameraLensMount2CameraProducer entity**: `domainEntities.md` Task D2
- **Shared ColumnRenderer**: Used by `ViewDataService` (viewIntegration.md V2) and `GridDataService` (G1)
- **Frontend styling**: `frontendStyling.md` — GRID panel pattern (S3), centralized theme (S1, S2)
- **DataFormPersistenceService**: Handles save/load for all DataForms, including stacked child forms (G5–G7)
- **EditorStack**: G5 — stacked editor navigation, stack path tree, state preservation
- **AddAction / ContextBinding**: G6 — add/edit action configuration, context bindings (generic, first used by GRID)
- **Post-save lifecycle**: G7 — save-pop-reload, cancel, pending children for new entities, generic error messages, future "add another"
- **Standalone ViewNode**: G8 — CameraLensMount2CameraProducer in main tree
