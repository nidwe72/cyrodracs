# Domain Entities

## Overview

This specification documents the JPA domain model entities in the `sciens.cyrodracs.camera` package.
All entities use `GenerationType.IDENTITY` for primary keys and reside in the `camera` sub-package.

---

## Entities

### CameraProducer

Represents a camera manufacturer.

| Field           | Type        | Column           | Description                                  |
|-----------------|-------------|------------------|----------------------------------------------|
| id              | Long        | id               | Primary key (auto-generated)                 |
| name            | String      | name             | Producer name (e.g. "ZeissIkon", "Pentax")   |
| foundationYear  | YearMonth   | foundation_year  | Year the company was founded                 |
| shutdownYear    | YearMonth   | shutdown_year    | Year the company shut down (nullable)        |

**Table:** `CAMERA_PRODUCER`

**Example rows:**

| id | name       | foundation_year | shutdown_year |
|----|------------|-----------------|---------------|
| 1  | ZeissIkon  | 1926-01         | 1972-01       |
| 2  | Pentax     | 1919-01         | NULL          |
| 3  | Praktica   | 1919-01         | NULL          |
| 4  | Fuji       | 1934-01         | NULL          |

---

### CameraLensMount

Represents a lens mount standard. A lens mount is originally designed by a specific producer
but may be adopted by other producers (see `CameraLensMount2CameraProducer`).

| Field    | Type            | Column      | Description                                       |
|----------|-----------------|-------------|---------------------------------------------------|
| id       | Long            | id          | Primary key (auto-generated)                      |
| name     | String          | name        | Mount name (e.g. "M42", "K-mount")                |
| producer | CameraProducer  | producer_id | @ManyToOne -- the original creator of this mount   |

**Table:** `CAMERA_LENS_MOUNT`

**Relationships:**
- `producer` -> `CameraProducer` (@ManyToOne): The producer that originally designed this mount.

**Example rows:**

| id | name    | producer_id |
|----|---------|-------------|
| 1  | M42     | 1           |
| 2  | K-mount | 2           |
| 3  | X-Mount | 4           |

**Example:** The CameraLensMount with name "M42" has CameraProducer with name "ZeissIkon".

---

### CameraLensMount2CameraProducer

Mapping entity that associates a `CameraLensMount` with additional `CameraProducer`s that
also use (or have used) that mount. This is needed because a lens mount can be adopted by
producers other than its original creator.

For example, the M42 mount was created by ZeissIkon but is also used by Pentax and Praktica.

| Field          | Type             | Column              | Description                                      |
|----------------|------------------|----------------------|--------------------------------------------------|
| id             | Long             | id                   | Primary key (auto-generated)                     |
| cameraLensMount| CameraLensMount  | camera_lens_mount_id | @ManyToOne -- the lens mount                     |
| cameraProducer | CameraProducer   | camera_producer_id   | @ManyToOne -- a producer that uses this mount     |

**Table:** `CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER`

**Relationships:**
- `cameraLensMount` -> `CameraLensMount` (@ManyToOne): The lens mount being adopted.
- `cameraProducer` -> `CameraProducer` (@ManyToOne): The producer that uses this mount.

**Unique constraint:** (`camera_lens_mount_id`, `camera_producer_id`) -- each producer can be
linked to a given mount only once.

**Example rows:**

| id | camera_lens_mount_id | camera_producer_id |
|----|----------------------|--------------------|
| 1  | 1 (M42)              | 2 (Pentax)         |
| 2  | 1 (M42)              | 3 (Praktica)       |
| 3  | 3 (X-Mount)          | 4 (Fuji)           |
| 4  | 1 (M42)              | 4 (Fuji)           |

**Note:** Every producer that uses a mount gets a row here, including the original creator.
This gives a complete picture per producer (see `gridElement.md` Task G2 for the Fuji example).

---

### Camera

Represents a camera model.

| Field       | Type            | Column       | Description                              |
|-------------|-----------------|--------------|------------------------------------------|
| id          | Long            | id           | Primary key (auto-generated)             |
| name        | String          | name         | Camera model name (e.g. "K1000")         |
| releaseYear | YearMonth       | release_year | Release date (year-month via converter)  |
| producer    | CameraProducer  | producer_id  | @ManyToOne -- the camera's manufacturer  |

**Table:** `CAMERA`

**Relationships:**
- `producer` -> `CameraProducer` (@ManyToOne): The manufacturer of this camera.

---

## Entity Relationship Diagram

```
CameraProducer
  |
  |--- 1:N ---> Camera.producer
  |
  |--- 1:N ---> CameraLensMount.producer  (original creator)
  |
  |--- N:M ---> CameraLensMount           (via CameraLensMount2CameraProducer)


CameraLensMount
  |
  |--- N:1 ---> CameraProducer            (original creator)
  |
  |--- 1:N ---> CameraLensMount2CameraProducer


CameraLensMount2CameraProducer
  |
  |--- N:1 ---> CameraLensMount
  |--- N:1 ---> CameraProducer
```

---

## Tasks

### Task D1 -- Add `producer` relationship to CameraLensMount

**Goal:** Extend `CameraLensMount` with a `@ManyToOne` reference to `CameraProducer`,
representing the original creator of the lens mount.

#### D1.1 Entity change

```java
@Entity
@Table(name = "CAMERA_LENS_MOUNT")
public class CameraLensMount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @ManyToOne
    @JoinColumn(name = "producer_id")
    private CameraProducer producer;

    // getters / setters
}
```

#### D1.2 DataFormEntityType

`CAMERA_LENS_MOUNT` is already registered in `DataFormEntityType`.

---

### Task D2 -- Create CameraLensMount2CameraProducer mapping entity

**Goal:** Introduce a new JPA entity that maps a `CameraLensMount` to additional
`CameraProducer`s that use that mount (beyond the original creator).

#### D2.1 Entity

```java
@Entity
@Table(name = "CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"camera_lens_mount_id", "camera_producer_id"}))
public class CameraLensMount2CameraProducer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "camera_lens_mount_id")
    private CameraLensMount cameraLensMount;

    @ManyToOne
    @JoinColumn(name = "camera_producer_id")
    private CameraProducer cameraProducer;

    // getters / setters
}
```

#### D2.2 DataFormEntityType

Add new entry:

```java
CAMERA_LENS_MOUNT_2_CAMERA_PRODUCER("sciens.cyrodracs.camera.CameraLensMount2CameraProducer")
```

---

## Implementation Order

```
D1  (add producer to CameraLensMount)
 |
 v
D2  (create CameraLensMount2CameraProducer mapping entity)
```

## Cross-References

- **GRID element** displaying CameraLensMount2CameraProducer in editor: `gridElement.md`
- **Expression system** for dynamic filtering by current editor entity: `expressions.md`
- **DataFormEntityType** enum registration: `dataForms.md`
- **EntityProvider / FilterNode** for querying mapping rows: `dataBinding.md` Task 6
