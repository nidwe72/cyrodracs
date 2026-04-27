# Frontend Styling Specification

## Overview

This specification defines the centralized styling approach for the Cyrodracs Flutter frontend.
All visual decisions are captured in two places:

1. **`main.dart` `ThemeData`** — Flutter's built-in theming for Material widgets (inputs, buttons,
   cards, dialogs, data tables, typography).
2. **`app_theme.dart` `AppTheme`** — A static constants class for custom styling that cannot be
   expressed through `ThemeData` (panel headers, spacing scale, icon sizes).

The goal is a **distinguished but not overstyled** appearance with sharp geometry, consistent
spacing, and a clean visual hierarchy. Style changes should require edits in at most one of
these two files.

---

## Implementation Status

| Component | Status |
|---|---|
| ThemeData: sharp corners on inputs, buttons, cards, dialogs | Done (S1.1) |
| ThemeData: `DataTableThemeData` (headers, spacing) | Done (S1.2) |
| `AppTheme` constants class | Done (S2) |
| GRID panel pattern (header bar + table body) | Done (S3) |
| ENTITY_LIST table styling consistency | Done (S4) |
| Migrate existing magic numbers | Pending (S5, low priority) |

---

## Task S1 — ThemeData Centralization

**Goal:** Configure all Material widget themes in `main.dart`'s `ThemeData` so that sharp corners,
table styling, and consistent appearance are applied globally without per-widget overrides.

### S1.1 Sharp Corners Everywhere

Replace all `BorderRadius.circular(4)` with `BorderRadius.zero` across the theme:

```dart
ThemeData(
  // Inputs
  inputDecorationTheme: InputDecorationTheme(
    border: OutlineInputBorder(
      borderRadius: BorderRadius.zero,
      borderSide: BorderSide(color: bs.colors.secondary),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.zero,
      borderSide: BorderSide(color: bs.colors.black50, width: 2),
    ),
    // ... existing label/padding settings unchanged
  ),

  // Buttons
  elevatedButtonTheme: ElevatedButtonThemeData(
    style: ElevatedButton.styleFrom(
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      // ... existing colors unchanged
    ),
  ),
  outlinedButtonTheme: OutlinedButtonThemeData(
    style: OutlinedButton.styleFrom(
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
    ),
  ),
  textButtonTheme: TextButtonThemeData(
    style: TextButton.styleFrom(
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
    ),
  ),

  // Cards
  cardTheme: const CardThemeData(
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
  ),

  // Dialogs
  dialogTheme: const DialogThemeData(
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.zero),
  ),

  // Dropdowns
  dropdownMenuTheme: const DropdownMenuThemeData(
    inputDecorationTheme: InputDecorationTheme(
      border: OutlineInputBorder(borderRadius: BorderRadius.zero),
    ),
  ),
)
```

### S1.2 Table styling — `TrinaGrid` (post-C1)

The project's tables (ENTITY_LIST, GRID DataFormElement) render via
`TrinaGrid` from the `trina_grid` package since the C1 refactor (see
`components.md` C1, shipped 2026-04-28). Their styling is centralised
in `lib/widgets/grid/trina_grid_theme.dart`'s
`trinaGridConfigForApp(...)` helper, which builds a
`TrinaGridConfiguration` from `AppTheme` constants:

- Heights, font, border colour, header text style.
- Striping via `evenRowColor: AppTheme.tableStripeColor` /
  `oddRowColor: Colors.white` (or the inverse — the helper carries
  the project's chosen orientation).
- Column auto-size to fill the table (`autoSizeMode: scale`).
- Resize redistribution between neighbours (`resizeMode: pushAndPull`).
- Suppressed scrollbar gutter (`columnShowScrollWidth: false`) and
  zero `gridPadding` / `gridBorderWidth` so the table fills its Card
  edge to edge.

A `DataTableThemeData` block also remains in `main.dart`'s
`ThemeData` for legacy reasons (Material 3 / pre-existing widgets);
the project no longer renders Material `DataTable` directly.

---

## Task S2 — AppTheme Constants Class

**Goal:** Provide a single static constants class for custom styling values that `ThemeData`
cannot express. Located at `lib/theme/app_theme.dart`.

### S2.1 Class Definition

```dart
import 'package:flutter/material.dart';

/// Centralized styling constants for Cyrodracs.
///
/// Material widget styling is handled by ThemeData in main.dart.
/// This class covers custom styling that ThemeData cannot express.
abstract final class AppTheme {
  // ── Spacing scale ──
  static const double spacingXs = 4;
  static const double spacingSm = 8;
  static const double spacingMd = 16;
  static const double spacingLg = 24;

  // ── Icon sizing ──
  static const double iconSize = 18;

  // ── Table row striping ──
  static const Color tableStripeColor = Color(0xFFF8F9FA);  // light grey, every other row

  // ── Panel header (used by GRID and reusable for future embedded panels) ──
  static const Color panelHeaderBackground = Color(0xFFF1F3F5);
  static const EdgeInsets panelHeaderPadding =
      EdgeInsets.symmetric(horizontal: 16, vertical: 10);
  static const TextStyle panelHeaderTitle = TextStyle(
    fontWeight: FontWeight.w600,
    fontSize: 14,
    color: Colors.black87,
  );

  // ── Table column headers ──
  static const TextStyle tableHeaderStyle = TextStyle(
    fontWeight: FontWeight.w600,
    fontSize: 13,
    color: Colors.black87,
  );

  // ── Borders ──
  static const BorderSide panelBorder = BorderSide(color: Color(0xFFDEE2E6));
}
```

### S2.2 Usage Pattern

Widgets import `AppTheme` and reference constants:

```dart
import '../theme/app_theme.dart';

// Spacing
Padding(padding: const EdgeInsets.all(AppTheme.spacingMd), ...)

// Icon sizing
Icon(Icons.refresh, size: AppTheme.iconSize)
```

Table-row striping for `TrinaGrid` surfaces lives in
`lib/widgets/grid/trina_grid_theme.dart`'s
`trinaGridConfigForApp(...)` helper, which maps `tableStripeColor`
onto `TrinaGridStyleConfig.evenRowColor` / `oddRowColor`. See
`components.md` C1.

### S2.3 Scope

`AppTheme` is strictly for **values** — no widget builders, no state, no logic.
Reusable patterns that need composition (e.g. the `TrinaGrid` configuration
builder) live in `lib/widgets/`, not on `AppTheme`.

---

## Task S3 — GRID Panel Pattern

**Goal:** Restyle the GRID element (`_GridField` in `form_renderer_view.dart`) to use the
panel pattern: a visually distinct header bar separated from the table body.

### S3.1 Structure

```
Card (sharp corners, thin border via CardTheme)
├── Panel header container (AppTheme.panelHeaderBackground)
│   ├── Title text (AppTheme.panelHeaderTitle)
│   ├── Count badge ("(2)")
│   ├── Spacer
│   └── Toolbar icons (Add, Clear filters, Reload)
├── 1-px separator (Container with the form-input border colour, lightened ~30%)
├── TrinaGrid (column headers + data rows; striped per S3.3)
└── Pagination bar (panelHeaderBackground tint; right-aligned)
```

### S3.2 Panel Header

The header is a `Container` with `AppTheme.panelHeaderBackground` and
`AppTheme.panelHeaderPadding`, containing the title in `AppTheme.panelHeaderTitle`.
A 1-px `Container` separator (using a 30 %-lightened version of the
form-input border colour, derived per surface) divides it from the
column-header row. This pattern replaces the prior `Text` title that
looked indistinguishable from a data row.

### S3.3 Row Striping

Applied via `TrinaGridStyleConfig.evenRowColor` / `oddRowColor` in
`trinaGridConfigForApp(...)` (see `lib/widgets/grid/trina_grid_theme.dart`).
For the GRID surface specifically, when `rowColorCallback` is engaged
for pending-row tinting, that callback re-implements the alternation
(see `components.md` C1.11 — `rowColorCallback` overrides the style-
config zebra). The `tableStripeColor` constant on `AppTheme` is the
underlying hue.

### S3.4 Pagination Alignment

The pagination bar is **right-aligned** within the card body, keeping the visual weight
on the action side rather than centered.

### S3.5 States

All states (loading, empty, error, create-new) render inside the card below the panel header,
keeping the title visible regardless of state.

---

## Task S4 — ENTITY_LIST Table Consistency

**Goal:** Apply the same table styling (striping, header prominence) to the ENTITY_LIST
`DataTable` in `app_view.dart`, ensuring visual consistency between the two table contexts.

### S4.1 Changes

The `_buildEntityTable()` method in `app_view.dart` renders a
`TrinaGrid` (post-C1) configured via `trinaGridConfigForApp(...)`
which carries:
- Row striping via `evenRowColor` / `oddRowColor` from
  `AppTheme.tableStripeColor`.
- Header text via `AppTheme.tableHeaderStyle`.
- Borders via `Colors.grey.shade200`.

Per-widget styling is limited to icon sizing (`AppTheme.iconSize`)
and spacing (`AppTheme.spacingMd` etc.) on toolbar / cell action
icons. The grid widget itself is themed centrally, not per-call-site.

### S4.2 Header Bar

The existing entity table header (label + count + add/refresh buttons) can optionally
adopt the same panel header pattern as GRID (S3.2) for full consistency. This is a
visual alignment choice — both table contexts would use the same header bar appearance.

---

## Task S5 — Migrate Existing Magic Numbers

**Goal:** Replace scattered literal padding, icon size, and spacing values with `AppTheme`
constants across the codebase.

### S5.1 Scope

Files to update:
- `form_renderer_view.dart` — padding values, icon sizes in all field widgets
- `app_view.dart` — padding, icon sizes, spacing in entity table and edit view
- `app_config_editor_view.dart` — tree row padding, icon sizes
- `app_config_detail_panel.dart` — section spacing, icon sizes
- `data_form_renderer_view.dart` — padding in form selector bar

### S5.2 Approach

This is a mechanical find-and-replace pass. Each `const EdgeInsets.all(16)` becomes
`const EdgeInsets.all(AppTheme.spacingMd)`, each `size: 18` becomes `size: AppTheme.iconSize`,
etc. No behavioral changes — purely a readability and maintainability improvement.

### S5.3 Priority

This task is **low priority** and can be done incrementally. New code should use `AppTheme`
constants from the start; existing code is migrated opportunistically.

---

## Task Dependency Order

```
Task S1 (ThemeData)          ← Foundation: global Material styling
Task S2 (AppTheme class)     ← Foundation: custom constants
  ├── Task S3 (GRID panel)   ← Depends on S1 + S2
  ├── Task S4 (ENTITY_LIST)  ← Depends on S1 + S2
  └── Task S5 (migration)    ← Depends on S2, low priority
```

S1 and S2 can be done in parallel. S3 and S4 can be done in parallel after S1+S2.
S5 is ongoing/incremental.

---

## Cross-References

- **GRID element**: `gridElement.md` Task G1.6 (frontend rendering)
- **ENTITY_LIST table**: `viewIntegration.md` Task V3 (frontend dynamic AppView)
- **ThemeData base**: `main.dart` (existing theme configuration)
