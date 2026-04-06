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

### S1.2 DataTableThemeData

Configure table appearance globally so that all `DataTable` widgets (GRID elements, ENTITY_LIST
tables) inherit consistent styling without per-widget code:

```dart
dataTableTheme: DataTableThemeData(
  headingRowColor: WidgetStateProperty.all(Colors.grey.shade100),
  headingTextStyle: const TextStyle(
    fontWeight: FontWeight.w600,
    color: Colors.black87,
    fontSize: 13,
  ),
  dataRowColor: WidgetStateProperty.resolveWith<Color?>((states) {
    // Alternating row colors for striping
    // Note: DataTable does not natively pass row index to WidgetStateProperty,
    // so striping is applied per-widget via DataRow.color (see S3.3, S4).
    return null;
  }),
  dataTextStyle: const TextStyle(fontSize: 13),
  columnSpacing: 24,
  horizontalMargin: 16,
  decoration: const BoxDecoration(
    border: Border.fromBorderSide(BorderSide(color: Color(0xFFDEE2E6))),
  ),
),
```

**Note on row striping:** Flutter's `DataTableThemeData.dataRowColor` receives `WidgetStateProperty`
but does not provide a row index, so alternating colors cannot be set purely in the theme.
Instead, row striping is applied via `DataRow.color` at the widget level, using a color constant
from `AppTheme` (see S2.1). This is the standard Flutter pattern — the theme sets the base
appearance, individual widgets apply index-based striping.

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

  /// Returns a DataRow.color that applies striping based on row index.
  static WidgetStateProperty<Color?>? stripeColor(int index) {
    if (index.isOdd) {
      return WidgetStateProperty.all(tableStripeColor);
    }
    return null;
  }
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

// Row striping (in DataTable rows)
DataRow(
  color: AppTheme.stripeColor(index),
  cells: [...],
)
```

### S2.3 Scope

`AppTheme` is strictly for **values** — no widget builders, no state, no logic beyond
the `stripeColor` convenience. If a pattern grows complex enough to warrant a widget,
it becomes a reusable widget in `lib/theme/` or `lib/widgets/`, not a method on `AppTheme`.

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
│   └── Refresh icon button
├── Divider
└── Table body
    ├── DataTable
    │   ├── Column headers (styled via DataTableTheme + AppTheme.tableHeaderStyle)
    │   └── Data rows (striped via AppTheme.stripeColor)
    └── Pagination bar (if totalPages > 1)
```

### S3.2 Panel Header

The header is a `Container` with `AppTheme.panelHeaderBackground` and
`AppTheme.panelHeaderPadding`, containing the title in `AppTheme.panelHeaderTitle`.
A `Divider` separates it from the table body. This pattern replaces the current approach
of a `Text` title that looks indistinguishable from a data row.

### S3.3 Row Striping

Applied per-row using `DataRow.color` and `AppTheme.stripeColor(index)`:

```dart
rows: _rows.asMap().entries.map((entry) {
  final index = entry.key;
  final row = entry.value;
  return DataRow(
    color: AppTheme.stripeColor(index),
    cells: widget.tableColumns
        .map((c) => DataCell(Text('${row[c.key] ?? ''}')))
        .toList(),
  );
}).toList(),
```

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

The `_buildEntityTable()` method in `app_view.dart` applies:
- Row striping via `AppTheme.stripeColor(index)` on each `DataRow`.
- Icon sizing via `AppTheme.iconSize` on action buttons.
- Spacing via `AppTheme` constants.

The column headers and overall table decoration are inherited from the global
`DataTableThemeData` (S1.2), so no per-widget header styling is needed.

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
