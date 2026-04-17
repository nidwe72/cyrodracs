# Master Specification Index

Overview of all specification documents and their implementation status.

## Specification Documents

| Document | Topic | Status |
|---|---|---|
| `appConfig.md` | AppConfig tree model, type system, persistence | Done |
| `dataForms.md` | DataForm elements, field types, form rendering | Done |
| `dataBinding.md` | Entity binding, EntityProvider, EntityRenderer, filters | Done |
| `domainEntities.md` | Camera domain model (Producer, LensMount, Camera) | Done |
| `viewIntegration.md` | ViewTree navigation, ViewNode types, view data endpoint | Done |
| `frontendStyling.md` | Theme, panel styling, table consistency | Done |
| `expressions.md` | Expression system, FilterInjectable, type resolver | Done |
| `gridElement.md` | GRID element, EditorStack, AddAction, context bindings, pending children | Done |
| `filteredEntitySelect.md` | Filtered ENTITY_SELECT via FilterInjectable, reloadOnChangeOf, mandatory, save validation | Done |
| `components.md` | CyrodracsTable custom widget, per-column widths, server-side sorting | Pending |

## Pending Items

- **C1 — CyrodracsTable** (`components.md`): Custom table widget replacing Flutter's DataTable.
  Per-column widths, server-side sorting with pagination, compact actions column.
- **Editor Tabs**: Concurrent editing of related entities in sibling tabs.
  Horizontal/breadth pattern complementing the vertical EditorStack. Not yet specified.

## Architectural Direction

- **GraphQL (Future)**: The current backend API uses REST endpoints (`/api/entity-select/options`,
  `/api/data-form/evaluate`, `/api/data-form-data`, etc.). Future service calls should migrate to
  **GraphQL**. GraphQL is a natural fit for the project's data model — the AppConfig tree,
  entity graphs with @ManyToOne relationships, and the evaluate endpoint's variable response
  shape (different elements return different state) all benefit from client-driven query
  selection. Not yet specified — current implementation continues with REST; GraphQL migration
  is a separate future initiative.

## Tooling

- **Spec Toolkit (Future)**: The project spans multiple directories (`cyrodracs` backend,
  `cyrodracs_frontend`, `cyrodracs_db`). Currently there is no mechanism to make cross-project
  structure discoverable at specification time — e.g., an AI assistant working on backend specs
  may not know that the frontend project exists one level up. A lightweight spec toolkit or
  project manifest should be introduced to declare the project topology, so that specification
  and implementation work can reliably span all subprojects without manual hints. Not yet
  specified.
