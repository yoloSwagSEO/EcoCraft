# Multi-AH Phase D: Settings Screen Refactor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the AH settings screen to a two-column layout with sidebar tabs (Général + one per AH) and per-AH configuration, including create/delete AH.

**Architecture:** AHSettingsScreen becomes a two-panel layout: left sidebar with tab buttons, right content panel that changes based on selected tab. Server sends list of all AH instances to admin. New payloads for CRUD operations on AH instances.

**Tech Stack:** Java 21, NeoForge, gui-lib widgets (Dropdown, Slider, Repeater, TextInput, Button).

---

### Task 1: Server-side — AH instances list payload + CRUD payloads

**Files to create/modify:**
- Create: `auction-house/.../network/payload/AHInstancesPayload.java` (S→C, list of all AHs)
- Create: `auction-house/.../network/payload/CreateAHPayload.java` (C→S)
- Create: `auction-house/.../network/payload/DeleteAHPayload.java` (C→S, ahId + deleteMode)
- Create: `auction-house/.../network/payload/UpdateAHInstancePayload.java` (C→S, replaces UpdateAHSettingsPayload)
- Modify: `auction-house/.../network/AHNetworkHandler.java` (register new payloads)
- Modify: `auction-house/.../network/ServerPayloadHandler.java` (handlers + send instances on settings open)
- Modify: `auction-house/.../network/ClientPayloadHandler.java` (receive instances)
- Modify: `auction-house/.../screen/AuctionHouseScreen.java` (store AH instances list)

AHInstancesPayload contains: `List<AHInstanceData>` where AHInstanceData is `(String id, String slug, String name, int saleRate, int depositRate, List<Integer> durations)`.

Sent alongside AHSettingsPayload when admin opens AH. The admin gets the full list of AH instances.

Server handlers:
- `handleCreateAH`: creates new AHInstance in storage, replies with success + re-sends instances list
- `handleDeleteAH`: validates not default, applies delete mode (RETURN_ITEMS/DELETE_LISTINGS/TRANSFER_TO_DEFAULT), deletes from storage, re-sends instances
- `handleUpdateAHInstance`: updates name/saleRate/depositRate/durations for one AH, re-sends instances

- [ ] Create payloads, register, implement handlers, verify build, commit.

---

### Task 2: Rewrite AHSettingsScreen with sidebar + panels

**Files to modify:**
- Rewrite: `auction-house/.../screen/AHSettingsScreen.java`

Complete rewrite with two-column layout:

**Constructor** receives: `Screen parent, int npcEntityId, String skinPlayerName, String currentAhId, List<AHInstanceData> ahInstances`

**Left sidebar (20% width):**
- Title "Configuration"
- Button "Général" (always first)
- One button per AH instance (labeled with AH name)
- Button "+ Créer un AH" at bottom
- Selected tab highlighted with accent color

**Right panel (80% width):**
- Content changes based on selected sidebar tab
- "Général" tab: skin TextInput (if NPC) + Dropdown to select which AH the NPC uses
- Per-AH tab: name TextInput + sale Slider + deposit Slider + durations Repeater + delete button (if not default)

**State management:**
- `selectedTab` index (0 = Général, 1+ = AH instances)
- Each AH has its own edited state (name, saleRate, depositRate, durations)
- Save sends updates for ALL modified AHs

**Key behaviors:**
- Renaming an AH updates the sidebar button label
- Creating an AH adds a new sidebar button and selects it
- Deleting an AH opens a Dialog with 3 options, then removes the tab
- Tab switching rebuilds the right panel only (not the whole screen)

For tab switching: clear right panel widgets and rebuild. Use a helper `initRightPanel()` that creates widgets based on `selectedTab`.

- [ ] Rewrite AHSettingsScreen, verify build, deploy, commit.

---

### Task 3: Wire NPC/Terminal AH binding

**Files to modify:**
- Modify: `auction-house/.../entity/AuctioneerEntity.java` (add linkedAhId field)
- Modify: `auction-house/.../network/ServerPayloadHandler.java` (read linkedAhId for AHContext)
- Modify: `auction-house/.../network/payload/UpdateNPCSkinPayload.java` (add linkedAhId)

AuctioneerEntity gets `linkedAhId` NBT field (String, default = AHInstance.DEFAULT_ID). Saved/loaded in NBT. The "Général" tab in settings has a Dropdown to change it.

When a player interacts with the NPC, `sendAHContext` uses `npc.getLinkedAhId()` instead of `AHInstance.DEFAULT_ID`.

UpdateNPCSkinPayload expanded to also carry `linkedAhId` (rename to `UpdateNPCPayload`?). Or create a separate payload.

- [ ] Add linkedAhId to entity, update AHContext sending, update settings save, verify, commit.

---

### Testing Instructions

1. Open AH via NPC → gear icon → settings screen with sidebar
2. "Général" tab shows skin field + AH dropdown
3. "Default" tab shows name + sliders + repeater
4. "+ Créer un AH" → new tab appears
5. Rename the new AH → sidebar label changes
6. Delete the new AH → dialog with 3 options → tab removed
7. Change NPC AH binding → save → NPC now opens different AH
8. All changes persist after restart
