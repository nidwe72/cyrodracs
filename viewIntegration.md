# View Integration Specification

## Status Quo

The frontend `AppView` uses a hard-coded `_entityDefs` list to define which entities appear in the navigation tree, their REST API paths, and their table columns. Adding a new entity type requires editing frontend code. There is no backend concept of navigation structure, table column definitions, or non-entity views (e.g., "About" pages).

### Current Gaps (moved from dataBinding.md)

1. **Hard-coded entity registry in frontend.** `_entityDefs` in `app_view.dart` duplicates knowledge that already exists in the backend AppConfig tree (`DataFormEntityType` enum, `DataForm` definitions with their elements). Adding a new entity type requires editing both backend and frontend code.

2. **Table column definitions are client-side only.** The backend has no concept of which entity attributes should appear in a list/table view; this is hard-coded per `_EntityDef`.

3. **No nestable navigation.** The tree is a flat list of entity types. There is no way to group nodes into folders or add non-entity pages.

4. **No configurable views.** Every tree node is an entity table. There is no mechanism for static pages, dashboards, or other view types.

---

## Task V1 — ViewTree: Configurable Navigation Tree

**Goal:** Replace the hard-coded `_entityDefs` with a configurable **ViewTree** stored in AppConfig. The ViewTree defines the app's navigation structure, supporting entity tables, nestable groups, and static pages — all managed in the admin editor.

### V1.1 ViewNode Types

```java
public enum ViewNodeType {
    ENTITY_LIST,   // Table of entities with add/edit/delete
    GROUP,         // Nestable folder containing child ViewNodes
    STATIC_PAGE    // Simple content page (e.g., "About the software")
}
```

### V1.2 In-Memory Models

```java
public class ViewNode implements Coded {
    Long id;
    String code;
    ViewNodeType type;
    Long typeNodeId;
    String label;                  // display text in the tree
    Long labelNodeId;
    String entityProviderRef;      // ENTITY_LIST: which entities to show
    Long entityProviderRefNodeId;
    String dataFormRef;            // ENTITY_LIST: which form for add/edit
    Long dataFormRefNodeId;
    String content;                // STATIC_PAGE: content identifier
    Long contentNodeId;
    List<ViewNode> children;       // GROUP: nested ViewNodes
    List<TableColumn> tableColumns; // ENTITY_LIST: column definitions
}

public class TableColumn implements Coded {
    Long id;
    String code;
    String key;                    // entity attribute name (e.g., "name", "producer")
    Long keyNodeId;
    String header;                 // display header (e.g., "Name", "Producer")
    Long headerNodeId;
    String entityRendererRef;      // optional: renderer for relationship columns
    Long entityRendererRefNodeId;
}
```

### V1.3 AppConfig Tree Structure

```
AppConfig
├── dataForms: {...}
├── entityProviders: {...}
├── entityRenderers: {...}
└── viewTree:                                    (ViewNode collection)
    ├── "equipment" (ViewNode)
    │   ├── type: GROUP
    │   ├── label: "Equipment"
    │   └── children:                            (ViewNode collection, recursive)
    │       ├── "cameras" (ViewNode)
    │       │   ├── type: ENTITY_LIST
    │       │   ├── label: "Cameras"
    │       │   ├── entityProvider: "allCameras"
    │       │   ├── dataForm: "cameraForm"
    │       │   └── tableColumns:                (TableColumn collection)
    │       │       ├── "col_id"
    │       │       │   ├── key: "id"
    │       │       │   └── header: "ID"
    │       │       ├── "col_name"
    │       │       │   ├── key: "name"
    │       │       │   └── header: "Name"
    │       │       ├── "col_producer"
    │       │       │   ├── key: "producer"
    │       │       │   ├── header: "Producer"
    │       │       │   └── renderer: "producerCaption"
    │       │       └── "col_releaseYear"
    │       │           ├── key: "releaseYear"
    │       │           └── header: "Release Year"
    │       ├── "producers" (ViewNode)
    │       │   ├── type: ENTITY_LIST
    │       │   ├── label: "Camera Producers"
    │       │   ├── entityProvider: "allCameraProducers"
    │       │   ├── dataForm: "cameraProducerForm"
    │       │   └── tableColumns: [...]
    │       └── "lensMounts" (ViewNode)
    │           └── ...
    ├── "about" (ViewNode)
    │   ├── type: STATIC_PAGE
    │   ├── label: "About the software"
    │   └── content: "staticAbout"
    └── ...future nodes...
```

### V1.4 AppConfigType Rows (Seeder)

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `ViewNode` | `AppConfig` | `viewTree` | true | false | `...appconfig.ViewNode` |
| `ViewNodeType` | `ViewNode` | `type` | false | true | `...appconfig.ViewNodeType` |
| `ViewNodeLabel` | `ViewNode` | `label` | false | false | `java.lang.String` |
| `ViewNodeProviderRef` | `ViewNode` | `entityProviderRef` | false | false | `java.lang.String` |
| `ViewNodeDataFormRef` | `ViewNode` | `dataFormRef` | false | false | `java.lang.String` |
| `ViewNodeContent` | `ViewNode` | `content` | false | false | `java.lang.String` |
| `ViewNodeChildren` | `ViewNode` | `children` | true | false | `...appconfig.ViewNode` |
| `TableColumn` | `ViewNode` | `tableColumns` | true | false | `...appconfig.TableColumn` |
| `TableColumnKey` | `TableColumn` | `key` | false | false | `java.lang.String` |
| `TableColumnHeader` | `TableColumn` | `header` | false | false | `java.lang.String` |
| `TableColumnRendererRef` | `TableColumn` | `entityRendererRef` | false | false | `java.lang.String` |

**Recursion:** `ViewNode` children are also `ViewNode`s. A GROUP node's children are stored as `APP_CONFIG_OBJECT` rows with `type_id = ViewNode` and `parent_object_id` pointing to the GROUP's `ViewNode` row. The tree builder handles this recursively, same as any other parent-child relationship. `ViewNodeChildren` is a collection type whose child type is `ViewNode`, enabling the admin editor to add child ViewNodes under a GROUP.

### V1.5 AppConfig In-Memory Model Update

```java
public class AppConfig implements Coded {
    // ... existing fields ...
    Map<String, ViewNode> viewTree = new LinkedHashMap<>();  // NEW
}
```

### V1.6 AppConfigTreeBuilder Update

The tree builder recursively builds ViewNodes. For GROUP nodes, it recurses into children (same `ViewNode` type). For ENTITY_LIST nodes, it builds the `tableColumns` list.

---

## Task V2 — View Data Endpoint

**Goal:** Provide a single generic endpoint that serves table data for any ENTITY_LIST ViewNode, with relationship columns pre-rendered via EntityRenderer.

### V2.1 Endpoint

```
GET /api/view/{viewNodeCode}/data
```

Returns rows as flat maps with all columns resolved. The `id` field is **always included** in every row (regardless of whether it is defined as a TableColumn), because the frontend needs it for edit/delete actions:

```json
[
  { "id": 1, "name": "Nikon F3", "producer": "Nikon (1917-Now)", "releaseYear": "1980-03" },
  { "id": 2, "name": "Canon AE-1", "producer": "Canon (1937-Now)", "releaseYear": "1976-04" }
]
```

### V2.2 Resolution Logic

1. Look up `ViewNode` by code from `AppConfigStore`.
2. Verify it is `ENTITY_LIST` type.
3. Resolve the `entityProviderRef` to get the entity class and query all entities.
4. For each entity, always put `id` into the row first.
5. For each `TableColumn`:
   - If the column has no `entityRendererRef`: read the attribute value via reflection (same as `DataFormPersistenceService.getProperty()`). If the value is a JPA entity (relationship), extract its ID.
   - If the column has an `entityRendererRef`: read the relationship entity, build a Mustache context from its attributes, render the template, and use the rendered string as the column value.
6. Return the list of row maps.

**Hibernate proxy handling:** When detecting whether a value is a JPA entity (for relationship column rendering), the `isJpaEntity` check must walk the class hierarchy since Hibernate wraps `@ManyToOne` entities in proxy subclasses, and `@Entity` is not `@Inherited`. The same applies to `buildEntityContext` which must resolve the actual `@Entity`-annotated class before querying the JPA metamodel.

### V2.3 Generic Delete Endpoint

```
DELETE /api/view/{viewNodeCode}/{id}
```

Resolves entity class from the ViewNode's entityProvider, finds by ID, removes.

### V2.4 Retire Per-Entity Controllers

Once the view data and delete endpoints are in place, `CameraProducerController`, `CameraLensMountController`, `CameraController`, and future entity controllers become unnecessary. The frontend switches to the generic view endpoints.

---

## Task V3 — Frontend: Dynamic AppView

**Goal:** Replace the hard-coded `_entityDefs` with a ViewTree-driven navigation, fully configured from the backend.

### V3.1 Tree Building

The frontend reads `viewTree` from the AppConfig response and builds the left-panel navigation tree:

- `GROUP` nodes render as expandable folders with child nodes.
- `ENTITY_LIST` nodes render as leaf items (or expandable if they also have children in the future).
- `STATIC_PAGE` nodes render as leaf items.

The label for each node comes from `ViewNode.label`.

### V3.2 Detail Panel Routing

When a ViewNode is activated (double-clicked):

| ViewNode type | Detail panel shows |
|---|---|
| `ENTITY_LIST` | Table of entities (fetched from `GET /api/view/{code}/data`), with add/edit/delete. Columns from `tableColumns`. Edit form from `dataFormRef`. |
| `GROUP` | Either nothing (just expand the group), or a summary of child nodes. |
| `STATIC_PAGE` | Content resolved from the `content` field (initially a simple text display, extensible later). |

### V3.3 Elimination of `_entityDefs`

The `_entityDefs` list, `_EntityDef` class, `_ColDef` class, and all hard-coded API paths are removed. Adding a new entity is purely admin-driven: create an EntityProvider, a DataForm, a ViewNode of type ENTITY_LIST with table columns, and it appears in the app.

---

## Task V4 — Admin Editor: ViewTree Configuration

**Goal:** Allow the admin to create, edit, and reorder ViewNodes in the AppConfigEditorView.

### V4.1 ViewTree in the Config Tree

The AppConfigEditorView shows `viewTree` as a collection under the root AppConfig node. The user can:

- Add ViewNodes (choosing type: ENTITY_LIST, GROUP, or STATIC_PAGE).
- For ENTITY_LIST: set label, entityProvider ref, dataForm ref, and manage tableColumns.
- For GROUP: set label, add child ViewNodes (recursive).
- For STATIC_PAGE: set label and content.

### V4.2 TableColumn Management

When editing an ENTITY_LIST ViewNode, the admin can add/edit/delete TableColumn entries via the `tableColumns` collection (always shown for ENTITY_LIST nodes). Each column has:

- **key** — the entity attribute, with **auto-proposals** from `DataBindingService` (same IDE-style completion as `DataFormElement.dataBinding`). The entity type is resolved by walking up from the TableColumn to its parent ViewNode, looking up the ViewNode's `entityProviderRef`, then resolving the EntityProvider's `entityType`. Shows both leaf (scalar) and non-leaf (relationship) attributes.
- **header** — the display header text.
- **renderer** — optional EntityRenderer reference (for relationship columns, e.g., to render `producer` as `"Nikon (1917-Now)"` instead of a raw ID).

### V4.3 ViewNode Ordering

ViewNodes within a collection should respect insertion order (the AppConfig tree already preserves order via `LinkedHashMap` and database insertion order). Future enhancement: drag-and-drop reordering with an explicit `sortOrder` field.

---

## Task Dependency Order

```
Task V1 (ViewTree model)                 ← Foundation
  └── In-memory classes, seeder types, tree builder

Task V2 (View Data Endpoint)             ← Depends on V1
  └── Generic list/delete endpoints for ENTITY_LIST nodes

Task V3 (Frontend Dynamic AppView)       ← Depends on V1, V2
  └── Replace _entityDefs, tree-driven navigation

Task V4 (Admin Editor)                   ← Depends on V1
  └── ViewTree config UI in AppConfigEditorView

Task V2 and V4 can proceed in parallel after V1.
Task V3 depends on both V2 (data endpoint) and V1 (tree model in frontend).
```

### Cross-References

- **EntityProvider** and **EntityRenderer** are defined in `dataBinding.md` Task 2 and reused here.
- **DataForm** (edit forms) are defined in `dataBinding.md` Task 1 and referenced by ENTITY_LIST ViewNodes.
- **DataBindingService** proposals are reused for TableColumn key auto-completion.
