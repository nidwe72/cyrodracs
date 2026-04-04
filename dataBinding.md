# Data Binding Specification

## Status Quo

The current system uses a **generic, metadata-driven** approach: `DataFormData` carries a flat `Map<String, Object> values` between frontend and backend. The backend `DataFormPersistenceService` maps these values to/from JPA entities using Java reflection (setter/getter by field code). The frontend `AppView` uses a hard-coded `_EntityDef` list to define which entities exist, their REST endpoints, and their table columns.

Currently, `DataFormElement.code` serves double duty: it is both the element's identity within the AppConfig tree and the binding path to the entity attribute. For example, setting code to `"name"` on an element whose parent DataForm is bound to `CAMERA_PRODUCER` implicitly binds to `CameraProducer.getName()`/`setName()`. This works but is fragile — the user must know the exact attribute name and type by hand with no guidance from the system.

### Current Gaps

1. **No dedicated data binding field.** `DataFormElement.code` conflates identity with binding. There is no separate field to specify which entity attribute a form element maps to.

2. **No binding auto-proposals.** When configuring a DataFormElement, the user must manually type the correct entity attribute name. The system does not introspect the entity to suggest available attributes.

3. **No relationship support.** Entities have only scalar attributes. The `Camera` entity currently has only primitives (`name`, `releaseYear`) but will need a `@ManyToOne` reference to `CameraProducer`. Neither the form model, the persistence layer, nor the frontend can express or resolve entity relationships yet.

4. **Hard-coded entity registry in frontend.** → Addressed in `viewIntegration.md` (ViewTree).

5. **No validation model.** DataFormElement has a type but no validation rules (required, min/max length, pattern, unique). The frontend does no field validation; the backend relies on JPA/DB constraints.

6. **No SELECT element data source.** `DataFormElementType.SELECT` exists but has no way to specify where its options come from (e.g., "all CameraProducer entities" for a foreign-key dropdown). Options are currently only a static `List<String>` on `DataFormElement`.

7. **Table column definitions are client-side only.** → Addressed in `viewIntegration.md` (ViewTree).

---

## Task 1 — Dedicated dataBinding Field with Auto-Proposals

**Goal:** Introduce a dedicated `dataBinding` field on `DataFormElement` that specifies which entity attribute the element maps to. Provide IDE-style auto-proposals by introspecting the JPA metamodel of the entity configured on the parent DataForm.

### Approach: JPA Metamodel Introspection

The backend uses `EntityManager.getMetamodel()` to discover bindable attributes at runtime. This is preferred over raw reflection or manual metadata registration because:

- The JPA metamodel provides exactly the persistable attributes, properly typed, without false positives from `Object.getClass()` or JPA proxy methods.
- It already classifies attributes as `BASIC` (primitives) vs. managed types (relationships), which aligns with the "primitive only for now, relationships later" roadmap.
- It is always in sync with the actual entity code — no manual duplication.

### 1.1 Backend: Segment-Based Binding Proposals Endpoint

New REST endpoint that returns completions for a given path prefix:

```
GET /api/data-binding/proposals/{entityType}?prefix={pathPrefix}
```

Where `entityType` is a `DataFormEntityType` enum value (e.g., `CAMERA_PRODUCER`) and `prefix` is the dot-separated path typed so far (empty or omitted for the root level).

**Implementation:**

1. Resolve `DataFormEntityType` to `Class<?>` via FQCN.
2. If `prefix` is empty, the target class is the entity class itself.
3. If `prefix` contains segments (e.g., `"producer"`), walk the dot-separated path through the JPA metamodel to resolve the target class at the end of the path. For example, `prefix=producer` on `Camera` resolves to `CameraProducer`.
4. Call `entityManager.getMetamodel().entity(targetClass).getSingularAttributes()`.
5. Exclude `id`.
6. Return a list of completions for the current level.

**Examples:**

Request: `GET /api/data-binding/proposals/CAMERA_PRODUCER` (no prefix)

```json
{
  "entityLabel": "CameraProducer",
  "completions": [
    {
      "segment": "name",
      "javaType": "String",
      "leaf": true,
      "suggestedElementType": "INPUT_STRING"
    },
    {
      "segment": "foundationYear",
      "javaType": "YearMonth",
      "leaf": true,
      "suggestedElementType": "DATE_PICKER__YEAR_MONTH"
    },
    {
      "segment": "shutdownYear",
      "javaType": "YearMonth",
      "leaf": true,
      "suggestedElementType": "DATE_PICKER__YEAR_MONTH"
    }
  ]
}
```

Request: `GET /api/data-binding/proposals/CAMERA?prefix=producer` (navigating into a relationship — future Task 2)

```json
{
  "entityLabel": "CameraProducer",
  "completions": [
    {
      "segment": "name",
      "javaType": "String",
      "leaf": true,
      "suggestedElementType": "INPUT_STRING"
    },
    {
      "segment": "foundationYear",
      "javaType": "YearMonth",
      "leaf": true,
      "suggestedElementType": "DATE_PICKER__YEAR_MONTH"
    }
  ]
}
```

**Completion fields:**

| Field | Description |
|---|---|
| `segment` | The attribute name at this level |
| `javaType` | The simple Java type name |
| `leaf` | `true` for basic types (bindable), `false` for `@ManyToOne` relationships (navigable) |
| `suggestedElementType` | Suggested `DataFormElementType` (only for leaf completions) |

**`leaf` flag and the `suggestedElementType` mapping:**

| Java Type | leaf | Suggested Element Type |
|---|---|---|
| `String` | `true` | `INPUT_STRING` |
| `Long`, `Integer` | `true` | `INPUT_NUMBER` |
| `Double`, `Float` | `true` | `INPUT_NUMBER` |
| `Boolean` | `true` | `CHECKBOX` |
| `YearMonth` | `true` | `DATE_PICKER__YEAR_MONTH` |
| `LocalDate` | `true` | `DATE_PICKER` |
| `LocalDateTime` | `true` | `DATE_TIME_PICKER` |
| `LocalTime` | `true` | `TIME_PICKER` |
| `@ManyToOne` entity | `false` | `ENTITY_SELECT` (Task 2) |

For Task 1, only `leaf: true` completions are returned. The `leaf: false` entries (relationships) are added in Task 2, enabling the user to type a `.` after a relationship segment and drill deeper.

### 1.2 Backend: New Service — DataBindingService

New service class `DataBindingService` (in `sciens.cyrodracs.appconfig.service`):

```java
@Service
public class DataBindingService {

    private final EntityManager entityManager;

    /**
     * Returns completions for the given path prefix on the entity type.
     * Empty prefix returns the root-level attributes of the entity.
     */
    public BindingProposalResponse getProposals(
            DataFormEntityType entityType, String prefix) {
        // 1. Resolve entity class from entityType
        // 2. Walk prefix segments through metamodel to find target class
        // 3. Introspect target class for singular attributes
        // 4. Filter to BASIC (leaf) for Task 1; include MANY_TO_ONE in Task 2
        // 5. Return completions
    }
}
```

DTOs:

```java
public class BindingProposalResponse {
    private String entityLabel;                // e.g. "CameraProducer"
    private List<BindingCompletion> completions;
}

public class BindingCompletion {
    private String segment;                    // e.g. "name"
    private String javaType;                   // e.g. "String"
    private boolean leaf;                      // true = bindable, false = navigable
    private String suggestedElementType;       // e.g. "INPUT_STRING" (null if !leaf)
}
```

### 1.3 Backend: AppConfig Tree — dataBinding as Child Node

Add `dataBinding` to the AppConfig tree model, following the same pattern as `type` on `DataFormElement`:

- New `AppConfigType` row: code `"DataBinding"`, parent type `"DataFormElement"`, field name `"dataBinding"`, `is_collection = false`, `is_enum = false`.
- `AppConfigTypeSeeder` registers this type.
- Stored as an `AppConfigObjectEntity` child of the `DataFormElement` node, with the binding path in the `code` field (e.g., `"name"`, `"foundationYear"`).

In-memory model update on `DataFormElement`:

```java
public class DataFormElement implements Coded {
    String code;
    DataFormElementType type;
    Long typeNodeId;
    String dataBinding;     // NEW — the entity attribute path
    Long dataBindingNodeId; // NEW — DB id of the DataBinding child node
}
```

`AppConfigTreeBuilder` populates `dataBinding` and `dataBindingNodeId` when building a `DataFormElement`.

### 1.4 Backend: DataFormPersistenceService Update

Update `DataFormPersistenceService` to use `dataBinding` for getter/setter resolution, falling back to `code` for backward compatibility:

```java
private String resolveBindingPath(DataFormElement element) {
    if (element.getDataBinding() != null && !element.getDataBinding().isEmpty()) {
        return element.getDataBinding();
    }
    return element.getCode();
}
```

This is used in `applyValues()` (save) and `load()` (read) when mapping between the values map and entity properties. The values map continues to be keyed by `element.getCode()` (the element's identity), while the entity property accessed is determined by the binding path.

### 1.5 Frontend: DataFormElement Model Update

Update the Dart `DataFormElement` class:

```dart
class DataFormElement {
  String key;
  String label;
  DataFormElementType type;
  String? dataBinding;       // NEW
  int? dataBindingNodeId;    // NEW
  List<String> options;
  int cols;
  bool breakBefore;
  double? min, max;
  int? rows;
}
```

Update `_buildDataFormForEntity()` in `app_view.dart` and `data_form_renderer_view.dart` to read `dataBinding` from the AppConfig tree node.

### 1.6 Frontend: Config Editor — dataBinding Field with Segment-Based Picker

When editing a `DataFormElement` in the admin AppConfigEditorView, show a `dataBinding` field with the following UX.

#### Text Input with Inline Auto-Completion

The primary interaction is a text field that behaves like an IDE code-completion input:

```
┌──────────────────────────────────────────┐
│ Data Binding                             │
│ ┌──────────────────────────────────┬───┐ │
│ │ CameraProducer.name              │ ⊞ │ │
│ └──────────────────────────────────┴───┘ │
└──────────────────────────────────────────┘
```

**Typing behavior:**

1. The field is pre-filled with the entity type name from the parent DataForm (e.g., `CameraProducer.`). This prefix is read-only — the user cannot edit or delete it.

2. As the user types after the dot, a **dropdown overlay** appears below the text field showing matching completions, filtered by the typed characters:

```
  ┌──────────────────────────────────┐
  │ CameraProducer.get               │
  └──────────────────────────────────┘
  ┌──────────────────────────────────┐
  │  getName           String        │
  │  getFoundationYear YearMonth     │
  │  getShutdownYear   YearMonth     │
  └──────────────────────────────────┘
```

3. The completions are fetched from `GET /api/data-binding/proposals/{entityType}?prefix=` (empty prefix for root level). The dropdown filters client-side as the user types.

4. Completions show the **getter method name** for display (e.g., `getName`) but the selected value stored is the **attribute path** (e.g., `name`). The display format `getXxx` is familiar from Java IDEs and makes the binding explicit.

5. Selecting a **leaf** completion (basic type) finalizes the binding. The text field shows the full path: `CameraProducer.name`. The stored `dataBinding` value is `name`.

6. Selecting a **non-leaf** completion (relationship, Task 2) appends the segment and a dot, then fetches the next level of completions:

```
  ┌──────────────────────────────────┐
  │ Camera.producer.                 │
  └──────────────────────────────────┘
  ┌──────────────────────────────────┐
  │  getName           String        │
  │  getFoundationYear YearMonth     │
  │  getShutdownYear   YearMonth     │
  └──────────────────────────────────┘
```

   This continues until the user selects a leaf. The stored `dataBinding` value becomes a dot-path: `producer.name`.

7. Typing `.` after a valid segment also triggers fetching the next level of completions (same as selecting a non-leaf).

**Keyboard interaction:**

| Key | Action |
|---|---|
| Any character | Filters the completion list |
| `↓` / `↑` | Navigate completions |
| `Enter` or `Tab` | Accept highlighted completion |
| `.` | If current text matches a non-leaf segment, navigate into it |
| `Escape` | Close the dropdown |
| `Backspace` past a `.` | Navigate back to the parent segment |

#### Picker Dialog (Alternative)

The picker button (⊞) opens a dialog for users who prefer point-and-click navigation. This is the same data, presented as a navigable tree:

```
┌──────────────────────────────────────────┐
│ Select Binding — CameraProducer          │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ 🔍 Filter...                       │  │
│  └────────────────────────────────────┘  │
│                                          │
│  Path: CameraProducer                    │
│                                          │
│  ┌──────────────┬──────────┬──────────┐  │
│  │ Attribute    │ Type     │          │  │
│  ├──────────────┼──────────┼──────────┤  │
│  │ name         │ String   │ [Select] │  │
│  │ foundationYr │ YearMonth│ [Select] │  │
│  │ shutdownYear │ YearMonth│ [Select] │  │
│  └──────────────┴──────────┴──────────┘  │
│                                          │
│                          [Cancel]        │
└──────────────────────────────────────────┘
```

For relationship attributes (Task 2), the row shows a `[▶]` button instead of `[Select]`, which navigates into the related entity and updates the path breadcrumb:

```
│  Path: Camera ▸ producer (CameraProducer)│
```

Already-bound attributes (used by other elements in the same DataForm) are shown greyed out to discourage duplicate bindings.

#### Persistence Flow

When the user finalizes a binding (via either the text input or the picker dialog), the frontend calls:

- `POST /api/app-config/node` to create the DataBinding child node (if new), or
- `PATCH /api/app-config/node/{id}` to update it (if `dataBindingNodeId` exists).

This is the same pattern as how `type` is managed on DataFormElement today.

### 1.7 Frontend: FormRendererView — No Change Needed

The `FormRendererView` does not need changes for this task. It already receives `DataFormElement` objects with `key` and `type`. The binding resolution happens entirely on the backend in `DataFormPersistenceService`. The frontend continues to send `{ "dataFormCode": "...", "values": { "<element.key>": "<value>" } }` and the backend maps `element.key` to the correct entity attribute via `dataBinding`.

---

## Task 2 — Entity Relationship Binding (ManyToOne) with EntityProvider & EntityRenderer

**Goal:** Allow a `DataFormElement` to bind to a `@ManyToOne` JPA relationship, rendered as a SELECT dropdown whose options come from a reusable **EntityProvider** (data source) and whose labels are formatted by a reusable **EntityRenderer** (Mustache template). Builds on the dataBinding infrastructure from Task 1.

### 2.1 Backend: Extend Binding Proposals for Relationships

Extend `DataBindingService.getProposals()` to include `@ManyToOne` singular attributes (where `getPersistentAttributeType() == MANY_TO_ONE`). These appear in the proposals with `leaf: false` and a new `referencedEntityType` field:

```json
{
  "segment": "producer",
  "javaType": "CameraProducer",
  "leaf": false,
  "suggestedElementType": "ENTITY_SELECT",
  "referencedEntityType": "CAMERA_PRODUCER"
}
```

The `referencedEntityType` is resolved by scanning `DataFormEntityType` enum values for a matching FQCN. The picker dialog (Task 1.6) shows these alongside primitive attributes, visually distinguished with a relationship icon.

### 2.2 Backend: New DataFormElementType — ENTITY_SELECT

Add `ENTITY_SELECT` to the `DataFormElementType` enum. This type indicates the field binds to a foreign-key relationship and uses an EntityProvider for its options.

### 2.3 Backend: EntityProvider — Reusable Data Source

An `EntityProvider` is a **named, reusable** object at `AppConfig.entityProviders[code]` that defines which entities to retrieve. Multiple `DataFormElement`s can reference the same provider.

**In-memory model:**

```java
public class EntityProvider implements Coded {
    Long id;
    String code;
    DataFormEntityType entityType;  // which entity table to query
    Long entityTypeNodeId;          // DB id of the EntityProviderEntityType child
}
```

**AppConfig tree structure:**

```
AppConfig
├── dataForms: {...}
└── entityProviders:
    └── "allCameraProducers":       (EntityProvider node)
        └── entityType: CAMERA_PRODUCER  (EntityProviderEntityType enum child)
```

**AppConfigType rows:**

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `EntityProvider` | `AppConfig` | `entityProviders` | true | false | `sciens.cyrodracs.appconfig.EntityProvider` |
| `EntityProviderEntityType` | `EntityProvider` | `entityType` | false | true | `sciens.cyrodracs.appconfig.DataFormEntityType` |

For the first implementation, the provider retrieves **all** entities of the given type (no query filters). The query/filter builder is a future extension.

### 2.4 Backend: EntityRenderer — Reusable Mustache Template

An `EntityRenderer` is a **named, reusable** object at `AppConfig.entityRenderers[code]` that defines how to format a display label for each entity instance.

**Template engine: jmustache** (`com.samskivert:jmustache`)

Chosen because:
- Simple field interpolation: `{{name}}`
- Conditional sections: `{{#field}}...{{/field}}` — renders block only if field is non-null
- Inverted sections: `{{^field}}...{{/field}}` — renders block only if field is null
- Intentionally logic-less — no full programming language, just the right granularity
- Single JAR, zero dependencies

**Example template for CameraProducer:**

```mustache
{{name}}{{#foundationYear}} ({{foundationYear}}-{{#shutdownYear}}{{shutdownYear}}{{/shutdownYear}}{{^shutdownYear}}Now{{/shutdownYear}}){{/foundationYear}}
```

Results:
- `{ name: "Nikon", foundationYear: "1917", shutdownYear: null }` → `Nikon (1917-Now)`
- `{ name: "Minolta", foundationYear: "1928", shutdownYear: "2003" }` → `Minolta (1928-2003)`
- `{ name: "Acme", foundationYear: null, shutdownYear: null }` → `Acme`

**In-memory model:**

```java
public class EntityRenderer implements Coded {
    Long id;
    String code;
    DataFormEntityType entityType;  // which entity type this renders
    Long entityTypeNodeId;
    String template;                // Mustache template string
    Long templateNodeId;            // DB id of the template child node
}
```

**AppConfig tree structure:**

```
AppConfig
├── dataForms: {...}
├── entityProviders: {...}
└── entityRenderers:
    └── "producerCaption":           (EntityRenderer node)
        ├── entityType: CAMERA_PRODUCER  (EntityRendererEntityType enum child)
        └── template: "{{name}}{{#foundationYear}} ({{foundationYear}}-...)"  (EntityRendererTemplate child)
```

**AppConfigType rows:**

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `EntityRenderer` | `AppConfig` | `entityRenderers` | true | false | `sciens.cyrodracs.appconfig.EntityRenderer` |
| `EntityRendererEntityType` | `EntityRenderer` | `entityType` | false | true | `sciens.cyrodracs.appconfig.DataFormEntityType` |
| `EntityRendererTemplate` | `EntityRenderer` | `template` | false | false | `java.lang.String` |

### 2.5 Backend: DataFormElement — Provider and Renderer References

When a `DataFormElement` has type `ENTITY_SELECT`, it references an EntityProvider and an EntityRenderer by code, stored as AppConfig child nodes:

```java
public class DataFormElement implements Coded {
    // ... existing fields ...
    String entityProviderRef;       // code of the EntityProvider
    Long entityProviderRefNodeId;
    String entityRendererRef;       // code of the EntityRenderer
    Long entityRendererRefNodeId;
}
```

**AppConfigType rows:**

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `EntityProviderRef` | `DataFormElement` | `entityProviderRef` | false | false | `java.lang.String` |
| `EntityRendererRef` | `DataFormElement` | `entityRendererRef` | false | false | `java.lang.String` |

### 2.6 Backend: EntitySelectService — Options Resolution

New service `EntitySelectService` that combines an EntityProvider and EntityRenderer to produce dropdown options:

```java
@Service
public class EntitySelectService {

    /**
     * Fetches all entities defined by the provider, renders each label
     * using the renderer's Mustache template, and returns the options list.
     */
    public List<EntityOption> getOptions(String providerCode, String rendererCode) {
        // 1. Look up EntityProvider and EntityRenderer from AppConfigStore
        // 2. Resolve entity class from provider's entityType
        // 3. Query all entities: entityManager.createQuery("SELECT e FROM X e").getResultList()
        // 4. For each entity, build a Map<String, Object> of all attributes (via reflection)
        // 5. Compile Mustache template, execute against the map
        // 6. Return list of { id, label }
    }
}
```

**DTO:**

```java
public class EntityOption {
    private Long id;
    private String label;
}
```

### 2.7 Backend: Options Endpoint

```
GET /api/entity-select/options?provider={providerCode}&renderer={rendererCode}
```

Returns:
```json
[
  { "id": 1, "label": "Nikon (1917-Now)" },
  { "id": 2, "label": "Canon (1937-Now)" },
  { "id": 3, "label": "Minolta (1928-2003)" }
]
```

### 2.8 Backend: DataFormPersistenceService — Relationship Resolution

When saving a `DataFormData` where `dataBinding` maps to a `@ManyToOne` attribute:

- The value in the map is the **ID** (Long) of the referenced entity.
- The service detects the setter parameter is a JPA entity (not a primitive) and resolves it via `EntityManager.find()` before calling the setter.

When loading:

- The getter returns a JPA entity object. The service extracts its `id` (via `getId()`) and puts that into the values map.

### 2.9 Backend: Extend Camera Entity with Relationships

The `Camera` entity already exists with `id`, `name`, and `releaseYear`. Add a `@ManyToOne` relationship as the first consumer:

```java
@ManyToOne
@JoinColumn(name = "producer_id")
private CameraProducer producer;
```

`CAMERA` is already registered in `DataFormEntityType`, `CameraController`, and `CameraRepository`.

### 2.10 Frontend: ENTITY_SELECT Widget

Add `entitySelect` to the Dart `DataFormElementType` enum. The `FormRendererView` renders it as a `DropdownButtonFormField` that:

1. On init, fetches options from `GET /api/entity-select/options?provider=...&renderer=...`
2. Displays `label` to the user, stores `id` as the form value.

The `DataFormElement` model gains `entityProviderRef` and `entityRendererRef` fields, populated from the AppConfig tree.

### 2.11 Frontend: Template Editor with Mustache Auto-Proposals

When editing an `EntityRenderer` in the AppConfigEditorView, the template field provides auto-completion:

1. When the user types `{{`, trigger a proposals dropdown using `DataBindingService.getProposals()` for the renderer's entity type.
2. Show available fields with their types: `name (String)`, `foundationYear (YearMonth)`, etc.
3. When the user types `{{#` or `{{^`, show the same proposals, indicating these are conditional/inverted blocks.
4. On selecting a conditional field like `{{#shutdownYear}}`, auto-insert the closing `{{/shutdownYear}}` tag.

No special library is needed for the frontend proposals — it reuses the same binding proposals endpoint (`GET /api/data-binding/proposals/{entityType}`), triggered by `{{` instead of `.`.

### 2.12 Frontend: AppConfigEditorView — EntityProvider and EntityRenderer Editing

The AppConfigEditorView is extended to manage EntityProvider and EntityRenderer nodes:

**EntityProvider detail panel:**
- Code field
- Entity type dropdown (same `_kEntityValues` list)

**EntityRenderer detail panel:**
- Code field
- Entity type dropdown
- Template text field with Mustache auto-proposals (see 2.11)

**DataFormElement detail panel (when type = ENTITY_SELECT):**
- Existing code, type, dataBinding fields
- EntityProvider ref dropdown (lists available EntityProvider codes)
- EntityRenderer ref dropdown (lists available EntityRenderer codes)

---

## Task 3 — Moved to viewIntegration.md

Task 3 (Backend-Driven Entity Registry) has been superseded by the ViewTree concept. See `viewIntegration.md` Tasks V1–V4 for the expanded specification covering configurable navigation, entity tables, nestable groups, static pages, and generic view data endpoints.

---

## Task 4 — Field Validation

**Goal:** Allow DataFormElements to carry validation rules, enforced on both frontend and backend.

### 4.1 Validation Rules Model

Extend `DataFormElement` (in both AppConfig tree and in-memory model) with optional validation properties stored as child nodes:

| Rule | Type | Description |
|---|---|---|
| `required` | Boolean | Field must have a non-empty value |
| `minLength` | Integer | Minimum string length |
| `maxLength` | Integer | Maximum string length |
| `pattern` | String | Regex pattern the value must match |
| `min` | Number | Minimum numeric value |
| `max` | Number | Maximum numeric value |

### 4.2 Backend Validation

`DataFormPersistenceService.save()` validates each field value against the element's rules before calling setters. Returns structured error response on failure:

```json
{
  "errors": {
    "name": ["Field is required"],
    "email": ["Must match pattern: ^.+@.+$"]
  }
}
```

### 4.3 Frontend Validation

`FormRendererView` reads validation rules from `DataFormElement` and applies them via Flutter's `TextFormField.validator`. Error messages are displayed inline below each field.

---

## Task 5 — Moved to viewIntegration.md

Task 5 (Generic List Endpoint) has been incorporated into the ViewTree data endpoint. See `viewIntegration.md` Task V2 for the view-aware generic list/delete endpoints with column rendering.

---

## Task 6 — EntityProvider: Configurable Filter & Sort

**Goal:** Extend EntityProvider with a configurable filter tree (WHERE clauses) and sort order (ORDER BY), so providers can return filtered subsets of entities. This enables use cases like "all Nikon cameras" or "Nikon cameras from the 1960s" without custom code.

### 6.1 Filter Model: Recursive FilterNode Tree

A filter is a **recursive tree of FilterNodes**. Each node is either a **comparison** (leaf predicate) or a **logical group** (AND/OR with children).

#### FilterNode types

```java
public enum FilterNodeType {
    COMPARISON,    // leaf: field + operator + value
    AND_GROUP,     // composite: all children must match
    OR_GROUP       // composite: any child must match
}
```

#### Comparison operators

```java
public enum FilterOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    IS_NULL,
    IS_NOT_NULL,
    IN,
    LIKE
}
```

#### In-memory model

```java
public class FilterNode implements Coded {
    Long id;
    String code;
    FilterNodeType type;         // COMPARISON, AND_GROUP, OR_GROUP
    Long typeNodeId;
    String field;                // dot-path, e.g. "producer.name" (COMPARISON only)
    Long fieldNodeId;
    FilterOperator operator;     // EQUALS, IN, etc. (COMPARISON only)
    Long operatorNodeId;
    String value;                // single value as string (COMPARISON, non-IN)
    Long valueNodeId;
    List<String> values;         // multiple values (IN operator)
    List<FilterNode> children;   // nested filters (AND_GROUP / OR_GROUP)
}
```

### 6.2 Use Case Examples

**`nikonCameras`** — single comparison, no grouping needed:

```
EntityProvider "nikonCameras"
├── entityType: CAMERA
└── filter (FilterNode)
    ├── type: COMPARISON
    ├── field: "producer.name"
    ├── operator: EQUALS
    └── value: "Nikon"
```

**`nikon60sCameras`** — AND group with three comparisons:

```
EntityProvider "nikon60sCameras"
├── entityType: CAMERA
└── filter (FilterNode)
    ├── type: AND_GROUP
    └── children:
        ├── (FilterNode)
        │   ├── type: COMPARISON
        │   ├── field: "producer.name"
        │   ├── operator: EQUALS
        │   └── value: "Nikon"
        ├── (FilterNode)
        │   ├── type: COMPARISON
        │   ├── field: "releaseYear"
        │   ├── operator: GREATER_THAN_OR_EQUAL
        │   └── value: "1960-01"
        └── (FilterNode)
            ├── type: COMPARISON
            ├── field: "releaseYear"
            ├── operator: LESS_THAN
            └── value: "1970-01"
```

**Nested example** — "all Nikon or Canon cameras from the 60s":

```
filter (AND_GROUP)
├── (OR_GROUP)
│   ├── producer.name EQUALS "Nikon"
│   └── producer.name EQUALS "Canon"
└── (AND_GROUP)
    ├── releaseYear GREATER_THAN_OR_EQUAL "1960-01"
    └── releaseYear LESS_THAN "1970-01"
```

**IN clause** — "Cameras from Nikon, Canon, or Minolta":

```
filter (FilterNode)
├── type: COMPARISON
├── field: "producer.name"
├── operator: IN
└── values: ["Nikon", "Canon", "Minolta"]
```

### 6.3 Sort Model

An EntityProvider can optionally define an ordered list of sort fields:

```java
public class SortField implements Coded {
    Long id;
    String code;
    String field;        // dot-path, e.g. "releaseYear", "producer.name"
    Long fieldNodeId;
    SortDirection direction;  // ASC or DESC
    Long directionNodeId;
}

public enum SortDirection {
    ASC,
    DESC
}
```

**Example — Nikon cameras ordered by release year descending:**

```
EntityProvider "nikonCameras"
├── entityType: CAMERA
├── filter: { producer.name EQUALS "Nikon" }
└── sortFields:
    └── (SortField)
        ├── field: "releaseYear"
        └── direction: DESC
```

Multiple sort fields are applied in order (first field is primary sort, second is tiebreaker, etc.).

### 6.4 AppConfig Tree Type Hierarchy

**FilterNode types:**

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `FilterNode` | `EntityProvider` | `filter` | false | false | `...appconfig.FilterNode` |
| `FilterNodeType` | `FilterNode` | `type` | false | true | `...appconfig.FilterNodeType` |
| `FilterField` | `FilterNode` | `field` | false | false | `java.lang.String` |
| `FilterOperator` | `FilterNode` | `operator` | false | true | `...appconfig.FilterOperator` |
| `FilterValue` | `FilterNode` | `value` | false | false | `java.lang.String` |
| `FilterValueItem` | `FilterNode` | `values` | true | false | `java.lang.String` |
| `FilterNodeChildren` | `FilterNode` | `children` | true | false | `...appconfig.FilterNode` |

`FilterNode` is **self-referential** — an AND_GROUP or OR_GROUP's children are also FilterNodes. The recursive pattern is the same as ViewNode children.

Note: `FilterNode` as a direct child of `EntityProvider` represents the root filter (single, non-collection). `FilterNodeChildren` enables nesting by making `FilterNode` a child of another `FilterNode`.

**SortField types:**

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `SortField` | `EntityProvider` | `sortFields` | true | false | `...appconfig.SortField` |
| `SortFieldField` | `SortField` | `field` | false | false | `java.lang.String` |
| `SortDirection` | `SortField` | `direction` | false | true | `...appconfig.SortDirection` |

### 6.5 EntityProvider In-Memory Model Update

```java
public class EntityProvider implements Coded {
    // ... existing fields ...
    FilterNode filter;          // optional root filter (null = all entities)
    List<SortField> sortFields; // optional sort order (empty = default/unordered)
}
```

If `filter` is null/absent, the provider returns all entities (backward compatible with existing providers).

### 6.6 Backend Execution: JPA Criteria API

The `EntitySelectService` and `ViewDataService` translate the filter tree into JPA Criteria predicates:

```java
private Predicate buildPredicate(FilterNode node, Root<?> root, CriteriaBuilder cb) {
    if (node.getType() == FilterNodeType.COMPARISON) {
        Path<?> path = walkPath(root, node.getField());
        return switch (node.getOperator()) {
            case EQUALS                -> cb.equal(path, convert(node.getValue(), path));
            case NOT_EQUALS            -> cb.notEqual(path, convert(node.getValue(), path));
            case GREATER_THAN          -> cb.greaterThan(path.as(Comparable.class), ...);
            case GREATER_THAN_OR_EQUAL -> cb.greaterThanOrEqualTo(...);
            case LESS_THAN             -> cb.lessThan(...);
            case LESS_THAN_OR_EQUAL    -> cb.lessThanOrEqualTo(...);
            case IS_NULL               -> cb.isNull(path);
            case IS_NOT_NULL           -> cb.isNotNull(path);
            case IN                    -> path.in(convertValues(node.getValues(), path));
            case LIKE                  -> cb.like(path.as(String.class), node.getValue());
        };
    } else {
        List<Predicate> childPredicates = node.getChildren().stream()
            .map(child -> buildPredicate(child, root, cb))
            .toList();
        return node.getType() == FilterNodeType.AND_GROUP
            ? cb.and(childPredicates.toArray(new Predicate[0]))
            : cb.or(childPredicates.toArray(new Predicate[0]));
    }
}
```

**Dot-path navigation** creates JPA joins: `"producer.name"` becomes `root.join("producer").get("name")`. Unlimited depth — each dot segment adds a join. This reuses the same metamodel navigation concept from `DataBindingService.walkPrefix()`.

**Sort execution:**

```java
for (SortField sf : provider.getSortFields()) {
    Path<?> path = walkPath(root, sf.getField());
    query.orderBy(sf.getDirection() == SortDirection.ASC
        ? cb.asc(path) : cb.desc(path));
}
```

**Value type conversion:** Filter values are stored as strings. On execution, the backend converts them to the field's Java type using the existing `convertValue` logic from `DataFormPersistenceService` (String, Long, Integer, Double, Boolean, YearMonth, LocalDate, etc.). The field's Java type is known from the JPA metamodel path.

### 6.7 Frontend: Filter Builder UI

The admin editor shows a visual filter builder when editing an EntityProvider that has (or should have) a filter.

#### Value input widgets — type-aware

When the user selects a field via auto-proposals (from `DataBindingService`), the `BindingCompletion.javaType` determines which input widget to render for the value:

| Field javaType | Value widget | Stored as |
|---|---|---|
| `String` | Text field | `"Nikon"` |
| `Long`, `Integer` | Number field | `"42"` |
| `Double`, `Float` | Decimal number field | `"3.14"` |
| `Boolean` | Checkbox | `"true"` / `"false"` |
| `YearMonth` | YearMonth picker (same as `_YearMonthField`) | `"1960-01"` |
| `LocalDate` | Date picker (same as `_DateField`) | `"1960-01-15"` |
| `LocalDateTime` | DateTime picker | `"1960-01-15T10:30"` |

For IN operator, the multi-value input renders a list of values, each using the appropriate type-aware picker. Values can be added/removed individually.

For IS_NULL / IS_NOT_NULL, no value input is shown (the operator is the entire predicate).

#### Operator dropdown — type-filtered

The available operators depend on the field's Java type:

| Field category | Available operators |
|---|---|
| String | EQUALS, NOT_EQUALS, LIKE, IN, IS_NULL, IS_NOT_NULL |
| Numeric (Long, Integer, Double) | EQUALS, NOT_EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, IN, IS_NULL, IS_NOT_NULL |
| Date/Time (YearMonth, LocalDate, LocalDateTime) | EQUALS, NOT_EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, IS_NULL, IS_NOT_NULL |
| Boolean | EQUALS, IS_NULL, IS_NOT_NULL |

#### Filter tree structure

The filter builder displays the FilterNode tree as an indented structure:

```
┌─ Filter ──────────────────────────────────────────┐
│                                                    │
│  AND                                    [+ Add]   │
│  ├── producer.name  EQUALS  "Nikon"     [× Del]   │
│  ├── releaseYear  >=  "1960-01"         [× Del]   │
│  └── releaseYear  <   "1970-01"         [× Del]   │
│                                                    │
│  [+ Add condition]  [+ Add AND group]  [+ Add OR] │
└────────────────────────────────────────────────────┘
```

- **Add condition** adds a COMPARISON child to the current group.
- **Add AND group** / **Add OR group** adds a nested group.
- Each comparison shows: field (with auto-proposals), operator (dropdown), value (type-aware widget).
- Each node has a delete button.
- The root can be either a single COMPARISON or a group. If the user adds a second condition at root level, the root automatically becomes an AND_GROUP wrapping both.

#### Sort fields

Below the filter, the admin can add sort fields:

```
┌─ Sort ────────────────────────────────────────────┐
│  1. releaseYear  DESC                  [× Del]    │
│  2. name         ASC                   [× Del]    │
│  [+ Add sort field]                               │
└────────────────────────────────────────────────────┘
```

Each sort field has: field (with auto-proposals), direction dropdown (ASC/DESC).

---

## Task 7 — Deep Copy of AppConfig Nodes

**Goal:** Allow the admin to duplicate any AppConfig instance node (EntityProvider, ViewNode, etc.) along with all its descendants, producing an independent copy that can be modified without affecting the original. This enables a copy-then-modify workflow — e.g., copy the "all cameras" EntityProvider, rename to "nikonCameras", then add a filter.

### 7.1 Motivation

Creating derived configurations from scratch is tedious and error-prone. Common workflows:

- **EntityProvider:** "Nikon cameras" is "all cameras" + a filter on `producer.name`. Copy the provider, add the filter.
- **ViewNode:** "Nikon" tree node is the "Cameras" node with a different label and provider ref. Copy the ViewNode (including its tableColumns), change label and provider.
- **General:** Any complex node with many children (DataForm with many elements, EntityRenderer, etc.) benefits from copy-then-modify.

### 7.2 Backend: AppConfigMutationService.copyNode()

New mutation operation that deep-copies a node and all its descendants:

```java
public Long copyNode(Long sourceNodeId, String newCode) {
    // 1. Load the source AppConfigObjectEntity
    // 2. Create a new entity with same type, same parent, code = newCode
    // 3. Copy enumValue if present
    // 4. Recursively copy all descendant nodes, maintaining parent-child relationships
    // 5. Return the new root node's ID
}
```

**Key behaviors:**

- The copy gets the **same parent** as the source (sibling copy).
- The copy's root node gets `code = newCode`. All descendant nodes retain their original codes (they are internal structure, not user-facing identifiers).
- `enumValue` is copied on all nodes.
- The operation is recursive — a ViewNode with children, tableColumns, and their sub-children all get copied.
- The copy is fully independent — modifying it does not affect the original.

### 7.3 Backend: REST Endpoint

```
POST /api/app-config/node/{id}/copy
Body: { "newCode": "nikonCameras" }
```

Returns the updated AppConfig tree (same pattern as other mutations).

### 7.4 Frontend: Copy Button in Admin Editor

When an instance node is selected in the AppConfigDetailPanel, show a **Copy** button alongside Save and Delete:

```
[Save]  [Copy]  [Delete]
```

Clicking **Copy** opens a dialog asking for the new code:

```
┌──────────────────────────────────┐
│ Copy "cameras"                   │
│                                  │
│ New code: [nikonCameras       ]  │
│                                  │
│            [Cancel]  [Copy]      │
└──────────────────────────────────┘
```

After copying, the tree refreshes and the new node is selected for editing.

### 7.5 Applicable Node Types

The copy operation is generic — it works on any `AppConfigObjectEntity`. However, it is most useful on:

| Node type | Typical workflow |
|---|---|
| `EntityProvider` | Copy provider, add/modify filter and sort |
| `ViewNode` | Copy view node (with tableColumns and children), change label and provider ref |
| `DataForm` | Copy form (with all elements), modify for a different entity or layout |
| `EntityRenderer` | Copy renderer, tweak template |

The Copy button appears on all deletable instance nodes (`isDeletable == true`).

---

## Task Dependency Order

```
Task 1 (dataBinding + Auto-Proposals)     ← DONE
  ├── 1.1–1.4: Backend (service, endpoint, AppConfig tree, persistence update)
  ├── 1.5–1.6: Frontend (model update, config editor picker)
  └── 1.7: No FormRendererView changes needed

Task 2 (Entity Relationships + EntityProvider/Renderer)  ← DONE
  ├── 2.1–2.9: Backend (proposals, ENTITY_SELECT, EntityProvider, EntityRenderer,
  │            jmustache templates, EntitySelectService, persistence, Camera entity)
  └── 2.10–2.12: Frontend (ENTITY_SELECT widget, template editor, config panels)

Task 3 → Moved to viewIntegration.md (Tasks V1–V4)

Task 4 (Validation)                       ← Independent, can proceed anytime
  ├── 4.1–4.2: Backend validation model + enforcement
  └── 4.3: Frontend validation

Task 5 → Moved to viewIntegration.md (Task V2)

Task 6 (EntityProvider Filter & Sort)     ← Depends on Task 2
  ├── 6.1–6.5: Backend (FilterNode/SortField models, seeder types, tree builder,
  │            EntityProvider model update)
  ├── 6.6: Backend execution (JPA Criteria API predicates, sort, dot-path joins)
  └── 6.7: Frontend (filter builder UI, type-aware value widgets, sort editor)

Task 7 (Deep Copy of AppConfig Nodes)    ← Independent, can proceed anytime
  ├── 7.2: Backend (AppConfigMutationService.copyNode, recursive deep copy)
  ├── 7.3: Backend (REST endpoint POST /api/app-config/node/{id}/copy)
  └── 7.4: Frontend (Copy button + new-code dialog in admin editor)
```

Task 6 depends on Task 2 (EntityProvider must exist). Task 7 is independent — it's a generic tree operation. The copy-then-modify workflow is especially useful in combination with Task 6: copy the "all cameras" provider, then add a filter to create "Nikon cameras". Same for ViewNodes: copy the "Cameras" view node, change label and provider ref.
