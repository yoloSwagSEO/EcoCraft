# Multi-AH Phase C: Service + Network — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire ahId through all network payloads and the AuctionService so each AH operation is scoped to a specific auction house.

**Architecture:** All scoped payloads gain a `String ahId` field. The AuctionService passes ahId to storage queries. ServerPayloadHandler reads ahId from payloads. Client sends ahId from the AH context it received at open time. AHContextPayload is sent at AH open to tell the client which AH it's viewing.

**Tech Stack:** Java 21, NeoForge network payloads, existing AuctionService patterns.

---

### Task 1: Add ahId to scoped payloads

**Files to modify:**
- `auction-house/.../network/payload/RequestListingsPayload.java`
- `auction-house/.../network/payload/RequestListingDetailPayload.java`
- `auction-house/.../network/payload/CreateListingPayload.java`
- `auction-house/.../network/payload/BuyListingPayload.java`
- `auction-house/.../network/payload/PlaceBidPayload.java`
- `auction-house/.../network/payload/RequestBestPricePayload.java`

**File to create:**
- `auction-house/.../network/payload/AHContextPayload.java`

For each scoped payload, add `String ahId` as the FIRST field in the record and update the STREAM_CODEC to encode/decode it (using `ByteBufCodecs.STRING_UTF8`).

Create `AHContextPayload(String ahId, String ahName)` — Server→Client, sent at AH open to tell client which AH.

Register `AHContextPayload` in AHNetworkHandler (playToClient), add handler in ClientPayloadHandler.

- [ ] Steps: modify all 6 payloads, create AHContextPayload, register, update handlers, fix all callers in ServerPayloadHandler and BuyTab/SellTab, verify build, commit.

---

### Task 2: Wire ahId through AuctionService

**Files to modify:**
- `auction-house/.../service/AuctionService.java`
- `auction-house/.../network/ServerPayloadHandler.java`

The service methods `createListing` and `buyListing` need to pass `ahId` through to storage.

For `createListing`: the `ahId` is already a param but the AuctionListing is built with `null` ahId — change to use the passed ahId.

For `buyListing`: add `ahId` parameter (currently not needed since we look up by listingId which is unique, but we need it for logPriceHistory).

For `placeBid`, `cancelListing`, `expireListings`: these work by listingId which is globally unique — no ahId needed in the query. But `logPriceHistory` calls need ahId from the listing.

ServerPayloadHandler reads `payload.ahId()` from each payload and passes it to the service.

- [ ] Steps: update service methods, update ServerPayloadHandler, verify build, commit.

---

### Task 3: AH context flow — client knows which AH

**Files to modify:**
- `auction-house/.../screen/AuctionHouseScreen.java`
- `auction-house/.../screen/BuyTab.java`
- `auction-house/.../screen/SellTab.java`
- `auction-house/.../network/ClientPayloadHandler.java`

AuctionHouseScreen stores `currentAhId` and `currentAhName` (received from AHContextPayload).

All client-side requests include `currentAhId`:
- BuyTab: RequestListingsPayload, RequestListingDetailPayload, BuyListingPayload, PlaceBidPayload
- SellTab: CreateListingPayload, RequestBestPricePayload

Server sends AHContextPayload alongside OpenAHPayload. The AH is determined by:
- NPC: read `linkedAhId` from entity (default if not set)
- Terminal: read from block entity (default if not set)
- Command: default AH, or specific AH if `/ah browse <slug>`

- [ ] Steps: add context fields to AuctionHouseScreen, update BuyTab/SellTab to pass ahId, update server to send AHContextPayload, verify build, deploy, commit.

---

### Testing Instructions

1. Start Minecraft — everything should work as before (all using default AH)
2. Buy/sell items — verify ahId is correctly passed in all operations
3. Check database — new listings have ah_id set to default UUID
