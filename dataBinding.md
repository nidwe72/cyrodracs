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
```

Task 1 is the foundation — it introduces the `dataBinding` field, the JPA metamodel introspection, and the picker UI. Task 2 extends this to relationships and introduces EntityProvider/EntityRenderer. Task 4 (Validation) is independent. Tasks 3 and 5 are now part of `viewIntegration.md` as they concern the view/navigation layer.
