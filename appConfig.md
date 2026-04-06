# App Configuration Specification

## Overview

The application configuration is modelled as a typed tree rooted at `AppConfig`.
Every node in the tree is a *config class* and every config class implements the
`Coded` interface, which provides a stable string identifier (`code`) for that
node within its parent's collection.

The tree is persisted in two PostgreSQL tables managed via JPA:

| Table | Purpose |
|---|---|
| `APP_CONFIG_TYPE` | Structural metadata – describes every config class and its fields (the "schema") |
| `APP_CONFIG_OBJECT` | Instance data – stores the actual configuration tree (the "data") |

---

## Domain Model

### Interface: `Coded`

All config classes implement this interface.

```java
public interface Coded {
    String getCode();
    void setCode(String code);
}
```

---

### Root: `AppConfig`

The single root object of the configuration tree.

| Field | Type | Description |
|---|---|---|
| `code` | `String` | Identifies this AppConfig instance (e.g. `"default"`) |
| `dataForms` | `Map<String, DataForm>` | Keyed by `DataForm.code` |

```java
public class AppConfig implements Coded {
    String code;
    Map<String, DataForm> dataForms;   // key == dataForm.getCode()
}
```

---

### Composite: `DataForm`

Represents a single form definition.

| Field | Type | Description |
|---|---|---|
| `code` | `String` | Unique identifier of this form (e.g. `"userRegistration"`) |
| `elements` | `Map<String, DataFormElement>` | Keyed by `DataFormElement.code` |

```java
public class DataForm implements Coded {
    String code;
    Map<String, DataFormElement> elements;   // key == element.getCode()
}
```

---

### Leaf: `DataFormElement`

Represents a single field within a form.

| Field | Type | Description |
|---|---|---|
| `code` | `String` | Unique identifier of this element (e.g. `"firstName"`) |
| `type` | `DataFormElementType` | Enum value controlling how the element is rendered |

```java
public class DataFormElement implements Coded {
    String code;
    DataFormElementType type;
}
```

---

### Enum: `DataFormElementType`

Specifies the rendering/input behaviour of a `DataFormElement`.

| Value | Description |
|---|---|
| `INPUT_STRING` | Single-line text input |
| `INPUT_NUMBER` | Numeric text input |
| `INPUT_EMAIL` | Email text input |
| `INPUT_PASSWORD` | Password text input |
| `TEXTAREA` | Multi-line text input |
| `SELECT` | Single-selection dropdown |
| `MULTI_SELECT` | Multi-selection dropdown |
| `CHECKBOX_GROUP` | Group of checkboxes |
| `RADIO_GROUP` | Group of radio buttons |
| `CHECKBOX` | Single checkbox |
| `TOGGLE` | Toggle / switch |
| `DATE_PICKER` | Date picker |
| `TIME_PICKER` | Time picker |
| `DATE_TIME_PICKER` | Date and time picker |
| `DATE_RANGE_PICKER` | Date range picker |
| `SLIDER` | Numeric range slider |
| `RATING` | Star rating input |

```java
public enum DataFormElementType {
    INPUT_STRING,
    INPUT_NUMBER,
    INPUT_EMAIL,
    INPUT_PASSWORD,
    TEXTAREA,
    SELECT,
    MULTI_SELECT,
    CHECKBOX_GROUP,
    RADIO_GROUP,
    CHECKBOX,
    TOGGLE,
    DATE_PICKER,
    TIME_PICKER,
    DATE_TIME_PICKER,
    DATE_RANGE_PICKER,
    SLIDER,
    RATING
}
```

---

## Example Tree

```
AppConfig (code="default")
 ├── DataForm (code="userRegistration")
 │    ├── DataFormElement (code="firstName",  type=INPUT_STRING)
 │    ├── DataFormElement (code="lastName",   type=INPUT_STRING)
 │    └── DataFormElement (code="email",      type=INPUT_STRING)
 └── DataForm (code="productSearch")
      ├── DataFormElement (code="keyword",    type=INPUT_STRING)
      ├── DataFormElement (code="category",   type=INPUT_STRING)
      ├── DataFormElement (code="priceMin",   type=INPUT_STRING)
      └── DataFormElement (code="priceMax",   type=INPUT_STRING)
```

---

## Persistence

### Table: `APP_CONFIG_TYPE`

Holds the **structural metadata** of the configuration model — one row per
config class and one row per primitive field. This is the "schema" side.

| Column | Type | Description |
|---|---|---|
| `id` | `BIGINT PK` | Surrogate key |
| `code` | `VARCHAR` | Unique name of this type node (e.g. `"AppConfig"`, `"DataForm"`, `"DataFormElement"`, `"DataFormElementType"`) |
| `parent_type_id` | `BIGINT FK → APP_CONFIG_TYPE.id` | Parent type in the type tree; NULL for the root type |
| `field_name` | `VARCHAR` | Name of the field on the parent class that holds this type (NULL for root) |
| `is_collection` | `BOOLEAN` | `true` when the field is a `Map` / collection |
| `is_enum` | `BOOLEAN` | `true` for enum types |
| `java_type` | `VARCHAR` | Fully-qualified Java class or enum name |

**Seed rows (example)**

| id | code | parent_type_id | field_name | is_collection | is_enum | java_type |
|---|---|---|---|---|---|---|
| 1 | AppConfig | NULL | NULL | false | false | `…AppConfig` |
| 2 | DataForm | 1 | dataForms | true | false | `…DataForm` |
| 3 | DataFormElement | 2 | elements | true | false | `…DataFormElement` |
| 4 | DataFormElementType | 3 | type | false | true | `…DataFormElementType` |

---

### Table: `APP_CONFIG_OBJECT`

Holds the **instance data** — one row per node in the configuration tree.
Together these rows represent the full `AppConfig` tree.

| Column | Type | Description |
|---|---|---|
| `id` | `BIGINT PK` | Surrogate key |
| `type_id` | `BIGINT FK → APP_CONFIG_TYPE.id` | Which type this object instantiates |
| `code` | `VARCHAR` | The `Coded.code` value for this node |
| `parent_object_id` | `BIGINT FK → APP_CONFIG_OBJECT.id` | Parent node; NULL for the root AppConfig row |
| `enum_value` | `VARCHAR` | Populated only when `APP_CONFIG_TYPE.is_enum = true` |

**Example rows matching the tree above**

| id | type_id | code | parent_object_id | enum_value |
|---|---|---|---|---|
| 1 | 1 | default | NULL | NULL |
| 2 | 2 | userRegistration | 1 | NULL |
| 3 | 3 | firstName | 2 | NULL |
| 4 | 4 | firstName_type | 3 | INPUT_STRING |
| 5 | 3 | lastName | 2 | NULL |
| 6 | 4 | lastName_type | 5 | INPUT_STRING |
| 7 | 3 | email | 2 | NULL |
| 8 | 4 | email_type | 7 | INPUT_STRING |
| 9 | 2 | productSearch | 1 | NULL |
| … | … | … | … | … |

---

## Bootstrap Database

### Purpose

Before the main PostgreSQL database can be reached, the application reads
connection credentials from a local SQLite file `appConfigBootstrap.db`.
This keeps sensitive database access data out of the application's packaged
configuration files and allows the target database to be changed without
redeployment.

### File location

Database files are stored **outside the repository** to avoid accidental
commits of binary data. The default location for local development is:

```
cyrodracs_db/
├── appConfigBootstrap.db     # bootstrap (JDBC connection config)
├── appConfig.db              # main application data
├── backup.sh                 # timestamped backup script
├── backups/                  # backup snapshots
└── test/                     # isolated test databases
    ├── appConfigBootstrap.db
    └── appConfig.db
```

Paths are configured via `application.properties`:

| Property | Default | Description |
|---|---|---|
| `app.bootstrap.path` | `./appConfigBootstrap.db` | Path to the bootstrap SQLite file |
| `app.default-db.url` | `jdbc:sqlite:./appConfig.db` | JDBC URL written into a newly created bootstrap file |

Tests override both properties to point to `cyrodracs_db/test/`, ensuring
they never read or write the production database.

### Table: `APP_CONFIG_DATABASE`

Holds the JDBC / datasource access data for the PostgreSQL database that
contains `APP_CONFIG_TYPE` and `APP_CONFIG_OBJECT`.

| Column | Type | Description |
|---|---|---|
| `id` | `INTEGER PK` | Surrogate key (SQLite `ROWID`) |
| `code` | `TEXT NOT NULL UNIQUE` | Logical name for this database entry (e.g. `"default"`) |
| `jdbc_url` | `TEXT NOT NULL` | Full JDBC URL, e.g. `jdbc:postgresql://host:5432/dbname` |
| `db_username` | `TEXT NOT NULL` | Database user name |
| `db_password` | `TEXT NOT NULL` | Database password (plain text or encrypted — see note below) |
| `schema_name` | `TEXT` | Target schema; NULL means the datasource default schema is used |
| `active` | `INTEGER NOT NULL DEFAULT 1` | `1` = this entry is used on startup; `0` = ignored |

Only the single row where `active = 1` is used at startup. Having multiple
rows allows switching target databases by flipping the `active` flag and
restarting.

**Example row**

| id | code | jdbc_url | db_username | db_password | schema_name | active |
|---|---|---|---|---|---|---|
| 1 | default | `jdbc:postgresql://localhost:5432/cyrodracs` | `app_user` | `s3cr3t` | `public` | 1 |

### Getting-started mode (default)

For local development the application ships with a SQLite-based default.
On first start, if the bootstrap file does not exist the application creates
it and inserts a default row using the `app.default-db.url` property value.

This means zero configuration is required to get started locally.
To switch to PostgreSQL later, update the `jdbc_url`, `db_username`, and
`db_password` in `APP_CONFIG_DATABASE` and restart.

### Startup sequence

1. Application starts and locates the bootstrap file via
   `app.bootstrap.path`.
2. If the file does not exist, it is created with a default SQLite entry
   using the `app.default-db.url` property value.
3. Reads the single `APP_CONFIG_DATABASE` row where `active = 1`.
4. Constructs the JPA / JDBC datasource from the retrieved URL and credentials.
   - For SQLite URLs the connection pool is capped at 1 (SQLite is single-writer).
5. Connects to the target database and loads the `AppConfig` tree into
   application scope (see [Application-scope store](#application-scope-store)).
6. If no active row exists the application fails fast with a descriptive error.

---

## Design Rules

1. **`code` is the natural key within a parent's collection.** The map key in
   Java always equals the child node's `code`.
2. **`APP_CONFIG_TYPE` is seeded once** (e.g. via a Liquibase/Flyway migration)
   and is read-only at runtime.
3. **`APP_CONFIG_OBJECT` is the mutable store** for all configuration changes.
4. Reconstructing the in-memory tree requires one joined query across
   `APP_CONFIG_OBJECT` and `APP_CONFIG_TYPE`, traversed top-down from the root
   row (`parent_object_id IS NULL`).
5. Every config class (except enums) must implement `Coded`.

---

## Client-Side AppConfig

The client (browser / frontend) maintains a JavaScript/TypeScript mirror of the
server-side domain model.

### Mirror classes

```ts
interface Coded {
  code: string;
}

class AppConfig implements Coded {
  code: string;
  dataForms: Map<string, DataForm>;   // key == dataForm.code
}

class DataForm implements Coded {
  code: string;
  elements: Map<string, DataFormElement>;  // key == element.code
}

class DataFormElement implements Coded {
  code: string;
  type: DataFormElementType;
}

enum DataFormElementType {
  INPUT_STRING = "INPUT_STRING",
  INPUT_NUMBER = "INPUT_NUMBER",
  INPUT_EMAIL = "INPUT_EMAIL",
  INPUT_PASSWORD = "INPUT_PASSWORD",
  TEXTAREA = "TEXTAREA",
  SELECT = "SELECT",
  MULTI_SELECT = "MULTI_SELECT",
  CHECKBOX_GROUP = "CHECKBOX_GROUP",
  RADIO_GROUP = "RADIO_GROUP",
  CHECKBOX = "CHECKBOX",
  TOGGLE = "TOGGLE",
  DATE_PICKER = "DATE_PICKER",
  TIME_PICKER = "TIME_PICKER",
  DATE_TIME_PICKER = "DATE_TIME_PICKER",
  DATE_RANGE_PICKER = "DATE_RANGE_PICKER",
  SLIDER = "SLIDER",
  RATING = "RATING"
}
```

### Loading

- On application startup the backend exposes a REST endpoint
  `GET /api/app-config` that serialises the full `AppConfig` tree to JSON.
- The frontend fetches this once and stores the result in **application scope**
  (e.g. a singleton store / context), making it available to all views without
  re-fetching.
- All subsequent reads by UI components come from this in-memory store.
- Mutations (add / delete nodes) go via dedicated REST endpoints and then
  update the in-memory store to stay in sync.

---

## AppConfigEditorView

A dedicated admin view for browsing and editing the live `AppConfig` tree.

### Layout

```
┌─────────────────────────────────────────────────────────────┐
│  AppConfigEditorView                                        │
│                                                             │
│  ┌──────────────────────┐  ┌─────────────────────────────┐ │
│  │  Config Tree         │  │  Node Detail Panel          │ │
│  │                      │  │  (shown on double-click)    │ │
│  │  AppConfig           │  │                             │ │
│  │  └─ dataForms        │  │  [primitive fields / form]  │ │
│  │      ├─ userReg...   │  │                             │ │
│  │      │   └─ elements │  │                             │ │
│  │      │       ├─ fN   │  │                             │ │
│  │      │       ├─ lN   │  │                             │ │
│  │      │       └─ em   │  │                             │ │
│  │      └─ productS...  │  │                             │ │
│  │          └─ elements │  │                             │ │
│  │              ├─ kw   │  │                             │ │
│  │              └─ …    │  │                             │ │
│  └──────────────────────┘  └─────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Config Tree component

#### Node representation

The tree renders the `AppConfig` object recursively. Two kinds of tree nodes
are distinguished:

| Node kind | Label | Example label |
|---|---|---|
| **Collection node** | getter method name (camelCase, no `get` prefix) | `dataForms`, `elements` |
| **Instance node** | `code` of the `Coded` object | `userRegistration`, `firstName` |

The rendered tree for the example data looks like:

```
AppConfig                          ← instance node (root)
└─ dataForms                       ← collection node  (getter: getDataForms)
    ├─ userRegistration             ← instance node   (DataForm.code)
    │   └─ elements                 ← collection node  (getter: getElements)
    │       ├─ firstName            ← instance node   (DataFormElement.code)
    │       ├─ lastName
    │       └─ email
    └─ productSearch                ← instance node
        └─ elements
            ├─ keyword
            ├─ category
            ├─ priceMin
            └─ priceMax
```

#### Interaction

| Action | Behaviour |
|---|---|
| Single-click a node | Selects the node (highlights it) |
| Double-click a node | Opens the **Node Detail Panel** on the right for the selected node |
| Delete button / key on a selected instance node | Removes that node and all its descendants; persists the deletion via REST; updates the in-memory store |

### Node Detail Panel

Shown on the right when a tree node is double-clicked.

- Displays the **primitive composites** of the selected node — i.e. all fields
  that are not collections (enum fields, plain string fields, etc.).
- Each primitive field is rendered as an appropriate input control:
  - `String` fields → text input
  - `enum` fields → dropdown / select populated with all enum values
- The panel also provides an **"Add child"** action when the selected node is a
  collection node or an instance node that owns collection fields:
  - A form is presented to enter the `code` of the new child node plus any
    primitive fields it requires.
  - On submit the new node is persisted via `POST /api/app-config/node` and
    inserted into the in-memory store and tree.
- A **"Delete"** button on the panel removes the currently selected instance
  node (disabled for collection nodes and the root).

### REST API (mutations)

| Method | Path | Body | Description |
|---|---|---|---|
| `GET` | `/api/app-config` | — | Load full AppConfig tree |
| `POST` | `/api/app-config/node` | `{ parentObjectId, typeId, code, enumValue? }` | Add a new node |
| `DELETE` | `/api/app-config/node/{id}` | — | Delete a node and its descendants |
| `PATCH` | `/api/app-config/node/{id}` | `{ code?, enumValue? }` | Update primitive fields of a node |

### Application-scope store

```ts
// Singleton — initialised once at startup, shared by all views
class AppConfigStore {
  appConfig: AppConfig;          // in-memory tree
  load(): Promise<void>;         // fetches GET /api/app-config, populates appConfig
  addNode(...): Promise<void>;   // POST then mutates appConfig
  deleteNode(id): Promise<void>; // DELETE then mutates appConfig
  updateNode(id, ...): Promise<void>; // PATCH then mutates appConfig
}
```

---

## DataFormRenderer

A view that renders any `DataForm` stored in the `AppConfig` tree using the
application's form-renderer components.

### Behaviour

1. On mount the view fetches `GET /api/app-config` to obtain the current tree.
2. All `DataForm` instance nodes are extracted and presented in a **dropdown**
   selector at the top of the view.
3. When a `DataForm` is selected its `DataFormElement` nodes are converted to
   frontend `DataFormElement` objects (mapping the backend
   `SCREAMING_SNAKE_CASE` type value to the frontend's camelCase enum) and
   passed to the shared **FormRendererView** for rendering.
4. A **Reload** button re-fetches the AppConfig tree so changes made in the
   Config editor are reflected without a full page refresh.

### Type mapping

Backend enum values are stored in `SCREAMING_SNAKE_CASE` (e.g. `INPUT_STRING`).
The frontend `DataFormElementType` enum uses camelCase (e.g. `inputString`).
The mapping converts each value at render time:

```
INPUT_STRING      → inputString
INPUT_NUMBER      → inputNumber
INPUT_EMAIL       → inputEmail
INPUT_PASSWORD    → inputPassword
TEXTAREA          → textarea
SELECT            → select
MULTI_SELECT      → multiSelect
CHECKBOX_GROUP    → checkboxGroup
RADIO_GROUP       → radioGroup
CHECKBOX          → checkbox
TOGGLE            → toggle
DATE_PICKER       → datePicker
TIME_PICKER       → timePicker
DATE_TIME_PICKER  → dateTimePicker
DATE_RANGE_PICKER → dateRangePicker
SLIDER            → slider
RATING            → rating
```

### Default element properties

When converting an `AppConfigNode` `DataFormElement` to a frontend
`DataFormElement`, the following defaults apply:

| Property | Default |
|---|---|
| `key` | `DataFormElement.code` |
| `label` | `DataFormElement.code` |
| `cols` | `12` |
| `breakBefore` | `false` |
| `options` | `[]` |
| `min`, `max`, `rows` | `null` |
