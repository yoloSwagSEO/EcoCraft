# EcoCraft — Minecraft Economy Suite

## Project

NeoForge mod suite for Minecraft 1.21.1. Mono-repo with 4 Gradle modules:
- `economy-api` — pure interfaces
- `economy-core` — SQLite storage, commands, permissions, vault block
- `gui-lib` — WoW-themed GUI widgets
- `auction-house` — WoW-style Auction House (HDV)

## Tech Stack

- Java 21, NeoForge 21.1.221, ModDevGradle 2.0.141
- Parchment mappings 2024.11.17
- SQLite via xerial (bundled with jarJar in economy-core only)
- JUnit 5 for tests

## Build & Deploy

```bash
./gradlew clean build                    # Build all
./gradlew :economy-core:test             # Run economy tests
./gradlew :auction-house:test            # Run AH tests

# Deploy to Minecraft
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
```

## In-Game Testing

- `/balance` — check balance (100 Gold starting)
- `/ah` — open Auction House
- `/ah sell <price>` — quick sell item in hand
- `/summon ecocraft_ah:auctioneer` — spawn NPC (looks like a cow currently)
- Creative tab "EcoCraft" has terminal block + spawn egg

## Language

All user-facing text must be in French. Code/comments in English.

## Design Docs

- Spec: `docs/superpowers/specs/2026-03-26-minecraft-economy-suite-design.md`
- Plans: `docs/superpowers/plans/`
