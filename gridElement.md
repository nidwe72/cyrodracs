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
| Frontend: GRID pagination | Done (G1.6) |
| Frontend: GRID add/edit/delete actions | Future |

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
        │     └── rows: from GRID endpoint response items[]
        ├── Pagination bar (if totalPages > 1):
        │     first / prev / page indicator / next / last
        └── (Future) Action row: Add / Edit / Delete buttons
```

#### G1.6.2 Data Fetching

1. The GRID widget receives the current `entityId` and `formState` from the parent
   DataForm editor.
2. On mount (and on reload triggers), it POSTs to:
   ```
   POST /api/view/grid-data/{dataFormCode}/{elementCode}?page=0&size=10
   Body: { "entityId": <id>, "formState": { ... } }
   ```
3. The `dataFormCode` comes from the parent DataForm's code (e.g., `"cameraProducer"`).
4. The `elementCode` comes from the GRID DataFormElement's code (e.g., `"lensMountMappings"`).
5. The response is a `PagedResponse` with `items` (list of row maps), `totalCount`, `page`,
   `pageSize`, `totalPages`.

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
- **Pagination** — when the user clicks a pagination button.

The GRID does NOT fetch in "create new" mode (entityId is null) unless the injectable explicitly
handles null and returns data.

#### G1.6.5 Loading / Empty States

| State | Display |
|---|---|
| Loading | Centered `CircularProgressIndicator` inside the Card |
| Empty (0 rows) | "No entries" message |
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

## Cross-References

- **Expression system** and FilterInjectable: `expressions.md` (Task E2.8 for this use case, E7.5 for FilterInjectable base class)
- **TableColumn model**: `viewIntegration.md` Task V1
- **EntityProvider / FilterNode**: `dataBinding.md` Task 2 and Task 6
- **CameraLensMount2CameraProducer entity**: `domainEntities.md` Task D2
- **Shared ColumnRenderer**: Used by `ViewDataService` (viewIntegration.md V2) and `GridDataService` (G1)
- **Frontend styling**: `frontendStyling.md` — GRID panel pattern (S3), centralized theme (S1, S2)
