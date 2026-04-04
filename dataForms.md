# DataForms Specification

## New JPA Entities

### CameraProducer
- JPA entity mapped to a database table
- Package: `sciens.cyrodracs.camera`
- Attributes:
  - `id` (Long, auto-generated primary key)
  - `name` (String)
- REST API: `GET /api/camera-producers` (list all), `DELETE /api/camera-producers/{id}` (delete)

### Camera
- JPA entity mapped to a database table
- Package: `sciens.cyrodracs.camera`
- Attributes:
  - `id` (Long, auto-generated primary key)
  - `name` (String)
  - `releaseYear` (YearMonth, custom converter)
- REST API: `GET /api/cameras` (list all), `DELETE /api/cameras/{id}` (delete)
- Future: `producer` (ManyToOne relationship to `CameraProducer`)

---

## DataFormEntityType Enum

- Package: `sciens.cyrodracs.appconfig`
- Maps entity keys to their fully qualified class names.
- Entries:
  - `CAMERA_PRODUCER("sciens.cyrodracs.camera.CameraProducer")`
  - `CAMERA_LENS_MOUNT("sciens.cyrodracs.camera.CameraLensMount")`
  - `CAMERA("sciens.cyrodracs.camera.Camera")`
- Each enum value carries a `fqcn` (String) property with the entity's fully qualified class name.

---

## DataForm Updates

### New Properties on DataForm

- `entity` (DataFormEntityType) — identifies which JPA entity this DataForm is bound to.
- `entityNodeId` (Long) — DB id of the DataFormEntityType child object in the config tree (same pattern as `DataFormElement.typeNodeId`).

### AppConfigTypeSeeder Update

- Register `DataFormEntityType` as a child type of `DataForm` (enum type, field name `entity`, not a collection).
- Seeder uses `ensureType()` to add missing types incrementally (no longer skips if types already exist).

### AppConfigTreeBuilder Update

- When building a `DataForm`, read the `DataFormEntityType` enum child node and populate `DataForm.entity` and `DataForm.entityNodeId`.

### Enum Values Endpoint

- `GET /api/app-config/types/{typeCode}/enum-values` — returns enum constant names for any enum type (e.g. `DataFormEntityType`, `DataFormElementType`).

---

## DataFormData — Generic Frontend Data Structure

- DTO class: `DataFormData`
- Package: `sciens.cyrodracs.appconfig`
- Properties:
  - `dataFormCode` (String) — code of the DataForm definition
  - `entityId` (Long, nullable) — null when creating, populated when editing
  - `values` (Map<String, Object>) — keyed by DataFormElement.code, entity-agnostic

The frontend works exclusively with this generic structure. It has no knowledge of `CameraProducer` or any specific entity class.

### REST API

- `POST /api/data-form-data` — save (create or update) an entity via DataForm
- `GET /api/data-form-data/{dataFormCode}/{entityId}` — load entity values into generic form data

---

## Workflows

### Create Flow

1. User navigates to App tab, double-clicks "CameraProducers" node, clicks Add (+) button.
2. Frontend resolves the DataForm configured for `CAMERA_PRODUCER` from the AppConfig tree.
3. An empty form is rendered. User fills fields (e.g. `name = "Nikon"`).
4. Frontend sends `DataFormData` (no `entityId`) to `POST /api/data-form-data`.
5. Backend looks up `DataForm.entity` → `CAMERA_PRODUCER` → FQCN → instantiates via reflection, maps each `DataFormElement.code` to the entity attribute, persists.
6. Frontend returns to the table view.

### Edit Flow

1. User clicks Edit button on a CameraProducer row in the table.
2. Frontend resolves the DataForm for `CAMERA_PRODUCER`, then loads values via `GET /api/data-form-data/{code}/{id}`.
3. Form is rendered with pre-populated values. User edits.
4. Frontend sends updated `DataFormData` (with `entityId`) to `POST /api/data-form-data`.
5. Backend loads existing entity by id, maps values back via reflection, persists.
6. Frontend returns to the table view.

### Delete Flow

1. User clicks Delete button on a CameraProducer row.
2. Confirmation dialog is shown.
3. On confirm, frontend calls `DELETE /api/camera-producers/{id}`.
4. Table is refreshed.

---

## Frontend Tab Structure

- **App** — renders AppView directly (tree with entity nodes + detail panel for tables/forms)
- **Admin** — sub-tabs: "Config editor" (AppConfig tree editor), "Config"
- **Playground** — sub-tabs: "App playground" (DataForm renderer), "Form Renderer", "Hello World"

---

## EntityProvider (future — not yet implemented)

A class that lets one provide JPA entities in a generic configurable way. Will be specified later.

### AppConfig Update (future)

- `AppConfig.getObjectProviders()` returns `Map<String, EntityProvider>`
