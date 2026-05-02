# Column Filters Specification

## Overview

This specification introduces **user-driven column filtering** on table surfaces
— ENTITY_LIST ViewNodes and GRID DataFormElements. Until now, filtering is
admin-config-time only: an `EntityProvider` carries a static `FilterNode` tree
and/or a `filterInjectableRef`. End users cannot narrow results at runtime.
Column filters close that gap without weakening the existing config mechanism:
user-supplied filters are ANDed on top of configured filters, never replacing
them.

The feature is delivered in three phases:

| Phase | Scope |
|---|---|
| **v1** | Per-column inline filter (R1), per-column sort toggle (R2), column metadata service, protocol extension, backend merge, User entity seed |
| **v2** | Advanced filter editor (R3a): nested AND/OR, named session-only filters, LITERAL + PARENT_ENTITY bindings |
| **v3** | SavedFilter persistence (R3b): per-user saved filters, dropdown selector, CURRENT_USER binding, TODAY/NOW bindings on demand |

**Motivating example (v1):** On the "cameras" ViewNode, a user wants only Fuji
cameras released after 2020. They type `Fuji` in the Producer column's filter
field and set the Release column's date-range `from` to `2020-01`. The table
updates immediately. No config change was required; the pre-configured filter
on the `chinonCameras` node (if activated) would have been ANDed on top.

---

## Implementation Status

**v1 (CF1–CF6) is shipped.** The motivating example — *"Fuji cameras
released after 2020"* — works end-to-end on both ENTITY_LIST and GRID
surfaces. v2 (advanced filter editor) and v3 (saved filters per user)
remain pending.

| Component | Phase | Status |
|---|---|---|
| Per-column inline filter widget (CF1) | v1 | Done |
| Per-column sort glyph (CF2) | v1 | Done |
| `columnFilterMetadata` GraphQL query (CF3) | v1 | Done |
| Picker base + projection (CF3.4 / CF3.4.1) | v1 | Done |
| Picker candidate restriction by distinct row values (CF3.4.3) | v1 | Done |
| `EntityRenderer.searchFields` + `.sortFields` (CF3.5) | v1 | Done |
| `viewData` / GRID data query — `userFilter` + `userSort` args (CF4) | v1 | Done |
| Filter + sort merge in backend services (CF5) | v1 | Done |
| `User` JPA entity + `DEFAULT_USER` seed (CF6) | v1 | Done |
| Advanced filter editor — EditorStack frame (CF7) | v2 | Pending |
| `SavedFilter` entity + per-user persistence (CF8) | v3 | Pending |

### v1 implementation notes

The implementation matches the spec with a few intentional simplifications,
each documented for the v2 / future passes:

- **Picker concurrency (CF1.9)** is realised via Flutter focus model
  (single focused field at a time naturally dismisses the previously
  open picker), not via an explicit cross-widget single-open notifier.
  Behaviour matches the spec; the explicit manager would be a refactor
  if a more aggressive guarantee is needed (e.g., simultaneous overlay
  in a non-focus context).
- **Wildcard escaping in user input is not done.** Typing a literal `%`
  or `_` in a STRING filter or ENTITY_REF picker term is interpreted as
  a SQL wildcard. Acceptable v1 limitation — fixing requires either
  frontend escaping or a backend `ESCAPE` clause; deferred.
- **Sticky header.** ✓ Resolved by the C1 migration to `TrinaGrid`,
  which provides native sticky header (column-titles row stays
  pinned during vertical row scroll). Was acknowledged as a v1
  limitation while CF1 sat on Material `DataTable`.
- **GRID filter / sort state** lives in the `_GridFieldState` widget
  state, not on `EditorFrame`. A child-editor push followed by pop
  resets filter/sort on the parent's GRID. CF1.3 EditorStack
  preservation is therefore not realised in v1; it is in scope for the
  same future pass that migrates filter state to `EditorFrame`
  alongside `formState`.
- **Admin-editor autoproposals (CF3.5.3)** for `searchFields` /
  `sortFields` config entries are deferred. The new types are
  reachable via the existing add-node mutation, so admins can configure
  them by typing attribute paths manually. Wiring `DataBindingService`
  proposals into the editor for these specific types is a UX polish
  task, not on the v1 critical path.
- **Admin-editor soft warnings (CF3.5.5)** — empty-`searchFields` on
  a column-filter renderer, and `searchFields` paths not in the
  template — are deferred to the same admin-editor pass as
  CF3.5.3 autoproposals. Until then, configuration mistakes surface
  only at runtime (no typeahead matches, or unexplained matches).
- **Last-wins fetch dedup** is implemented on both surfaces (sequence
  number per fetch); discarded responses from earlier in-flight queries
  are silently dropped per CF1.4.

### v1 defects (resolved)

Both items below were tightened in the spec and the corresponding
implementation work has shipped. Kept here for historical context.

- **CF1.1 — Filter input width.** ✓ Resolved by the C1 table-component
  refactor (`components.md` C1) — adoption of `trina_grid`.
  `DataColumn2`-equivalent flex widths (`ColumnSize.S/M/L` →
  `TrinaColumn.width` + `autoSizeMode: scale`) distribute the table
  width across data columns; the filter input lives in
  `TrinaColumn.titleRenderer` and inherits the column's resolved
  width via `crossAxisAlignment: stretch`. The interim `IntrinsicWidth`
  crutch on the prior `DataTable` host was removed during the
  migration. CF1 filter widgets themselves are reused verbatim;
  `trina_grid`'s built-in filter UI is bypassed per `components.md`
  C1.3.
- **CF1.10 — Picker keyboard navigation.** ✓ Implemented prior to
  the C1 migration and survived intact through it. The entity-ref
  picker (`EntityRefFilterInput`) wraps its `TextField` in
  `CallbackShortcuts` binding `Arrow Up`/`Down`/`Home`/`End`/`Enter`/
  `Escape`; the candidate list scrolls the highlighted item into
  view; `Tab` is left to default focus traversal so the existing
  focus-loss listener closes the overlay. Enum filter delegates to
  Flutter's `DropdownButton` which has built-in keyboard nav.

---

## Status Quo

### What exists today

- **Config-time filters.** `EntityProvider.filter` (static `FilterNode` tree)
  and `EntityProvider.filterInjectableRef` (runtime Janino expression) are
  applied in `FilterExecutor.executePagedQuery`. Both are admin-only, defined
  in AppConfig.
- **FilterNode / FilterOperator model.** Supports `COMPARISON`, `AND_GROUP`,
  `OR_GROUP`; operators `EQUALS, NOT_EQUALS, GT, GTE, LT, LTE, IS_NULL, IS_NOT_NULL, IN, LIKE`;
  dot-path fields (`producer.name`).
- **TableColumn.** `{ key, header, entityRendererRef }`. Shared by ENTITY_LIST
  ViewNodes (field `tableColumns` on `ViewNode`) and GRID elements (field
  `tableColumns` on `DataFormElement`, seeded via `GridTableColumn` type).
- **Sorting.** `EntityProvider.sortFields` applies server-side ORDER BY. No
  user-facing sort control yet.
- **Pagination.** Implemented on ENTITY_LIST. Was implemented on
  embedded GRID too, but is being removed by `gridElement.md` G1.6.8
  (embedded GRIDs render all rows of the effective filter — no
  pagination, layout hugs content).

### Current gaps

1. **No runtime filtering.** End users cannot add clauses to a query.
2. **No runtime sorting.** Column headers are not clickable.
3. **No column type metadata in the protocol.** The frontend has no way to
   know a column is a String vs. a Date vs. a `@ManyToOne` reference.
4. **No User entity.** Required scaffolding for future per-user SavedFilter
   persistence.

### Dependencies on other specifications

- `gridElement.md` — GRID DataFormElement type, EditorStack (G5), AddAction,
  ContextBinding. CF7 reuses the EditorStack for the advanced filter editor.
- `viewIntegration.md` — ENTITY_LIST ViewNode, TableColumn, view data query.
- `components.md` — custom table widget (Pending, approach undecided).
  **Sequencing decision:** CF1 and CF2 ship on the current Flutter
  `DataTable` first. The table-component refactor (including the
  third-party vs. proprietary decision) is a separate pending track and
  does not gate filtering / sorting. CF1 / CF2 widgets are implemented so
  their per-column filter inputs and sort glyphs can be re-hosted on
  whichever table widget eventually replaces `DataTable` — see the
  *Known DataTable limitations (interim)* note in CF1.1.
- `entityTableUnification.md` — future refactor unifying ENTITY_LIST and
  GRID. The column filter design must treat both surfaces identically so
  unification does not invalidate the implementation.

---

## Task CF1 — Per-Column Inline Filter

**Goal:** Render a filter input under each column header. The input widget is
chosen from the column's inferred type. Changes apply immediately (debounced
for text) and AND with configured filters.

### CF1.1 Widget Structure

The column header becomes two rows per column:

```
┌───────────────────────┬─────────────┬─────────────┐
│ Name           ↕     │ Producer  ↕ │ Release   ↕ │   ← label row (label + sort glyph)
│ [ Fuji........ ]     │ [ ▼ any   ] │ [====] [==] │   ← filter input row
├───────────────────────┼─────────────┼─────────────┤
│ X-T5                  │ Fuji        │ 2022-05     │
│ ...                   │ ...         │ ...         │
```

- **No icon button.** The input widget itself is the affordance.
- **No popover.** The input lives inline in the header.
- **Per-column alignment.** Each column's filter is aligned with its column
  width; there is no separate filter row spanning the whole table.
- **Full-width input.** The filter input stretches to fill the column's
  available content width (minus standard cell padding). Range pairs
  (number / date `from` | `to`) share the column width equally with a
  small gap between them. The picker / enum dropdown's *trigger* widget
  also fills the column width; its floating list (CF1.9) is sized
  independently. A noticeably-thin input in a wide column is a defect,
  not a width-distribution artifact.
- The filter row belongs to the header region. Sticky-header behavior
  is provided natively by `TrinaGrid` (`components.md` C1.3).

**Historical: Material `DataTable` interim host.** During CF1's first
release the filter widgets sat on Flutter's Material `DataTable`,
which sized columns to `IntrinsicColumnWidth` with no flex
distribution, had no native two-row header cell, and no native
sticky header. The CF1 widgets used an `IntrinsicWidth` + `stretch`
crutch in `sortableDataColumn` to keep the filter input filling the
column-intrinsic width. Those constraints are gone — the C1 refactor
(`components.md` C1) shipped 2026-04-28 and replaced the host with
`TrinaGrid`. Flex distribution, native sticky header, and a clean
two-row header (label + filter input) all come from `trina_grid`
without behaviour or state-model changes to CF1.

### CF1.2 Filter Inputs — Smart Inputs by Type

No operator chooser in the header. The **shape of the input implies the
operator**.

| Column type | Inline UI | Implied operator(s) |
|---|---|---|
| String | Single text field | `LIKE %v%` (case-insensitive CONTAINS — see CF5.5). Empty → no filter. |
| Integer / Long / Decimal | Two numeric inputs side-by-side: `from` \| `to`, both optional | Both → range (`≥ from AND ≤ to`). `from` only → `≥`. `to` only → `≤`. Both empty → no filter. |
| Date / YearMonth / DateTime | Two pickers side-by-side: `from` \| `to`, both optional | Same rule as number range. The picker widget provides Today/Now shortcuts as UI sugar (the picker inserts the literal value; v1 has no TODAY/NOW bindings). |
| Boolean | Tri-state toggle: `true` / `false` / `any` | `EQUALS true`, `EQUALS false`, or no filter. |
| `@ManyToOne` reference | **Single-select** entity picker with typeahead (reuses the ENTITY_SELECT infrastructure from `filteredEntitySelect.md` — typeahead, server-side paging). Empty → no filter. Picker displays candidates via the column's `entityRendererRef`; the picker's base query and list-filter projection are defined in CF3.4. | `EQUALS` |
| Enum | **Single-select** dropdown | `EQUALS`. Values from column's enum class via JPA metamodel. |

Rationale: the range-input shape covers `=, ≥, ≤, BETWEEN` for scalars without
a separate chooser. Single-select covers the common "narrow to exactly one
value" case for references and enums. More advanced operators
(`≠, IS_NULL, NOT_IN, STARTS_WITH`) and multi-value `IN` / OR composition
move to CF7's advanced editor.

**Null semantics in range operators.** Range comparisons (`≥`, `≤`, between)
exclude rows where the column value is `NULL`, per SQL three-valued logic.
Users who need to explicitly match `NULL` (or `NOT NULL`) must use the
advanced editor (CF7), which exposes `IS_NULL` / `IS_NOT_NULL` directly.
Inline filters do not surface null-matching.

### CF1.3 Filter State Lifecycle

- Filter state is **session-transient**, scoped per table instance (per
  ViewNode or per GRID DataFormElement within its parent DataForm).
- Navigating to a different ViewNode discards ENTITY_LIST filter state for
  the previous node. Opening a different DataForm discards all GRID filter
  states within it.
- Re-opening the same ViewNode starts with a fresh filter state. No
  persistence; no session storage in v1.
- Filter state does **not** participate in the unsaved-changes dialog (G5.7).
  It is read-only state; there is nothing to discard.
- **EditorStack preservation.** For a GRID inside an editor frame, filter
  state is preserved across child-frame push / pop alongside form state,
  per `gridElement.md` G5.2. When the parent frame becomes active again,
  the GRID's filter inputs retain the values the user set before the push.
  Row visibility after pop reflects the current filter predicate — a row
  edited in the child frame may legitimately fall out of view if the edit
  moved it past the filter; clearing the filter restores it in its new
  position.

### CF1.4 Immediate Apply & Debouncing

- String inputs: refetch debounced 300 ms after the last keystroke.
- Number / Date pickers (`from` / `to`): refetch debounced 300 ms after the
  last change to either field, applying the combined state.
- Boolean tri-state, entity picker, enum chips: refetch **immediately** on
  change (no debounce — each click is a deliberate commit).
- Concurrent debounced fetches: only the most recent request's response is
  applied; earlier in-flight requests' responses are discarded.

### CF1.5 Merge With Configured Filters

The effective WHERE clause is the AND-composition of:

1. `EntityProvider.filter` (static, admin-configured).
2. Result of `EntityProvider.filterInjectableRef` (runtime, Janino-produced).
3. `userNamedFilter` (v2 only — the advanced filter, if one is active).
4. `userColumnFilter` (all currently set per-column inputs, composed as
   `AND_GROUP` of `COMPARISON` nodes).

Order is irrelevant for correctness; AND is commutative. None of the
configured filters are ever dropped — user filters are purely additive.

#### CF1.5.1 Client-Side Filtering of Pending Rows (Embedded GRID Only)

On embedded GRID surfaces (`gridElement.md`), the **pending rows** held
by the stacked editor (per `gridElement.md` G7.6 — rows added in a
child editor frame, not yet persisted) are **subject to the same
column-filter predicates as committed rows**. Because the backend has
no knowledge of pending state, the predicate is evaluated **client-side**
on the frontend, mirroring server semantics per filter type:

| Filter type | Client-side predicate (`values[key]` against the filter input) |
|---|---|
| STRING | `LOWER(value).contains(LOWER(filterText))` — case-insensitive `LIKE %v%` |
| NUMBER | parse value, compare against `from` / `to` (inclusive) |
| DATE / YEAR_MONTH / DATETIME | parse value, compare against `from` / `to` (inclusive) |
| BOOLEAN | `value == filterValue` (tristate `true` / `false` / `any`) |
| ENUM | `value == filterValue` |
| ENTITY_REF | `value (id) == filterValue (id)` |

The match operates on `pending.values[key]` — the raw column value as
captured in the child editor — to match the server's behaviour
(server `LIKE` runs on the raw column, not on a rendered display
string). For STRING columns this is normally identical to the
display string anyway.

**Why client-side?** Pending rows aren't in the database. Sending them
to the server just to be filtered would require a round-trip and a
representation that doesn't exist in the persistence layer. Local
evaluation matches the user's mental model ("the filter applies to
everything I see in this table") and ships with the GRID's existing
state machine — no protocol changes.

**Why not just exclude pending rows from filtering?** The opposite
default (pending rows render unfiltered while committed rows are
filtered) creates the surprising "I typed in a filter but those rows
ignored me" behaviour. The "user forgets about filtered-out pending
rows on save" risk is genuine but applies equally to filtered-out
committed rows; both are signalled by the `(N of M)` row-count badge
(`gridElement.md` G1.6.9).

ENTITY_REF picker typeahead remains unchanged — it uses
`EntityRenderer.searchFields` (CF3.5.1) for OR-composed multi-field
matching to *help the user find* the entity to select. Once selected,
the column-filter predicate is the single ID-equality match described
in the table above. The two mechanisms (typeahead in the picker,
predicate in the filter) operate at different stages; only the latter
applies to pending-row filtering.

### CF1.6 Clear-All Toolbar Action

The grid toolbar (next to the existing Add / Reload buttons) gains a **Clear
Filters** action. It is **visible whenever the GRID has at least one
filterable column** — independent of edit / create-new mode, because filters
apply to pending rows too via the client-side path (CF1.5.1). It is
**enabled** only when at least one column filter is set.
Clicking it:
- Resets every column's filter input to its empty state.
- In **edit mode**: triggers a single backend refetch (the userFilter has
  changed).
- In **create-new mode** (embedded GRID, parent unsaved): triggers a
  re-render only — no backend call, since there's no parent entity to
  query against and the only rows on screen are pending. The freshly
  unfiltered pending row set is computed locally per CF1.5.1.
- Does **not** clear the active user sort (CF2).
- Does **not** clear the v2 named filter — named filters have their own
  dismiss control in the toolbar (and in CF8 their own Delete affordance on
  saved filters).

The two concerns are independent: **Clear** operates on in-progress inline
filter editing; **Delete** (CF8) operates on saved named filters.

The same edit-vs-create-new-mode rule applies to **any column filter or
sort change** that would normally trigger a refetch: in edit mode the
backend is re-queried; in create-new mode the GRID just re-renders the
client-side-filtered pending rows.

### CF1.7 Pagination Reset

**Applies to ENTITY_LIST surfaces only.** On ENTITY_LIST ViewNodes, any filter
change resets the current page to 0; this applies to each column's filter
independently and to the clear-all action. Sort changes (CF2) also reset
to page 0.

**Embedded GRID surfaces have no pagination** (per `gridElement.md` G1.6.8 —
embedded GRIDs render all rows of the effective filter in one shot). There
is no "page 0" to reset to; a filter change re-issues the fetch, and the
GRID's height tracks the new row count directly.

### CF1.8 Loading / Empty / Error States

Filter changes reuse the existing loading, empty, and error states of the
underlying table. No new state is introduced.

### CF1.9 Picker Concurrency

At most **one** column filter picker (entity-ref or enum dropdown) is open
across the table at any moment. Opening a second picker closes the first
without applying any pending change to it. Simple text / range inputs are
not "pickers" in this sense — they're always inline and editable
independently of each other.

The picker is **non-modal** — a floating dropdown attached to its column
header input. It dismisses on click-outside, on `Escape`, on opening
another column's picker, and on hard reload triggers (parent frame pop,
navigation away, GRID `reloadOnChange`); in-progress typeahead text is
discarded but the previously applied filter value (if any) is preserved.

Rationale: stacked open dropdowns on a narrow header row are visually
noisy and hard to hit; constraining to one at a time matches how native
select widgets behave. Modal would be heavyweight for a column-header
dropdown and would block scrolling the table while the picker is open.

### CF1.10 Picker Keyboard Interaction

The entity-ref picker and the enum dropdown are fully keyboard-operable.
Once the picker is open (entity-ref: typeahead field focused, list shown
with at least one candidate; enum: dropdown opened from its trigger):

| Key | Behavior |
|---|---|
| `Arrow Down` | Move highlight to the next item. If no item is highlighted yet, highlight the first. On a closed enum dropdown, opens the list and highlights the current value (or the first item). |
| `Arrow Up` | Move highlight to the previous item. Wraps from first to last (or stops at first — implementation choice, but consistent with native dropdowns). |
| `Home` / `End` | Jump to the first / last item in the list (when the list has focus). |
| `Enter` | Select the highlighted item, close the picker, apply the filter. If no item is highlighted but the list has exactly one match, select it; otherwise no-op. |
| `Escape` | Close the picker without selecting. The previously applied filter value (if any) is preserved; the typeahead text in the input is reset to the applied value's display label, or cleared if there is no applied value. |
| `Tab` | Close the picker without selecting; move focus to the next focusable field in the header (typically the next column's filter). `Shift+Tab` moves to the previous focusable field. |

**Highlight state.** The keyboard-highlighted item is visually distinct
from the mouse-hovered item. When the user moves the mouse over the list
after navigating with the keyboard, the highlight follows the mouse;
when the user presses an arrow key after hovering, the highlight returns
to keyboard control. Only one item is "highlighted" at a time, regardless
of input source.

**Equivalence.** Any item reachable via mouse click MUST be reachable via
`Arrow` keys + `Enter`. No item is mouse-only.

**Typeahead vs. navigation (entity-ref picker).** Typing characters in the
typeahead field updates the candidate list (CF1.4 debounced). `Arrow Down`
shifts focus from the text field into the list and highlights the first
result; pressing a printable character returns focus to the text field
and resumes typing.

---

## Task CF2 — Per-Column Sort Toggle

**Goal:** Clicking a column header cycles the sort on that column.

### CF2.1 Sort Glyph

A small up/down glyph is shown next to the column label in the label row
(see CF1.1). States:

| State | Glyph | Meaning |
|---|---|---|
| Inactive | `↕` (muted) | This column is not sorting; click to sort ascending. |
| Ascending | `↑` | Sort asc by this column. Click to flip to descending. |
| Descending | `↓` | Sort desc by this column. Click to clear. |

### CF2.2 Cycle

Clicking the label row (anywhere in the label row) cycles:
`none → asc → desc → none`.

Activating sort on one column **deactivates** sort on any other column. Only
one column at a time has an active user sort in v1. Multi-column sort is in
CF7's advanced editor.

### CF2.3 Interaction With `EntityProvider.sortFields`

- When **no** user sort is active, the configured `sortFields` apply (current
  behavior).
- When a user sort is active on column `X`, the user sort **fully replaces**
  the configured `sortFields`. The single user-chosen column is the ORDER BY.
- Clearing the user sort restores the configured `sortFields`.

Rationale: user intent is explicit and should not be mixed with defaults.
Mixing risks surprising behavior ("I sorted by name but rows still look
grouped by year").

### CF2.4 Sort Field Resolution

The sort field is the column's `key` (dot-path supported — the backend walks
joins the same way `FilterExecutor.walkPath` does).

### CF2.5 Pending Rows on Embedded GRIDs

Sort changes are evaluated **server-side** — the userSort travels with the
fetch and the backend's ORDER BY produces the row order. On embedded GRIDs
this means **pending rows are not reordered by the user sort today**:
pending rows live only in the frontend and bypass the backend query
entirely (see `gridElement.md` G7.6 and CF1.5.1's symmetric note for
filtering).

The asymmetry with CF1.5.1 is intentional v1: the filter case had a
crisp UX bug (typing a filter and pending rows ignoring it); the sort
case is less acute (pending rows hold their insertion order, which is a
defensible default). Closing the gap symmetrically — client-side sort on
pending rows that mirrors the column's userSort — is queued as a
follow-up; the predicate-level work would mirror CF1.5.1's per-type
predicate table, just producing a `Comparator` instead of a `bool`.

In **edit mode** (`entityId != null`), pending rows don't exist and this
note doesn't apply. In **create-new mode** the sort glyph still cycles
visually on header click, but the rendered pending row order doesn't
change until the follow-up lands.

---

## Task CF3 — `columnFilterMetadata` Query

**Goal:** Expose per-column filter-type metadata to the frontend so it can
render the correct inline input widget.

### CF3.1 Query Shape (GraphQL)

```graphql
type ColumnFilterMeta {
    columnKey: String!
    filterType: ColumnFilterType!      # enum below
    entityProviderRef: String          # populated when filterType = ENTITY_REF
    entityRendererRef: String          # populated when filterType = ENTITY_REF
    enumValues: [String!]              # populated when filterType = ENUM
}

enum ColumnFilterType {
    STRING
    NUMBER
    DATE
    YEAR_MONTH
    DATETIME
    BOOLEAN
    ENUM
    ENTITY_REF
    UNSUPPORTED      # fallback when no input widget is available
}

extend type Query {
    columnFilterMetadata(scope: ColumnFilterScopeInput!): [ColumnFilterMeta!]!
}

input ColumnFilterScopeInput {
    viewNodeCode: String               # one of (viewNodeCode) or (dataFormCode + elementCode)
    dataFormCode: String
    elementCode: String
}
```

Invoked once when the frontend first mounts a table surface; result is cached
for the session.

**v1 cache invalidation.** The metadata cache is **not** auto-invalidated
when an admin publishes an AppConfig change — the browser keeps serving the
old shape until reload. This matches current AppConfig handling in the
frontend; the unified invalidation story is tracked as an open pending
item in `specifications.md` ("AppConfig Reload Mechanism"). For v1, users
refresh the browser after config changes.

### CF3.2 Type Inference

Backend logic in a new `ColumnFilterMetadataService`:

1. Resolve the scope:
   - `viewNodeCode` → ViewNode → entity class via `EntityProvider.entityType.fqcn`.
   - `dataFormCode + elementCode` → GRID DataFormElement → same resolution.
2. For each `TableColumn`:
   - Walk the dot-path via JPA metamodel (same mechanism as
     `FilterExecutor.walkPath`), resolving to the final attribute's Java type.
   - Map Java type → `ColumnFilterType` using the following table.
3. Return the metadata list.

| Java type | `ColumnFilterType` | Notes |
|---|---|---|
| `String` | `STRING` | |
| `Integer` / `Long` / `Short` / `BigInteger` | `NUMBER` | Integer range input. |
| `Double` / `Float` / `BigDecimal` | `NUMBER` | Decimal range input. |
| `LocalDate` / `java.sql.Date` | `DATE` | |
| `YearMonth` | `YEAR_MONTH` | Uses existing `YearMonthConverter`. |
| `LocalDateTime` / `Instant` / `OffsetDateTime` | `DATETIME` | |
| `Boolean` / `boolean` | `BOOLEAN` | |
| `enum` | `ENUM` | `enumValues` populated. |
| `@Entity` class | `ENTITY_REF` | `entityProviderRef` / `entityRendererRef` resolved per CF3.4. |
| Anything else | `UNSUPPORTED` | Filter input hidden; sort still works. |

### CF3.3 `entityRendererRef` Population

For `ENTITY_REF` columns the column's existing `entityRendererRef` (used for
rendering row values) is reused verbatim for the picker's display template.
No new config is required.

### CF3.4 Picker Base Query for `ENTITY_REF` Columns

The picker for an entity-ref column returns candidates of a *different*
entity type from the table's own rows — e.g. the `cameras` table returns
`Camera`, but the picker for its `producer` column returns
`CameraProducer`. The column's target entity type is resolved at CF3.2
time via the JPA metamodel walk; no admin configuration is required.

**Base picker query.** The backend materializes a transient "all of type
X" query where X is the column's resolved target entity class:

```
SELECT candidate FROM <X> candidate
```

No `EntityProvider` config entry is needed. The query is built on demand
by the picker candidate-list endpoint at the time the picker opens or the
user types.

**Effective picker query.** The base is AND-combined with the projection
of the table's own list filter onto the column (see CF3.4.1) and shaped
by the column's `EntityRenderer` for searching and ordering (see CF3.5):

```
SELECT candidate FROM <X> candidate
WHERE <projectedListFilter>
  AND <typeaheadPredicate>      (built from EntityRenderer.searchFields)
ORDER BY <rendererSortFields>, id ASC
LIMIT <pageSize>
```

**No explicit provider override in v1.** There is no
`filterEntityProviderRef` field on `TableColumn`. If a later need arises
(picker-specific filter or sort, reuse of a specialized provider), the
field is additive and can be introduced without breaking the current
contract — the resolution would become: if override set, use it;
otherwise transient default.

**Empty `searchFields` → no filter input rendered.** When the column
resolves to `ENTITY_REF` *and* the resolved `EntityRenderer` has an
empty `searchFields` list, the column's `columnFilterMetadata` (CF3.1)
reports `filterType: UNSUPPORTED` instead of `ENTITY_REF`. The
frontend renders no filter input for that column (sort still works).
Rationale: a column with no search capability should not display an
inert input that the user might type into expecting results — better
to show no input at all and keep the header clean.

### CF3.4.2 Picker Candidate Response — Backend-Rendered Labels

The picker's candidate-list response carries each result as
`{ id, label }` rather than as the raw entity's fields:

```json
{
  "items": [
    { "id": 7,  "label": "Nikon (1917)" },
    { "id": 12, "label": "Canon (1937)" }
  ],
  "totalCount": 2,
  "page": 0,
  "pageSize": 20,
  "totalPages": 1
}
```

The label is produced **server-side** by running the column's
resolved `EntityRenderer` Mustache template against the entity. This
mirrors GRID row rendering (`gridElement.md` G1.6.3) — same Mustache
library, same template-resolution rules, same single source of truth
for display strings.

**Consequences:**
- Smaller wire payload (label string only, not all renderable fields).
- One Mustache engine in the system (server-side `mustache.java`); no
  need for a Dart Mustache implementation or template parser on the
  frontend.
- Frontend is rendering-engine-agnostic — it shows whatever string
  the backend produces.
- The selected value the frontend sends back in `userFilter.value`
  for the row query is still the entity's `id` (e.g. `"7"`); the
  label is display-only.

### CF3.4.1 List-Filter Projection

The table's own list filter (the one applied when fetching rows) is
projected onto the picker's entity type via a pure tree rewrite. This
keeps the picker from offering candidates that could never produce a
visible row — e.g., on `chinonCameras` the producer picker shows only
"Chinon", not every producer in the system.

**Algorithm.** For each `FilterNode` in the list filter tree, compute
`project(node, columnKey)`:

```
COMPARISON:
  if node.field startsWith (columnKey + "."):
      return COMPARISON(
          field    = stripPrefix(node.field, columnKey + "."),
          operator = node.operator,
          value    = node.value,
          values   = node.values)
  else:
      return NO_CONSTRAINT

AND_GROUP:
  keep projectable children; drop non-projectable ones;
  combine kept children as AND_GROUP.
  - Pure AND chains of comparisons whose paths all start with
    "{columnKey}." project exactly.
  - Mixed AND groups (some clauses on the picker entity, some not)
    project as a safe over-approximation — picker may include candidates
    that don't actually yield rows, but never excludes a valid one.
  - If no children are projectable → NO_CONSTRAINT.

OR_GROUP:
  project only if *every* child projects; return OR_GROUP(projected).
  Any non-projectable OR branch would permit any picker value, so drop
  the whole group → NO_CONSTRAINT.

filterInjectableRef (Janino):
  In v1 the projection skips injectables — an entity-ref column on a
  table whose list filter is Janino-based gets an unprojected picker
  (base "all of type X"). The future Tabular ENTITY_SELECT task
  (see `specifications.md`) extends this to execute the injectable
  first and feed its materialized result into this same algorithm.
```

**Correctness characterization.**
- Precise for pure AND chains of comparisons on the picker entity.
- Safe over-approximation for mixed AND groups — never excludes a valid
  candidate.
- Falls back to the unrestricted picker for non-projectable OR groups.
- Skips Janino in v1.

**Timing.** The projection runs at picker-open / typeahead-query time —
not at `columnFilterMetadata` cache time. The metadata cache holds only
static descriptors (filter type, enum values, renderer ref); the
picker's *query* is dynamic per call.

### CF3.4.3 Picker Candidate Restriction by Distinct Row Values

> **Status: shipped 2026-04-28.** Closes the picker-precision gap exposed
> during the C1 / TrinaGrid migration smoke-test — junction-table GRIDs
> whose row filter doesn't project cleanly onto the picker's column path.
> Backend: combined inner-DISTINCT subquery + IN-clause via JPA Criteria;
> picker request shape extended with `userFilter` + `editorEntityId`;
> non-ENTITY_REF column rejected loudly. Frontend: picker dismissal on
> any other-column filter change, distinct empty-restricted-picker
> message, full picker payload sent through.

#### Surface scope

CF3.4.3 applies equally to **ENTITY_LIST ViewNodes** and **GRID
DataFormElements**. Both surfaces drive their picker via an
`EntityProvider`, producing the same `rowEntityType` + `baseFilter`
inputs the algorithm needs. The canonical example below uses a GRID
because the precision gap was first observed there during the C1
migration smoke test, but a `cameras` ENTITY_LIST with a Janino-built
filter would behave identically.

#### Canonical example

A `lensMountMappings` GRID inside the CameraProducer edit form,
currently editing **Fuji**. The example assumes three columns (the
existing two plus an **Inventor** column added as the seed
prerequisite below):

| Column key | Header | Renderer | Picker target type |
|---|---|---|---|
| `cameraLensMount` | Lens Mount | `lensMountCaption` | `CameraLensMount` |
| `cameraProducer` | Producer | `producerCaption` | `CameraProducer` |
| `cameraLensMount.producer` | Inventor | `producerCaption` | `CameraProducer` |

The GRID's `EntityProvider.filterInjectableRef = producerMountFilter`
(see `expressions.md` E2.8) materialises to
`cameraProducer.id EQUALS <Fuji.id>`. Visible rows:

| Lens Mount | Producer | Inventor |
|---|---|---|
| M42        | Fuji     | ZeissIkon |
| X-Mount    | Fuji     | Fuji      |

The user opens the **Inventor** column picker. The expected candidate
set is `{ZeissIkon, Fuji}` — the two distinct inventors visible in
the GRID. Without CF3.4.3 the picker offers every CameraProducer in
the system (7 in the seed: Fuji, ZeissIkon, Pentax, Praktica, Nikon,
Ernemann, Mamiya).

#### Why CF3.4.1 cannot help here

CF3.4.1's tree-rewrite is precise only when the list filter touches
paths that begin with `<columnKey>.`. Two reasons it doesn't apply
here:

- **Janino-built filter.** The GRID's row predicate comes from a
  `FilterInjectable` (`producerMountFilter`). CF3.4.1 explicitly
  skips Janino in v1 (see CF3.4.1's `filterInjectableRef` rule), so
  the projection returns `NO_CONSTRAINT` and the picker is
  unrestricted.
- **Cross-entity comparison.** Even if Janino were materialised
  before projection, the resulting clause is `cameraProducer.id
  EQUALS X`. Its path starts with `cameraProducer.`, which projects
  only onto the `cameraProducer` column. For the **Inventor** picker
  (column key `cameraLensMount.producer`) CF3.4.1 still returns
  `NO_CONSTRAINT`.

Without CF3.4.3 the user sees candidates that *could not produce a
visible row* in the current grid, defeating the column-filter UX.

#### Algorithm

Restrict the picker's candidate set to the **distinct values of the
picker's column path observed in the surface's full filter result —
page-independent — with the picker's own column filter excluded**.
The DISTINCT operates over every row that matches the effective
filter, not only over the rows on the currently visible page; a 30-row
mount-set spread across three pages still surfaces every distinct
inventor present in those 30 rows.

This bypasses CF3.4.1's tree-rewrite entirely; instead we ask the
database what's actually there.

##### Inputs

When the picker for column `K` opens:

| Input | Source | Example value (Inventor picker) |
|---|---|---|
| `rowEntityType` | GRID's `EntityProvider.entityType` | `CameraLensMount2CameraProducer` |
| `columnKey` | the picker column's `key` (dot-path supported) | `"cameraLensMount.producer"` |
| `columnTargetType` | resolved at CF3.2 via JPA metamodel walk of `columnKey` from `rowEntityType` | `CameraProducer` |
| `baseFilter` | `EntityProvider.filter` AND result of `EntityProvider.filterInjectableRef`, **already materialised** by the existing `mergeFilters` + `resolveFilterExpressions` pipeline (CF5.1) — Janino is executed once, not re-projected | `cameraProducer.id EQUALS <Fuji.id>` |
| `otherUserFilters` | all currently-active CF1 column filters on the GRID **except** the one for `K` | (none in canonical scenario) |
| `typeaheadTerm` | text in the picker input | (empty initially) |

##### Step 1 — Inner DISTINCT query

```
SELECT DISTINCT row.<columnKey>.id
FROM   <rowEntityType> row
WHERE  <baseFilter>
  AND  <otherUserFilters>
```

Concretely, for the Inventor picker on Fuji's GRID:

```
SELECT DISTINCT row.cameraLensMount.producer.id
FROM   CameraLensMount2CameraProducer row
WHERE  row.cameraProducer.id = <Fuji.id>
```

→ result: `{ZeissIkon.id, Fuji.id}`.

The inner query reuses the existing filter pipeline (CF5.1's
`mergeFilters` + `resolveFilterExpressions`), called with the picker's
own column filter omitted — same plumbing, different inputs.

JPA Criteria walks multi-segment dot-paths via implicit joins — same
mechanism `FilterExecutor.walkPath` already uses for filter and sort
fields. Arbitrary depth is supported in `columnKey`.

**Why exclude the picker's own filter.** Excel-autofilter convention:
if the user already has `Inventor = ZeissIkon` set and re-opens the
Inventor picker, they need to be able to *switch* to "Fuji". Leaving
the Inventor filter in the inner query collapses the candidate set to
what's already selected, making the picker useless. Including only
*other* user filters preserves the standard autofilter mental model.

**Stripping rule — exact-equals OR prefix.** A user filter on column
`K` arrives in different wire shapes depending on the column's filter
type:

- STRING / NUMBER / DATE / BOOLEAN columns — `field` is exactly `K`
  (e.g. `field: "name"`).
- ENTITY_REF columns — `field` is `${K}.id` because the comparison is
  by the related entity's id (e.g. `field: "cameraLensMount.id"`,
  `field: "cameraLensMount.producer.id"`).

Both shapes belong to the picker's own column. The strip rule
therefore drops any node whose field equals `K` **or** starts with
`${K}.`. The prefix form also covers any future AND-chained extension
filters on sub-paths of `K`.

**Always run when CF3.4.1 doesn't apply — no short-circuit.** Even
when both `baseFilter` and `otherUserFilters` are null, the inner
DISTINCT still runs (without a `WHERE`). For a junction-table GRID
this constrains candidates to the values that *actually appear* in
the row entity — junction tables only reach a subset of the picker's
target entity table. Only CF3.4.1's projection short-circuits the
DISTINCT (when applicable, it's cheaper); a null total predicate
still goes through the DISTINCT path.

##### Step 2 — Outer picker query

```
SELECT candidate FROM <columnTargetType> candidate
WHERE candidate.id IN (<inner DISTINCT query>)
  AND <typeaheadPredicate>          -- per CF3.5.1, OR-composed across EntityRenderer.searchFields
ORDER BY <rendererSortFields>, id ASC   -- per CF3.5.1
LIMIT <pageSize>
```

For the Inventor picker on Fuji's GRID with no typeahead term — and
assuming `producerCaption` is configured with
`searchFields = ["name", "foundationYear", "shutdownYear"]`:

```
SELECT candidate FROM CameraProducer candidate
WHERE candidate.id IN ({ZeissIkon.id, Fuji.id})
ORDER BY id ASC
LIMIT 20
```

→ candidates: `{ZeissIkon, Fuji}`. Each is rendered server-side via
`producerCaption` per CF3.4.2: `{ id, label: "ZeissIkon" }` and
`{ id, label: "Fuji" }`.

If the user types `Fuji`, the typeahead OR-clause matches via `name`
and narrows to one candidate. Typing `1934` matches via
`foundationYear` (cast to `"1934-…"`) and narrows to Fuji alone.
Display and search are decoupled per CF3.5.1; a user can match a
candidate by any path in `searchFields` regardless of whether the
renderer's template displays it.

##### Multi-segment dot-paths

Both `columnKey` (Step 1) and `searchFields` entries (Step 2) accept
arbitrary-depth attribute paths; they share the same
`FilterExecutor.walkPath` machinery already used for filter and sort
fields. Mid-path nullable references generate OUTER joins; null
leaves yield UNKNOWN under `LOWER`/`LIKE`, so a candidate with a null
mid-path is naturally excluded from that ORed sub-clause but can
still match via other `searchFields`.

##### Recomputation and invalidation

The algorithm runs per call (per picker-open and per typeahead
keystroke). Caching is not specified at this stage — revisit only if
profiling shows a concrete bottleneck.

While a picker is open, it is **dismissed** (closed without
selection; any in-flight typeahead query is dropped) on:

- A change to any *other* column's user filter — `otherUserFilters`
  would change, leaving stale candidates on screen. Recommended
  default is "close": users can re-open the picker and see a
  fresh, correct candidate set, matching Excel-autofilter convention.
- The user navigating to a different ViewNode, or pushing / popping
  an `EditorFrame` so the active surface or active editor entity
  changes. `baseFilter` may depend on `editorEntity.id`, which is
  no longer valid.
- The GRID's row set being reloaded by a `reloadOnChange` trigger
  (already covered by CF1.9's "hard reload triggers" list).

The frontend MUST NOT cache picker results across editor-entity
changes inside an EditorStack: the same parent GRID re-shown with a
different parent entity produces a different `baseFilter` and
therefore a different inner-DISTINCT result.

#### Wire-level walkthroughs

Two sequence diagrams ground the algorithm against the canonical
example. They reproduce step-for-step against the seeded test data
once the seed prerequisite below is in place.

##### Cold picker-open

User is editing CameraProducer "Fuji" (id = 4) and clicks the
**Inventor** column header to open its picker.

```
Frontend                Backend                   Janino                   DB
   │                       │                        │                       │
   │ POST /pickerCandidates                         │                       │
   │ ────────────────────► │                        │                       │
   │  { scope: { dataFormCode: "cameraProducer",                            │
   │             elementCode: "lensMountMappings",                          │
   │             columnKey:   "cameraLensMount.producer" },                 │
   │    editorEntityId:  4,                         │                       │
   │    userFilter:      ∅,                         │                       │
   │    pickerColumnKey: "cameraLensMount.producer",│                       │
   │    typeaheadTerm:   "" }                       │                       │
   │                       │                        │                       │
   │                       │ resolve config:        │                       │
   │                       │   provider = mountsForCurrentProducer          │
   │                       │   renderer = producerCaption                   │
   │                       │   columnTargetType = CameraProducer            │
   │                       │                        │                       │
   │                       │ build baseFilter via   │                       │
   │                       │ mergeFilters +         │                       │
   │                       │ resolveFilterExpressions                       │
   │                       │ ─────────────────────► │                       │
   │                       │                        │ getEditorEntity()     │
   │                       │                        │   → CameraProducer{4} │
   │                       │                        │ build comparison      │
   │                       │                        │   "cameraProducer.id" │
   │                       │                        │   EQUALS 4            │
   │                       │ ◄───────────────────── │                       │
   │                       │   baseFilter materialised:                     │
   │                       │   COMPARISON("cameraProducer.id", EQUALS, 4)   │
   │                       │                        │                       │
   │                       │ try CF3.4.1.project(   │                       │
   │                       │   baseFilter,          │                       │
   │                       │   "cameraLensMount.producer"):                 │
   │                       │   path "cameraProducer.id" does not start      │
   │                       │   with "cameraLensMount.producer." →           │
   │                       │   NO_CONSTRAINT → fall back to CF3.4.3         │
   │                       │                        │                       │
   │                       │ strip pickerColumnKey  │                       │
   │                       │ from userFilter →      │                       │
   │                       │ otherUserFilters = ∅   │                       │
   │                       │                        │                       │
   │                       │ combined picker query  │                       │
   │                       │ (inner DISTINCT as     │                       │
   │                       │ subquery in IN-clause) │                       │
   │                       │ ─────────────────────────────────────────────► │
   │                       │  SELECT c FROM CameraProducer c                │
   │                       │  WHERE c.id IN (                               │
   │                       │     SELECT DISTINCT m.cameraLensMount.producer.id │
   │                       │     FROM CameraLensMount2CameraProducer m      │
   │                       │     WHERE m.cameraProducer.id = 4              │
   │                       │  )                                             │
   │                       │  ORDER BY c.id ASC                             │
   │                       │  LIMIT 20                                      │
   │                       │ ◄───────────────────────────────────────────── │
   │                       │   [ ZeissIkon, Fuji ]                          │
   │                       │                        │                       │
   │                       │ render labels via      │                       │
   │                       │ producerCaption        │                       │
   │                       │ Mustache template      │                       │
   │                       │                        │                       │
   │ ◄──────────────────── │                        │                       │
   │  { items: [                                    │                       │
   │     { id: <ZeissIkon.id>, label: "ZeissIkon" },│                       │
   │     { id: <Fuji.id>,      label: "Fuji" } ],   │                       │
   │    totalCount: 2 }                             │                       │
   │                       │                        │                       │
   │ picker shows          │                        │                       │
   │ 2 candidates          │                        │                       │
```

##### Typeahead refinement

The picker is open with `{ZeissIkon, Fuji}`. The user types `1926`
(ZeissIkon's foundation year, illustrative). Debounced 300 ms per
CF1.4.

```
Frontend                Backend                                            DB
   │                       │                                                │
   │ POST /pickerCandidates  { ..., typeaheadTerm: "1926" }                 │
   │ ────────────────────► │                                                │
   │                       │                                                │
   │                       │ algorithm selection identical to cold open     │
   │                       │ → CF3.4.3 path                                 │
   │                       │                                                │
   │                       │ combined picker query, with typeahead OR-clause│
   │                       │ over producerCaption.searchFields              │
   │                       │ ─────────────────────────────────────────────► │
   │                       │  SELECT c FROM CameraProducer c                │
   │                       │  WHERE c.id IN (                               │
   │                       │     SELECT DISTINCT m.cameraLensMount.producer.id │
   │                       │     FROM CameraLensMount2CameraProducer m      │
   │                       │     WHERE m.cameraProducer.id = 4              │
   │                       │  )                                             │
   │                       │  AND ( LOWER(CAST(c.name           AS string)) LIKE '%1926%' │
   │                       │     OR LOWER(CAST(c.foundationYear AS string)) LIKE '%1926%' │
   │                       │     OR LOWER(CAST(c.shutdownYear   AS string)) LIKE '%1926%' )│
   │                       │  ORDER BY c.id ASC                             │
   │                       │  LIMIT 20                                      │
   │                       │ ◄───────────────────────────────────────────── │
   │                       │   [ ZeissIkon ]                                │
   │                       │                                                │
   │ ◄──────────────────── │                                                │
   │  picker narrows to    │                                                │
   │  one candidate        │                                                │
```

The inner DISTINCT subquery is identical to the cold open;
`baseFilter` and `otherUserFilters` haven't changed. Only the
typeahead OR-clause is added. If profiling later shows the inner
DISTINCT to be a hot path, an open-picker-scoped cache becomes a
candidate optimisation. CF3.4.3 deliberately does not specify
caching at this stage (see *Recomputation and invalidation*).

#### Worked variants

**A. Canonical — Inventor picker, no other filters:** inner →
`{ZeissIkon, Fuji}` → picker shows 2 of the 7 producers. ✓

**B. Same picker, user already selected ZeissIkon then reopens:** own
filter excluded → inner identical to A → user can switch to Fuji. ✓

**C. `cameraLensMount` picker (same GRID):** `columnTargetType =
CameraLensMount`. Inner: `SELECT DISTINCT row.cameraLensMount.id
WHERE row.cameraProducer.id = <Fuji.id>` → `{M42.id, X-Mount.id}` →
picker shows 2 of 3 mounts (K-mount excluded — Fuji never adopted
it).

**D. `cameraProducer` picker (degenerate):** inner → `{Fuji.id}` →
picker shows only Fuji. **Accepted as correct, not a defect** — the
GRID's filter pins `cameraProducer = Fuji`, so no other producer can
ever appear as a row. The collapse is the truthful answer. We
deliberately do *not* hide the picker for singleton-restriction
columns: detecting that case at metadata time would require running
the inner DISTINCT eagerly, contradicting CF3.4.3's "picker-open
time only" rule, and the simpler "always show the picker" behavior
is predictable for users and reviewers.

**E. Empty visible row set** (e.g. editing a brand-new producer with
no mappings yet): inner DISTINCT returns no rows → picker shows the
standard "no results" state per CF1.8.

#### Coexistence with CF3.4.1

Both algorithms remain. The runtime chooses per picker-open:

- If CF3.4.1's projection produces a non-`NO_CONSTRAINT` result (i.e.
  the list filter is purely on `<columnKey>.…` paths), use it. It's
  precise and avoids the DISTINCT subquery — the `chinonCameras`
  example continues to work as documented in CF3.4.1.
- Otherwise (cross-entity, mixed groups that drop most clauses, or
  Janino-only filters), fall back to CF3.4.3's DISTINCT-from-rows
  query.

The "Tabular ENTITY_SELECT" extension previously sketched in
`specifications.md` (executing the FilterInjectable first, then
running CF3.4.1's projection on the materialised tree) is **subsumed**
by CF3.4.3 — the DISTINCT approach already handles Janino-built
filters via the row-set query, and produces a tighter result on
junction tables than CF3.4.1's projection ever could on those tables.

#### Frontend UX

- **Empty restricted picker — distinct messaging.** When the inner
  DISTINCT yields no candidates (variant E, or any `otherUserFilters`
  combination that produces an empty row set), the picker MUST show
  a message specific to the situation, not the generic typeahead
  "no results". Suggested wording: *"No candidates match the current
  filters."* This signals that *clearing other column filters* is
  the user's path forward, distinguishing the case from "your
  typeahead term doesn't match anything in the candidate set".
- **Loading state.** The two-step query (inner DISTINCT + outer
  SELECT + Mustache rendering) can land in the 100–300 ms range
  on a cold cache. The picker MUST show a small spinner while the
  request is in flight; the candidate list area should not flicker
  through a momentary empty state, which would read as "no
  candidates" and confuse the user. Same pattern as the existing
  ENTITY_SELECT picker.

#### Edge cases

- **Non-`ENTITY_REF` columns.** CF3.4.3 applies only to ENTITY_REF
  picker columns. STRING / NUMBER / DATE / BOOLEAN columns have no
  picker — no DISTINCT pre-query. A `pickerCandidates` request whose
  `pickerColumnKey` resolves (per CF3.2) to a non-`ENTITY_REF` filter
  type is **rejected with HTTP 400**: pickers exist only for
  ENTITY_REF columns per CF1.2, and the frontend reads each column's
  `filterType` from `columnFilterMetadata` to choose the right
  widget. A 400 here signals a client-side defect (frontend bug,
  stale metadata cache, or hand-crafted client) — not an admin
  config error, which surfaces earlier as `filterType: UNSUPPORTED`
  with no filter widget rendered. The general principle: things that
  cannot be sensibly handled MUST fail visibly, not silently
  degrade.
- **Cost.** One extra `SELECT DISTINCT` per picker-open / typeahead
  query. With a small row set (typical for embedded GRIDs) this is
  cheap. For large ENTITY_LIST surfaces the cost is bounded by the
  row count of the full filter result; the DISTINCT operates on the
  same row-set the row fetch operates on (page-independent — see
  *Algorithm* above).
- **Nullable mid-path in `searchFields`.** Covered above under
  *Multi-segment dot-paths*.

#### Known limitations

- **ENUM columns are not restricted by visible rows.** ENUM picker
  semantics in v1 are **static** — every declared enum constant is
  always offered, regardless of which values currently appear in the
  surface's rows. This is asymmetric with ENTITY_REF columns (which
  CF3.4.3 *does* tighten) and admins WILL ask why their `Inventor`
  picker collapses to two candidates while a sibling enum picker keeps
  offering every constant. The asymmetry is intentional in v1: ENUM
  values are a small fixed set, the cost of "show all constants" is
  trivial, and the static behavior is predictable. Restricting ENUM
  values via the same DISTINCT-from-rows approach is a defensible
  future polish, not in scope here.

  *Canonical use case for the future work* —
  `Camera.photoEquipmentMarketSegment` (`PhotoEquipmentMarketSegment`,
  see `domainEntities.md` Task D3). Filtering the `cameras`
  ENTITY_LIST by producer should produce a visibly-restricted segment
  dropdown (Polaroid → mostly `ENTRY_LEVEL`; Hasselblad → mostly
  `PROFESSIONAL`; Fuji spans 3–4 tiers). Today the dropdown ignores
  the row filter and offers all four constants regardless.

#### Future considerations

- **Picker-query observability.** When a picker shows surprisingly
  few or surprisingly many candidates, admins currently cannot tell
  whether CF3.4.1 projected cleanly, CF3.4.3 fell back to DISTINCT,
  or the renderer's `searchFields` was empty and the typeahead
  matched nothing. A debug log line at picker-query time naming the
  algorithm chosen, the materialised inner SQL, and the resulting
  candidate-count would pay for itself the first time something
  looks off. Tracked here as a future improvement; will be folded
  into the broader logging story when that lands.

#### Timing

Same as CF3.4.1 — runs at picker-open / typeahead query time, not at
`columnFilterMetadata` cache time.

#### Seed prerequisite for the worked example

The canonical example assumes the `lensMountMappings` GRID has a
third **Inventor** column with key `cameraLensMount.producer`
(renderer `producerCaption`), and that `producerCaption` carries
`searchFields = ["name", "foundationYear", "shutdownYear"]`. Both are
seed-only additions; no domain or AppConfigType change is required
(the `EntityRendererSearchField` AppConfigType is already seeded per
CF3.5.4, and the runtime already consumes it via
`PickerCandidatesService.buildTypeaheadPredicate`). The CF3.4.3
implementation pass extends the existing seed accordingly.

### CF3.5 EntityRenderer — Search Fields and Sort Fields

To drive the typeahead matching and the default ordering of picker
candidates, `EntityRenderer` gains two new optional fields:

```
EntityRenderer "producerCaption"
├── template:     "{{name}} ({{foundationYear}})"     (existing)
├── searchFields:                                     (NEW)
│   ├── "name"
│   └── "code"
└── sortFields:                                       (NEW)
    └── SortField { field: "name", direction: ASC }
```

#### CF3.5.1 Behavior

- **Typeahead matching — multi-type, single text widget.** The
  typeahead input is always a text field in v1. The user's typed
  term is matched against **every** entry in `searchFields`,
  OR-composed. `searchFields` accepts attribute paths of **any**
  JPA-resolvable type, not only String. This lets a renderer like
  `"{{name}} ({{foundationYear}})"` be searched by `nik` *or* by
  `1962`. Search and display are conceptually distinct — they may
  diverge in fine detail (Mustache formatting vs. JPA CAST output for
  some types, e.g. Boolean on Postgres) — but **in practice align**:
  typing what you see in the displayed label generally finds the
  match. The promise is approximate, not contractual.
  - **String** fields: case-insensitive `LIKE %term%`
    (`LOWER(field) LIKE LOWER('%term%')` per CF5.5).
  - **Non-String** fields: same predicate after JPA-side CAST to
    string. The cast uses the JVM's default string representation —
    `YearMonth.toString()` → `"2017-03"`, `Long.toString()` → `"42"`,
    `LocalDate` → ISO `yyyy-MM-dd`, `Enum` → constant name. This
    matches what Mustache renders by default, so display and
    searchability stay aligned. Wildcards in the user's term are
    escaped (CF5.5 wildcard escaping applies uniformly).
  - **Search and display are decoupled.** Admins MAY include
    attributes in `searchFields` that the template does not display,
    and MAY display attributes that are not in `searchFields`. The
    coupling is by convention (search-what-you-see) rather than by
    enforcement.
- **Sort.** Picker candidates are ordered by `sortFields` in declaration
  order. `id ASC` is **always appended** as the CF5.1 tiebreaker, so
  admins do not need to (and SHOULD NOT) specify it explicitly.
- **Empty `searchFields`.** Typeahead opens with paged-all results, no
  term-matching. This is an explicit admin opt-out — it tells the
  system "this entity has no meaningful textual search". Acceptable
  for rare entities (e.g. mapping/junction tables).
- **Missing `sortFields`.** Defaults to `id ASC` only.

##### Cross-DB notes for non-String CAST

Most JPA types CAST to a string representation that matches
`toString()` directly across HSQL / Postgres / MySQL. Known
divergence: `Boolean` on Postgres casts to `"t"` / `"f"` rather than
`"true"` / `"false"`. If a renderer includes a Boolean in
`searchFields` (uncommon), Phase B can either accept the per-DB
divergence (document it) or wrap with a `CASE WHEN` to normalize.
Other primitive / temporal / enum casts are uniform.

##### Conflicts with `EntityProvider.sortFields`

Two `sortFields` lists now exist in the system. They never conflict
because they govern different queries:

- `EntityProvider.sortFields` orders the **list table's rows** (e.g.
  the `cameras` list).
- `EntityRenderer.sortFields` orders **picker candidates** when the
  renderer is used in a picker context.

Same name, different scope. The picker query uses *only* the
renderer's `sortFields`; the list query uses *only* the provider's.

#### CF3.5.2 Reuse Across Picker Contexts

The same `EntityRenderer` is referenced by both column filter pickers
(this spec) and `ENTITY_SELECT` form-field pickers
(`filteredEntitySelect.md`). Centralizing search/sort on the renderer
lets one config drive both contexts. **During implementation, audit
`filteredEntitySelect.md`'s current typeahead mechanism — if it uses
its own search/sort scheme, unify it onto these new fields in the
same change.** If unification turns out riskier than expected
(e.g. semantic differences), it spins out as a separate task and the
two contexts coexist temporarily.

#### CF3.5.3 Admin Editor — Autoproposals

When configuring `searchFields` or `sortFields` entries in the AppConfig
admin editor, the attribute-path input offers autoproposals via the
existing `DataBindingService` mechanism (same as `gridElement.md` G6.4.1
for `ContextBinding.target`/`source`).

Resolution:
- The renderer's entity type is determined from the renderer's
  `entityType` field.
- `DataBindingService` returns attribute dot-paths for that entity
  via JPA metadata.
- For **`searchFields`** entries: all attribute types are valid
  candidates (CF3.5.1 accepts multi-type search). The autoproposal
  shows them with their resolved Java type so admins can see what
  they're picking.
- For **`sortFields`** entries: all attributes are valid candidates;
  the editor additionally asks for the direction (`ASC` / `DESC`).

The autoproposal pattern reduces typing errors and surfaces the
admin's choices — same UX already familiar from ContextBinding
configuration.

#### CF3.5.4 AppConfigType Seeder Rows

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `EntityRendererSearchField` | `EntityRenderer` | `searchFields` | true | false | `java.lang.String` |
| `EntityRendererSortField` | `EntityRenderer` | `sortFields` | true | false | `...appconfig.SortField` |

`SortField` is the existing AppConfig type (already used by
`EntityProvider.sortFields`); reused here, not duplicated.

#### CF3.5.5 Admin Editor — Soft Warnings

When configuring an `EntityRenderer` in the AppConfig admin editor,
the editor SHOULD surface two soft warnings (non-blocking, advisory).
Both are deferred work — they ride alongside CF3.5.3's autoproposal
pass — but the rules are spec'd here so the implementing change has
a definite contract:

1. **Empty `searchFields` on a column-filter renderer.** If an
   `EntityRenderer` is referenced from at least one ENTITY_REF column
   filter context (any `TableColumn.entityRendererRef` or
   `GridTableColumn.entityRendererRef` whose resolved
   `ColumnFilterType` is `ENTITY_REF`) and its `searchFields` list is
   empty, warn: *"This renderer is used by a column-filter picker
   but has no searchFields — the picker's typeahead will match
   nothing. Add at least one searchField (typically `name`)."*
   Rationale: an empty `searchFields` is a legitimate opt-out
   (CF3.5.1) for renderers that never back a picker, but silently
   broken when one does. The warning surfaces the mismatch without
   forcing anyone — the admin can dismiss it if they have a reason.
2. **`searchField` path not displayed in template.** When the admin
   adds a path to `searchFields` that does not appear as a Mustache
   variable in `template`, warn: *"Search field `foundationYear` is
   not displayed in the template `{{name}}` — typeahead matches will
   look unexplained to users. Consider extending the template to
   include it (e.g. `{{name}} ({{foundationYear}})`), or removing
   the searchField if only `name` should be matchable."* This
   reinforces the search-what-you-see convention from CF3.5.1
   without enforcing it (the decoupling promise stands; the warning
   is purely advisory).

Both warnings are computed locally in the editor — no server round
trip needed — by comparing `searchFields` against the configured
`template` string and against the editor's known set of column-filter
references. They do **not** block save; they only annotate the
relevant fields. Sequencing: implement these soft warnings in the
same pass as CF3.5.3's autoproposal work, since both touch the same
admin-editor surface for `EntityRenderer`.

### CF3.6 `TableColumn.key` Autoproposal

> **🔜 Next implementation target.** Closes a DX-consistency gap:
> `TableColumn.key` is the same attribute-dot-path concept as several
> sibling fields that already have autoproposal, and admins currently
> have to type it by hand.

**Goal.** When configuring a `TableColumn.key` (or `GridTableColumn.key`)
in the AppConfig admin editor, offer the same attribute-path
autoproposal already provided for `ContextBinding.target` /
`ContextBinding.source` (`gridElement.md` G6.4.1) and
`EntityRenderer.searchFields` / `sortFields` (CF3.5.3).

**Why.** `TableColumn.key` stores an attribute dot-path on the table's
row entity (e.g. `"name"`, `"producer.foundationYear"`,
`"releaseYear"`). It is consumed by:

- The view / grid data fetch (server-side projection and ORDER BY).
- `columnFilterMetadata` resolution (CF3.2 walks the path via JPA
  metamodel to determine the column's Java type → ColumnFilterType).
- The frontend cell renderer (read entity value at `key`).

Typing it by hand is error-prone. Sibling fields with the same
semantic (ContextBinding paths, EntityRenderer search/sort fields)
already use `DataBindingService.bindingProposals` for completion —
this section extends the same mechanism to `TableColumn.key`.

**Resolution flow.**

1. Admin focuses the `TableColumn.key` input. The editor needs to know
   the *row entity type* the column applies to.
2. Walk the AppConfig tree from the `TableColumn` node upward to find
   the parent table:
   - If parent is a `ViewNode` of type `ENTITY_LIST`: read its
     `entityProviderRef` → resolve to `EntityProvider.entityType`.
   - If parent is a `DataFormElement` of type `GRID`: read its
     `entityProviderRef` → resolve to `EntityProvider.entityType`.
3. Call `bindingProposals(entityType, prefix)` with the input's
   current value as `prefix`. The existing GraphQL endpoint
   (`EntityQueryController.bindingProposals`) returns dot-path
   candidates with their resolved Java types.
4. Render the same proposal dropdown UI used by ContextBinding /
   EntityRenderer fields. Selecting a proposal writes the dot-path
   into the input.

**Validity rules.**

- All attribute paths the JPA metamodel exposes are valid candidates,
  regardless of type — `TableColumn.key` already accepts any type
  (the column's `ColumnFilterType` is derived from the path's
  resolved Java type, with `UNSUPPORTED` for unmappable types).
- The proposal display includes the resolved Java type as a hint
  (e.g. `producer.foundationYear : YearMonth`) — consistent with
  CF3.5.3.

**Other TableColumn fields.**

- `header` (display label) — free text, no autoproposal.
- `entityRendererRef` — refers to an `EntityRenderer` code in the
  AppConfig. A *different* kind of autoproposal (a registry lookup,
  not a path walk) is appropriate but out of scope for this section.

**Adjacent field worth flagging (not in scope here).**
`EntityProvider.sortFields[].field` is also a JPA dot-path on the
provider's entity type. It currently has no autoproposal; the same
pattern applies. Tracked separately if/when it surfaces.

---

## Task CF4 — Protocol Extension for `userFilter` + `userSort`

**Goal:** Extend the existing data queries with optional user filter and sort
arguments.

### CF4.1 `viewData` (ENTITY_LIST)

```graphql
extend type Query {
    viewData(
        viewNodeCode: String!,
        page: Int,
        size: Int,
        userFilter: FilterNodeInput,
        userSort: [SortFieldInput!]
    ): PagedResponse!
}
```

### CF4.2 GRID Data Query

The GRID data operation currently takes `{ entityId, formState }` in its
request body (per `gridElement.md` G1.5). It gains the same two optional
fields:

```json
{
    "entityId": 4,
    "formState": { ... },
    "userFilter": { ... },
    "userSort": [ { "field": "name", "direction": "ASC" } ]
}
```

### CF4.3 Input Shapes

```graphql
input FilterNodeInput {
    type: FilterNodeType!              # COMPARISON | AND_GROUP | OR_GROUP
    field: String                      # dot-path; COMPARISON only
    operator: FilterOperator           # COMPARISON only
    value: String                      # COMPARISON only (non-IN)
    values: [String!]                  # COMPARISON only (IN)
    children: [FilterNodeInput!]       # AND_GROUP / OR_GROUP
}

input SortFieldInput {
    field: String!
    direction: SortDirection!          # ASC | DESC
}
```

The shapes mirror the existing server-side `FilterNode` and `SortField`
types. No new enum values are introduced in v1.

**Implementation note — one Java class vs. two.** The default
implementation uses two Java classes (`FilterNode` for output/domain,
`FilterNodeInput` for input) glued by a small converter (~15 lines).
This is library-agnostic and explicitly omits `expressionRef` from the
input shape. Before committing to two classes, **investigate
`JavaSchemaGenerator`** — if it supports emitting one Java class as
both an output `type` and an input `input` (via field-level
include/exclude annotations), prefer the one-class form. In that case
`expressionRef` is rejected by explicit server-side validation rather
than by absence-from-shape, but the rest of the pipeline is identical.
Refactoring between one-class and two-class is mechanical (~15 lines
of converter code and an optional class split) and can be revisited
on demand without touching protocol or domain.

For v1 the frontend always produces a single-level `AND_GROUP` wrapping one
`COMPARISON` per active column filter. Nested groups are v2. The backend
tolerates any valid `FilterNodeInput` shape for forward compatibility.

### CF4.5 Injectable References Forbidden in `userFilter`

User-supplied `FilterNodeInput` MUST NOT carry an `expressionRef` (the field
exists on the server-side `FilterNode` for admin injectable filters). The
backend rejects the request with a validation error if set. Code authoring
stays admin-only.

---

## Task CF5 — Backend Filter & Sort Merge

**Goal:** Apply the user filter and user sort inside the existing query
pipeline without duplicating query-building logic.

### CF5.1 Merge Strategy

`FilterExecutor.executePagedQuery` gains two optional parameters:

```java
PagedResponse executePagedQuery(
        EntityProvider provider,
        Class<?> entityClass,
        int offset,
        int limit,
        FilterNode userFilter,    // nullable
        List<SortField> userSort  // nullable / empty
);
```

Filter merge:
- Build the effective filter as `AND_GROUP` of:
  1. Resolved static + injectable filter (already produced by existing
     `mergeFilters` + `resolveFilterExpressions`).
  2. `userFilter`, if non-null.
- If both are null, pass no WHERE predicate (current behavior).

Sort merge:
- If `userSort` is non-empty, it **replaces** `provider.sortFields`.
- Otherwise use `provider.sortFields`.
- **Stable pagination tiebreaker.** In either case, `id ASC` is appended as
  the last ORDER BY clause. Without it, rows that tie on the primary sort
  key can reshuffle between pages (SQL makes no ordering guarantee for
  ties), causing rows to appear twice or vanish across page boundaries.
  The tiebreaker is purely a backend correctness detail, never part of
  `userSort` and never visible in the API.

### CF5.2 Reuse of Existing `buildPredicate`

`userFilter` is a `FilterNode` tree by the time it reaches `FilterExecutor`.
`buildPredicate` already recursively converts `FilterNode` to JPA
`Predicate`. No new tree-walking code is needed.

### CF5.3 Callers Updated

- `ViewDataService.getDataPaged` passes the new args through from the
  GraphQL controller.
- `GridDataService` (the GRID equivalent) does the same.

### CF5.4 Validation

`userFilter` is validated before being passed to `FilterExecutor`:
- `COMPARISON` nodes must have non-null `field` and `operator`.
- Field names must resolve via JPA metamodel (otherwise the DB would throw).
- `expressionRef` must be absent (CF4.5).
- Values are coerced to the target attribute's Java type (the same coercion
  used for static filter values today — extended as needed for
  Date / YearMonth / Number / Boolean / Enum).

### CF5.5 Case-Insensitive `LIKE` for STRING Filters

The inline String filter (CF1.2) uses `LIKE %v%` with case-insensitive
semantics regardless of database. `FilterExecutor.buildComparison` wraps
both sides in `LOWER(...)`:

```java
// COMPARISON with FilterOperator.LIKE and a STRING attribute
cb.like(cb.lower(path), "%" + value.toLowerCase() + "%")
```

This is portable across HSQL / Postgres / MySQL (the project's supported
targets) and avoids a per-DB branching on `ILIKE`. The same treatment
applies to any future `STARTS_WITH` / `ENDS_WITH` operators introduced
by CF7. Only the String case gets `LOWER`-wrapping; other operand types
use their native comparison.

---

## Task CF6 — User Entity & DEFAULT_USER Seed

**Goal:** Introduce the `User` JPA entity now so CF8 (SavedFilter) can
reference it later. No authentication, no authorization in this task.

### CF6.1 Entity

```java
@Entity
@Table(name = "app_user")
public class User {
    @Id @GeneratedValue Long id;

    @Column(nullable = false, unique = true) String username;
    @Column(nullable = false) String displayName;
    @Column(nullable = false) Instant createdAt;
    @Column(nullable = false) Instant updatedAt;
}
```

Table name `app_user` to avoid collision with reserved `user` in some
databases. No password, no roles, no enabled flag — those belong to an
authentication task not scheduled here.

### CF6.2 `UserService`

```java
@Service
public class UserService {
    User getCurrentUser();   // returns DEFAULT_USER in v1
    User findById(Long id);
    User findByUsername(String username);
}
```

`getCurrentUser()` is the single call site that a future authentication
implementation will flip to read the authenticated principal. All downstream
code (e.g., CF8's SavedFilter queries) goes through this method.

### CF6.3 `DEFAULT_USER` Seed

A bootstrap seeder creates a user row with:
- `username = "default"`
- `displayName = "Default User"`
- timestamps at seed time.

If the row already exists (matched by `username`), the seeder is a no-op.

### CF6.4 Non-Goals for CF6

- No login UI.
- No session cookies, no JWT, no Spring Security wiring.
- No roles, no permissions.
- No self-service signup.

Authentication is a separate future task. This task establishes the entity
only.

### CF6.5 Future Auth Migration — Orphaned Data

When real authentication lands, any data owned by `DEFAULT_USER` from the
pre-auth phase (notably CF8's `SavedFilter` rows) becomes orphaned. At
that migration the project is **not yet in production**, so the decision
is to **delete such orphans** — no reassignment, no sentinel preservation.
Auth-migration tooling will include a cleanup step that removes rows
referencing the `DEFAULT_USER` owner.

---

## Task CF7 — Advanced Filter Editor (v2 / R3a)

**Goal:** A "Add filter" toolbar action opens a full editor for a nested
AND/OR filter. The filter gets a name and becomes active for the table in
the current session. No persistence yet.

### CF7.1 Entry Point

Grid toolbar gains an **Add filter** action. Activating it pushes a new
`EditorFrame` onto the EditorStack (reusing the stack from `gridElement.md`
G5) with a new frame kind: `FILTER_EDITOR`.

### CF7.2 EditorFrame Extension

```dart
enum FrameKind { DATA_FORM, FILTER_EDITOR }

class EditorFrame {
    // existing fields ...
    FrameKind kind;
    FilterEditorState? filterState;   // populated when kind = FILTER_EDITOR
}

class FilterEditorState {
    String? name;                     // user-chosen filter name
    FilterNode root;                  // always an AND_GROUP or OR_GROUP
    String scopeViewNodeCode;         // or
    String? scopeDataFormCode;
    String? scopeElementCode;
}
```

The stack path tree (G5.3) renders an additional node kind for the filter
editor frame with label `"Filter: {name or 'Untitled'}"`.

### CF7.3 Layout — Indented Panels, Not a Tree Widget

The filter root is always a group (`AND_GROUP` or `OR_GROUP`). The group
renders as a panel with:
- A header: group operator toggle (AND ⇄ OR), group actions (add clause,
  add subgroup, delete group).
- A body: each child renders as a panel indented one level deeper.
  Nesting is visualized by indentation + left-border color, not by tree
  disclosure arrows.
- Unlimited nesting depth. Practical depth beyond 3 is poor UX but allowed.

A leaf clause (`COMPARISON` node) renders as a panel with:
- **Field picker** — dropdown of entity dot-paths, sourced from the same
  JPA metamodel walk used by `columnFilterMetadata` (CF3), but covering
  the full entity attribute tree, not just configured columns. The picker
  shows friendly labels (column header if the path matches a configured
  column, otherwise the attribute name).
- **Operator picker** — full operator palette:
  `EQUALS, NOT_EQUALS, GT, GTE, LT, LTE, IN, NOT_IN (v2),
   IS_NULL, IS_NOT_NULL, LIKE, STARTS_WITH (v2), ENDS_WITH (v2)`.
  Operators compatible with the picked field's type only.
- **Value input** — widget chosen by the field's type (same widgets as
  CF1.2, extended for operators that need distinct UX, e.g. `IS_NULL`
  needs no value input).
- **Binding selector** — for the value, the user can choose `LITERAL`
  (v2 default) or `PARENT_ENTITY` (v2, only when editing a GRID inside an
  editor stack whose parent has an entity ID). Further bindings
  (`CURRENT_USER`, `TODAY`, `NOW`) are v3 / on-demand.
- **Delete clause** action.

### CF7.4 Filter Naming & Activation

The editor has a single text field at the top for the filter **name** (free
text, no uniqueness requirement in v1 — names are session-only).

Footer actions:
- **Apply** — validates the tree, pops the frame, activates the filter on
  the underlying grid. The grid refetches. The named filter shows as a
  dismissible chip in the grid toolbar.
- **Cancel** — pops the frame, no change.
- **Apply & Close** alias in case dismissing is distinct from cancelling
  the edit.

### CF7.5 Interaction With Per-Column Inline Filters

When a named filter is active, per-column inline filters remain visible and
usable. Both AND together (see CF1.5 #3 and #4).

### CF7.6 Protocol

`userFilter` already supports arbitrary `FilterNode` trees (CF4.3). The
frontend assembles the named filter + per-column filters into a single
`AND_GROUP` root and sends it. The backend does not distinguish between
the two sources.

### CF7.7 Injectable Refs Remain Forbidden

Per CF4.5, `expressionRef` is rejected by the backend regardless of where
in the tree it appears. The advanced editor UI does not expose any way to
author one.

### CF7.8 Non-Goals for v2

- No persistence. Closing the browser (or activating a different ViewNode)
  loses the named filter.
- No sharing between users.
- No filter library / management view.
- No TODAY / NOW / CURRENT_USER bindings — those wait for v3 SavedFilter.

---

## Task CF8 — SavedFilter Persistence (v3 / R3b)

**Goal:** Persist named filters per user. Activating a saved filter loads it
into the grid.

### CF8.1 Entity

```java
@Entity
@Table(
    name = "saved_filter",
    uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "scope_key", "name"})
)
public class SavedFilter {
    @Id @GeneratedValue Long id;

    @ManyToOne(optional = false) User owner;
    @Column(nullable = false) String scopeKey;         // viewNodeCode or "grid:{dataFormCode}:{elementCode}"
    @Column(nullable = false) String name;
    @Lob String filterDefinitionJson;                   // serialized FilterNode tree
    @Lob String sortSpecJson;                           // serialized List<SortField>
    @Column(nullable = false) Instant createdAt;
    @Column(nullable = false) Instant updatedAt;
}
```

### CF8.2 GraphQL Mutations

```graphql
extend type Mutation {
    saveFilter(input: SaveFilterInput!): SavedFilter!
    deleteFilter(id: ID!): Boolean!
}

extend type Query {
    savedFilters(scope: ColumnFilterScopeInput!): [SavedFilter!]!
}
```

Each mutation uses `userService.getCurrentUser()` as the owner; users see
only their own filters.

### CF8.3 Frontend — Dropdown Selector

The grid toolbar gains a saved-filter dropdown listing
`savedFilters(scope)` for the current user. Selecting one loads its tree
into the grid's active filter state. A "Save as..." button saves the current
composed filter (per-column + named advanced) under a chosen name.

### CF8.4 `CURRENT_USER` Binding

In the advanced editor (CF7.3 binding selector), `CURRENT_USER` becomes
available. At query time it resolves to `userService.getCurrentUser().id`.

### CF8.5 `TODAY` / `NOW` Bindings — On Demand

Add only when a concrete use case arises. The binding resolver is already
extensible by the shape adopted in `gridElement.md` `ContextBinding`.

---

## Non-Goals (v1–v3 altogether)

- No cross-column compound quick filters in the header row. Multi-column
  expressions go to CF7's advanced editor.
- No multi-column sort in v1. CF7 covers it as part of the advanced editor.
- No free-text search box across all columns. A user can approximate by
  chaining per-column `CONTAINS` filters.
- No Excel-style value-list filter (unique values of a column). Deferred.
- No column show / hide / reorder. Deferred.
- No authentication in CF6. The `User` entity is scaffolding only.
- No shared / team filters in CF8. Per-user only.

---

## Phase Dependency Order

```
CF1 (per-column inline) ─┐
CF2 (sort glyph) ────────┤
CF3 (column metadata) ───┼── independent v1 tasks; any order after CF4 shape is fixed
CF4 (protocol extension) ┤
CF5 (backend merge) ─────┘
CF6 (User entity) ──────── independent; ship any time in v1

CF7 (advanced editor) ─── depends on CF3 (field picker sources from metadata),
                          CF4 (tree protocol), CF5 (merge)

CF8 (SavedFilter) ─────── depends on CF6 (User), CF7 (editor produces the tree)
```

Suggested implementation order for v1: **CF4 → CF3 → CF5 → CF2 → CF1 → CF6.**
Backend protocol first fixes the contract; metadata query unblocks the
frontend widget work; merge logic closes the backend loop; sort ships
before filter because it has fewer moving parts and sanity-checks the
protocol round-trip.

---

## Cross-References

- **FilterNode / FilterOperator / FilterExecutor**: `dataBinding.md` Task 2,
  Task 6. Reused directly by CF4, CF5.
- **EntityProvider**: `dataBinding.md` — `filter`, `filterInjectableRef`,
  `sortFields`. ANDed with user filters in CF5.
- **TableColumn / GridTableColumn**: `viewIntegration.md` V1,
  `gridElement.md` G1. v1 adds no new fields to these types — picker
  base queries are derived transiently (CF3.4).
- **EntityRenderer**: existing concept in `dataBinding.md`. v1 extends
  it with `searchFields` and `sortFields` (CF3.5) to drive picker
  typeahead and ordering. Editor uses the autoproposal pattern from
  `gridElement.md` G6.4.1.
- **EditorStack**: `gridElement.md` G5. Reused by CF7 with a new
  `FILTER_EDITOR` frame kind.
- **ContextBinding**: `gridElement.md` G6. Binding enum reused and extended
  by CF7 (`LITERAL`, `PARENT_ENTITY`) and CF8 (`CURRENT_USER`, `TODAY`,
  `NOW`).
- **Table component refactor**: `components.md` C1 — done
  (2026-04-28). Both ENTITY_LIST and GRID now render via `TrinaGrid`.
  CF1 / CF2 widgets re-hosted into `TrinaColumn.titleRenderer`
  unchanged at the protocol and state-model level; `trina_grid`'s
  built-in filter UI is bypassed per the C1.3 integration boundary.
- **Entity table unification**: `entityTableUnification.md`. Column filters
  must treat ENTITY_LIST and GRID identically; unification work must not
  require column-filter changes.
- **GraphQL**: `graphql.md`. All protocol extensions in CF3, CF4, CF8 use
  the existing code-first GraphQL pipeline.
- **Domain entities**: `domainEntities.md`. Camera-domain JPA model
  consumed throughout this spec — `CameraProducer`, `CameraLensMount`,
  `CameraLensMount2CameraProducer`, `Camera`. The
  `PhotoEquipmentMarketSegment` enum (Task D3) is the canonical
  example for the future ENUM picker-restriction work flagged in
  CF3.4.3 *Known limitations*.
- **Selectable GRID** (future): `specifications.md` pending item.
  Extends CF3.4.1's projection to execute `FilterInjectable`s at
  picker-open time when a GRID is used as a form field's picker source.
  `ENTITY_SELECT` itself is untouched by that track; it remains the
  dropdown option. No change to the CF1 UX contract.
