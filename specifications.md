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
| `components.md` | CyrodracsTable custom widget, per-column widths, server-side sorting | Pending |

## Pending Items

- **C1 — CyrodracsTable** (`components.md`): Custom table widget replacing Flutter's DataTable.
  Per-column widths, server-side sorting with pagination, compact actions column.
- **Editor Tabs**: Concurrent editing of related entities in sibling tabs.
  Horizontal/breadth pattern complementing the vertical EditorStack. Not yet specified.
