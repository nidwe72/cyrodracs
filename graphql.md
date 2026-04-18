# GraphQL Migration Specification

## Overview

Replace all REST endpoints with GraphQL using a **code-first** approach.
The GraphQL schema is generated at startup from Java domain classes via reflection —
no handwritten `.graphqls` files, no physical schema files on disk.

**Schema generation:** Custom `JavaSchemaGenerator` (~230 lines) that scans Java classes
via `java.lang.reflect`, produces an SDL string, and registers it with Spring for GraphQL.

**Server:** [Spring for GraphQL](https://docs.spring.io/spring-graphql/reference/) (official Spring project,
v2.0.x via `spring-boot-starter-graphql`) — parses the generated SDL, wires resolvers via
`@QueryMapping`/`@SchemaMapping`, serves the schema over HTTP via WebMVC.

**No external schema library.** Previous iterations evaluated graphql-kotlin (Kotlin-only,
Java interop issues with primitives/getters/nullability) and other code-first libraries
(SPQR, graphql-java-annotations — all unmaintained or incompatible with graphql-java 25.x).
A custom Java reflection layer is the only viable code-first approach for Spring Boot 4.x.

## Motivation

- **Partial data loading**: Clients request only the fields they need. As domain models grow,
  REST responses bloat with unused fields. GraphQL eliminates over-fetching at the protocol level.
- **Graph traversal**: The AppConfig tree, entity relationships (`@ManyToOne`), and dot-notation
  bindings are graph structures. GraphQL's nested query model maps naturally to these.
- **Variable response shapes**: The `evaluate` endpoint returns different state per element type.
  GraphQL's client-driven field selection handles this without server-side conditional logic.
- **Single canonical approach**: One protocol for all service calls — no hybrid REST/GraphQL split.
- **Early adoption**: Migrating now (with ~12 endpoints) is cheaper than migrating later when the
  domain model has grown and more client code depends on REST conventions.

## Architecture

### Layer Separation

```
Flutter Client (Dart)
    │ HTTP POST /graphql (via graphql Dart package)
    ▼
Spring for GraphQL (WebMVC)        ← full integration: schema + resolvers + transport
    │ @QueryMapping / @SchemaMapping
    ▼
Java: GraphQL Controllers          ← NEW — thin adapter layer (replaces REST controllers)
    │ calls into
    ▼
Java: Services, Repositories       ← UNCHANGED
```

Spring for GraphQL handles everything: HTTP transport, schema parsing, resolver wiring,
error handling. The custom `JavaSchemaGenerator` only produces the SDL string at startup.

### What changes

| Artifact | Purpose |
|---|---|
| `JavaSchemaGenerator` | Scans Java classes via reflection, produces SDL string |
| `@GraphQLInternal` annotation | Marks fields to exclude from schema |
| GraphQL `@Controller` classes | Replace REST `@RestController` classes — use `@QueryMapping`/`@SchemaMapping` |
| `GraphQLSchemaConfig` | Startup config — generates SDL, registers `GraphQlSource` |
| Custom scalars (JSON, YearMonth) | Registered via `RuntimeWiringConfigurer` |
| `CorsConfig` | Global CORS for `/graphql` |

### What stays unchanged

Everything else: JPA entities, domain model classes (AppConfig, DataForm, DataFormElement, etc.),
services, repositories, assemblers, expression engine, persistence logic.

**Pure Java — no Kotlin needed.**

## Code-First Schema Generation

### Principle

The GraphQL schema is derived from Java class structure at application startup via reflection.
No `.graphqls` schema files exist on disk.

**Adding a field:** Add it to the Java class → restart → it appears in the GraphQL schema automatically.
**Removing a field:** Remove it from the Java class → restart → it disappears from the schema.
**No schema drift possible.** The Java classes ARE the source of truth.

With `spring-boot-devtools`, restart is automatic on code change (~2-3 seconds).
Schema generation itself takes ~20ms — invisible in startup time.

### How it works

```java
@Configuration
class GraphQLSchemaConfig {

    @Bean
    GraphQlSource graphQlSource(JavaSchemaGenerator generator) {
        String sdl = generator.generateSDL();
        log.info("Generated GraphQL schema:\n{}", sdl);  // inspectable at startup

        // Spring for GraphQL parses the SDL and wires @QueryMapping resolvers
        return GraphQlSource.schemaResourceBuilder()
            .schemaFactory((typeRegistry, wiring) ->
                new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring))
            .build();
    }
}
```

### JavaSchemaGenerator — what it does

The generator uses `java.lang.reflect` (not Kotlin reflection) to scan Java classes:

1. **Scans getters** — finds `getCode()` → field `code: String`, `isMandatory()` → field `mandatory: Boolean`
2. **Maps Java types** — `String`→`String`, `Long`→`Int`, `boolean`/`Boolean`→`Boolean`,
   `List<X>`→`[X]`, enums→GraphQL enums
3. **Handles relationships** — `CameraProducer getProducer()` → `producer: CameraProducer`
4. **Handles recursive types** — `List<FilterNode> getChildren()` → `children: [FilterNode]`
5. **Handles Maps** — `Map<String, Object>` → `JSON` scalar, `Map<String, DataForm>` → `[DataForm]`
6. **Applies blacklisting** — `@GraphQLInternal`, pattern rules, per-class exclusions

Output: a complete SDL string like:

```graphql
type DataFormElement {
  code: String
  type: DataFormElementType
  dataBinding: String
  mandatory: Boolean
  tableColumns: [TableColumn]
  addAction: AddAction
  visibilityRule: VisibilityRule
  reloadOnChangeOf: [String]
}
```

### Field Blacklisting

Fields are exposed by default. Unwanted fields are excluded via three mechanisms
(all in plain Java, all coexist):

#### 1. Global pattern rules (in generator code)

```java
// In JavaSchemaGenerator
private boolean isExcluded(String fieldName) {
    if (fieldName.endsWith("NodeId")) return true;        // tree provenance bookkeeping
    if (fieldName.equals("parentObjectId")) return true;   // redundant FK
    if (fieldName.equals("parentTypeId")) return true;     // redundant FK
    return false;
}
```

#### 2. Annotation-based (on Java domain classes)

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface GraphQLInternal {}
```

```java
public class DataFormElement {
    private String code;           // exposed

    @GraphQLInternal
    private Long typeNodeId;       // hidden
}
```

The generator checks: `clazz.getDeclaredField(name).isAnnotationPresent(GraphQLInternal.class)`

#### 3. Programmatic per-class exclusions

```java
var generator = new JavaSchemaGenerator();
generator.exclude(AppConfigObjectEntity.class, "parentObjectId", "parentTypeId");
```

### Blacklisted Fields

The following field patterns are blacklisted globally:

| Pattern | Reason |
|---|---|
| `*NodeId` fields | Internal tree-to-field provenance (typeNodeId, dataBindingNodeId, etc.) |
| `parentObjectId` | Redundant FK column on AppConfigObjectEntity |
| `parentTypeId` | Redundant FK column on AppConfigTypeEntity |

## Type Mapping

### Maps — General Strategy

GraphQL has no native `Map` type. Java Map fields are handled depending on context:

| Map type | Key redundant? | Strategy |
|---|---|---|
| `Map<String, DataForm>` | Yes — `code` inside value | Resolver returns `values().toList()` → `[DataForm]` |
| `Map<String, EntityProvider>` | Yes — `code` inside value | Same — `[EntityProvider]` |
| `Map<String, EntityRenderer>` | Yes — `code` inside value | Same — `[EntityRenderer]` |
| `Map<String, ViewNode>` | Yes — `code` inside value | Same — `[ViewNode]` |
| `Map<String, Expression>` | Yes — `code` inside value | Same — `[Expression]` |
| `Map<String, DataFormElement>` | Yes — `code` inside value | Same — `[DataFormElement]` |
| `Map<String, ElementState>` | No | `[ElementStateEntry]` with `key` + `value` fields |
| `Map<String, String>` | No | `[StringMapEntry]` with `key` + `value` fields |
| `Map<String, List<MethodInfo>>` | No | `[MethodInfoListEntry]` with `key` + `value` fields |
| `Map<String, Object>` | N/A — dynamic | `JSON` custom scalar (pass-through) |

Map→List conversions for redundant-key maps are handled in the `@SchemaMapping` resolver methods,
not in the schema generator. The schema declares the list type; the resolver calls `values().toList()`.

For non-redundant maps, concrete entry types are defined in the SDL:

```graphql
type ElementStateEntry {
  key: String!
  value: ElementState!
}
```

### Enums

Java enums are scanned by the generator and emitted as GraphQL enums:

```graphql
enum DataFormElementType {
  INPUT_STRING, INPUT_NUMBER, INPUT_EMAIL, ...
}
```

| Java Enum | GraphQL Enum |
|---|---|
| DataFormElementType | DataFormElementType |
| DataFormEntityType | DataFormEntityType |
| ExpressionType | ExpressionType |
| ViewNodeType | ViewNodeType |
| FilterNodeType | FilterNodeType |
| FilterOperator | FilterOperator |
| SortDirection | SortDirection |
| InjectableBaseClass | InjectableBaseClass |

### Recursive Types

FilterNode (AND/OR groups), ViewNode (tree), and PendingChild contain `children: List<Self>`.
The generator handles self-referencing types by emitting type references in the SDL.

### Custom Scalars

| Java Type | GraphQL Scalar | Serialized as | Used by |
|---|---|---|---|
| `Long` (IDs) | `Int` | Number | All entity IDs |
| `YearMonth` | `YearMonth` (custom) | `"2024-03"` | Camera.releaseYear, CameraProducer.foundationYear/shutdownYear |
| `Map<String, Object>` | `JSON` (custom) | Raw JSON object | DataFormData.values, PendingChild.values, PagedResult.items |

Custom scalars are registered via `RuntimeWiringConfigurer` in the Spring configuration.

## Query Design

### Root Queries

```
appConfig                                          → AppConfig
appConfigTypes                                     → [AppConfigType]
appConfigTypeEnumValues(typeCode: String!)          → [String]
dataFormData(dataFormCode: String!, entityId: Int!) → DataFormData
evaluateDataForm(input: EvaluateInput!)             → EvaluateResult
bindingProposals(entityType: String!, prefix: String) → BindingProposalResult
entitySelectOptions(provider: String!, renderer: String!) → [EntityOption]
viewData(viewNodeCode: String!, page: Int, size: Int)     → PagedResult
gridData(input: GridDataInput!)                     → PagedResult
cameras                                            → [Camera]
cameraProducers                                    → [CameraProducer]
cameraLensMounts                                   → [CameraLensMount]
compileCheckExpression(input: CompileCheckInput!)    → CompileCheckResult
```

### Root Mutations

#### Fine-grained (tree node level)

```
addAppConfigNode(input: AddNodeInput!)              → AppConfig
updateAppConfigNode(id: Int!, input: UpdateNodeInput!) → AppConfig
copyAppConfigNode(id: Int!, newCode: String!)       → AppConfig
deleteAppConfigNode(id: Int!)                       → AppConfig
```

#### Coarse-grained (domain operation level)

The frontend currently chains multiple fine-grained REST calls for single logical operations
(e.g., `updateDataFormElementFull` makes up to 5 sequential calls). GraphQL coarse-grained
mutations replace these chains with single atomic operations:

```
addDataFormElement(input: AddDataFormElementInput!)                  → AppConfig
updateDataFormElementFull(input: UpdateDataFormElementFullInput!)    → AppConfig
updateDataForm(input: UpdateDataFormInput!)                         → AppConfig
updateEntityProvider(input: UpdateEntityProviderInput!)              → AppConfig
updateEntityRenderer(input: UpdateEntityRendererInput!)              → AppConfig
```

These mutations call existing Java services internally but combine multiple tree operations
into one request. Benefits:
- Single round-trip instead of 3-5 sequential calls
- Atomic — either all changes succeed or none do
- Simpler frontend code — no chaining logic in Dart

**Child node resolution:** The backend looks up existing child nodes itself via
`AppConfigObjectEntity` queries. Mutation inputs stay simple — no nodeId fields needed.

**Fallback naming:** When creating child nodes, the backend generates codes using the pattern
`{elementCode}_type`, `{elementCode}_entity`, etc.

#### Data persistence

```
saveDataFormData(input: DataFormDataInput!)          → DataFormDataResult
```

#### Entity deletion

```
deleteCamera(id: Int!)                              → Boolean
deleteCameraProducer(id: Int!)                      → Boolean
deleteCameraLensMount(id: Int!)                     → Boolean
deleteViewEntity(viewNodeCode: String!, id: Int!)   → Boolean
deleteGridEntity(dataFormCode: String!, elementCode: String!, entityId: Int!) → Boolean
```

### Mutation Return Pattern

All AppConfig mutations return `AppConfig` (the full tree). This matches the current REST
behavior where the frontend replaces its local state wholesale after mutations.

The client controls response depth via the query:

```graphql
mutation {
  addAppConfigNode(input: { ... }) {
    code
    dataForms { code }     # lightweight — just refresh the sidebar
  }
}
```

## Input Types

### EvaluateInput

```graphql
input EvaluateInput {
  dataFormCode: String!
  entityId: Int
  changedElement: String
  formState: [MapEntryInput!]!
}
```

### GridDataInput

```graphql
input GridDataInput {
  dataFormCode: String!
  elementCode: String!
  entityId: Int!
  formState: [MapEntryInput!]!
  page: Int = 0
  size: Int = 10
}
```

### MapEntryInput

```graphql
input MapEntryInput {
  key: String!
  value: String!
}
```

GraphQL has no `Map` in input types. `Map<String, String>` (formState) becomes
`[MapEntryInput]`. The resolver converts this to a `Map` before calling the Java service.

### DataFormDataInput

```graphql
input DataFormDataInput {
  dataFormCode: String!
  entityId: Int
  values: JSON!
  pendingChildren: [PendingChildInput!]
}
```

Note: `values` uses the `JSON` scalar because values are `Map<String, Object>`.

### PendingChildInput (recursive)

```graphql
input PendingChildInput {
  dataFormCode: String!
  contextBindingTarget: String!
  values: JSON!
  pendingChildren: [PendingChildInput!]
}
```

### AddNodeInput

```graphql
input AddNodeInput {
  parentObjectId: Int
  typeCode: String!
  code: String!
  enumValue: String
}
```

### UpdateNodeInput

```graphql
input UpdateNodeInput {
  code: String
  enumValue: String
}
```

### AddDataFormElementInput (coarse-grained)

```graphql
input AddDataFormElementInput {
  parentFormId: Int!
  code: String!
  type: DataFormElementType
}
```

### UpdateDataFormElementFullInput (coarse-grained)

```graphql
input UpdateDataFormElementFullInput {
  elementId: Int!
  code: String
  type: DataFormElementType
  dataBinding: String
  entityProviderRef: String
  entityRendererRef: String
}
```

### UpdateDataFormInput (coarse-grained)

```graphql
input UpdateDataFormInput {
  formId: Int!
  code: String
  entity: DataFormEntityType
}
```

### UpdateEntityProviderInput (coarse-grained)

```graphql
input UpdateEntityProviderInput {
  providerId: Int!
  code: String
  entityType: DataFormEntityType
}
```

### UpdateEntityRendererInput (coarse-grained)

```graphql
input UpdateEntityRendererInput {
  rendererId: Int!
  code: String
  entityType: DataFormEntityType
  template: String
}
```

### CompileCheckInput

```graphql
input CompileCheckInput {
  type: String!
  baseClass: String!
  expression: String!
  expectedEntityType: String
}
```

## Result Types

### EvaluateResult

```graphql
type EvaluateResult {
  elements: [ElementStateEntry!]!
}

type ElementStateEntry {
  key: String!
  value: ElementState!
}
```

### ElementState

```graphql
type ElementState {
  visible: Boolean
  options: [EntityOption]
}
```

### EntityOption

```graphql
type EntityOption {
  id: Int
  label: String
}
```

### PagedResult

```graphql
type PagedResult {
  items: [JSON]
  totalCount: Int
  page: Int
  pageSize: Int
  totalPages: Int
}
```

### DataFormData

```graphql
type DataFormData {
  dataFormCode: String
  entityId: Int
  values: JSON
  pendingChildren: [PendingChild]
}
```

### DataFormDataResult

```graphql
type DataFormDataResult {
  success: Boolean!
  error: String
  dataFormCode: String
  data: DataFormData
}
```

### BindingProposalResult

```graphql
type BindingProposalResult {
  entityLabel: String
  completions: [BindingCompletion]
}
```

### BindingCompletion

```graphql
type BindingCompletion {
  segment: String
  javaType: String
  leaf: Boolean
  suggestedElementType: String
  referencedEntityType: String
}
```

### CompileCheckResult

```graphql
type CompileCheckResult {
  valid: Boolean
  errors: [CompileCheckError]
  warnings: [CompileCheckWarning]
  typeContext: TypeContext
}
```

### TypeContext

```graphql
type TypeContext {
  variables: [StringMapEntry]
  methods: [MethodInfoListEntry]
}

type StringMapEntry {
  key: String!
  value: String!
}

type MethodInfoListEntry {
  key: String!
  value: [MethodInfo]!
}
```

### MethodInfo

```graphql
type MethodInfo {
  name: String
  returnType: String
  returnsResolvable: Boolean
}
```

## N+1 Prevention

For JPA entity queries (cameras with producers), Spring for GraphQL provides `@BatchMapping`
to batch relationship resolution. This replaces the DataLoader pattern:

```java
@BatchMapping
public Map<Camera, CameraProducer> producer(List<Camera> cameras) {
    // batch-load all producers in one query
    return cameras.stream().collect(toMap(c -> c, Camera::getProducer));
}
```

The AppConfig domain models (DataForm, ViewNode, etc.) are pre-assembled in memory by
`AppConfigTreeBuilder` and do not need batch loading.

## Partial Data Loading — Performance Rationale

The primary motivation for GraphQL in this project. Example with growing domain:

```
REST (current):
  GET /api/view/cameraList/data
  → returns ALL fields of every Camera, CameraProducer, CameraLensMount
  → client renders only: name, producer.name in the table
  → wasted: releaseYear, lensMount, producer.foundationYear, producer.shutdownYear, ...

GraphQL:
  query {
    viewData(viewNodeCode: "cameraList") {
      items
      totalCount
    }
  }
  → returns only requested fields
```

## CORS Configuration

Replace per-controller `@CrossOrigin(origins = "*")` annotations with a single global config:

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/graphql").allowedOrigins("*");
    }
}
```

## Migration Strategy

### Phase 1 — Setup

1. Remove graphql-kotlin dependencies and all Kotlin source files
2. Remove Kotlin Maven plugin configuration
3. Keep `spring-boot-starter-graphql` dependency (already present)
4. Create `JavaSchemaGenerator` class (~300 lines)
5. Create `@GraphQLInternal` annotation
6. Create `GraphQLSchemaConfig` — startup wiring
7. Create custom scalar definitions (JSON, YearMonth) via `RuntimeWiringConfigurer`
8. Create `CorsConfig`
9. Verify schema generation — log SDL at startup, inspect via GraphiQL

### Phase 2 — Query Controllers

Create Java `@Controller` classes with `@QueryMapping`:
1. `AppConfigQueryController` — appConfig, types, enumValues
2. `DataFormQueryController` — evaluateDataForm, dataFormData
3. `EntityQueryController` — entitySelectOptions, bindingProposals
4. `ViewQueryController` — viewData, gridData
5. `CameraQueryController` — cameras, cameraProducers, cameraLensMounts
6. `ExpressionQueryController` — compileCheckExpression

### Phase 3 — Mutation Controllers

Create Java `@Controller` classes with `@MutationMapping`:
1. `AppConfigMutationController` — fine-grained + coarse-grained node operations
2. `DataMutationController` — saveDataFormData, entity deletions, view/grid deletions

### Phase 4 — Frontend Migration

Update Flutter `AppConfigService` to use `graphql` Dart package.
Update `AppConfigNode.fromJson` for List-based responses (Maps→Lists).

### Phase 5 — Cleanup

- Remove all REST controllers (12 files)
- Remove `@CrossOrigin` annotations (replaced by global CORS config)
- Remove demo/test endpoints (`HelloController`, `/api/hello`, `/api/demo-form`, `/api/tree`)

## Dependencies

### Backend (Maven)

```xml
<!-- Spring for GraphQL (WebMVC — schema + resolvers + transport) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-graphql</artifactId>
</dependency>
```

No other new dependencies. graphql-java is pulled in transitively by `spring-boot-starter-graphql`.
No Kotlin dependencies needed.

### Frontend (pubspec.yaml)

```yaml
dependencies:
  graphql: ^5.2.0    # GraphQL client
```

## Frontend Client

### Client Setup

```dart
import 'package:graphql/client.dart';

final httpLink = HttpLink('http://localhost:8080/graphql');
final client = GraphQLClient(link: httpLink, cache: GraphQLCache());
```

### Response Structure

GraphQL wraps responses in `{"data": {...}}`. The `graphql` Dart package unwraps this —
`result.data` contains the inner data map. Existing `fromJson` methods stay unchanged;
only call sites change:

```dart
// REST:    AppConfigNode.fromJson(jsonDecode(response.body))
// GraphQL: AppConfigNode.fromJson(result.data!['appConfig'])
```

### Error Handling

GraphQL always returns HTTP 200. Errors are in `result.exception.graphqlErrors`.
Frontend code checks `result.hasException` instead of HTTP status codes.

## Open Questions

- **Subscriptions**: Real-time updates could use GraphQL subscriptions over WebSocket.
  Not needed now but Spring for GraphQL supports them natively.
- **Pagination strategy**: Current `PagedResult` uses offset-based pagination. GraphQL convention
  favors cursor-based (Relay spec). Evaluate whether to adopt cursor-based for new queries.
- **Error conventions**: Define project conventions for error codes and partial success scenarios.
- **GraphiQL**: Enabled by default in dev mode. Disable in production via
  `spring.graphql.graphiql.enabled=false`.
