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

| Field                          | Type                         | Column                            | Description                                              |
|--------------------------------|------------------------------|-----------------------------------|----------------------------------------------------------|
| id                             | Long                         | id                                | Primary key (auto-generated)                             |
| name                           | String                       | name                              | Camera model name (e.g. "K1000")                         |
| releaseYear                    | YearMonth                    | release_year                      | Release date (year-month via converter)                  |
| producer                       | CameraProducer               | producer_id                       | @ManyToOne -- the camera's manufacturer                  |
| photoEquipmentMarketSegment    | PhotoEquipmentMarketSegment  | photo_equipment_market_segment    | Market-tier segment (entry / enthusiast / prosumer / pro) |

**Table:** `CAMERA`

**Relationships:**
- `producer` -> `CameraProducer` (@ManyToOne): The manufacturer of this camera.

---

## Enumerations

### PhotoEquipmentMarketSegment

Market-tier segmentation for photo equipment. The four-tier breakdown
matches the canonical photography-press taxonomy (entry → enthusiast →
prosumer → professional) and has no internal overlap.

This is **one shared enum across photo-equipment types** that have a
real market-tier story — cameras (initial consumer; this spec) and
later tripods, lenses, flashes (any product line that's segmented by
target user). Equipment types whose nature is *standards / parts /
formats* — `CameraLensMount` is the obvious example, since a mount is
a coupling standard, not a tier — do **not** get a segment field.
That distinction is intentional: only attach
`PhotoEquipmentMarketSegment` where the four constants meaningfully
classify the entity.

Field-naming convention follows the project's "members reflect their
type" rule, and because the enum is shared the field name is the
same on every consuming entity:
`Camera.photoEquipmentMarketSegment`,
`Tripod.photoEquipmentMarketSegment`, and so on.

| Constant       | Description                                                                  |
|----------------|------------------------------------------------------------------------------|
| `ENTRY_LEVEL`  | Beginner / kit-lens consumer bodies (e.g. Canon Rebel, Nikon D3xxx).         |
| `ENTHUSIAST`   | Serious-amateur bodies (e.g. Nikon D7xxx, Fuji X-T30).                       |
| `PROSUMER`     | High-end semi-pro bodies (e.g. Canon 5D, Nikon D850, Fuji X-T5).             |
| `PROFESSIONAL` | Flagship working bodies (e.g. Canon 1D-X, Nikon D6, Sony a1, Fuji X-H2S).    |

**Java location:** `sciens.cyrodracs.camera.PhotoEquipmentMarketSegment`
(same package as the only current consumer, `Camera`). When a second
consumer arrives outside the camera package — a `Tripod` entity, for
instance — relocate to a shared `sciens.cyrodracs.photoequipment`
package so both consumers reference one canonical location.

**Used by:**
- `Camera.photoEquipmentMarketSegment` (this spec, Task D3).
- Surfaced as a column on the `cameras` ENTITY_LIST — see
  `viewIntegration.md` V1.3 (`col_photoEquipmentMarketSegment`).
- Canonical example for the future ENUM picker-restriction work
  flagged in `columnFilters.md` CF3.4.3 *Known limitations*. With this
  field in place, filtering the `cameras` ENTITY_LIST by producer
  produces a meaningfully-restricted segment set (e.g. Polaroid →
  mostly `ENTRY_LEVEL`; Hasselblad → mostly `PROFESSIONAL`; Fuji
  spans 3–4 tiers), exposing the asymmetry the future feature is
  meant to fix.

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

### Task D3 -- Add `PhotoEquipmentMarketSegment` enum and `Camera.photoEquipmentMarketSegment` field

**Goal:** Introduce a market-tier enum for camera bodies and add a
`photoEquipmentMarketSegment` attribute on `Camera`. See *Enumerations
→ PhotoEquipmentMarketSegment* above for constants and rationale.

The field name fully echoes the enum type — per the project's
"members reflect their type" convention. Because the enum is shared
across photo equipment (see *Enumerations →
PhotoEquipmentMarketSegment*), every consuming entity carries the
same field name: `Camera.photoEquipmentMarketSegment`,
`Tripod.photoEquipmentMarketSegment`, and so on.

#### D3.1 Enum

```java
package sciens.cyrodracs.camera;

public enum PhotoEquipmentMarketSegment {
    ENTRY_LEVEL,
    ENTHUSIAST,
    PROSUMER,
    PROFESSIONAL
}
```

#### D3.2 `Camera` entity change

```java
@Entity
@Table(name = "CAMERA")
public class Camera {

    // existing fields ...

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_equipment_market_segment")
    private PhotoEquipmentMarketSegment photoEquipmentMarketSegment;

    // getter / setter
}
```

`EnumType.STRING` chosen over `EnumType.ORDINAL` so that:
- The DB column carries human-readable values (`PROSUMER`, not `2`).
- Reordering or inserting future constants does not silently shift
  existing rows.

#### D3.3 Test data

Backfill existing camera seed rows with a plausible
`photoEquipmentMarketSegment` value each, chosen so that filtering
the `cameras` ENTITY_LIST by producer visibly restricts the segment
set (the asymmetry the future ENUM picker-restriction feature in
`columnFilters.md` CF3.4.3 *Known limitations* is meant to fix).
Concrete assignments are seed-only; no constraint that every constant
be represented per producer.

#### D3.4 No DataFormEntityType change

`PhotoEquipmentMarketSegment` is an enum, not a `@Entity`; the
`DataFormEntityType` registry covers JPA entities only. No entry is
added.

#### D3.5 Surface on the `cameras` ENTITY_LIST

The new field is added as a column on the `cameras` ViewNode — see
`viewIntegration.md` V1.3 (`col_photoEquipmentMarketSegment`, key
`photoEquipmentMarketSegment`, header `"MarketSegment"`). With CF3 in
place the column auto-resolves to `ColumnFilterType.ENUM`, giving
users a dropdown filter populated from the enum constants. This also
enables the canonical demo for the future ENUM-restriction work
(CF3.4.3 *Known limitations*).

---

## Implementation Order

```
D1  (add producer to CameraLensMount)
 |
 v
D2  (create CameraLensMount2CameraProducer mapping entity)
 |
 v
D3  (PhotoEquipmentMarketSegment enum + Camera.photoEquipmentMarketSegment field)
```

## Cross-References

- **GRID element** displaying CameraLensMount2CameraProducer in editor: `gridElement.md`
- **Expression system** for dynamic filtering by current editor entity: `expressions.md`
- **DataFormEntityType** enum registration: `dataForms.md`
- **EntityProvider / FilterNode** for querying mapping rows: `dataBinding.md` Task 6
- **`cameras` ViewNode** that surfaces `Camera.photoEquipmentMarketSegment`
  as a column: `viewIntegration.md` V1.3 (`col_photoEquipmentMarketSegment`).
- **Future ENUM picker restriction** that will use
  `Camera.photoEquipmentMarketSegment` as its canonical example:
  `columnFilters.md` CF3.4.3 *Known limitations*.
