# Entity Table Unification Specification

**Status:** Future / Aspirational. Not scheduled; no target phase.

## Overview

Two specification documents describe two table surfaces that are, on close
inspection, the same abstraction realized twice:

- `viewIntegration.md` — `ENTITY_LIST` ViewNode: a full-page entity table
  opened from the left navigation tree.
- `gridElement.md` — `GRID` DataFormElement: an embedded entity table
  rendered inside a DataForm editor.

Both render a paginated table of JPA entities sourced by an `EntityProvider`,
both define columns via `TableColumn`, both navigate to a `DataForm` on row
edit. The divergence is accidental: ENTITY_LIST came first as a top-level
navigation target; GRID came later to embed a table inside a form. The
consequence today is two copies of most of the table machinery —
AppConfigTypes (`TableColumn` vs. `GridTableColumn`), data services
(`ViewDataService` vs. `GridDataService`), tree-builder methods
(`buildTableColumn` vs. `buildGridTableColumn`), and frontend rendering
paths.

This document captures the target model and the migration approach. It is
**aspirational** — a North Star for ongoing work, not a scheduled refactor.
Specs that land in the meantime (e.g. `columnFilters.md`) must treat the two
surfaces uniformly so that unification does not require rework.

---

## Status Quo — Duplication

| Concern | ENTITY_LIST | GRID |
|---|---|---|
| Config type | `ViewNode` with `type=ENTITY_LIST`, holding `tableColumns` | `DataFormElement` with `type=GRID`, holding `tableColumns` |
| Column AppConfigType | `TableColumn` (+ `TableColumnKey`, `TableColumnHeader`, `TableColumnRendererRef`) | `GridTableColumn` (+ `GridTableColumnKey`, `GridTableColumnHeader`, `GridTableColumnRendererRef`) |
| Tree-builder method | `AppConfigTreeBuilder.buildTableColumn` | `AppConfigTreeBuilder.buildGridTableColumn` — near-identical; partially unified in `gridElement.md` G4 via a parameterized private method, but two type codes remain. |
| Data service | `ViewDataService` | `GridDataService` |
| Data query | `viewData(viewNodeCode, page, size)` | GRID data query (body with `entityId`, `formState`) |
| Row rendering utility | `ColumnRenderer` (shared, per `gridElement.md` G3) | Same |
| Parent-context filter | — | `filterInjectableRef` resolving against the editor's current entity |
| Row edit target | `ViewNode.dataFormRef` | `DataFormElement.addAction.targetDataFormRef` |
| Frontend widget | `_buildEntityTable()` in `app_view.dart` | GRID case in `_buildField()` in `form_renderer_view.dart` |
| Pagination | Implemented | Implemented |

Both surfaces have been extended with new cross-cutting features twice over:
sort fields, injectable filters, EntityRenderer rendering, pagination.
Every new feature (including column filters per `columnFilters.md`) must be
applied to both.

---

## Target Model — GRID as the Single Abstraction

The GRID element is the richer abstraction: it carries parent-context
filtering, add actions, context bindings, and the EditorStack integration.
Everything an ENTITY_LIST does, a GRID can do; the inverse is not true.

**Proposed model:** every entity table is a GRID element. An `ENTITY_LIST`
ViewNode becomes an anchor in the navigation tree that, on activation,
**materializes a transient DataForm containing exactly one GRID element** —
the whole table is the form. The transient DataForm has no persisted
entity; it exists to host the GRID.

```
Before (two code paths):
    ViewNode(type=ENTITY_LIST, tableColumns=[...]) ────► ViewDataService
    DataFormElement(type=GRID, tableColumns=[...])  ────► GridDataService

After (one code path):
    ViewNode(type=GRID_HOST, gridRef=<GRID element>)
        ─ materializes ─►  transient DataForm { single GRID element }  ────► GridDataService
```

The frontend routing reduces to: "activating any ViewNode mounts a DataForm
renderer". No separate `_buildEntityTable()` path exists.

---

## Task U1 — Type System Consolidation

Collapse the duplicated AppConfigTypes.

### U1.1 Column Type

- Delete `GridTableColumn` and its children. `TableColumn` becomes the
  single column type, reachable under any parent that can hold columns.
- The AppConfigType system today enforces a single parent per type via
  `AppConfigTypeEntity.parentType`. The unification requires allowing
  `TableColumn` under multiple parents. Two approaches:
  - **A — multi-parent type**: extend `AppConfigTypeEntity` to permit a
    set of parent types. Schema change; small data migration.
  - **B — single parent, GRID-only**: remove the ability to put columns
    directly under a ViewNode (U2 makes this moot since ViewNodes no
    longer hold columns directly).
- **B** is preferred: it follows naturally from the target model and
  avoids a schema change to `AppConfigTypeEntity`.

### U1.2 ViewNode Simplification

`ViewNode` loses:
- `tableColumns` (moves into the wrapped GRID).
- `entityProviderRef` as a direct list source (moves into the wrapped GRID).
- `dataFormRef` as the row-edit target (the wrapped GRID's `addAction`
  takes over).

`ViewNode` gains:
- `gridRef` or an inline GRID definition — the table hosted by this node.

A new ViewNodeType `GRID_HOST` replaces `ENTITY_LIST` conceptually. The
name is a placeholder; the actual type name should reflect the project's
vocabulary.

### U1.3 Transient DataForm

Activating a `GRID_HOST` node materializes a DataForm at runtime:
- DataForm code is derived: e.g. `viewNode:{code}`.
- DataForm entity is unset (or a synthetic "no entity" marker).
- The DataForm has a single element: the GRID being hosted.
- The transient DataForm is not persisted in AppConfig; it is a runtime
  projection.

---

## Task U2 — Routing & Activation Consolidation

Collapse the two frontend activation paths.

### U2.1 Unified Activation

`_onNodeActivated(viewNode)`:
1. If the node is a folder / group, expand/collapse (current behavior).
2. Otherwise, materialize a `DataFormRenderer` for the node's GRID. No
   branch on ViewNodeType.

### U2.2 No Separate List Widget

`_buildEntityTable()` in `app_view.dart` is removed. The GRID renderer in
`form_renderer_view.dart` (per `gridElement.md` G1.6) becomes the single
table rendering path.

### U2.3 Navigation Tree Unchanged

The left navigation tree still displays ViewNodes. The distinction between
"a node that contains children" vs. "a node that hosts a grid" remains
visible to the user; it is just that the latter no longer runs a separate
rendering code path.

---

## Task U3 — Data Service Consolidation

Collapse `ViewDataService` into `GridDataService` (or a renamed unified
service). The GRID data query becomes the single query for all entity
tables.

### U3.1 Query Shape

The existing GRID data query body already carries `entityId` and
`formState`. For top-level (formerly ENTITY_LIST) tables these are simply
null / empty — no parent entity, no ancestor form state.

```json
{
    "entityId": null,
    "formState": {},
    "userFilter": null,
    "userSort": []
}
```

The `viewData` GraphQL query is deprecated; callers migrate to the unified
query.

### U3.2 Column Filter Protocol

`columnFilters.md` CF4 proposes identical `userFilter` / `userSort` args for
both surfaces. After unification, only one query carries them — no
migration of that spec is needed.

---

## Task U4 — Seeded Data Migration

Existing ENTITY_LIST ViewNodes in AppConfig migrate at upgrade time.

### U4.1 Migration Logic

For each `ViewNode` with legacy `type = ENTITY_LIST`:

1. Create a GRID `DataFormElement` with:
   - `entityProviderRef` copied from the ViewNode.
   - `tableColumns` copied (via their AppConfigObjectIds, not cloned).
   - `addAction.targetDataFormRef` = the ViewNode's old `dataFormRef`.
2. Create a transient DataForm wrapping the GRID (or store the GRID
   directly on the ViewNode, depending on U1.3 resolution).
3. Update the ViewNode type to `GRID_HOST`.
4. Detach `tableColumns`, `entityProviderRef`, `dataFormRef` from the
   ViewNode.

### U4.2 Backward Compatibility During Rollout

Until all consumers are migrated, the backend can serve both shapes:
- Legacy `viewData` continues to work on unmigrated ViewNodes.
- New `gridData` works on migrated ViewNodes.
- A feature flag or config migration step handles the cutover.

After the cutover the legacy path is deleted.

---

## Task U5 — Frontend Model Collapse

- Delete `_ColDef` in `app_view.dart`.
- Use `GridTableColumn` (from `models/data_form_element.dart`) as the
  single Dart column model. Rename to `TableColumn` to match the backend's
  post-unification naming.
- Remove any frontend branching on ViewNodeType for rendering.

---

## Impact on Column Filters (`columnFilters.md`)

The column filter spec is deliberately surface-agnostic:

- **CF3 (metadata query)** accepts either `viewNodeCode` or
  `dataFormCode + elementCode`. Post-unification the first form maps
  automatically to the latter via the transient DataForm.
- **CF4 (protocol extension)** applies `userFilter` / `userSort` to both
  queries independently. Post-unification only one query remains; no spec
  change needed.
- **CF7 (advanced editor)** uses the EditorStack, which is already
  GRID-native. Post-unification nothing changes.

As long as column-filter code paths never branch on "is this ENTITY_LIST
vs. GRID", unification is safe to land whenever convenient.

---

## Open Questions

1. **Where does the GRID live on a `GRID_HOST` ViewNode?** Inline element
   on the ViewNode, a transient wrapper DataForm materialized at runtime,
   or a persisted wrapper DataForm? Trade-offs: inline is simplest but
   breaks the "all tables are GRID-in-DataForm" invariant the frontend
   renderer relies on; transient wrapper is clean but complicates
   lifecycle; persisted wrapper bloats AppConfig.
2. **AppConfigType multi-parent support.** If kept single-parent (U1.1
   option B), the wrapping strategy in Q1 must avoid requiring
   `TableColumn` under `ViewNode`.
3. **Per-ViewNode toolbar actions.** ENTITY_LIST nodes today have no
   toolbar actions beyond the implicit Add (driven by `dataFormRef`).
   GRID elements have explicit `addAction` configuration. After
   unification, ViewNode-level toolbar customization is inherited from
   GRID.
4. **Sort-field ownership.** Currently `sortFields` live on
   `EntityProvider`. After unification, a GRID under a ViewNode vs. a
   GRID inside an editor may want different default sorts — is
   `EntityProvider` still the right level, or does the GRID override?
5. **Breadcrumb labeling.** The EditorStack root label currently derives
   from `ViewNode.label`; after unification the ViewNode still has a
   label, but the rendering is a DataForm. Ensure the stack path tree
   (G5.3) still resolves correctly.

---

## Decision Log Entries to Produce When Scheduling

Before starting the refactor, record answers to:

- Schema approach for multi-parent `TableColumn` — option A or B (U1.1).
- Wrapper storage strategy — inline vs. transient vs. persisted (Q1).
- Migration cutover — big-bang vs. feature-flagged rollout (U4.2).
- Deprecation window for `viewData` GraphQL query (U3.1).

---

## Cross-References

- `viewIntegration.md` — ENTITY_LIST ViewNode type, TableColumn model,
  view data endpoint. Everything here gets simplified by U1–U3.
- `gridElement.md` — GRID DataFormElement, EditorStack, AddAction,
  ContextBinding, shared ColumnRenderer (G3), parameterized tree builder
  (G4). The target model is essentially this spec applied universally.
- `columnFilters.md` — must remain surface-agnostic (see above).
- `appConfig.md` — AppConfigType multi-parent constraint (U1.1).
- `components.md` — `trina_grid` adoption shipped (C1, 2026-04-28).
  Unification simplifies things further: today's two adapter call
  sites (`app_view.dart` ENTITY_LIST and `form_renderer_view.dart`
  GRID) collapse to one, with one host integration point for the
  `TrinaGrid` adapter and the C1.3 carve-outs.
