# Filtered ENTITY_SELECT Specification

## Overview

This specification extends the ENTITY_SELECT DataFormElementType to support **dynamic filtering
via FilterInjectable** — the same mechanism already used by GRID elements (see `gridElement.md`).

**Motivating example:** When editing a Camera, the user selects a CameraProducer. A second
ENTITY_SELECT for lens mount then shows only `CameraLensMount2CameraProducer` entries matching
the selected producer. The options render as e.g. "EF Mount (Canon)".

**Related specifications:**
- `gridElement.md` — GRID element, FilterInjectable, EditorEntityBuilder, ExpressionContext
- `expressions.md` — Expression system, IInjectable, InjectableExecutor
- `dataBinding.md` — EntityProvider, EntityRenderer, data binding model

---

## Implementation Status

| Component | Status |
|---|---|
| Camera entity: new nullable FK `cameraLensMount2CameraProducer` | Done |
| `EntitySelectService` context-aware overload | Done |
| `EntitySelectController` POST endpoint with formState | Done |
| FilterInjectable expression `cameraMountFilter` | Done |
| EntityProvider `mountsForCamera` with filterInjectableRef | Done |
| EntityRenderer for `{{cameraLensMount.name}} ({{cameraLensMount.producer.name}})` | Done |
| DataForm `camera` with all elements | Done |
| `reloadOnChangeOf` mechanism (replaces `reloadOnChange`) | Done |
| Remove deprecated `reloadOnChange` flag | Done |
| Generic reset of dependent ENTITY_SELECTs on reload | Done |
| `mandatory` attribute on DataFormElement | Done |
| Server-side save validation (FilterInjectable re-run) | Done |
| Cascading rename of element codes in `reloadOnChangeOf` | Done |
| Tree build validation for `reloadOnChangeOf` references | Done |
| Seeder test for Camera form configuration | Done |
| End-to-end test (filtered options, save validation, mandatory) | Done |

---

## Task F1 — Camera Entity Extension

### F1.1 New Field on Camera

```java
@Entity
@Table(name = "CAMERA")
public class Camera {
    // ... existing fields: id, name, releaseYear, producer ...

    @ManyToOne
    @JoinColumn(name = "camera_lens_mount_2_camera_producer_id")
    private CameraLensMount2CameraProducer cameraLensMount2CameraProducer;
}
```

- **Nullable:** Yes. Not every camera has an interchangeable lens mount (fixed-lens cameras).
- **Constraint:** When set, `cameraLensMount2CameraProducer.cameraProducer` must equal
  `camera.producer`. Enforced server-side on save (see Task F6).

### F1.2 DataFormEntityType

`DataFormEntityType.CAMERA` already exists. No change needed.

---

## Task F2 — Context-Aware EntitySelectService

### F2.1 Problem

Currently, `EntitySelectService.getOptions(providerCode, rendererCode)` calls the 2-argument
`FilterExecutor.executeQuery(provider, entityClass)` which ignores `filterInjectableRef`.
There is no way to pass formState or editor entity context.

### F2.2 New Overload

```java
public List<EntityOption> getOptions(String providerCode, String rendererCode,
                                     String dataFormCode, Long entityId,
                                     Map<String, String> formState)
```

When the EntityProvider has a `filterInjectableRef` and `dataFormCode` is provided:
1. Resolve the DataForm from AppConfig
2. Build a transient editor entity from formState via `EditorEntityBuilder`
3. Create an `ExpressionContext` with `"editor"` and `"formState"` entries
4. Call the 4-argument `FilterExecutor.executePagedQuery(provider, entityClass, 0, MAX, context)`

This mirrors the pattern already established in `GridDataService.getGridData()`.

The original 2-argument `getOptions(providerCode, rendererCode)` delegates to the new overload
with null context parameters, preserving backward compatibility.

### F2.3 EntitySelectController POST Endpoint

New POST endpoint alongside the existing GET:

```
POST /api/entity-select/options
```

Request body:
```json
{
  "provider": "mountsForCamera",
  "renderer": "mountMappingCaption",
  "dataFormCode": "camera",
  "entityId": 42,
  "formState": {
    "name": "X-T5",
    "producer": "3"
  }
}
```

Response: same `List<EntityOption>` as the existing GET endpoint.

The existing GET endpoint remains for ENTITY_SELECTs without `filterInjectableRef`.

The frontend uses the POST endpoint whenever the EntityProvider referenced by the
ENTITY_SELECT has a `filterInjectableRef`. This applies both to initial form load and
to reloads triggered by dependency changes.

---

## Task F3 — Dependency Mechanism: `reloadOnChangeOf`

### F3.1 Replaces `reloadOnChange`

The existing `reloadOnChange` boolean flag on DataFormElement is **removed**. It is replaced by
a consumer-side declaration: the dependent element declares which sibling element(s) it listens to.

**Rationale:** Every DataFormElement implicitly emits change events (based on its `code`). There
is no need for a flag to enable emitting. The relevant question is always on the consumer side:
"which elements do I depend on?"

### F3.2 New Attribute: `reloadOnChangeOf`

```java
public class DataFormElement implements Coded {
    // ... existing fields ...

    /** List of sibling element codes whose changes trigger a reload of this element. */
    private List<String> reloadOnChangeOf = new ArrayList<>();
}
```

Example — Camera form:
```
"cameraLensMount2CameraProducer" (DataFormElement)
    ├── type: ENTITY_SELECT
    ├── dataBinding: "cameraLensMount2CameraProducer"
    ├── entityProviderRef: "mountsForCamera"
    ├── entityRendererRef: "mountMappingCaption"
    └── reloadOnChangeOf: ["producer"]
```

When the `producer` element changes, the `cameraLensMount2CameraProducer` element:
1. Resets its current value to null
2. Reloads its options via the context-aware POST endpoint with updated formState

### F3.3 AppConfigType Seeder

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `ReloadOnChangeOf` | `DataFormElement` | `reloadOnChangeOf` | true | false | `java.lang.String` |

Replaces the existing `ReloadOnChange` (Boolean) type.

### F3.4 Cascading Dependencies

The signal mechanism naturally supports multi-level cascading. If element A changes:
- Element B (with `reloadOnChangeOf: ["A"]`) resets and reloads
- If B also changes as a result (reset to null), element C (with `reloadOnChangeOf: ["B"]`)
  resets and reloads in turn

No special cascade logic is needed — each element independently reacts to its declared dependencies.

### F3.5 Config Editor UX

In the AppConfig editor, `reloadOnChangeOf` is configured as a multi-select picker showing all
sibling DataFormElement codes within the same DataForm. This prevents typos and provides a clear
overview of available dependencies.

### F3.6 Cascading Rename

When a DataFormElement's `code` is renamed within a DataForm, all `reloadOnChangeOf` entries
referencing the old code in sibling elements are automatically updated to the new code.

**Scope:** Limited to elements within the same DataForm (since `reloadOnChangeOf` only references
siblings).

### F3.7 Tree Build Validation

During `AppConfigTreeBuilder.buildTree()`, all `reloadOnChangeOf` entries are validated:
- Each referenced code must correspond to an existing sibling DataFormElement within the same DataForm
- Broken references are logged as errors (or cause the build to fail)

This serves as a safety net regardless of how the inconsistency was introduced (manual DB edit,
migration, config editor bug).

### F3.8 Migration: Remove `reloadOnChange`

- Remove the `reloadOnChange` boolean field and `reloadOnChangeNodeId` from `DataFormElement`
- Remove the `ReloadOnChange` AppConfigType seeder entry
- Update existing GRID configurations (e.g., on `cameraProducer` form) to use `reloadOnChangeOf`
  on the GRID element instead of `reloadOnChange` on the trigger element
- Update `gridElement.md` sections G1.6.4 (Reload Triggers) to reference `reloadOnChangeOf`

---

## Task F4 — `mandatory` Attribute on DataFormElement

### F4.1 New Attribute

```java
public class DataFormElement implements Coded {
    // ... existing fields ...

    /** When true, the field must have a non-null value on save. Default: false. */
    private boolean mandatory;
}
```

### F4.2 AppConfigType Seeder

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `Mandatory` | `DataFormElement` | `mandatory` | false | false | `java.lang.Boolean` |

### F4.3 Validation Behavior

- `mandatory=true` + value is null → validation error on save
- `mandatory=false` (default) + value is null → allowed, skip further validation
- Value is not null → proceed with FilterInjectable validation (Task F6) regardless of mandatory flag

---

## Task F5 — FilterInjectable for Camera Lens Mount

### F5.1 Expression: `cameraMountFilter`

```java
@Override
public void execute() {
    Camera c = (Camera) getInjectionContext().getEditorEntity();
    if (c == null || c.getProducer() == null) {
        setResult(null);
        return;
    }
    setResult(
        comparison("cameraProducer.id", FilterOperator.EQUALS, c.getProducer().getId())
    );
}
```

Filters `CameraLensMount2CameraProducer` rows to those matching the camera's selected producer.
When no producer is selected, `setResult(null)` returns an empty result set (no options).

### F5.2 EntityProvider: `mountsForCamera`

```
EntityProvider: code="mountsForCamera"
    entityType: CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER
    filterInjectableRef: "cameraMountFilter"
    sortFields:
        - field: "cameraLensMount.name", direction: ASC
```

### F5.3 EntityRenderer: `mountMappingCaption`

```
EntityRenderer: code="mountMappingCaption"
    entityType: CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER
    template: "{{cameraLensMount.name}} ({{cameraLensMount.producer.name}})"
```

Renders e.g. "EF Mount (Canon)", "X-Mount (Fuji)".

---

## Task F6 — Server-Side Save Validation

### F6.1 Generic FilterInjectable Validation

When saving an entity via a DataForm, for each ENTITY_SELECT element whose EntityProvider has
a `filterInjectableRef`:

1. If the field value is null → skip (allowed unless `mandatory=true`, handled by Task F4)
2. Build the ExpressionContext from the entity being saved
3. Re-execute the FilterInjectable query via FilterExecutor
4. Check that the selected entity's ID is in the result set
5. If not → reject the save with a validation error

**Rationale:** The FilterInjectable already encodes the constraint ("which values are valid for
this context"). Re-running it on save is the most generic approach — no additional constraint
configuration needed.

### F6.2 Error Response

Validation error returns a structured response indicating which field failed and why, e.g.:
```json
{
  "field": "cameraLensMount2CameraProducer",
  "message": "Selected value is not valid for the current context"
}
```

---

## Task F7 — Camera DataForm Configuration

### F7.1 DataForm: `camera`

```
DataForm: code="camera"
    entity: CAMERA
    elements:
        "name" (DataFormElement)
            type: INPUT_STRING
            dataBinding: "name"
        "releaseYear" (DataFormElement)
            type: DATE_PICKER__YEAR_MONTH
            dataBinding: "releaseYear"
        "producer" (DataFormElement)
            type: ENTITY_SELECT
            dataBinding: "producer"
            entityProviderRef: "allCameraProducers"
            entityRendererRef: "producerCaption"
        "cameraLensMount2CameraProducer" (DataFormElement)
            type: ENTITY_SELECT
            dataBinding: "cameraLensMount2CameraProducer"
            entityProviderRef: "mountsForCamera"
            entityRendererRef: "mountMappingCaption"
            reloadOnChangeOf: ["producer"]
```

### F7.2 Behavior Summary

| User Action | System Response |
|---|---|
| Opens new Camera form | name, releaseYear, producer visible; lens mount hidden (no producer) |
| Selects a producer | Lens mount field becomes visible; options load filtered by producer |
| Changes producer | Lens mount resets to null; options reload for new producer |
| Selects lens mount | Value stored; rendered as "EF Mount (Canon)" |
| Saves with lens mount set | Server validates lens mount belongs to selected producer |
| Saves without lens mount | Allowed (field is not mandatory; fixed-lens camera) |

---

## Future Improvements

### Visibility Mechanism

Currently, the lens mount ENTITY_SELECT should be hidden when no producer is selected.
A client-side boolean expression/injectable mechanism is needed to control element visibility
dynamically based on form state. This is not yet specified and will be addressed in a separate
specification.

**Interim behavior:** The lens mount field is always visible but shows an empty dropdown when
no producer is selected.

### Stacked Editor for Inline Creation

When the filtered options list is empty (no lens mount mappings exist for the selected producer),
the user currently has no options to pick from. A future enhancement could allow creating a
`CameraLensMount2CameraProducer` entry directly from within the Camera editor via a stacked
editor — similar to the GRID's AddAction pattern but triggered from an ENTITY_SELECT's empty state.
