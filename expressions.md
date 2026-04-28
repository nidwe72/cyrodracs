# Expression System Specification (IInjectible)

## Overview

This specification defines a generic **expression and context-injection system** that enables
config-tree nodes to reference dynamic runtime values. The system addresses two fundamental needs:

1. **Server-side:** EntityProvider filters that depend on the entity currently being edited
   (e.g., "show lens mounts for *this* producer").
2. **Server-side visibility:** Conditional element visibility driven by entity/form state
   (e.g., "show field B only if field A has a value"), evaluated via BooleanInjectable.

The system is designed to be **future-proof**, **sandboxed**, and **config-tree native** — expressions
are first-class AppConfig nodes, selectable by reference from any config node that needs dynamic values.

---

## Design Principles

1. **Config-tree citizen.** Expressions live in the AppConfig tree like EntityProvider and EntityRenderer.
   They are created, edited, and referenced through the same admin editor.
2. **Sandboxed evaluation.** Expressions do NOT have arbitrary code execution. They access a
   well-defined, read-only context through declared paths — no reflection, no method calls, no
   side effects.
3. **Dual-stack.** The same expression model is evaluated server-side (Java / JPA context) and
   client-side (Dart/JS / form state), using stack-specific resolvers that share the model.
4. **Multiple implementation strategies.** The system supports different ways to define expressions
   (path-based, SpEL-subset, rule-based) via an extensible type enum, without coupling consumers
   to a single implementation.
5. **Declarative, not imperative.** Expressions describe *what* to access, not *how* to compute it.

---

## Prior Art & Best Practices

| Pattern / Library | Relevance |
|---|---|
| **Spring Expression Language (SpEL)** | Already on the classpath. Supports property navigation (`entity.producer.id`), safe-navigation (`?.`), and can be sandboxed via `SimpleEvaluationContext` (no type references, no method calls). Server-side only. |
| **Jakarta Expression Language (EL)** | Standard in Jakarta EE. Similar to SpEL but less powerful. Not needed if SpEL is used. |
| **JSON Logic** | JSON-serializable rule trees. Good for cross-stack portability (JS + Java implementations exist). Heavier than needed for path-based access but excellent for complex conditions. |
| **JSON Schema conditionals** | `if`/`then`/`else` for conditional field schemas. Influences our visibility rule design. |
| **MVEL** | Lightweight expression language. No advantage over SpEL since Spring is already present. |
| **Camunda/Flowable** | BPMN expression evaluation with context injection. Same pattern: context object + expression string + sandboxed evaluator. |
| **Angular Reactive Forms** | Client-side conditional visibility via `valueChanges` observables. Influences the client-side evaluation model. |

**Chosen approach:** A **path-based expression model** stored in the config tree, evaluated via
**SpEL `SimpleEvaluationContext`** on the server and a **lightweight path resolver** on the client.
For complex conditions, we use a **composable rule tree** (similar to FilterNode's AND_GROUP/OR_GROUP
pattern). This avoids pulling in a separate library while staying extensible.

---

## Context Model

### What is a Context?

A **context** is a read-only map of named objects available during expression evaluation. Different
situations provide different contexts:

| Context Name | Available When | Contents |
|---|---|---|
| `editor` | DataForm is editing an existing entity | The persisted entity loaded into the editor |
| `formState` | DataForm is open (create or edit) | Current form field values as a flat map (key = dataBinding path, value = current input) |
| `route` | Any view is active | Route parameters (e.g., `viewNodeCode`, URL query params) |
| `session` | Always | Session-level data (e.g., current user, locale) |

Contexts are **layered** — an expression resolver receives all applicable contexts and resolves
references by walking `contextName.path.to.value`.

### Context Providers

Each context is populated by a **ContextProvider** — a server-side or client-side component that
knows how to build the context map for its scope:

```java
public interface ContextProvider {
    /** The name under which this context is accessible (e.g., "editor", "formState"). */
    String getContextName();
    /** Build the context map for the given request/state. */
    Map<String, Object> buildContext(ContextRequest request);
}
```

`ContextRequest` carries the request-scoped information needed to build context (entity ID,
form values, route params, etc.).

---

## Task E1 — Expression Model (Config Tree)

**Goal:** Define `Expression` as a config-tree node that declares how to resolve a dynamic value.

### E1.1 ExpressionType Enum

```java
public enum ExpressionType {
    CONTEXT_PATH,         // Simple dot-path into a named context: "editor.id", "formState.name"
    SPEL,                 // SpEL expression (server-side only, sandboxed)
    STATIC,               // Literal value (useful as default/fallback)
    INJECTABLE_SNIPPET,   // Inline lambda/function body evaluated server-side (see Task E7)
    INJECTABLE_CLASS      // Full class body (source in DB) extending a base class (see Task E7)
}
```

**CONTEXT_PATH** covers 90% of use cases and works on both server and client.
**SPEL** is the escape hatch for server-side logic that path-based access cannot express
(e.g., string concatenation, arithmetic). It is evaluated in a `SimpleEvaluationContext` that
exposes only the declared contexts — no bean references, no class access, no method invocation
beyond property getters.
**STATIC** provides a literal value, useful as a fallback or for testing.
**INJECTABLE_SNIPPET** is an inline method body for cases that need imperative logic (if/else,
loops) but don't warrant a full class. The snippet source code is stored in the config tree
(database). Evaluated server-side via the sandbox API.
**INJECTABLE_CLASS** stores the full Java class body (source code) in the config tree (database).
The class extends a base class from the codebase (e.g., `ScalarValueInjectable`,
`BooleanInjectable`) and is compiled at runtime. This gives maximum expressiveness: helper methods,
fields, complex control flow — while the base class in the codebase enforces the sandbox contract.
See Task E7 for the full design.

### E1.2 Expression In-Memory Model

```java
public class Expression implements Coded {
    Long id;
    String code;                    // e.g., "currentEditorEntityId"
    ExpressionType type;
    Long typeNodeId;

    String expression;              // the expression body (type-dependent):
                                    //   CONTEXT_PATH:       "editor.id"
                                    //   SPEL:               "#editor.producer?.name"
                                    //   STATIC:             "42"
                                    //   INJECTABLE_SNIPPET:  method body source code (DB-persisted)
                                    //   INJECTABLE_CLASS:    full class body source code (DB-persisted)
    Long expressionNodeId;

    InjectableBaseClass baseClass;  // INJECTABLE_SNIPPET/CLASS only: which base class to extend
    Long baseClassNodeId;           //   determines the return type contract (scalar, boolean, list, ...)

    String description;             // human-readable purpose (optional)
    Long descriptionNodeId;
}
```

### E1.3 AppConfig Update

```java
public class AppConfig implements Coded {
    // ... existing fields ...
    Map<String, Expression> expressions = new LinkedHashMap<>();  // NEW
}
```

### E1.4 AppConfigType Rows (Seeder)

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `Expression` | `AppConfig` | `expressions` | true | false | `...appconfig.Expression` |
| `ExpressionType` | `Expression` | `type` | false | true | `...appconfig.ExpressionType` |
| `ExpressionBody` | `Expression` | `expression` | false | false | `java.lang.String` |
| `InjectableBaseClass` | `Expression` | `baseClass` | false | true | `...appconfig.InjectableBaseClass` |
| `ExpressionDescription` | `Expression` | `description` | false | false | `java.lang.String` |

### E1.5 Example Expressions

```
AppConfig
└── expressions:
    ├── "currentEditorEntityId" (Expression)
    │   ├── type: CONTEXT_PATH
    │   ├── expression: "editor.id"
    │   └── description: "ID of the entity currently being edited"
    ├── "currentEditorEntityName" (Expression)
    │   ├── type: CONTEXT_PATH
    │   ├── expression: "editor.name"
    │   └── description: "Name of the entity currently being edited"
    ├── "staticDefaultPageSize" (Expression)
    │   ├── type: STATIC
    │   ├── expression: "10"
    │   └── description: "Default page size for grids"
    ├── "currentEditorIdViaSnippet" (Expression)
    │   ├── type: INJECTABLE_SNIPPET
    │   ├── baseClass: SCALAR_VALUE
    │   ├── expression: |    ← source code persisted in DB
    │   │       CameraProducer p = getInjectionContext().getEditorEntity(CameraProducer.class);
    │   │       setResult(p != null ? p.getId() : null);
    │   └── description: "Same as currentEditorEntityId but via inline snippet"
    ├── "mountFilterForCurrentProducer" (Expression)
    │   ├── type: INJECTABLE_CLASS
    │   ├── baseClass: FILTER
    │   ├── expression: |    ← full class body source code persisted in DB
    │   │       @Override
    │   │       public void execute() {
    │   │           CameraProducer p = getInjectionContext()
    │   │               .getEditorEntity(CameraProducer.class);
    │   │           if (p == null) {
    │   │               setResult(null);
    │   │               return;
    │   │           }
    │   │           setResult(
    │   │               comparison("cameraProducer.id", FilterOperator.EQUALS, p.getId())
    │   │           );
    │   │       }
    │   └── description: "Restricts CameraLensMount2CameraProducer to the currently edited producer"
    └── "isProducerDiscontinued" (Expression)
        ├── type: INJECTABLE_SNIPPET
        ├── baseClass: BOOLEAN_VALUE
        ├── expression: |
        │       String sy = getInjectionContext().getFormValue("shutdownYear");
        │       setResult(sy != null && !sy.isEmpty());
        └── description: "True if the producer has a shutdown year set"
```

---

## Task E2 — Dynamic Filters (Server-Side)

**Goal:** Enable EntityProvider filters to be resolved dynamically at runtime. Two mechanisms:

1. **FilterNode.expressionRef** — a single filter value is resolved dynamically (e.g., the `value`
   in a COMPARISON comes from an expression). The FilterNode structure is still configured statically.
2. **EntityProvider.filterInjectableRef** — the entire filter tree is produced by a `FilterInjectable`.
   No static FilterNode is needed. The injectable builds the FilterNode tree programmatically.

Both can coexist: if both a static filter and a filterInjectableRef are set, they are merged via AND.

### E2.1 FilterNode Extension (expressionRef on values)

```java
public class FilterNode implements Coded {
    // ... existing fields (value, values, etc.) ...

    /** Optional: expression reference that resolves the filter value dynamically. */
    String expressionRef;
    Long expressionRefNodeId;
}
```

**Resolution rule:** If `expressionRef` is set, the filter executor resolves it at runtime and uses
the result as the comparison value. If `expressionRef` is not set, the existing static `value` field
is used. This is fully backward-compatible.

### E2.2 EntityProvider Extension (filterInjectableRef)

```java
public class EntityProvider implements Coded {
    // ... existing fields ...

    FilterNode filter;                    // static filter (existing, can be null)

    /** Optional: expression code of a FilterInjectable that produces a FilterNode tree. */
    String filterInjectableRef;
    Long filterInjectableRefNodeId;
}
```

**Type constraint:** The referenced expression MUST have `baseClass: FILTER`. This is validated
at config save time and at config load time (see E2.9).

### E2.3 AppConfigType Rows

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `FilterExpressionRef` | `FilterNode` | `expressionRef` | false | false | `java.lang.String` |
| `FilterInjectableRef` | `EntityProvider` | `filterInjectableRef` | false | false | `java.lang.String` |

### E2.4 FilterExecutor Update

```java
@Component
public class FilterExecutor {

    private final EntityManager entityManager;
    private final ExpressionResolver expressionResolver;

    // ... existing methods ...

    /**
     * Execute a query with runtime context for dynamic expression resolution.
     * Merges static filter, injectable filter, and expressionRef-resolved values.
     */
    public PagedResult executePagedQuery(EntityProvider provider, Class<?> entityClass,
                                         int offset, int limit,
                                         ExpressionContext context) {

        // 1. Resolve static filter (expressionRefs on individual FilterNode values)
        FilterNode staticFilter = resolveFilterExpressions(provider.getFilter(), context);

        // 2. Resolve injectable filter (entire FilterNode tree from FilterInjectable)
        FilterNode injectableFilter = null;
        if (provider.getFilterInjectableRef() != null) {
            injectableFilter = expressionResolver.resolveFilter(
                provider.getFilterInjectableRef(), context);
        }

        // 3. Merge: AND-combine if both present
        FilterNode effectiveFilter = mergeFilters(staticFilter, injectableFilter);

        // ... rest of existing criteria query logic using effectiveFilter ...
    }

    private FilterNode mergeFilters(FilterNode staticFilter, FilterNode injectableFilter) {
        if (staticFilter == null && injectableFilter == null) return null;
        if (staticFilter == null) return injectableFilter;
        if (injectableFilter == null) return staticFilter;

        // Both present — AND them together
        FilterNode merged = new FilterNode();
        merged.setType(FilterNodeType.AND_GROUP);
        merged.setChildren(List.of(staticFilter, injectableFilter));
        return merged;
    }

    private FilterNode resolveFilterExpressions(FilterNode node, ExpressionContext context) {
        if (node == null) return null;
        if (node.getExpressionRef() != null) {
            String resolved = expressionResolver.resolve(node.getExpressionRef(), context);
            FilterNode copy = deepCopy(node);
            copy.setValue(resolved);
            return copy;
        }
        // Recurse into children for AND_GROUP/OR_GROUP
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            FilterNode copy = deepCopy(node);
            copy.setChildren(node.getChildren().stream()
                .map(child -> resolveFilterExpressions(child, context))
                .toList());
            return copy;
        }
        return node;
    }
}
```

### E2.5 ExpressionResolver

```java
@Component
public class ExpressionResolver {

    private final AppConfigStore appConfigStore;
    private final InjectableExecutor injectableExecutor;

    /**
     * Resolve an expression to a scalar String value.
     */
    public String resolve(String expressionCode, ExpressionContext context) {
        Expression expr = appConfigStore.getAppConfig().getExpressions().get(expressionCode);
        if (expr == null) throw new IllegalArgumentException("Unknown expression: " + expressionCode);

        return switch (expr.getType()) {
            case STATIC             -> expr.getExpression();
            case CONTEXT_PATH       -> resolveContextPath(expr.getExpression(), context);
            case SPEL               -> resolveSpel(expr.getExpression(), context);
            case INJECTABLE_SNIPPET -> injectableExecutor.executeSnippet(expr, context);
            case INJECTABLE_CLASS   -> injectableExecutor.executeClass(expr, context);
        };
    }

    /**
     * Resolve an expression to a FilterNode tree.
     * The referenced expression MUST have baseClass: FILTER.
     */
    public FilterNode resolveFilter(String expressionCode, ExpressionContext context) {
        Expression expr = appConfigStore.getAppConfig().getExpressions().get(expressionCode);
        if (expr == null) throw new IllegalArgumentException("Unknown expression: " + expressionCode);
        if (expr.getBaseClass() != InjectableBaseClass.FILTER) {
            throw new IllegalArgumentException(
                "Expression '" + expressionCode + "' has baseClass " + expr.getBaseClass()
                + " but FILTER is required for filterInjectableRef");
        }
        return injectableExecutor.executeFilter(expr, context);
    }

    private String resolveContextPath(String path, ExpressionContext context) {
        // Split "editor.id" into contextName="editor", propertyPath="id"
        // Look up context object, navigate dot-path via PropertyAccessor
        // Return String.valueOf(result)
    }

    private String resolveSpel(String spelString, ExpressionContext context) {
        // Use SimpleEvaluationContext (no bean refs, no type access)
        // Register context objects as variables: #editor, #formState, #route, #session
        // Parse and evaluate, return String.valueOf(result)
    }
}
```

### E2.6 ExpressionContext

```java
/**
 * Carries runtime context for expression evaluation.
 * Built per-request from ContextProviders.
 */
public class ExpressionContext {
    private final Map<String, Object> contexts = new LinkedHashMap<>();

    public void put(String name, Object contextObject) { contexts.put(name, contextObject); }
    public Object get(String name) { return contexts.get(name); }
    public Map<String, Object> getAll() { return Collections.unmodifiableMap(contexts); }
}
```

### E2.7 Wiring: GRID Endpoint → FilterExecutor

When the GRID data endpoint is called:

```
POST /api/view/grid-data/{dataFormCode}/{elementCode}?page=0&size=10
Body: { "entityId": 4, "formState": { "name": "Fuji", "foundationYear": "1934-01" } }
```

1. Build transient editor entity via `EditorEntityBuilder.buildFromFormState()` using
   the entity class from the DataForm, entityId, and formState from the request body.
2. Build `ExpressionContext` with `editor` = the transient entity.
3. Resolve the GRID element's EntityProvider.
4. Call `filterExecutor.executePagedQuery(provider, entityClass, offset, limit, context)`.
5. The FilterExecutor resolves the `filterInjectableRef`, which produces a FilterNode tree,
   merges it with any static filter, and executes the JPA Criteria query.

### E2.8 Full Example: mountsForCurrentProducer (via FilterInjectable)

This is the primary use case — no static filter, fully dynamic via injectable:

```
expressions:
  └── "producerMountFilter" (Expression)
      ├── type: INJECTABLE_CLASS
      ├── baseClass: FILTER
      ├── expression: |
      │       @Override
      │       public void execute() {
      │           CameraProducer p = getInjectionContext()
      │               .getEditorEntity(CameraProducer.class);
      │           if (p == null || p.getId() == null) {
      │               // Brand-new (transient) producer or no editor entity
      │               // at all — emit a match-nothing predicate so the GRID
      │               // (and any picker built on top of it via CF3.4.3)
      │               // legitimately returns zero rows. See "Match-nothing
      │               // for missing editor entity" below.
      │               setResult(isNull("cameraProducer.id"));
      │               return;
      │           }
      │           setResult(
      │               comparison("cameraProducer.id", FilterOperator.EQUALS, p.getId())
      │           );
      │       }
      └── description: "Restricts CameraLensMount2CameraProducer to the currently edited producer"

entityProviders:
  └── "mountsForCurrentProducer"
      ├── entityType: CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER
      ├── filter: (none)                                          ← no static filter
      ├── filterInjectableRef: "producerMountFilter"              ← dynamic!
      └── sortFields:
          └── field: "cameraLensMount.name", direction: ASC
```

**Runtime flow when editing CameraProducer "Fuji" (id=4):**

1. GRID endpoint receives POST with `entityId=4`,
   `formState: {name: "Fuji", foundationYear: "1934-01"}`.
2. Resolves DataForm → entity class `CameraProducer.class`.
3. `EditorEntityBuilder.buildFromFormState(CameraProducer.class, 4, formState)` →
   transient `CameraProducer{id=4, name="Fuji", foundationYear=1934-01}`.
   Scalar fields come from formState (reflecting any unsaved edits).
4. Builds `ExpressionContext { editor: transientCameraProducer }`.
5. FilterExecutor sees `filterInjectableRef = "producerMountFilter"`.
6. Calls `expressionResolver.resolveFilter("producerMountFilter", context)`.
7. `InjectableExecutor` compiles (or retrieves from cache) the DB-persisted source,
   instantiates it as a `FilterInjectable`, injects the context, calls `execute()`.
8. The injectable runs:
   `CameraProducer p = getInjectionContext().getEditorEntity(CameraProducer.class)`
   → `p.getId()` → `4` → builds `comparison("cameraProducer.id", EQUALS, 4)`.
9. FilterExecutor receives the FilterNode, merges with static filter (null → no merge needed).
10. Builds JPA Criteria: `WHERE cameraProducer.id = 4 ORDER BY cameraLensMount.name ASC`.
11. Returns: `[{M42, ZeissIkon}, {X-Mount, Fuji}]`.

**Match-nothing for missing editor entity.** A `FilterInjectable`
that depends on the editor entity faces three runtime cases:

| Case | Editor entity | Recommended `setResult(...)` |
|---|---|---|
| Persisted entity (existing producer being edited) | non-null with non-null id | `comparison("…", EQUALS, entity.getId())` — the normal restrict |
| Brand-new (transient) entity, before first save | non-null but `id == null` | **`isNull("…id")` (match-nothing)** |
| No editor entity at all (no formState, no entityId) | null | **`isNull("…id")` (match-nothing)** |

The two "match-nothing" rows used to emit `setResult(null)` (= "no
filter" = "show all rows") in early seeds. That looked tempting —
"if I can't compute a meaningful predicate, leave the query
unconstrained" — but it's wrong UX: in create-new mode, no child
rows can possibly reference the unsaved parent, so the GRID must
show **zero rows**, not all rows. Returning a structurally
unsatisfiable predicate (e.g. `cameraProducer.id IS NULL` against a
non-null FK column) achieves this cleanly. CF3.4.3's picker builds
on top of the same row predicate, so the picker also legitimately
collapses to zero candidates — Excel-autofilter convention.

Generalisation: every `filterInjectableRef` whose computation
requires the editor entity SHOULD have a "no editor entity" branch
emitting a match-nothing predicate, not a null result.

### E2.9 Alternate Example: expressionRef on FilterNode value (simpler approach)

For comparison, the same result can be achieved without a FilterInjectable, using a static
FilterNode with an expressionRef on its value:

```
expressions:
  └── "currentEditorEntityId"
      ├── type: CONTEXT_PATH
      └── expression: "editor.id"

entityProviders:
  └── "mountsForCurrentProducer"
      ├── entityType: CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER
      ├── filter:
      │   └── (FilterNode)
      │       ├── type: COMPARISON
      │       ├── field: "cameraProducer.id"
      │       ├── operator: EQUALS
      │       └── expressionRef: "currentEditorEntityId"    ← dynamic value
      ├── filterInjectableRef: (none)
      └── sortFields:
          └── field: "cameraLensMount.name", direction: ASC
```

**When to use which:**

| Approach | Use when |
|---|---|
| **expressionRef on FilterNode** | The filter *structure* is static (field, operator are known), only the *value* is dynamic |
| **filterInjectableRef** | The filter *structure* itself is dynamic (different fields, operators, or AND/OR logic depending on runtime state) |
| **Both combined** | Static base filter + additional dynamic restriction merged via AND |

### E2.10 Reference Type Validation

When the admin assigns a `filterInjectableRef`, the system validates that the referenced
expression has the correct `baseClass`:

| Config field | Expected baseClass | Error if wrong |
|---|---|---|
| `EntityProvider.filterInjectableRef` | `FILTER` | "Expression 'X' has baseClass SCALAR_VALUE but FILTER is required" |
| `FilterNode.expressionRef` | `SCALAR_VALUE` or `LIST_VALUE` | "Expression 'X' has baseClass FILTER but SCALAR_VALUE is required" |
| `VisibilityRule.expressionRef` | `BOOLEAN_VALUE` | "Expression 'X' has baseClass FILTER but BOOLEAN_VALUE is required" |

Validation happens:
1. **At save time** in the admin editor (prevents saving invalid references).
2. **At config load time** in `AppConfigTreeBuilder` (logs warning for existing invalid references).

---

## Task E3 — Conditional Visibility via BooleanInjectable

**Goal:** Enable DataFormElements to declare visibility conditions evaluated **server-side** using
a `BooleanInjectable` expression. This follows the same Injectable pattern already established
for `FilterInjectable` in EntityProviders — the expression receives the full `InjectionContext`
(editor entity, formState, session data) and returns a `Boolean` that directly controls visibility.

**Why server-side, not client-side?** A BooleanInjectable has access to the full entity graph,
JPA relationships, and injection context — it can answer questions like "does this entity have a
producer?" without the frontend needing to understand entity structure. The `reloadOnChangeOf`
mechanism already triggers server calls on field changes, so visibility evaluation piggybacks on
that same flow at no additional architectural cost.

### E3.1 VisibilityRule Model

The VisibilityRule is simplified to a single `expressionRef` pointing to a `BOOLEAN_VALUE`
expression. The expression itself encapsulates the full visibility logic — no separate operator
or compareValue is needed.

```java
public class VisibilityRule implements Coded {
    Long id;
    String code;
    String expressionRef;           // references an Expression with baseClass BOOLEAN_VALUE
    Long expressionRefNodeId;
}
```

### E3.2 DataFormElement Extension

```java
public class DataFormElement implements Coded {
    // ... existing fields ...

    /** Optional: controls whether this element is visible. Null = always visible. */
    VisibilityRule visibilityRule;

    /**
     * When true, changing this element's value triggers a reload of all
     * dependent elements in the same DataForm (e.g., GRID elements whose
     * EntityProvider filter depends on the editor entity state).
     *
     * The frontend re-POSTs the current formState to dependent GRID endpoints
     * when a reloadOnChange element's value changes (debounced).
     *
     * Default: false. Set to true on elements whose values influence
     * injectable logic in other elements.
     */
    boolean reloadOnChange;
    Long reloadOnChangeNodeId;
}
```

### E3.3 AppConfigType Rows

| code | parent | fieldName | collection | enum | javaType |
|---|---|---|---|---|---|
| `VisibilityRule` | `DataFormElement` | `visibilityRule` | false | false | `...appconfig.VisibilityRule` |
| `VisibilityExpressionRef` | `VisibilityRule` | `expressionRef` | false | false | `java.lang.String` |
| `ReloadOnChange` | `DataFormElement` | `reloadOnChange` | false | false | `java.lang.Boolean` |

### E3.4 ExpressionResolver Extension

`ExpressionResolver` gains a `resolveBoolean()` method, analogous to the existing `resolveFilter()`:

```java
/**
 * Resolve an expression to a Boolean value.
 * The referenced expression MUST have baseClass: BOOLEAN_VALUE.
 */
public Boolean resolveBoolean(String expressionCode, ExpressionContext context) {
    Expression expr = getExpression(expressionCode);
    if (expr.getBaseClass() != InjectableBaseClass.BOOLEAN_VALUE) {
        throw new IllegalArgumentException(
            "Expression '" + expressionCode + "' has baseClass " + expr.getBaseClass()
            + " but BOOLEAN_VALUE is required for visibilityRule");
    }
    return injectableExecutor.executeBoolean(expr, context);
}
```

### E3.5 Unified Evaluation Endpoint

Visibility and options reload are evaluated together in a single server call. The existing
`reloadOnChangeOf` mechanism declares the dependency graph; the server walks it transitively
and returns the full state of all affected elements.

#### Endpoint Contract

```
POST /api/data-form/evaluate

Request:
{
  "dataFormCode": "camera",
  "entityId": 42,                          // null for new entities
  "changedElement": "producer",            // element CODE; null on initial form load
  "formState": {                           // keys are dataBinding paths (JPA field names)
    "producer": "5",
    "name": "EOS R5"
  }
}

Response:
{
  "elements": {
    "cameraLensMount2CameraProducer": {
      "visible": true,
      "options": [                         // present only for ENTITY_SELECT elements
        { "id": 7, "label": "EF Mount (Canon)" },
        { "id": 12, "label": "RF Mount (Canon)" }
      ]
    },
    "compatibleLenses": {
      "visible": false,
      "options": []
    }
  }
}
```

#### Evaluation Modes

| `changedElement` | Behaviour |
|---|---|
| `null` / omitted | **Initial load.** Evaluate ALL elements that have a `visibilityRule` and/or `entityProviderRef`. Used when the form first renders. |
| `"producer"` | **Field change.** Walk the transitive dependency graph from `"producer"` and evaluate only the affected elements. |

#### Design Rule: Data Independence from Visibility

Data is always kept up-to-date regardless of visibility. When an element is evaluated, both its
visibility AND its options are computed — even if the result is `visible: false`. This avoids
the need to detect "became visible" transitions and trigger separate reloads. The `visible` flag
is purely a **view concern**, not a data-flow gate.

### E3.6 Dependency Graph and Topological Evaluation

The `reloadOnChangeOf` declarations on DataFormElements form a **directed acyclic graph (DAG)**
that the server uses to determine which elements need re-evaluation when a field changes.

#### Graph Theory Background

The structure at hand is a **DAG** (Directed Acyclic Graph) from order theory:

- **Vertices** = DataFormElement codes within a DataForm
- **Edges** = `reloadOnChangeOf` declarations (directed: from dependency → dependent)
- **Acyclic constraint** = no circular dependencies allowed (A depends on B depends on A is
  a configuration error, detected at config load time)

When a vertex (element) changes, we need to evaluate all vertices **reachable** from it —
its transitive closure in the graph. The order in which we evaluate them matters: if B depends
on A and C depends on B, we must evaluate B before C, because C's BooleanInjectable may read
entity state that was influenced by B's evaluation.

This is a classic **topological sort** problem. For a DAG, a topological ordering is a linear
sequence of all vertices such that for every directed edge (u → v), u appears before v. In our
case, we only need the topological order of the **subgraph reachable from the changed element**,
not the entire form.

**BFS (Breadth-First Search)** naturally produces a valid topological order for DAGs when
traversing level by level from the changed element — elements at distance 1 are evaluated
before elements at distance 2, and so on. This is sometimes called **Kahn's algorithm** when
combined with in-degree tracking, but for our tree-like dependency structures a simple BFS
suffices.

#### Formal Properties

| Property | Implication |
|---|---|
| **DAG** | Guarantees a topological order exists. No infinite evaluation loops. |
| **Transitive closure** | A single change can propagate through the full chain. One round-trip regardless of depth. |
| **Topological order** | Elements are evaluated in dependency order — a dependent never sees stale state from its dependency. |
| **Confluence** | If an element is reachable via multiple paths (diamond dependency), it is evaluated only once, after all its dependencies have been evaluated. |

#### Why This Structure is Sufficient

The dependency graph models **reactive propagation**: a change flows forward through declared
dependencies. This covers:
- Cascading visibility (A visible → B visible → C visible)
- Cascading option filtering (producer → mounts → lenses)
- Mixed cascades (visibility + options in one chain)

A natural question is whether future requirements might need non-topological evaluation — e.g.,
bidirectional dependencies or fixed-point iteration. Such patterns would indicate circular
dependencies, which are excluded by the DAG constraint. If a future use case genuinely requires
bidirectional reactivity (rare in form UIs), it would be modelled differently — e.g., as a
constraint satisfaction problem. For now, the DAG/topological model matches the domain
accurately.

#### Implementation

```java
/**
 * Resolves the transitive closure of elements affected by a change,
 * returned in topological (BFS) order.
 */
public List<DataFormElement> resolveAffectedElements(DataForm form, String changedElement) {
    // Build adjacency map: dependency → list of dependents
    Map<String, List<DataFormElement>> dependents = new HashMap<>();
    for (DataFormElement el : form.getElements()) {
        if (el.getReloadOnChangeOf() != null) {
            for (String dep : el.getReloadOnChangeOf()) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(el);
            }
        }
    }

    // BFS from changedElement
    List<DataFormElement> result = new ArrayList<>();
    Set<String> visited = new LinkedHashSet<>();
    Queue<String> queue = new LinkedList<>();
    queue.add(changedElement);
    visited.add(changedElement);

    while (!queue.isEmpty()) {
        String current = queue.poll();
        for (DataFormElement dep : dependents.getOrDefault(current, List.of())) {
            if (visited.add(dep.getCode())) {
                result.add(dep);
                queue.add(dep.getCode());
            }
        }
    }
    return result;
}
```

For **initial form load** (`changedElement` is null), the method returns all elements that have
a `visibilityRule` and/or an `entityProviderRef`, evaluated in declaration order (no dependency
walk needed — everything is evaluated).

#### Cycle Detection

At config load time, `AppConfigTreeBuilder` validates the `reloadOnChangeOf` graph for cycles.
A cycle is a configuration error and is logged as a warning:

```
WARN: Circular reloadOnChangeOf dependency detected: producer → mount → producer.
      Elements involved will not be evaluated to prevent infinite loops.
```

### E3.7 Evaluation Service

The `DataFormEvaluationService` orchestrates the full evaluation:

```java
@Component
public class DataFormEvaluationService {

    @Autowired ExpressionResolver expressionResolver;
    @Autowired EntitySelectService entitySelectService;

    public Map<String, ElementState> evaluate(
            DataForm form, Long entityId,
            String changedElement, Map<String, String> formState) {

        List<DataFormElement> toEvaluate;
        if (changedElement == null) {
            // Initial load: evaluate all elements with visibility or provider
            toEvaluate = form.getElements().stream()
                .filter(el -> el.getVisibilityRule() != null
                           || el.getEntityProviderRef() != null)
                .toList();
        } else {
            toEvaluate = resolveAffectedElements(form, changedElement);
        }

        ExpressionContext ctx = buildContext(entityId, formState);
        Map<String, ElementState> result = new LinkedHashMap<>();

        for (DataFormElement el : toEvaluate) {
            ElementState state = new ElementState();

            // Visibility
            if (el.getVisibilityRule() != null) {
                Boolean visible = expressionResolver.resolveBoolean(
                    el.getVisibilityRule().getExpressionRef(), ctx);
                state.setVisible(visible != null && visible);
            } else {
                state.setVisible(true);  // no rule = always visible
            }

            // Options (for ENTITY_SELECT elements with a provider)
            if (el.getEntityProviderRef() != null) {
                state.setOptions(entitySelectService.getOptions(
                    el.getEntityProviderRef(),
                    el.getEntityRendererRef(),
                    ctx));
            }

            result.put(el.getCode(), state);
        }
        return result;
    }
}
```

### E3.8 Example: Two-Level Chain (Current Requirement)

Show `cameraLensMount2CameraProducer` only when a CameraProducer is selected.

```
expressions:
  └── "isCameraProducerSelected"
      ├── type: INJECTABLE_SNIPPET
      ├── baseClass: BOOLEAN_VALUE
      ├── expression: |
      │     Camera c = (Camera) getInjectionContext().getEditorEntity();
      │     setResult(c != null && c.getProducer() != null);
      └── description: "True when a CameraProducer has been selected on the Camera"

dataForms:
  └── "camera"
      └── elements:
          ├── "producer" (DataFormElement)
          │   ├── type: ENTITY_SELECT
          │   ├── dataBinding: "producer"
          │   ├── entityProviderRef: "allCameraProducers"
          │   ├── entityRendererRef: "producerCaption"
          │   └── mandatory: true
          └── "cameraLensMount2CameraProducer" (DataFormElement)
              ├── type: ENTITY_SELECT
              ├── dataBinding: "cameraLensMount2CameraProducer"
              ├── entityProviderRef: "mountsForCamera"
              ├── entityRendererRef: "mountMappingCaption"
              ├── reloadOnChangeOf: ["producer"]
              └── visibilityRule:
                  └── expressionRef: "isCameraProducerSelected"
```

**Dependency graph:**
```
producer ──→ cameraLensMount2CameraProducer
```

**Scenario — user selects Canon as producer:**

Request: `{ changedElement: "producer", formState: { "producer": "5" } }`

1. BFS from "producer" → affected: `[cameraLensMount2CameraProducer]`
2. Evaluate visibility: `isCameraProducerSelected` → `true`
3. Evaluate options: `cameraMountFilter` → `[EF Mount (Canon), RF Mount (Canon)]`
4. Response: `{ "cameraLensMount2CameraProducer": { visible: true, options: [...] } }`

**Scenario — initial load, new Camera (no producer yet):**

Request: `{ changedElement: null, formState: {} }`

1. Evaluate all elements with rules → includes `cameraLensMount2CameraProducer`
2. Evaluate visibility: `isCameraProducerSelected` → `false`
3. Evaluate options: `cameraMountFilter` → `[]`
4. Response: `{ "cameraLensMount2CameraProducer": { visible: false, options: [] } }`

### E3.9 Example: Three-Level Chain (Illustrative)

A hypothetical extension: after selecting a mount, show compatible lenses.

```
expressions:
  ├── "isCameraProducerSelected"
  │   ├── type: INJECTABLE_SNIPPET
  │   ├── baseClass: BOOLEAN_VALUE
  │   └── expression: |
  │         Camera c = (Camera) getInjectionContext().getEditorEntity();
  │         setResult(c != null && c.getProducer() != null);
  │
  └── "isMountSelected"
      ├── type: INJECTABLE_SNIPPET
      ├── baseClass: BOOLEAN_VALUE
      └── expression: |
            Camera c = (Camera) getInjectionContext().getEditorEntity();
            setResult(c != null && c.getCameraLensMount2CameraProducer() != null);

dataForms:
  └── "camera"
      └── elements:
          ├── "producer"
          │   ├── type: ENTITY_SELECT
          │   └── (no reloadOnChangeOf — root element)
          │
          ├── "cameraLensMount2CameraProducer"
          │   ├── type: ENTITY_SELECT
          │   ├── reloadOnChangeOf: ["producer"]
          │   ├── visibilityRule:
          │   │   └── expressionRef: "isCameraProducerSelected"
          │   └── entityProviderRef: "mountsForCamera"
          │
          └── "compatibleLenses"
              ├── type: ENTITY_SELECT
              ├── reloadOnChangeOf: ["cameraLensMount2CameraProducer"]
              ├── visibilityRule:
              │   └── expressionRef: "isMountSelected"
              └── entityProviderRef: "lensesForMount"
```

**Dependency graph:**
```
producer ──→ cameraLensMount2CameraProducer ──→ compatibleLenses
```

**Scenario — user selects Canon as producer (mount not yet selected):**

Request: `{ changedElement: "producer", formState: { "producer": "5" } }`

1. BFS from "producer":
   - Level 1: `cameraLensMount2CameraProducer`
   - Level 2: `compatibleLenses`
   - Topological order: `[cameraLensMount2CameraProducer, compatibleLenses]`
2. Evaluate `cameraLensMount2CameraProducer`:
   - visibility: `isCameraProducerSelected` → `true` (producer is set)
   - options: `cameraMountFilter` → `[EF Mount, RF Mount]`
3. Evaluate `compatibleLenses`:
   - visibility: `isMountSelected` → `false` (no mount selected yet)
   - options: `lensesForMount` → `[]`
4. Response: both elements in one payload, **one round-trip**

**Scenario — user then selects RF Mount:**

Request: `{ changedElement: "cameraLensMount2CameraProducer", formState: { "producer": "5", "cameraLensMount2CameraProducer": "12" } }`

1. BFS from "cameraLensMount2CameraProducer": `[compatibleLenses]`
2. Evaluate `compatibleLenses`:
   - visibility: `isMountSelected` → `true`
   - options: `lensesForMount` → `[RF 50mm f/1.2, RF 85mm f/1.2, ...]`
3. Response: `{ "compatibleLenses": { visible: true, options: [...] } }`

### E3.10 Composable Conditions (Future)

For complex visibility logic (A AND B, A OR B), VisibilityRule can be extended with a `children`
list and a `logicType` (AND/OR), each child referencing its own BooleanInjectable expression.
This mirrors FilterNode's composable structure. Deferred until a concrete use case requires it.

---

## Task E4 — Expression Admin Editor

**Goal:** Allow expressions to be created and edited in the AppConfigEditorView.

### E4.1 Expression Collection

The admin editor shows `expressions` as a collection under the root AppConfig node, alongside
`dataForms`, `entityProviders`, `entityRenderers`, and `viewTree`. Each expression node shows:

- **code** — identifier used by `expressionRef` references
- **type** — dropdown: CONTEXT_PATH, SPEL, STATIC, INJECTABLE_SNIPPET, INJECTABLE_CLASS
- **expression** — text input (with context-aware auto-proposals for CONTEXT_PATH type)
- **description** — optional human-readable explanation

### E4.2 Expression Reference Proposals

When editing a field that accepts an `expressionRef` (FilterNode, VisibilityRule), the admin editor
provides auto-proposals listing all available Expression codes from the tree.

---

## Task E5 — Extensibility: Alternative Expression Implementations

**Goal:** Document the extension points for future expression types beyond CONTEXT_PATH, SPEL,
and STATIC.

### E5.1 Adding a New ExpressionType

1. Add a new enum value to `ExpressionType`.
2. Implement the resolution logic in `ExpressionResolver` (server-side) and/or the client-side
   resolver.
3. Register the type in the seeder.

### E5.2 Potential Future Types

| Type | Description | Use Case |
|---|---|---|
| `JSON_LOGIC` | JSON Logic rule evaluated cross-stack | Complex boolean conditions portable between server and client |
| `JAVASCRIPT` | JS expression (client-side only) | Complex client-side transformations (client-side equivalent of INJECTABLE_SNIPPET) |
| `LOOKUP` | Reference another config node's value | Indirection within the config tree |

**Note:** `INJECTABLE_SNIPPET` and `INJECTABLE_CLASS` (see Task E7) replace the previously
proposed `GROOVY` type — they provide the same imperative power with type safety and the same
sandbox guarantees, without adding a Groovy dependency.

Each type should document its sandbox boundaries explicitly.

### E5.3 Sandbox Contract

Every expression type MUST adhere to these constraints:

1. **Read-only.** Expressions cannot mutate state — not the entity, not the database, not
   the session.
2. **Bounded context.** Expressions can only access the declared `ExpressionContext` objects.
   No access to Spring beans, the filesystem, or the network.
3. **Deterministic.** Given the same context, the same expression must return the same result.
4. **Timeout.** Server-side expressions are evaluated with a timeout (default: 100ms) to prevent
   accidental infinite loops (relevant for SPEL/GROOVY).

---

## Task E6 — Sandbox API

**Goal:** Define the concrete, engine-independent API surface that every expression type evaluates
against. The sandbox is not just a set of rules — it is a **Java interface** that expression engines
receive instead of raw objects, ensuring the contract is enforced at compile time rather than by
convention.

### E6.1 The Problem

Without a sandbox API, each expression engine accesses context objects directly:
- SpEL would call arbitrary getters on JPA entities (including lazy collections → N+1 queries).
- A future Groovy engine could call `entity.setName(...)` and mutate state.
- CONTEXT_PATH could walk into internal fields never intended for exposure.

The sandbox API provides a **projection layer** between the raw runtime objects and what
expressions can see.

### E6.2 SandboxedContext — The Core Interface

```java
/**
 * Read-only, engine-independent view of a runtime context.
 * Expression engines receive this instead of raw domain objects.
 * All property access goes through this interface — there is no way
 * to reach the underlying object.
 */
public interface SandboxedContext {

    /**
     * Resolve a dot-path to a value.
     * E.g., "id" → 4L, "producer.name" → "ZeissIkon"
     *
     * @return the resolved value, or null if the path does not exist.
     * @throws SandboxViolationException if the path targets a disallowed property.
     */
    Object resolvePath(String dotPath);

    /**
     * List the property names available at the root level.
     * Used by admin editor auto-proposals and for introspection.
     * E.g., ["id", "name", "foundationYear", "shutdownYear"] for CameraProducer
     */
    List<String> availableProperties();

    /**
     * List the property names available at a given sub-path.
     * E.g., availableProperties("producer") → ["id", "name", "foundationYear", ...]
     */
    List<String> availableProperties(String parentPath);
}
```

### E6.3 SandboxedContextFactory

Builds a `SandboxedContext` from a raw object, using JPA metamodel introspection to determine
which properties are exposed. This ensures:
- Only JPA-declared attributes are accessible (no internal fields, no `getClass()`).
- Lazy relationships are navigable via ID without triggering full collection loads.
- The allowlist is derived automatically — no manual per-entity configuration.

```java
@Component
public class SandboxedContextFactory {

    private final EntityManager entityManager;

    /**
     * Create a sandboxed view of a JPA entity.
     * Only attributes declared in the JPA metamodel are accessible.
     */
    public SandboxedContext fromEntity(Object entity) {
        return new JpaEntitySandboxedContext(entity, entityManager.getMetamodel());
    }

    /**
     * Create a sandboxed view of a flat map (e.g., formState).
     * All keys in the map are accessible; values are read-only.
     */
    public SandboxedContext fromMap(Map<String, Object> map) {
        return new MapSandboxedContext(map);
    }
}
```

### E6.4 JpaEntitySandboxedContext — Implementation

```java
/**
 * Sandboxed property access for JPA entities.
 * Uses the JPA metamodel as the allowlist — only declared attributes are accessible.
 */
class JpaEntitySandboxedContext implements SandboxedContext {

    private final Object entity;
    private final Metamodel metamodel;

    @Override
    public Object resolvePath(String dotPath) {
        String[] segments = dotPath.split("\\.");
        Object current = entity;

        for (String segment : segments) {
            if (current == null) return null;

            // Resolve Hibernate proxies to actual entity class
            Class<?> entityClass = resolveEntityClass(current);

            // Check: is this segment a declared JPA attribute?
            EntityType<?> entityType = metamodel.entity(entityClass);
            Attribute<?, ?> attr = entityType.getAttribute(segment);
            if (attr == null) {
                throw new SandboxViolationException(
                    "Property '" + segment + "' is not a declared attribute of " + entityClass.getSimpleName());
            }

            // Read via getter (same reflection as DataFormPersistenceService)
            current = readProperty(current, segment);

            // For @ManyToOne: current is now the related entity, continue walking.
            // For collections (@OneToMany, @ManyToMany): NOT navigable — return null
            // to prevent N+1 and unbounded data access.
            if (attr.isCollection()) {
                throw new SandboxViolationException(
                    "Collection property '" + segment + "' is not accessible in expressions. " +
                    "Use an EntityProvider with a filter instead.");
            }
        }

        return current;
    }

    @Override
    public List<String> availableProperties() {
        Class<?> entityClass = resolveEntityClass(entity);
        return metamodel.entity(entityClass).getSingularAttributes().stream()
                .map(Attribute::getName)
                .sorted()
                .toList();
    }

    @Override
    public List<String> availableProperties(String parentPath) {
        Object parent = resolvePath(parentPath);
        if (parent == null) return List.of();
        Class<?> parentClass = resolveEntityClass(parent);
        try {
            return metamodel.entity(parentClass).getSingularAttributes().stream()
                    .map(Attribute::getName)
                    .sorted()
                    .toList();
        } catch (IllegalArgumentException e) {
            return List.of(); // not a managed entity — no further navigation
        }
    }
}
```

### E6.5 MapSandboxedContext — Implementation

```java
/**
 * Sandboxed access for flat maps (formState, route params, session data).
 * All map keys are accessible; nested maps are navigable.
 */
class MapSandboxedContext implements SandboxedContext {

    private final Map<String, Object> map;

    @Override
    public Object resolvePath(String dotPath) {
        String[] segments = dotPath.split("\\.");
        Object current = map;

        for (String segment : segments) {
            if (current instanceof Map<?, ?> m) {
                current = m.get(segment);
            } else {
                return null; // cannot navigate further
            }
        }
        return current;
    }

    @Override
    public List<String> availableProperties() {
        return map.keySet().stream().sorted().toList();
    }

    @Override
    public List<String> availableProperties(String parentPath) {
        Object parent = resolvePath(parentPath);
        if (parent instanceof Map<?, ?> m) {
            return m.keySet().stream().map(Object::toString).sorted().toList();
        }
        return List.of();
    }
}
```

### E6.6 SandboxViolationException

```java
/**
 * Thrown when an expression attempts to access a property outside the sandbox boundary.
 * This is a RuntimeException — it signals a configuration error, not a recoverable situation.
 */
public class SandboxViolationException extends RuntimeException {
    public SandboxViolationException(String message) { super(message); }
}
```

### E6.7 Integration with ExpressionResolver

The `ExpressionResolver` (E2.4) wraps raw context objects in sandboxed contexts before evaluation:

```java
@Component
public class ExpressionResolver {

    private final AppConfigStore appConfigStore;
    private final SandboxedContextFactory sandboxFactory;

    public String resolve(String expressionCode, ExpressionContext rawContext) {
        Expression expr = appConfigStore.getAppConfig().getExpressions().get(expressionCode);

        // Wrap raw context objects in sandboxed views
        SandboxedExpressionContext sandboxed = wrapInSandbox(rawContext);

        return switch (expr.getType()) {
            case STATIC             -> expr.getExpression();
            case CONTEXT_PATH       -> resolveContextPath(expr.getExpression(), sandboxed);
            case SPEL               -> resolveSpel(expr.getExpression(), sandboxed);
            case INJECTABLE_SNIPPET -> injectableExecutor.executeSnippet(expr.getExpression(), sandboxed);
            case INJECTABLE_CLASS   -> injectableExecutor.executeClass(expr.getExpression(), sandboxed);
        };
    }

    private SandboxedExpressionContext wrapInSandbox(ExpressionContext raw) {
        SandboxedExpressionContext sandboxed = new SandboxedExpressionContext();
        for (var entry : raw.getAll().entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            SandboxedContext sc = (value instanceof Map<?, ?>)
                    ? sandboxFactory.fromMap((Map<String, Object>) value)
                    : sandboxFactory.fromEntity(value);
            sandboxed.put(name, sc);
        }
        return sandboxed;
    }

    private String resolveContextPath(String path, SandboxedExpressionContext ctx) {
        // "editor.id" → contextName="editor", propertyPath="id"
        int dot = path.indexOf('.');
        String contextName = path.substring(0, dot);
        String propertyPath = path.substring(dot + 1);
        SandboxedContext sc = ctx.get(contextName);
        Object result = sc.resolvePath(propertyPath);
        return result == null ? null : String.valueOf(result);
    }

    private String resolveSpel(String spelStr, SandboxedExpressionContext ctx) {
        // Build SimpleEvaluationContext with SandboxedContext objects as root properties.
        // SpEL calls .resolvePath() / property accessors on the sandboxed wrappers,
        // NOT on the raw entities. A custom PropertyAccessor bridges SpEL's
        // property resolution to SandboxedContext.resolvePath().
        SimpleEvaluationContext evalCtx = SimpleEvaluationContext
                .forPropertyAccessors(new SandboxedContextPropertyAccessor())
                .withRootObject(ctx.asMap())
                .build();

        org.springframework.expression.Expression parsed =
                new SpelExpressionParser().parseExpression(spelStr);
        Object result = parsed.getValue(evalCtx);
        return result == null ? null : String.valueOf(result);
    }
}
```

### E6.8 SandboxedContextPropertyAccessor (SpEL Bridge)

```java
/**
 * Bridges SpEL property access to SandboxedContext.resolvePath().
 * When SpEL evaluates "#editor.producer.name", it calls:
 *   1. read(rootMap, "editor")  → returns SandboxedContext for editor
 *   2. read(sandboxedCtx, "producer")  → delegates to resolvePath("producer")
 *   3. read(producerResult, "name")  → if result is another entity, wraps again
 *
 * This ensures SpEL can NEVER bypass the sandbox — every property access
 * goes through SandboxedContext.resolvePath().
 */
public class SandboxedContextPropertyAccessor implements PropertyAccessor {
    // ... implements canRead/read/canWrite/write ...
    // read() delegates to SandboxedContext.resolvePath() for single segments
    // write() always throws SandboxViolationException (read-only)
}
```

### E6.9 Sandbox Boundaries Summary

| Boundary | Enforced By | What It Prevents |
|---|---|---|
| **Property allowlist** | `JpaEntitySandboxedContext` + JPA Metamodel | Access to non-JPA fields (`getClass()`, internal state) |
| **No collections** | `JpaEntitySandboxedContext.resolvePath()` | Walking into `@OneToMany`/`@ManyToMany` (N+1, unbounded data) |
| **Read-only** | `SandboxedContextPropertyAccessor.write()` throws | Mutation of entity state |
| **No bean access** | `SimpleEvaluationContext` (no `BeanResolver`) | Reaching Spring beans, services, EntityManager |
| **No type access** | `SimpleEvaluationContext` (no `TypeLocator`) | `T(Runtime).exec(...)`, class instantiation |
| **No method calls** | `SimpleEvaluationContext` (no `MethodResolver`) | Arbitrary method invocation on objects |
| **Timeout** | `Future.get(100, MILLISECONDS)` in resolver | Infinite loops, expensive expressions |
| **Deterministic** | Contract (no random, no clock, no I/O in contexts) | Non-reproducible behavior |
| **Injectable isolation** | `InjectionContext` is the only injected object (E7) | No EntityManager, no beans, no I/O in injectable code |

### E6.10 Client-Side Sandbox

The client-side expression resolver (Dart/JS) implements the same `SandboxedContext` contract:

- `formState` context: built from the form's current field values (flat `Map<String, dynamic>`).
  All keys are accessible. No deeper navigation than one level (form values are flat).
- `route` context: built from URL parameters. Same flat-map access.
- No `editor` context on the client (the raw entity is server-side). If a client-side expression
  needs entity data, it must reference `formState` fields that were populated from the entity.

The client-side resolver supports `CONTEXT_PATH` and `STATIC` only — `SPEL` is server-side.
This is fine because client-side expressions are primarily for UI reactivity (visibility, enabled
state), which only needs form field values.

### E6.11 Auto-Proposals from Sandbox

The `availableProperties()` and `availableProperties(parentPath)` methods on `SandboxedContext`
serve double duty:

1. **Runtime validation:** Before resolving, optionally check that the path is valid.
2. **Admin editor proposals:** When configuring a `CONTEXT_PATH` expression, the admin editor
   calls a backend endpoint that uses `SandboxedContextFactory` to build a context for the
   relevant entity type and returns `availableProperties()` as auto-proposals.

```
GET /api/expressions/proposals/{contextName}?entityType=CAMERA_PRODUCER&prefix=producer
```

Returns: `["id", "name", "foundationYear", "shutdownYear"]` — the navigable properties
at `editor.producer.*`.

### E6.12 Extension Point: Custom SandboxedContext Implementations

For future expression types (E5.2), custom `SandboxedContext` implementations can be registered
via `SandboxedContextFactory`:

```java
@Component
public class SandboxedContextFactory {
    // ... existing fromEntity(), fromMap() ...

    /**
     * Register a custom context provider that produces its own SandboxedContext.
     * E.g., a "computed" context that derives values from multiple sources.
     */
    public void registerProvider(String contextName, 
                                  Function<ContextRequest, SandboxedContext> provider) {
        customProviders.put(contextName, provider);
    }
}
```

This allows future contexts (e.g., `"computed"`, `"permissions"`, `"i18n"`) to participate in
the expression system without modifying the core resolver.

---

## Task E7 — Injectable System (INJECTABLE_SNIPPET & INJECTABLE_CLASS)

**Goal:** Provide two imperative expression strategies where the **source code is persisted in the
config tree (database)**, compiled at runtime against base classes from the codebase. This gives
config authors full programmatic control (if/else, loops, local variables) while the base class
hierarchy enforces the sandbox contract.

### E7.1 Core Concept: Code in DB, Contract in Codebase

```
┌──────────────────────────────────────────────────────┐
│                  CODEBASE (compiled)                   │
│                                                        │
│  IInjectable (marker interface)                        │
│    ├── ScalarValueInjectable (abstract)                │
│    ├── BooleanInjectable (abstract)                    │
│    ├── ListValueInjectable (abstract)                  │
│    └── ... future base classes                         │
│                                                        │
│  InjectionContext (sandbox API interface)               │
│  EditorEntityBuilder (form-state → typed entity)       │
│  InjectableExecutor (runtime compiler + executor)      │
└──────────────────────────────────────────────────────┘
                        ▲
                        │ extends at runtime
                        │
┌──────────────────────────────────────────────────────┐
│           CONFIG TREE / DATABASE (source code)         │
│                                                        │
│  Expression "mountFilterForCurrentProducer"             │
│    type: INJECTABLE_CLASS                              │
│    baseClass: SCALAR_VALUE                             │
│    expression: |                                       │
│      @Override                                         │
│      public void execute() {                           │
│          CameraProducer p = getInjectionContext()      │
│              .getEditorEntity(CameraProducer.class);   │
│          setResult(p != null ? p.getId() : null);      │
│      }                                                 │
│                                                        │
│  Expression "currentEditorIdViaSnippet"                │
│    type: INJECTABLE_SNIPPET                            │
│    baseClass: SCALAR_VALUE                             │
│    expression: |                                       │
│      CameraProducer p = getInjectionContext()          │
│          .getEditorEntity(CameraProducer.class);       │
│      setResult(p != null ? p.getId() : null);          │
└──────────────────────────────────────────────────────┘
```

**Key principle:** The admin author writes only the implementation body. The package declaration,
imports, class declaration, and `extends` clause are generated by the framework based on the
`baseClass` field. The author never sees or touches framework plumbing.

### E7.2 Motivation: When Path-Based Access Is Not Enough

`CONTEXT_PATH` and `SPEL` are declarative — they resolve a value from a known path. But some
use cases require logic:

- **Conditional filter values:** "If the producer is discontinued, filter by legacy mounts only;
  otherwise, filter by the producer's ID."
- **Computed values:** "Concatenate producer name + foundation year as a display label."
- **Multi-step resolution:** "Look up the producer, check if it has a parent company, and use
  the parent company's ID if so."

For these, we need imperative code — but still sandboxed.

### E7.3 InjectableBaseClass Enum

Determines which base class the DB-persisted source extends, and therefore the return type
contract:

```java
public enum InjectableBaseClass {

    SCALAR_VALUE("sciens.cyrodracs.expression.ScalarValueInjectable"),
    BOOLEAN_VALUE("sciens.cyrodracs.expression.BooleanInjectable"),
    LIST_VALUE("sciens.cyrodracs.expression.ListValueInjectable"),
    FILTER("sciens.cyrodracs.expression.FilterInjectable");

    private final String fqcn;

    InjectableBaseClass(String fqcn) { this.fqcn = fqcn; }
    public String getFqcn() { return fqcn; }
}
```

### E7.4 IInjectable — Marker Interface (Codebase)

```java
/**
 * Marker interface for all injectable implementations.
 * Lives in the codebase. DB-persisted classes extend a base class
 * that implements this interface.
 */
public interface IInjectable {
    void execute();
}
```

### E7.5 Base Class Hierarchy (Codebase)

These classes live in the codebase and define the contract. DB-persisted source code extends
one of them.

#### ScalarValueInjectable

```java
/**
 * Base class for injectables that produce a single scalar value (String).
 * The DB-persisted class body overrides execute() and calls setResult().
 */
public abstract class ScalarValueInjectable implements IInjectable {

    private InjectionContext injectionContext;
    private String result;

    public abstract void execute();

    /** Called by the framework before execute(). */
    final void setInjectionContext(InjectionContext ctx) {
        this.injectionContext = ctx;
    }

    protected InjectionContext getInjectionContext() {
        return injectionContext;
    }

    protected void setResult(Object value) {
        this.result = value == null ? null : String.valueOf(value);
    }

    /** Called by the framework after execute(). */
    final String getResult() { return result; }
}
```

#### BooleanInjectable

```java
/**
 * Base class for injectables that produce a boolean value.
 * Useful for visibility rules, conditional logic, feature flags.
 */
public abstract class BooleanInjectable implements IInjectable {

    private InjectionContext injectionContext;
    private Boolean result;

    public abstract void execute();

    final void setInjectionContext(InjectionContext ctx) {
        this.injectionContext = ctx;
    }

    protected InjectionContext getInjectionContext() {
        return injectionContext;
    }

    protected void setResult(boolean value) {
        this.result = value;
    }

    final Boolean getResult() { return result; }
}
```

#### ListValueInjectable

```java
/**
 * Base class for injectables that produce a list of values.
 * Useful for multi-select options, IN-clause filter values.
 */
public abstract class ListValueInjectable implements IInjectable {

    private InjectionContext injectionContext;
    private List<String> result = new ArrayList<>();

    public abstract void execute();

    final void setInjectionContext(InjectionContext ctx) {
        this.injectionContext = ctx;
    }

    protected InjectionContext getInjectionContext() {
        return injectionContext;
    }

    protected void setResult(List<?> values) {
        this.result = values == null ? List.of()
            : values.stream().map(String::valueOf).toList();
    }

    final List<String> getResult() { return result; }
}
```

#### FilterInjectable

```java
/**
 * Base class for injectables that produce a FilterNode tree.
 * Used when an EntityProvider's filter restriction must be computed
 * dynamically at runtime rather than configured statically.
 *
 * Provides convenience methods for building FilterNode trees
 * (comparison, and, or) so that DB-persisted source code stays concise.
 */
public abstract class FilterInjectable implements IInjectable {

    private InjectionContext injectionContext;
    private FilterNode result;

    public abstract void execute();

    final void setInjectionContext(InjectionContext ctx) {
        this.injectionContext = ctx;
    }

    protected InjectionContext getInjectionContext() {
        return injectionContext;
    }

    /**
     * Set the filter result. The FilterExecutor will merge this
     * with any statically configured filter via AND.
     * Pass null to indicate "no restriction" (show all).
     */
    protected void setResult(FilterNode filterNode) {
        this.result = filterNode;
    }

    // ── Convenience builders ────────────────────────────────────────

    /**
     * Build a COMPARISON FilterNode.
     * E.g., comparison("cameraProducer.id", FilterOperator.EQUALS, 4)
     */
    protected FilterNode comparison(String field, FilterOperator operator, Object value) {
        FilterNode node = new FilterNode();
        node.setType(FilterNodeType.COMPARISON);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(value == null ? null : String.valueOf(value));
        return node;
    }

    /**
     * AND-combine multiple filter nodes.
     */
    protected FilterNode and(FilterNode... children) {
        FilterNode node = new FilterNode();
        node.setType(FilterNodeType.AND_GROUP);
        node.setChildren(List.of(children));
        return node;
    }

    /**
     * OR-combine multiple filter nodes.
     */
    protected FilterNode or(FilterNode... children) {
        FilterNode node = new FilterNode();
        node.setType(FilterNodeType.OR_GROUP);
        node.setChildren(List.of(children));
        return node;
    }

    /**
     * Build an IN-clause FilterNode.
     * E.g., in("status", List.of("ACTIVE", "PENDING"))
     */
    protected FilterNode in(String field, List<?> values) {
        FilterNode node = new FilterNode();
        node.setType(FilterNodeType.COMPARISON);
        node.setField(field);
        node.setOperator(FilterOperator.IN);
        node.setValues(values.stream().map(String::valueOf).toList());
        return node;
    }

    /**
     * Build an IS_NULL FilterNode.
     */
    protected FilterNode isNull(String field) {
        FilterNode node = new FilterNode();
        node.setType(FilterNodeType.COMPARISON);
        node.setField(field);
        node.setOperator(FilterOperator.IS_NULL);
        return node;
    }

    /**
     * Build an IS_NOT_NULL FilterNode.
     */
    protected FilterNode isNotNull(String field) {
        FilterNode node = new FilterNode();
        node.setType(FilterNodeType.COMPARISON);
        node.setField(field);
        node.setOperator(FilterOperator.IS_NOT_NULL);
        return node;
    }

    final FilterNode getResult() { return result; }
}
```

**"Create new" mode:** When the user opens a fresh form, `entityId` is null and `formState` is
empty. The framework still calls the injectable — it does NOT skip the call. The injectable
receives `null` from `getEditorEntity()` and decides what to do: return `null` (no filter →
show all), return a restrictive filter (show nothing), or any other logic. This is a deliberate
design choice: the injectable class is responsible for its own null-handling and create-new
behavior, not the framework.

### E7.6 InjectionContext — The Sandbox API for Injectables (Codebase)

`InjectionContext` is the **only** API surface available to injectable code. It provides
**typed access** to the editor entity — the real JPA entity class with full method access
and `@ManyToOne` navigation. The entity is a **transient instance** built from form state,
reflecting unsaved edits.

```java
/**
 * Read-only API available to injectable expressions.
 * This is the ONLY object injected into snippet/class execution —
 * injectables cannot access EntityManager, Spring beans, or anything else.
 */
public interface InjectionContext {

    // ── Editor entity (typed) ───────────────────────────────────────

    /**
     * The entity currently in the editor, as a typed instance.
     * Built from form state (reflects unsaved edits) with @ManyToOne
     * relationships loaded from DB for full navigation.
     *
     * The returned entity is TRANSIENT — not JPA-managed, not attached to
     * any persistence context. Calling setters has no effect on the database.
     *
     * Returns null in "create new" mode before any data exists.
     *
     * Example:
     *   CameraProducer p = getEditorEntity(CameraProducer.class);
     *   p.getName();           // "Fujifilm" (edited, not yet saved)
     *   p.getShutdownYear();   // the value currently in the form
     *
     *   Camera c = getEditorEntity(Camera.class);
     *   c.getProducer().getName();  // "Fuji" (loaded from DB via @ManyToOne)
     */
    <T> T getEditorEntity(Class<T> entityClass);

    // ── Form state context ──────────────────────────────────────────

    /**
     * Current form field values as a read-only map.
     * Keys are dataBinding paths, values are the current input (always String).
     * Available in both create and edit mode.
     */
    Map<String, String> getFormState();

    /**
     * Shorthand: get a single form field value by its dataBinding path.
     */
    String getFormValue(String dataBindingPath);

    // ── Route context ───────────────────────────────────────────────

    /**
     * Route parameters (e.g., viewNodeCode, URL query params).
     */
    Map<String, String> getRouteParams();

    String getRouteParam(String name);

    // ── Session context ─────────────────────────────────────────────

    /**
     * Session-level data (current user, locale, etc.).
     */
    Map<String, String> getSessionData();

    String getSessionValue(String key);
}
```

### E7.7 EditorEntityBuilder — Form State to Typed Entity (Codebase)

Builds a **transient** entity instance from form state values. Scalar fields come from the
form (reflecting unsaved edits). `@ManyToOne` relationships are loaded from DB by their ID
(as sent by the ENTITY_SELECT element), giving full navigation.

```java
@Component
public class EditorEntityBuilder {

    private final EntityManager entityManager;

    /**
     * Build a transient entity from form state.
     *
     * - Scalar fields: set from formState values, type-converted.
     * - @ManyToOne fields: formState contains the related entity's ID.
     *   The related entity is loaded from DB via entityManager.find(),
     *   giving full navigation (e.g., camera.getProducer().getName()).
     * - The root entity is NOT managed — not in any persistence context.
     *
     * @param entityClass  e.g., CameraProducer.class, Camera.class
     * @param entityId     the ID being edited (null for "create new")
     * @param formState    current form field values {dataBinding → value}
     */
    public <T> T buildFromFormState(Class<T> entityClass, Long entityId,
                                     Map<String, String> formState) {
        try {
            T entity = entityClass.getDeclaredConstructor().newInstance();

            if (entityId != null) {
                setProperty(entity, "id", entityId);
            }

            Metamodel metamodel = entityManager.getMetamodel();
            EntityType<?> entityType = metamodel.entity(entityClass);

            for (var attr : entityType.getSingularAttributes()) {
                String attrName = attr.getName();
                if ("id".equals(attrName)) continue;

                String formValue = formState.get(attrName);
                if (formValue == null) continue;

                if (attr.isAssociation()) {
                    // @ManyToOne — formValue is the related entity's ID.
                    // Load from DB for full navigation.
                    Class<?> relatedClass = attr.getJavaType();
                    Long relatedId = Long.valueOf(formValue);
                    Object related = entityManager.find(relatedClass, relatedId);
                    setProperty(entity, attrName, related);
                } else {
                    // Scalar — convert string to target type and set
                    Object converted = convertValue(formValue, attr.getJavaType());
                    setProperty(entity, attrName, converted);
                }
            }

            return entity;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to build entity from form state", e);
        }
    }

    // convertValue() — same logic as FilterExecutor.convertValue()
    // setProperty() — same reflection as DataFormPersistenceService
}
```

**What the builder produces (Camera example):**

```
Camera (transient, NOT JPA-managed)
  ├── id = 7                        ← from entityId param
  ├── name = "X-T5"                 ← from formState (may be edited)
  ├── releaseYear = 2022-11         ← from formState, type-converted
  └── producer = CameraProducer     ← loaded from DB by ID "4"
        ├── id = 4
        ├── name = "Fuji"           ← DB state (authoritative for related entities)
        ├── foundationYear = 1934-01
        └── shutdownYear = null
```

**Safety properties:**

| Concern | Why it's safe |
|---|---|
| Root entity mutations | Transient — not in persistence context. `setName()` changes nothing in DB. |
| Related entity mutations | Managed by Hibernate, but no `EntityManager` exposed to flush/commit. |
| Lazy loading on @ManyToOne | Works normally within request-scoped transaction. |
| N+1 queries | Bounded by 100ms timeout. In practice, 1-2 levels deep. |
| Entities are pure POJOs | Data containers, no business logic, no side effects. |

### E7.8 INJECTABLE_CLASS — Full Class Body in DB

The `expression` field stores the **class body** — everything between the outer braces. The
framework generates the surrounding class declaration, package, imports, and `extends` clause
based on the `baseClass` enum.

**What the admin writes (stored in DB):**
```java
@Override
public void execute() {
    CameraProducer producer = getInjectionContext().getEditorEntity(CameraProducer.class);
    if (producer == null) {
        setResult(null);
        return;
    }
    setResult(producer.getId());
}
```

**What the framework generates and compiles:**
```java
package sciens.cyrodracs.expression.generated;

import sciens.cyrodracs.expression.*;
import sciens.cyrodracs.appconfig.*;
import sciens.cyrodracs.*;            // wildcard — all entity packages available
import java.util.*;

public class Injectable_mountFilterForCurrentProducer
        extends ScalarValueInjectable {

    // ── admin-authored body inserted here ──
    @Override
    public void execute() {
        CameraProducer producer = getInjectionContext().getEditorEntity(CameraProducer.class);
        if (producer == null) {
            setResult(null);
            return;
        }
        setResult(producer.getId());
    }
    // ── end of admin-authored body ──
}
```

This means the admin can also define **helper methods and fields** in the class body,
with full typed access and `@ManyToOne` navigation:

```java
private boolean isDiscontinued(CameraProducer producer) {
    return producer.getShutdownYear() != null;   // typed method, no string literals
}

@Override
public void execute() {
    CameraProducer producer = getInjectionContext().getEditorEntity(CameraProducer.class);
    if (producer == null) {
        setResult(null);
        return;
    }
    if (isDiscontinued(producer)) {
        setResult("LEGACY_" + producer.getId());
    } else {
        setResult(producer.getId());
    }
}
```

### E7.9 INJECTABLE_SNIPPET — Method Body in DB

For simpler cases, the `expression` field stores only the **method body** (the statements inside
`execute()`). The framework generates the full class + method wrapper.

**What the admin writes (stored in DB):**
```java
CameraProducer p = getInjectionContext().getEditorEntity(CameraProducer.class);
setResult(p != null ? p.getId() : null);
```

**What the framework generates and compiles:**
```java
package sciens.cyrodracs.expression.generated;

import sciens.cyrodracs.expression.*;
import java.util.*;

public class Snippet_currentEditorIdViaSnippet
        extends ScalarValueInjectable {

    @Override
    public void execute() {
        // ── admin-authored snippet inserted here ──
        Object __snippetResult = (/* snippet body */);
        setResult(__snippetResult);
        // ── or, if snippet contains explicit setResult() calls: ──
        // CameraProducer p = getInjectionContext().getEditorEntity(CameraProducer.class);
        // setResult(p != null ? p.getId() : null);
    }
}
```

**Snippet vs. Class — the difference:**

| | INJECTABLE_SNIPPET | INJECTABLE_CLASS |
|---|---|---|
| What's in the DB | Method body only | Full class body (methods + fields) |
| Helper methods | No | Yes |
| Fields | No | Yes |
| Typical size | 1–5 lines | 5+ lines |
| Framework wraps | Class + method declaration | Class declaration only |

### E7.10 Concrete Example: BooleanInjectable for Visibility

A `BooleanInjectable` used by a VisibilityRule to determine if the shutdownYear field should
be shown:

**Config tree:**
```
expressions:
  └── "isProducerDiscontinued" (Expression)
      ├── type: INJECTABLE_SNIPPET
      ├── baseClass: BOOLEAN_VALUE
      ├── expression: |
      │       CameraProducer p = getInjectionContext().getEditorEntity(CameraProducer.class);
      │       setResult(p != null && p.getShutdownYear() != null);
      └── description: "True if the producer has a shutdown year set"
```

**Generated class:**
```java
public class Snippet_isProducerDiscontinued extends BooleanInjectable {
    @Override
    public void execute() {
        CameraProducer p = getInjectionContext().getEditorEntity(CameraProducer.class);
        setResult(p != null && p.getShutdownYear() != null);
    }
}
```

### E7.11 InjectableExecutor — The Runtime Engine

```java
@Component
public class InjectableExecutor {

    private final EditorEntityBuilder editorEntityBuilder;

    /**
     * Cache: expression code → compiled Class.
     * Invalidated when AppConfig is reloaded (source code changed in DB).
     */
    private final Map<String, Class<?>> compilationCache = new ConcurrentHashMap<>();

    /**
     * Execute an INJECTABLE_CLASS expression, returning a String result.
     */
    public String executeClass(Expression expr, ExpressionContext rawContext) {
        Class<?> compiled = getOrCompile(expr, true);
        return executeAndExtractString(compiled, rawContext);
    }

    /**
     * Execute an INJECTABLE_SNIPPET expression, returning a String result.
     */
    public String executeSnippet(Expression expr, ExpressionContext rawContext) {
        Class<?> compiled = getOrCompile(expr, false);
        return executeAndExtractString(compiled, rawContext);
    }

    /**
     * Execute a FILTER injectable, returning a FilterNode tree.
     * Used by ExpressionResolver.resolveFilter().
     */
    public FilterNode executeFilter(Expression expr, ExpressionContext rawContext) {
        boolean isClassBody = (expr.getType() == ExpressionType.INJECTABLE_CLASS);
        Class<?> compiled = getOrCompile(expr, isClassBody);
        IInjectable injectable = instantiateAndExecute(compiled, rawContext);
        if (injectable instanceof FilterInjectable f) {
            return f.getResult();
        }
        throw new IllegalStateException(
            "Expression '" + expr.getCode() + "' is not a FilterInjectable");
    }

    private Class<?> getOrCompile(Expression expr, boolean isClassBody) {
        return compilationCache.computeIfAbsent(expr.getCode(), code ->
            isClassBody
                ? compileClassBody(code, expr.getBaseClass(), expr.getExpression())
                : compileSnippetBody(code, expr.getBaseClass(), expr.getExpression())
        );
    }

    /**
     * Generate full Java source for INJECTABLE_CLASS, compile via Janino.
     */
    private Class<?> compileClassBody(String code, InjectableBaseClass baseClass,
                                       String classBody) {
        String className = "Injectable_" + sanitize(code);
        String source = """
            package sciens.cyrodracs.expression.generated;

            import sciens.cyrodracs.expression.*;
            import sciens.cyrodracs.appconfig.*;   // FilterNode, FilterOperator, etc.
            import sciens.cyrodracs.*;              // all entity packages (wildcard)
            import java.util.*;
            import java.time.*;

            public class %s extends %s {
                %s
            }
            """.formatted(className, baseClass.getFqcn(), classBody);

        return compileSource(className, source);
    }

    /**
     * Generate full Java source for INJECTABLE_SNIPPET, compile via Janino.
     */
    private Class<?> compileSnippetBody(String code, InjectableBaseClass baseClass,
                                         String methodBody) {
        String className = "Snippet_" + sanitize(code);
        String source = """
            package sciens.cyrodracs.expression.generated;

            import sciens.cyrodracs.expression.*;
            import sciens.cyrodracs.appconfig.*;
            import sciens.cyrodracs.*;
            import java.util.*;
            import java.time.*;

            public class %s extends %s {
                @Override
                public void execute() {
                    %s
                }
            }
            """.formatted(className, baseClass.getFqcn(), methodBody);

        return compileSource(className, source);
    }

    private Class<?> compileSource(String className, String source) {
        // Use Janino SimpleCompiler to compile from source string.
        // The parent classloader provides access to base classes
        // (ScalarValueInjectable, InjectionContext, etc.) and entity classes
        // (sciens.cyrodracs.camera.*) but NOT to Spring beans, EntityManager,
        // or java.io/java.net.
        // ...
    }

    /**
     * Instantiate, inject context, execute with timeout, return the injectable.
     * Callers extract the typed result (String, FilterNode, etc.).
     */
    private IInjectable instantiateAndExecute(Class<?> compiled, ExpressionContext rawContext) {
        try {
            IInjectable injectable = (IInjectable) compiled
                .getDeclaredConstructor().newInstance();

            InjectionContext ctx = buildInjectionContext(rawContext);

            // Inject context via base class's package-private setter
            if (injectable instanceof ScalarValueInjectable s)  s.setInjectionContext(ctx);
            else if (injectable instanceof BooleanInjectable b) b.setInjectionContext(ctx);
            else if (injectable instanceof ListValueInjectable l) l.setInjectionContext(ctx);
            else if (injectable instanceof FilterInjectable f)  f.setInjectionContext(ctx);

            // Execute with timeout
            executeWithTimeout(injectable);
            return injectable;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to execute injectable: " + compiled.getName(), e);
        }
    }

    /** Convenience: execute and extract String result for scalar/boolean/list types. */
    private String executeAndExtractString(Class<?> compiled, ExpressionContext rawContext) {
        IInjectable injectable = instantiateAndExecute(compiled, rawContext);
        if (injectable instanceof ScalarValueInjectable s) return s.getResult();
        if (injectable instanceof BooleanInjectable b)     return String.valueOf(b.getResult());
        if (injectable instanceof ListValueInjectable l)   return String.join(",", l.getResult());
        throw new IllegalStateException("Cannot extract String from " + injectable.getClass());
    }

    private InjectionContext buildInjectionContext(ExpressionContext rawContext) {
        // The editor entity is already a transient typed instance,
        // built by EditorEntityBuilder from form state before reaching here.
        Object editorEntity = rawContext.get("editor");  // e.g., CameraProducer instance

        Map<String, String> formState = /* extract from rawContext */ Map.of();
        Map<String, String> routeParams = /* extract from rawContext */ Map.of();
        Map<String, String> sessionData = /* extract from rawContext */ Map.of();

        return new InjectionContextImpl(editorEntity, formState, routeParams, sessionData);
    }

    private void executeWithTimeout(IInjectable injectable) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(injectable::execute);
            future.get(100, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new SandboxViolationException("Injectable execution exceeded 100ms timeout");
        } catch (ExecutionException e) {
            throw new RuntimeException("Injectable execution failed", e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    /** Invalidate cache when AppConfig is reloaded (source changed in DB). */
    public void invalidateCache() {
        compilationCache.clear();
    }

    /** Invalidate a single expression (e.g., after editing in admin UI). */
    public void invalidateCache(String expressionCode) {
        compilationCache.remove(expressionCode);
    }
}
```

### E7.12 Compilation: Janino Integration

```java
private Class<?> compileSource(String className, String source) {
    try {
        org.codehaus.janino.SimpleCompiler compiler = new org.codehaus.janino.SimpleCompiler();

        // Parent classloader: provides access to base classes + sandbox API only.
        // A restricted classloader that blocks java.io, java.net, java.lang.reflect,
        // Spring framework classes, and JPA classes.
        compiler.setParentClassLoader(new SandboxedClassLoader(getClass().getClassLoader()));

        compiler.cook(source);
        return compiler.getClassLoader().loadClass(
            "sciens.cyrodracs.expression.generated." + className);
    } catch (Exception e) {
        throw new InjectableCompilationException(
            "Failed to compile injectable '" + className + "': " + e.getMessage(), e);
    }
}
```

**SandboxedClassLoader** blocks loading of dangerous classes but allows entity classes:

```java
class SandboxedClassLoader extends ClassLoader {
    private static final Set<String> BLOCKED_PREFIXES = Set.of(
        "java.io.",              // File I/O
        "java.net.",             // Network access
        "java.lang.reflect.",    // Reflection
        "java.lang.Runtime",     // Process execution
        "java.lang.ProcessBuilder",
        "javax.persistence.",    // Direct JPA access (annotations are fine on entities)
        "jakarta.persistence.",  //   but EntityManager, Query, etc. are blocked
        "org.springframework.",  // Spring framework
        "org.hibernate."         // Hibernate internals
    );

    // Entity classes are explicitly ALLOWED — they are pure POJOs
    // sciens.cyrodracs.camera.* (CameraProducer, Camera, etc.) pass through

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        for (String blocked : BLOCKED_PREFIXES) {
            if (name.startsWith(blocked)) {
                throw new SandboxViolationException(
                    "Injectable code cannot access " + name);
            }
        }
        return super.loadClass(name, resolve);
    }
}
```

### E7.13 Security: What Injectables CAN and CANNOT Do

| Allowed | How |
|---|---|
| Typed entity access | `getInjectionContext().getEditorEntity(CameraProducer.class)` |
| Call entity getters | `producer.getName()`, `producer.getShutdownYear()` — real methods, IDE autocomplete |
| @ManyToOne navigation | `camera.getProducer().getName()` — related entities loaded from DB |
| Read form field values | `getInjectionContext().getFormValue("name")` |
| Read route/session params | `getInjectionContext().getRouteParam(...)` |
| if/else, switch, loops | Standard Java control flow |
| Local variables, fields (CLASS) | Standard Java |
| Helper methods (CLASS only) | Private methods in the class body |
| String manipulation | `String.format(...)`, concatenation, etc. |
| Math operations | Standard Java arithmetic |
| Build FilterNode trees (FILTER only) | Via convenience methods: `comparison()`, `and()`, `or()`, `in()`, `isNull()` |
| Access FilterOperator enum | Imported in generated source |
| java.util collections | `List.of(...)`, `Map.of(...)`, etc. |

| **Blocked** | **How** |
|---|---|
| Entity mutation (persisted) | Editor entity is transient — setters change nothing in DB |
| Database access | `SandboxedClassLoader` blocks `jakarta.persistence.*` |
| Spring bean access | `SandboxedClassLoader` blocks `org.springframework.*` |
| File I/O | `SandboxedClassLoader` blocks `java.io.*` |
| Network access | `SandboxedClassLoader` blocks `java.net.*` |
| Reflection | `SandboxedClassLoader` blocks `java.lang.reflect.*` |
| Process execution | `SandboxedClassLoader` blocks `Runtime`, `ProcessBuilder` |
| Hibernate internals | `SandboxedClassLoader` blocks `org.hibernate.*` |
| @OneToMany / @ManyToMany | Root entity is transient — collections not initialized. Related entities may have them but bounded by timeout. |
| Infinite loops | 100ms timeout kills the thread |

### E7.14 Admin Editor: Source Code Editing

When editing an expression with type `INJECTABLE_SNIPPET` or `INJECTABLE_CLASS`, the admin editor
shows:

1. **baseClass** dropdown: `SCALAR_VALUE`, `BOOLEAN_VALUE`, `LIST_VALUE`, `FILTER`
2. **expression** field: a **multi-line code editor** (monospace font, syntax highlighting if
   available) for entering the source code.
3. **Compile check** button: calls a backend endpoint that attempts to compile the source without
   executing it, returning success or compilation errors with line numbers.
4. **Entity type cross-check**: when the expression is referenced by a config node (e.g.,
   `filterInjectableRef` on an EntityProvider that belongs to a DataForm), the compile-check
   traces the reference chain to determine the expected entity type and warns if the
   `getEditorEntity(X.class)` call in the source doesn't match.

```
POST /api/expressions/compile-check
{
    "type": "INJECTABLE_CLASS",
    "baseClass": "FILTER",
    "expression": "@Override\npublic void execute() {\n  ...\n}",
    "usageContext": {
        "dataFormCode": "cameraProducerForm",
        "entityType": "CAMERA_PRODUCER"
    }
}
```

Response (success):
```json
{ "valid": true }
```

Response (compilation error):
```json
{
    "valid": false,
    "errors": [
        { "line": 3, "message": "cannot find symbol: method setResul(...)" }
    ]
}
```

Response (entity type mismatch warning):
```json
{
    "valid": true,
    "warnings": [
        { "message": "Source calls getEditorEntity(Camera.class) but this expression is used in dataForm 'cameraProducerForm' which binds to CAMERA_PRODUCER. Expected getEditorEntity(CameraProducer.class)." }
    ]
}
```

**How the cross-check works:**
1. The compile-check endpoint receives the optional `usageContext` (the DataForm and entity type
   where this expression is used).
2. It compiles the source (catching syntax/type errors).
3. If compilation succeeds AND `usageContext` is provided, it scans the compiled source for
   `getEditorEntity(*.class)` calls and compares the class argument against the entity type
   from the DataForm.
4. A mismatch produces a **warning** (not an error) — the expression might be intentionally
   generic or used across multiple DataForms.
```

### E7.15 Cache Lifecycle

```
App startup
  └── compilationCache is empty

First expression evaluation
  └── source compiled from DB → cached by expression code

AppConfig reload (source changed in admin editor)
  └── invalidateCache() clears all compiled classes
  └── next evaluation recompiles from updated DB source

Single expression edited
  └── invalidateCache(expressionCode) clears that entry
  └── compile-check endpoint validates before save
```

### E7.16 Choosing Between Expression Types — Decision Guide

```
Is the value a literal constant?
  └── YES → STATIC

Is the value a single property of a context object?
  └── YES → CONTEXT_PATH  (e.g., "editor.id")

Do you need string manipulation or safe-navigation?
  └── YES → SPEL  (e.g., "#editor.producer?.name ?: 'Unknown'")

Do you need if/else or loops, but the logic is short (< 5 lines)?
  └── YES → INJECTABLE_SNIPPET  (method body in DB)

Do you need helper methods, fields, or complex multi-method logic?
  └── YES → INJECTABLE_CLASS  (class body in DB)

Do you need a boolean result (visibility, conditions)?
  └── Use baseClass: BOOLEAN_VALUE with either SNIPPET or CLASS

Do you need a list result (IN-clause, multi-select)?
  └── Use baseClass: LIST_VALUE with either SNIPPET or CLASS

Do you need to produce an entire filter tree dynamically?
  └── Use baseClass: FILTER with either SNIPPET or CLASS
      Referenced via EntityProvider.filterInjectableRef
```

### E7.17 Unit Testing

DB-persisted injectables can be tested by compiling the source in a test, same as the framework
does at runtime:

```java
@Test
void filterInjectable_producesCorrectFilter_forExistingProducer() {
    // Arrange — build a real transient entity (same as EditorEntityBuilder would)
    CameraProducer producer = new CameraProducer();
    producer.setId(4L);
    producer.setName("Fuji");
    producer.setFoundationYear(YearMonth.of(1934, 1));

    InjectionContext ctx = mock(InjectionContext.class);
    when(ctx.getEditorEntity(CameraProducer.class)).thenReturn(producer);

    // Compile the DB-persisted source (same as InjectableExecutor would)
    String source = """
        @Override
        public void execute() {
            CameraProducer producer = getInjectionContext()
                .getEditorEntity(CameraProducer.class);
            if (producer == null) {
                setResult(null);
                return;
            }
            setResult(
                comparison("cameraProducer.id", FilterOperator.EQUALS, producer.getId())
            );
        }
        """;
    FilterInjectable injectable = compileAndInstantiate(
        "TestFilter", InjectableBaseClass.FILTER, source);
    injectable.setInjectionContext(ctx);

    // Act
    injectable.execute();

    // Assert
    FilterNode result = injectable.getResult();
    assertNotNull(result);
    assertEquals(FilterNodeType.COMPARISON, result.getType());
    assertEquals("cameraProducer.id", result.getField());
    assertEquals(FilterOperator.EQUALS, result.getOperator());
    assertEquals("4", result.getValue());
}

@Test
void filterInjectable_returnsNull_forCreateNewMode() {
    InjectionContext ctx = mock(InjectionContext.class);
    when(ctx.getEditorEntity(CameraProducer.class)).thenReturn(null);

    FilterInjectable injectable = compileAndInstantiate(...);
    injectable.setInjectionContext(ctx);

    injectable.execute();

    assertNull(injectable.getResult());  // no restriction → show all
}

@Test
void filterInjectable_usesEditedFormState_notPersistedValues() {
    // The user changed shutdownYear in the editor but hasn't saved yet.
    // The injectable should see the edited value.
    CameraProducer producer = new CameraProducer();
    producer.setId(4L);
    producer.setName("Fuji");
    producer.setShutdownYear(YearMonth.of(1972, 1));  // edited in form, not yet persisted

    InjectionContext ctx = mock(InjectionContext.class);
    when(ctx.getEditorEntity(CameraProducer.class)).thenReturn(producer);

    String source = """
        private boolean isDiscontinued(CameraProducer p) {
            return p.getShutdownYear() != null;
        }

        @Override
        public void execute() {
            CameraProducer p = getInjectionContext().getEditorEntity(CameraProducer.class);
            if (p == null) { setResult(null); return; }

            // This sees the EDITED shutdownYear (1972-01), not the persisted null
            if (isDiscontinued(p)) {
                setResult(comparison("cameraProducer.id", FilterOperator.EQUALS, p.getId()));
            } else {
                setResult(null);
            }
        }
        """;
    FilterInjectable injectable = compileAndInstantiate(
        "TestSmartFilter", InjectableBaseClass.FILTER, source);
    injectable.setInjectionContext(ctx);

    injectable.execute();

    // Injectable saw the edited shutdownYear → isDiscontinued returned true
    assertNotNull(injectable.getResult());
    assertEquals("4", injectable.getResult().getValue());
}

@Test
void injectable_canNavigateManyToOne() {
    // Camera with a loaded producer — full @ManyToOne navigation
    CameraProducer producer = new CameraProducer();
    producer.setId(4L);
    producer.setName("Fuji");

    Camera camera = new Camera();
    camera.setId(7L);
    camera.setName("X-T5");
    camera.setProducer(producer);  // @ManyToOne populated

    InjectionContext ctx = mock(InjectionContext.class);
    when(ctx.getEditorEntity(Camera.class)).thenReturn(camera);

    String source = """
        @Override
        public void execute() {
            Camera camera = getInjectionContext().getEditorEntity(Camera.class);
            // Navigate @ManyToOne — typed, no string literals
            String producerName = camera.getProducer().getName();
            setResult(producerName);
        }
        """;
    ScalarValueInjectable injectable = compileAndInstantiate(
        "TestNavigation", InjectableBaseClass.SCALAR_VALUE, source);
    injectable.setInjectionContext(ctx);

    injectable.execute();

    assertEquals("Fuji", injectable.getResult());
}
```

---

## Task E8 — Injectable Code Editor

**Goal:** Replace the plain textarea in E7.14 with a proper code editor that provides Java
syntax highlighting, line numbers, code folding, error markers, and server-backed autocomplete
for the DB-persisted injectable source code.

### E8 Implementation Status

| Component | Status |
|---|---|
| `re_editor` + `re_highlight` dependencies | Done |
| Backend: `CompileCheckService` (compile-only via Janino, line mapping) | Done |
| Backend: `POST /api/expressions/compile-check` endpoint | Done |
| Frontend: `ExpressionEditorDialog` modal with `CodeEditor` | Done |
| Frontend: Java syntax highlighting (`langJava` + `atomOneLightTheme`) | Done |
| Frontend: Line numbers + code folding | Done |
| Frontend: Client-side autocomplete (injectable API, FilterOperator) | Done |
| Frontend: Autocomplete popup UI | Done |
| Frontend: Compile-check on demand ("Check" button) | Done |
| Frontend: Compile-check on save (block save on errors) | Done |
| Frontend: Error/warning panel in dialog | Done |
| Frontend: Gutter error icons (`_ErrorGutter` via `indicatorBuilder`) | Done |
| Frontend: Imports section (read-only) in dialog | Done |
| Frontend: "Edit Source" button + scrollable preview on detail panel | Done |
| Frontend: Conditional rendering (INJECTABLE types only) | Done |
| Frontend: Debounced compile-check while typing (500ms) | Done |
| Frontend: Server-backed type resolution + method proposals (E8.4.4) | Done |
| Frontend: Custom prompts builder with variable chain resolution | Done |
| Backend: `ExpressionTypeResolver` (JavaParser AST + reflection) | Done |
| Frontend: Inline error underlines (`spanBuilder`) | Pending (Phase 2) |

### E8.1 Editor Choice: `re_editor`

**Decision:** Use `re_editor` (pub.dev, by Reqable, MIT license).

| Criterion | `re_editor` | Alternatives considered |
|---|---|---|
| **Platform** | All (Web, Android, iOS, Desktop) — pure Flutter widget | Monaco: web-only (`HtmlElementView`). Rules out Android. |
| **Java highlighting** | Yes, via `re_highlight` (Dart port of highlight.js 11.9, `langJava`) | `flutter_code_editor`: also yes, via `highlight.dart` |
| **Autocomplete** | Built-in `CodeAutocomplete` wrapper with `CodePrompt` model | `flutter_code_editor`: none (DIY). Monaco: native but web-only. |
| **Code folding** | Yes, `DefaultCodeChunkAnalyzer` (brace-based) | `flutter_code_editor`: yes |
| **Line numbers** | Yes, via `indicatorBuilder` + `DefaultCodeLineNumber` | `flutter_code_editor`: yes (built-in) |
| **Error markers** | No built-in API. Workaround via `spanBuilder` (custom `TextSpan` per line) and `indicatorBuilder` (gutter icons). | Monaco: native `setModelMarkers`. |
| **Bundle size** | Pure Dart, no platform assets | Monaco: ~2-3MB JS/CSS assets |

**Why `re_editor` over Monaco:** The app may run on Android. Monaco requires `HtmlElementView`
(web-only). `re_editor` is a pure Flutter widget — same code runs everywhere. The missing
built-in error marker API is solvable via `spanBuilder` and `indicatorBuilder`.

**Why `re_editor` over `flutter_code_editor`:** `re_editor` has a built-in autocomplete
framework (`CodeAutocomplete`, `CodePrompt` types) that can be wired to the backend proposals
endpoint. `flutter_code_editor` has no autocomplete support at all.

### E8.2 Dependencies

```yaml
# pubspec.yaml
dependencies:
  re_editor: ^0.8.0
  re_highlight: ^0.0.3   # transitive, but explicit for langJava import
```

### E8.3 Widget Integration

The current `_expressionBodyField()` in `app_config_detail_panel.dart` is a plain
`TextFormField` with `maxLines: 12` and monospace font. It is replaced with a `CodeEditor`
widget wrapped in `CodeAutocomplete`.

#### E8.3.1 Basic Editor Setup

```dart
final _codeController = CodeLineEditingController.fromText(sourceCode);

CodeEditor(
  controller: _codeController,
  style: CodeEditorStyle(
    fontSize: 13,
    fontFamily: 'monospace',
    codeTheme: CodeHighlightTheme(
      languages: {'java': CodeHighlightThemeMode(mode: langJava)},
      theme: atomOneLightTheme,
    ),
  ),
  indicatorBuilder: (context, editingController, chunkController, notifier) {
    return Row(children: [
      DefaultCodeLineNumber(
        controller: editingController,
        notifier: notifier,
      ),
      DefaultCodeChunkIndicator(
        width: 20,
        controller: chunkController,
        notifier: notifier,
      ),
    ]);
  },
  chunkAnalyzer: DefaultCodeChunkAnalyzer(),  // brace-based folding
)
```

#### E8.3.2 Modal Dialog

The code editor opens in a modal dialog, triggered by an "Edit Source" button on the Expression
detail panel. The detail panel retains the expression type dropdown, baseClass dropdown, and
description field. Only the code editor goes into the dialog.

```
┌─────────────────────────────────────────────────────────────┐
│  producerMountFilter : FilterInjectable                 [X] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  CodeEditor (editable, user's code only)                    │
│  ─ Java highlighting, line numbers, code folding            │
│  ─ Autocomplete for injectable API                          │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│  Error panel (compile errors/warnings, if any)              │
├─────────────────────────────────────────────────────────────┤
│  Imports (read-only, scrollable):                           │
│  import sciens.cyrodracs.expression.*;                      │
│  import sciens.cyrodracs.appconfig.*;                       │
│  import sciens.cyrodracs.camera.*;                          │
│  import java.util.*;                                        │
│  import java.time.*;                                        │
├─────────────────────────────────────────────────────────────┤
│  [Check]                                   [Cancel] [Save]  │
└─────────────────────────────────────────────────────────────┘
```

**Dialog structure:**
- **Title bar:** `expressionCode : baseClassName` (e.g., `producerMountFilter : FilterInjectable`).
  Shows the expression name and what base class is being extended — the user sees at a glance
  what they are coding for. Close button on the right.
- **Code editor:** fills the main dialog body. Contains only the user's editable code (the
  method body for SNIPPET, or full method(s) for CLASS). No scaffold mixed in.
- **Error panel:** shown between editor and imports when compile errors exist. Lists line
  number + severity icon + message.
- **Imports section:** read-only scrollable text area at the bottom showing the import
  statements that the compiler adds. Informational — lets the user know which classes are
  available without needing to memorize packages.
- **Footer:** "Check" button (compile-check), "Cancel", and "Save".
- **Dialog size:** ~80% of viewport width, ~80% of viewport height.
- **Styling:** `BorderRadius.zero`, `AppTheme.panelBorder`, `AppTheme.panelHeaderBackground`
  on title bar (consistent with panel pattern from `frontendStyling.md`).

#### E8.3.3 Imports Section

The imports displayed at the bottom match exactly what `InjectableExecutor` adds at compile
time:

```java
import sciens.cyrodracs.expression.*;
import sciens.cyrodracs.appconfig.*;
import sciens.cyrodracs.camera.*;
import java.util.*;
import java.time.*;
```

These are the same for all `baseClass` values. The import list includes all domain entity
packages so the user can reference any entity class (e.g., `CameraProducer`, `FilterOperator`)
without needing to know the exact import.

The imports section is rendered as a read-only `Text` widget with monospace font, inside a
`Container` with `AppTheme.panelBorder` top border and light background. It is always visible
(not collapsed) since the list is short (5 lines).

#### E8.3.4 INJECTABLE_SNIPPET vs INJECTABLE_CLASS

The editor content differs by expression type:

| Type | What the editor shows | What DB stores |
|---|---|---|
| `INJECTABLE_CLASS` | Full method(s) including `@Override`, signatures, braces | Same as editor content |
| `INJECTABLE_SNIPPET` | Method body only (statements inside `execute()`) | Same as editor content |

For `INJECTABLE_CLASS`, a typical editor content:
```java
@Override
public void execute() {
    CameraProducer p = (CameraProducer) getInjectionContext().getEditorEntity();
    if (p == null) { setResult(null); return; }
    setResult(
        comparison("cameraProducer.id", FilterOperator.EQUALS, p.getId())
    );
}
```

For `INJECTABLE_SNIPPET`, a typical editor content:
```java
CameraProducer p = (CameraProducer) getInjectionContext().getEditorEntity();
if (p == null) { setResult(null); return; }
setResult(comparison("cameraProducer.id", FilterOperator.EQUALS, p.getId()));
```

The wrapping (package declaration, imports, class declaration, and for SNIPPET the
`@Override public void execute() { ... }`) is added by `InjectableExecutor` at compile time
and by the compile-check endpoint when validating. The editor never shows the wrapping.

#### E8.3.5 Compile-Check Line Number Mapping

The compile-check endpoint compiles the full wrapped source (scaffold + user code). Error
line numbers reference the full source. The frontend subtracts the scaffold line count to
map errors to editor line numbers:

- **INJECTABLE_CLASS scaffold:** package (1) + blank (1) + imports (5) + blank (1) +
  class declaration (1) = **9 lines** before user code.
- **INJECTABLE_SNIPPET scaffold:** same 9 lines + `@Override` (1) + `public void execute() {` (1)
  = **11 lines** before user code.

```dart
final editorLine = compileErrorLine - scaffoldLineCount;
```

#### E8.3.6 Content Sync and Save Flow

The modal dialog receives the current expression body text from the detail panel's controller.
On "Save":
1. The dialog updates the in-memory text (`_expressionBodyCtrl.text = editorContent`).
2. The dialog closes.
3. The user clicks the detail panel's existing Save button to persist.

This keeps the save flow consistent with other fields (type, baseClass, description). "Cancel"
discards any edits and closes without updating the controller.

### E8.4 Autocomplete

`re_editor` provides autocomplete via the `CodeAutocomplete` wrapper widget and
`CodeAutocompletePromptsBuilder` interface.

#### E8.4.1 Widget Structure

```dart
CodeAutocomplete(
  viewBuilder: (context, notifier, onSelected) {
    // Build the dropdown popup UI showing suggestions
    // notifier.value contains the current CodeAutocompleteEditingValue
    // onSelected(CodeAutocompleteResult) inserts the selected completion
  },
  promptsBuilder: _InjectablePromptsBuilder(
    baseClass: expression.baseClass,
    entityType: resolvedEntityType,
  ),
  child: CodeEditor( ... ),
)
```

#### E8.4.2 Prompt Types

`re_editor` provides typed prompt classes extending `CodePrompt`:

| Class | Use | Fields |
|---|---|---|
| `CodeKeywordPrompt` | Java keywords | `word` |
| `CodeFieldPrompt` | Entity getters, context accessors | `word`, `type` |
| `CodeFunctionPrompt` | Methods with params | `word`, `type`, `parameters`, `optionalParameters` |

#### E8.4.3 Client-Side Prompts (Phase 1)

Static prompts derived from the expression's `baseClass` and known API:

```dart
// Always available (from InjectionContext):
CodeFunctionPrompt(word: 'getInjectionContext', type: 'InjectionContext', parameters: {}),

// From FilterInjectable base class (when baseClass == FILTER):
CodeFunctionPrompt(word: 'comparison', type: 'FilterNode',
    parameters: {'field': 'String', 'operator': 'FilterOperator', 'value': 'Object'}),
CodeFunctionPrompt(word: 'and', type: 'FilterNode',
    parameters: {'children': 'FilterNode...'}),
CodeFunctionPrompt(word: 'or', type: 'FilterNode',
    parameters: {'children': 'FilterNode...'}),
CodeFunctionPrompt(word: 'in', type: 'FilterNode',
    parameters: {'field': 'String', 'values': 'List<Object>'}),
CodeFunctionPrompt(word: 'setResult', type: 'void',
    parameters: {'result': 'FilterNode'}),

// FilterOperator enum constants:
CodeFieldPrompt(word: 'EQUALS', type: 'FilterOperator'),
CodeFieldPrompt(word: 'NOT_EQUALS', type: 'FilterOperator'),
// ... etc.
```

**Related prompts** (triggered after `.`): configured via the `relatedPrompts` map on
`DefaultCodeAutocompletePromptsBuilder`, keyed by the preceding identifier:

```dart
relatedPrompts: {
  'getInjectionContext()': [
    CodeFunctionPrompt(word: 'getEditorEntity', type: 'Object', parameters: {'clazz': 'Class<T>'}),
    CodeFunctionPrompt(word: 'getFormState', type: 'Map<String,String>', parameters: {}),
    CodeFunctionPrompt(word: 'getFormValue', type: 'String', parameters: {'key': 'String'}),
  ],
  'FilterOperator': [
    CodeFieldPrompt(word: 'EQUALS', type: 'FilterOperator'),
    CodeFieldPrompt(word: 'NOT_EQUALS', type: 'FilterOperator'),
    CodeFieldPrompt(word: 'GREATER_THAN', type: 'FilterOperator'),
    // ... all enum constants
  ],
}
```

#### E8.4.4 Server-Backed Prompts (Phase 2)

For entity method proposals (after `getEditorEntity(X.class).`), the frontend calls:

```
POST /api/expressions/completions
{
    "baseClass": "FILTER",
    "entityType": "CAMERA_PRODUCER",
    "cursorContext": "getInjectionContext().getEditorEntity(CameraProducer.class).get"
}
```

Response:
```json
{
    "completions": [
        { "label": "getId()",              "detail": "Long",      "kind": "method" },
        { "label": "getName()",            "detail": "String",    "kind": "method" },
        { "label": "getFoundationYear()",  "detail": "YearMonth", "kind": "method" },
        { "label": "getShutdownYear()",    "detail": "YearMonth", "kind": "method" }
    ]
}
```

A custom `CodeAutocompletePromptsBuilder` calls this endpoint asynchronously when it detects
a dot after a `getEditorEntity(...)` call, converting the response into `CodeFunctionPrompt`
instances.

#### E8.4.5 Autocomplete Context Sources

The backend derives proposals from multiple sources depending on cursor position:

| Cursor after | Source | Proposals |
|---|---|---|
| `getInjectionContext().` | `InjectionContext` interface | `getEditorEntity()`, `getFormState()`, `getFormValue()`, `getRouteParam()`, `getSessionValue()` |
| `getEditorEntity(X.class).` | JPA metamodel for `X` | All getter methods on the entity |
| `entity.getProducer().` | JPA metamodel for `CameraProducer` (via @ManyToOne type resolution) | Getters on the related entity |
| `comparison(` | `FilterInjectable` method signature | Parameter hints: `field, operator, value` |
| `FilterOperator.` | Enum constants | `EQUALS`, `NOT_EQUALS`, `GREATER_THAN`, `IN`, ... |
| `setResult(` | Base class method | Parameter hint based on `baseClass` |
| (top level in class body) | Base class + common patterns | `@Override public void execute()`, snippet templates |

#### E8.4.6 Autocomplete Popup UI

The `viewBuilder` callback is responsible for rendering the dropdown. It receives a
`ValueNotifier<CodeAutocompleteEditingValue?>` and an `onSelected` callback. The popup
should follow the app's styling (`AppTheme`, sharp corners, `panelBorder`). A `ListView`
of items showing `word` and `type` in a compact row, keyboard-navigable.

### E8.5 Error Markers

The compile-check endpoint (E7.14) returns line numbers and error messages. `re_editor` has
no built-in marker API, so error display uses two mechanisms:

#### E8.5.1 Gutter Icons via `indicatorBuilder`

The `indicatorBuilder` already renders line numbers. When compilation errors are present,
it additionally renders a red circle icon (or warning triangle for warnings) in the gutter
next to the affected line number. Tapping the icon shows the error message in a tooltip.

```dart
indicatorBuilder: (context, editingController, chunkController, notifier) {
  return Row(children: [
    _ErrorGutterIndicator(
      errors: _compilationErrors,   // List<CompilationError>
      notifier: notifier,
    ),
    DefaultCodeLineNumber(
      controller: editingController,
      notifier: notifier,
    ),
    DefaultCodeChunkIndicator(
      width: 20,
      controller: chunkController,
      notifier: notifier,
    ),
  ]);
},
```

#### E8.5.2 Inline Underlines via `spanBuilder`

The `CodeLineEditingController.spanBuilder` callback intercepts the `TextSpan` for each line.
When a line has a compilation error, the span is split at the error position and the affected
range gets a wavy red underline (`TextDecoration.underline`, `TextDecorationStyle.wavy`,
`color: Colors.red`). Entity type cross-check warnings use yellow.

#### E8.5.3 Error Panel Below Editor

Additionally, compilation errors and warnings are listed in a compact panel below the editor
(similar to VS Code's "Problems" panel), showing line number, severity icon, and message text.
This ensures errors are visible even when the affected line is scrolled out of view.

### E8.6 Compile-Check Integration (Phase 1)

The compile-check endpoint (E7.14) is implemented as part of Phase 1. It is the backend
counterpart to the "Check" button in the editor dialog.

#### E8.6.1 Backend: Compile-Check Endpoint

A new `POST /api/expressions/compile-check` endpoint (specified in E7.14). Implementation:
1. Extract the compilation step from `InjectableExecutor` into a reusable method
   `compileOnly(type, baseClass, source)` that compiles via Janino without executing.
2. Catch `CompileException` and map it to structured error responses with line numbers.
3. Optionally scan for `getEditorEntity(*.class)` calls and compare against `usageContext`
   entity type for cross-check warnings.

#### E8.6.2 Frontend: Trigger Points

The compile-check is triggered:
- **On demand:** via the "Check" button in the editor dialog footer.
- **On save:** before persisting — if compilation fails, the save is blocked and errors
  are displayed.
- **Debounced:** 500ms after the user stops typing, providing near-real-time feedback.

#### E8.6.3 Error Display

Compile errors from the endpoint are displayed in:
1. **Error panel** below the editor (always visible in Phase 1) — list of line number +
   message, styled with `AppTheme`.
2. **Gutter icons** via `indicatorBuilder` — red circle on error lines, yellow triangle
   on warning lines.
3. **Inline underlines** via `spanBuilder` (Phase 2) — wavy red underline on error ranges.

The line numbers in the error response correspond to the full source (including scaffold),
matching what the editor displays (see E8.3.3).

### E8.7 Editor Features Summary

| Feature | Phase 1 (MVP) | Phase 2 (IDE-like) |
|---|---|---|
| Modal dialog with "Edit Source" button | Yes | Yes |
| Read-only import/class scaffold (E8.3.3) | Yes | Yes |
| Java syntax highlighting (`langJava` via `re_highlight`) | Yes | Yes |
| Line numbers (`DefaultCodeLineNumber`) | Yes | Yes |
| Code folding (`DefaultCodeChunkAnalyzer`, brace-based) | Yes | Yes |
| Undo/redo (built-in) | Yes | Yes |
| Compile-check endpoint (E8.6.1, backend) | Yes | Yes |
| Compile-check on demand + on save (E8.6.2) | Yes | Yes |
| Error panel below editor (E8.6.3) | Yes | Yes |
| Gutter error/warning icons (`indicatorBuilder`) | Yes | Yes |
| Client-side keyword/API autocomplete (E8.4.3) | Yes | Yes |
| Inline error underlines (`spanBuilder`) | No | Yes |
| Server-backed type resolution + method proposals (E8.4.4) | Yes | Yes |
| Variable chain resolution (p.getProducer().getName()) | Yes | Yes |
| @ManyToOne navigation proposals (transitive via reflection) | Yes | Yes |
| Debounced compile-check while typing (500ms) | Yes | Yes |
| Parameter hints | No | Future |
| Go-to-definition (base class methods) | No | Future |
| Inline documentation (Javadoc hover) | No | Future |

### E8.8 Styling Integration

The editor follows the centralized styling from `frontendStyling.md`:

- **Border:** matches `inputDecorationTheme` border color, `BorderRadius.zero`.
- **Background:** white, consistent with other form fields.
- **Autocomplete popup:** sharp corners, `AppTheme.panelBorder`, `AppTheme.panelHeaderBackground`
  for hover highlight.
- **Error panel:** uses `AppTheme.panelBorder` separator, `AppTheme.spacingSm` padding,
  monospace font for consistency with editor content.
- **Theme:** `atomOneLightTheme` from `re_highlight` (light theme matching the app's overall
  appearance). Dark theme variant available for future preference toggle.

---

## Task E9 — Client-Side Expression Evaluation (Future)

**Goal:** Enable expressions to be evaluated **client-side** using a reactive signal graph,
reducing server round-trips for evaluations that don't require server-side data (JPA entities,
database access). This is a generic mechanism — any expression whose inputs are available
client-side (formState, route params, session data) can be shifted from server to client
evaluation.

**Status:** Future task. Not part of the initial implementation. The first implementation
(E3) evaluates all expressions server-side. This task generalises the evaluation to a
dual-stack model where expressions are evaluated on whichever side has the required context.

### E9.1 Motivation

The server-side evaluation model (E3.5) works correctly but incurs a round-trip for every field
change — even for expressions that only inspect formState values the client already has. For
example, `isCameraProducerSelected` checks whether `producer != null`, which the client can
answer locally without a server call.

Moving eligible expressions client-side:
- **Eliminates latency** for simple visibility checks (field non-null, checkbox checked, etc.)
- **Reduces server load** — only expressions that need JPA/entity access remain server-side
- **Improves UX** — visibility changes feel instantaneous

### E9.2 Expression Evaluation Locality

Each expression declares (or the system infers) where it can be evaluated:

| Locality | Meaning | Example |
|---|---|---|
| `SERVER` | Requires InjectionContext with JPA entity access | `getEditorEntity(Camera.class).getProducer()` |
| `CLIENT` | Only needs formState / route / session — evaluable in Dart | `formState["producer"] != null` |
| `DUAL` | Has both server and client implementations | Complex expression with a simplified client equivalent |

For the initial E9 implementation, the focus is on `CLIENT` expressions. The system falls back
to `SERVER` for any expression it cannot evaluate locally.

### E9.3 Client-Side Signal Graph

The client-side dependency graph is implemented using the **`signals` Dart package**, which
provides fine-grained reactive primitives with glitch-free topological propagation — the same
mathematical properties (DAG, topological order, confluence) described in E3.6, but handled
by the signal runtime instead of hand-written BFS.

#### Core Mapping

| Concept | signals API | Form equivalent |
|---|---|---|
| Reactive source | `signal<T>(initialValue)` | A form field's current value |
| Derived value | `computed<T>(() => ...)` | Visibility rule result, derived field |
| Side effect | `effect(() => ...)` | Server call for filtered options |

#### Example: Camera Form with Client-Side Visibility

```dart
// Form field values as signals
final producer = signal<int?>(null);          // selected producer ID
final mount = signal<int?>(null);             // selected mount ID

// Visibility as computed signals — evaluated locally, no server call
final isMountVisible = computed(() => producer.value != null);
final isLensesVisible = computed(() => mount.value != null);

// Options reload as effects — these still need the server
effect(() {
  final p = producer.value;
  if (p != null) {
    fetchMountOptions(p);    // POST to server for filtered options
  }
});

effect(() {
  final m = mount.value;
  if (m != null) {
    fetchLensOptions(m);     // POST to server for filtered options
  }
});
```

The signal runtime guarantees:
- When `producer` changes, `isMountVisible` recomputes **before** the effect fires
- In a diamond dependency, downstream signals compute only once after all upstreams settle
- No manual BFS or topological sort needed — the runtime handles propagation order

### E9.4 Hybrid Evaluation Model

With E9, the evaluation flow splits:

```
Field changes
  │
  ├── Client-side (immediate, no round-trip):
  │     signal graph recomputes visibility for CLIENT expressions
  │     → element shows/hides instantly
  │
  └── Server-side (async, via POST /api/data-form/evaluate):
        only for elements whose expressions require SERVER locality
        or whose options need filtered entity data
        → response updates options + server-evaluated visibility
```

The unified endpoint from E3.5 remains available and is used for:
- Elements with `SERVER` expressions
- Elements with `entityProviderRef` (options always come from server)
- Initial load (server evaluates everything, client takes over reactivity afterward)

### E9.5 Relation to Existing Tasks

| Task | Relation |
|---|---|
| **E3** (Visibility via BooleanInjectable) | E9 moves eligible E3 evaluations client-side. E3's server-side model remains the fallback. |
| **E5** (Extensibility) | E9 is one way to extend expression evaluation — a new evaluation stack, not a new expression type. |
| **E7** (Injectable System) | `INJECTABLE_SNIPPET` / `INJECTABLE_CLASS` remain server-only. Client expressions use a simpler model (formState path checks, simple boolean logic). |

### E9.6 What E9 Does NOT Change

- **Expression model** (E1) — unchanged. Expressions are still AppConfig tree nodes.
- **Server-side evaluation** (E3.5–E3.7) — unchanged. Remains the authoritative fallback.
- **Dependency graph structure** (E3.6) — unchanged. `reloadOnChangeOf` still declares the DAG.
  The signal graph is the client-side implementation of the same DAG.
- **Injectable system** (E7) — unchanged. Janino-compiled injectables remain server-side.

---

## Architecture Summary

```
┌──────────────────────────────────────────────────────────────────┐
│                         AppConfig Tree                            │
│                                                                    │
│  expressions:                                                      │
│    "currentEditorId"       → CONTEXT_PATH: "editor.id"            │
│    "producerMountFilter"   → INJECTABLE_CLASS (FILTER, source DB) │
│    "isDiscontinued"        → INJECTABLE_SNIPPET (BOOLEAN, src DB) │
│    "pageSize"              → STATIC: "10"                         │
│                                                                    │
│  entityProviders:                                                  │
│    "mountsForCurrentProducer"                                      │
│      filter: (none)                                                │
│      filterInjectableRef → "producerMountFilter"   ← FILTER       │
│    "allCameras"                                                    │
│      filter.expressionRef → "currentEditorId"      ← SCALAR_VALUE │
│                                                                    │
│  dataForms:                                                        │
│    "producerForm"                                                  │
│      elements:                                                     │
│        "shutdownYear"                                              │
│          visibilityRule.expressionRef → "isDiscontinued"           │
│        "lensMountGrid"                                             │
│          type: GRID                                                │
│          entityProviderRef → "mountsForCurrentProducer"            │
└──────────────┬──────────────────────────┬────────────────────────┘
               │                          │
      ┌────────▼──────────┐     ┌────────▼────────┐
      │    Server-Side    │     │   Client-Side   │
      │                   │     │                 │
      │ ExpressionResolver│     │ ExpressionReslvr│
      │  ├ CONTEXT_PATH   │     │  ├ CONTEXT_PATH │
      │  ├ SPEL           │     │  └ STATIC       │
      │  ├ STATIC         │     │                 │
      │  ├ INJCTBL_SNIP   │     │ ContextProviders│
      │  └ INJCTBL_CLS    │     │  ├ formState    │
      │                   │     │  └ route        │
      │ InjectableExecutor│     │                 │
      │  ├ compile(DB src)│     │ VisibilityEval  │
      │  ├ executeString()│     │  (reactive)     │
      │  └ executeFilter()│     └─────────────────┘
      │                   │
      │ FilterExecutor    │
      │  ├ static filter  │
      │  ├ + injectable   │
      │  └ = AND merge    │
      │                   │
      │ Base classes:     │
      │  ├ ScalarValue    │
      │  ├ Boolean        │
      │  ├ ListValue      │
      │  └ Filter         │
      │                   │
      │ EditorEntityBldr  │
      │  ├ formState→POJO │
      │  ├ @ManyToOne→DB  │
      │  └ typed access   │
      │                   │
      │ ContextProviders  │
      │  ├ editor(typed)  │
      │  ├ formState      │
      │  ├ route          │
      │  └ session        │
      └───────────────────┘
```

---

## Task Dependency Order

```
E1 (Expression model & tree)              ← Foundation
  │
  ├── E6 (Sandbox API)                    ← Depends on E1 (core interface)
  │     │
  │     ├── E7 (Injectable system)        ← Depends on E6 (uses InjectionContext)
  │     │     ├── INJECTABLE_SNIPPET
  │     │     └── INJECTABLE_CLASS
  │     │
  │     ├── E2 (Dynamic filter values)    ← Depends on E1 + E6, can use E7
  │     │     └── Enables gridElement.md G1
  │     └── E3 (Visibility via BooleanInjectable) ← Depends on E1 + E6 + E7 (server-side)
  │           │
  │           └── E9 (Client-side evaluation) ← Depends on E3, shifts eligible expressions
  │                 └── signals Dart package     to client-side signal graph (Future)
  │
  ├── E4 (Admin editor)                   ← Depends on E1, uses E6 for proposals
  │     │
  │     └── E8 (Code editor / re_editor)   ← Depends on E4 + E7
  │           ├── Phase 1: Monaco/CodeMirror + error markers
  │           └── Phase 2: server-backed autocomplete
  │
  └── E5 (Extensibility)                  ← Documentation-only, no code dependency
```

---

## Cross-References

- **GRID element**: `gridElement.md` — primary consumer of dynamic filter expressions
- **FilterNode / FilterExecutor**: `dataBinding.md` Task 6 — extended with expressionRef
- **EntityProvider**: `dataBinding.md` Task 2 — provides entities for GRID
- **AppConfig tree and seeder**: `appConfig.md` — tree structure and bootstrap
- **CameraLensMount2CameraProducer**: `domainEntities.md` Task D2 — example entity for GRID
