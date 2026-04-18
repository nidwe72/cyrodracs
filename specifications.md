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
| `graphql.md` | GraphQL migration — code-first with JavaSchemaGenerator, partial data loading | Done |
| `components.md` | CyrodracsTable custom widget, per-column widths, server-side sorting | Pending |

## Pending Items

- **C1 — CyrodracsTable** (`components.md`): Custom table widget replacing Flutter's DataTable.
  Per-column widths, server-side sorting with pagination, compact actions column.
- **Editor Tabs**: Concurrent editing of related entities in sibling tabs.
  Horizontal/breadth pattern complementing the vertical EditorStack. Not yet specified.

## Architectural Direction

- **GraphQL**: Full migration from REST to GraphQL using a custom `JavaSchemaGenerator`
  (~180 lines) that generates the GraphQL SDL at startup from Java classes via reflection.
  Served by Spring for GraphQL with `@QueryMapping`/`@MutationMapping` controllers.
  Pure Java — no Kotlin, no external schema library. See `graphql.md` for full specification.

## Tooling

- **Spec Toolkit (Future)**: The project spans multiple directories (`cyrodracs` backend,
  `cyrodracs_frontend`, `cyrodracs_db`). Currently there is no mechanism to make cross-project
  structure discoverable at specification time — e.g., an AI assistant working on backend specs
  may not know that the frontend project exists one level up. A lightweight spec toolkit or
  project manifest should be introduced to declare the project topology, so that specification
  and implementation work can reliably span all subprojects without manual hints. Not yet
  specified.
