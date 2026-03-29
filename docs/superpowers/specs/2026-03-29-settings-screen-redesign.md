# AH Settings Screen Redesign — Design Spec

## Overview

Redesign the AH Settings screen (`AHSettingsScreen`) to use the EcoGrid 12-column layout system instead of full-width manual positioning. Uses Panel sections with uppercase titles for visual grouping (style "fieldgroups").

## Problem

Current layout stretches all inputs (text fields, sliders, repeaters) across the full panel width (~1100px), which looks bad on modern monitors. No visual grouping of related settings.

## Layout — Approved Mockup v3

**Structure:** Left sidebar (unchanged, 20%) + right panel using EcoGrid.

### Right Panel — Per-AH Tab

4 themed sections (Panel with `titleUppercase(true)` and `padding(10)`):

#### Section 1: "Identité"
- Grid row: name input on **col-6** (same width as tax recipient field)

#### Section 2: "Modes de vente"
- Grid row: content constrained to **col-4**
- Two lines, each with label (left) + toggle (right-aligned)
- Line 1: "Achat immédiat" + toggle
- Line 2: "Enchères" + toggle

#### Section 3: "Taxes"
- Grid row 1: two sliders side-by-side on **col-6 + col-6**
  - "Taxe sur les ventes" (0-100%, step 1)
  - "Dépôt" (0-100%, step 1)
- Grid row 2: tax recipient input on **col-6**

#### Section 4: "Durées de listing"
- Grid row: repeater centered on **col-6 offset-3**

#### Delete button
- Below sections, only for non-default AH

### Right Panel — General Tab (NPC config)
- Unchanged layout (skin input + AH dropdown), but wrapped in EcoGrid for consistency

### Footer
- Centered: Annuler (ghost) + Sauvegarder (success)

## Components Used

- `EcoGrid` + `EcoRow` + `EcoCol` — 12-column layout
- `Panel` — section containers with `titleUppercase(true)`, `padding(10)`
- `EcoTextInput` — name, tax recipient, skin
- `EcoToggle` — buyout/auction toggles
- `EcoSlider` — tax rates
- `EcoRepeater<Integer>` — durations
- `EcoDropdown` — AH linking
- `EcoButton` — sidebar, footer, delete, create
- `Label` — field labels

## Grid Column Mapping

| Section | Content | Columns |
|---------|---------|---------|
| Identité — name | EcoTextInput | col-6 |
| Modes de vente | 2× (Label + Toggle) | col-4 |
| Taxes — sliders | 2× EcoSlider | col-6 + col-6 |
| Taxes — recipient | EcoTextInput | col-6 |
| Durées — repeater | EcoRepeater | col-6 offset-3 |

## Files Changed

| File | Action |
|------|--------|
| `gui-lib/.../core/Panel.java` | Already enhanced (padding, titleUppercase, content helpers) |
| `auction-house/.../screen/AHSettingsScreen.java` | Rewrite right panel layout using EcoGrid |

## Text (French)

- "Identité" (section)
- "Modes de vente" (section)
- "Taxes" (section)
- "Durées de listing" (section)
- All existing labels unchanged
