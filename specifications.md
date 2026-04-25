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
| `components.md` | Table component refactor — per-column widths, sticky header, two-row header (label+sort / filter), server-side sort; implementation approach undecided | Pending |
| `columnFilters.md` | User-driven column filters and sort for ENTITY_LIST and GRID tables, User entity, advanced filter editor, SavedFilter | v1 Done; v2/v3 Pending |
| `entityTableUnification.md` | Future refactor — unify ENTITY_LIST and GRID under a single GRID abstraction | Future |

## Pending Items

- **C1 — Table Component Refactor** (`components.md`): Replacement for
  Flutter's `DataTable` with per-column widths, sticky header, two-row
  header (label+sort / filter input), server-side sort, keyboard /
  accessibility. **Implementation approach undecided** — pure-Flutter
  custom widget vs. third-party packages (data_table_2, pluto_grid,
  Syncfusion, …) to be evaluated before picking. Does **not** block
  column-filter work (CF1 / CF2 ship on the interim `DataTable` first).
- **CF7 — Advanced Filter Editor v2** (`columnFilters.md`): Nested AND/OR editor in
  EditorStack frame, session-only named filters, LITERAL + PARENT_ENTITY bindings.
  v1 (CF1–CF6) is shipped; see `columnFilters.md` "v1 implementation notes" for
  intentional v1 simplifications carried into v2 (picker concurrency, EditorStack-
  preserved filter state for GRID, admin autoproposals, wildcard escaping).
- **CF8 — SavedFilter v3** (`columnFilters.md`): Per-user persisted filters with
  dropdown selector and CURRENT_USER binding.
- **Entity Table Unification** (`entityTableUnification.md`): Aspirational refactor,
  not scheduled. Column-filter work must remain surface-agnostic so unification can
  land without rework.
- **Editor Tabs**: Concurrent editing of related entities in sibling tabs.
  Horizontal/breadth pattern complementing the vertical EditorStack. Not yet specified.
- **AppConfig Reload Mechanism**: Today the frontend loads the AppConfig tree
  once per session; admin-published config changes are only picked up on a
  browser reload. This affects anything derived from the config — notably
  the column-filter metadata cache (`columnFilters.md` CF3.1). A future
  mechanism is needed, combining (a) a time-interval reload baseline and
  (b) a fine-grained invalidation signal (e.g. a backend-issued
  `configVersion` stamp on responses that the frontend compares against its
  cached version and reloads on change). Scope, cadence, and scope-of-
  invalidation (whole tree vs. sub-tree) to be specified. Not yet
  scheduled.
- **Selectable GRID**: Today GRID is a table embedded in a DataForm editor
  for displaying related entities (e.g. a producer's lens-mount
  assignments). A future extension lets a DataForm **field** bind to a
  GRID as its picker source — clicking a row selects that row's entity as
  the field's value. This provides a columnar alternative to
  `ENTITY_SELECT` for use cases where a dropdown is insufficient
  (e.g. choosing a specific `CameraLensMount2CameraProducer` with
  visibility of multiple attributes). **`ENTITY_SELECT` itself is
  unchanged by this track** — it remains the dropdown option. Admin picks
  which form-element type to use per field at config time.
  - **Rendering choices to specify:** inline vs. overlay placement;
    single- vs. multi-select in table mode; how the GRID's selected
    row(s) write back to the field binding; how an existing value
    initializes the GRID's selection state.
  - **Existing dependent-source behavior preserved:** when the source
    field of a dependent picker is unset, the GRID is not displayed at
    all — matches today's dependent `ENTITY_SELECT` UX.
  - **Column filtering inside the selectable GRID** works via
    `columnFilters.md` CF1 without protocol change: user filters AND
    with the GRID's existing base filter (static + `FilterInjectable`),
    which CF5.1 already handles.
  - **Entity-ref column pickers inside a selectable GRID** require
    extending `columnFilters.md` CF3.4.1's projection to **execute the
    `FilterInjectable` first, then project its materialized
    `FilterNode`**. The projection algorithm itself is unchanged; it
    just receives a tree with no remaining injectables. Invocation
    timing: on every picker-open / typeahead query, with current
    `formState` as injection context. Not cached; microsecond-scale
    cost.
  - **Host widget:** the table component from `components.md` C1, once
    chosen, is the natural renderer. Column filter UI from CF1 applies
    directly.
  - **Relationship to `entityTableUnification.md`:** compatible and
    reinforcing. If unification lands first, the selectable GRID
    capability applies uniformly to both ENTITY_LIST-style and
    form-embedded tables (they'd be the same underlying primitive).
  - Cross-refs: `filteredEntitySelect.md` (the untouched ENTITY_SELECT
    dropdown track), `gridElement.md` (current GRID spec extended by
    this track), `columnFilters.md` CF3.4.1, `components.md` C1,
    `entityTableUnification.md`. Not yet scheduled.

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
