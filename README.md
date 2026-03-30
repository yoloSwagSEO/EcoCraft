# EcoCraft

<!-- Logo placeholder -->
<!-- ![EcoCraft Logo](docs/assets/logo.png) -->

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-green?logo=mojangstudios)
![NeoForge 21.1.x](https://img.shields.io/badge/NeoForge-21.1.x-orange)
![Java 21](https://img.shields.io/badge/Java-21-blue?logo=openjdk)
![License](https://img.shields.io/badge/License-TBD-lightgrey)

Suite economique complete pour Minecraft, propulsee par NeoForge. EcoCraft apporte un systeme monetaire multi-devises, un Hotel des Ventes avec encheres en temps reel, un courrier joueur-a-joueur avec pieces jointes et contre-remboursement, le tout habille d'une interface graphique inspiree de World of Warcraft. Chaque module est independant et extensible via KubeJS.

## Modules

| Module | Description | README |
|--------|-------------|--------|
| **economy-api** | Interfaces pures (devises, comptes, transactions) — aucune implementation | [Lire](economy-api/README.md) |
| **economy-core** | Stockage SQLite, commandes `/balance` `/pay` `/eco`, permissions, bloc coffre-fort, KubeJS | [Lire](economy-core/README.md) |
| **gui-lib** | Bibliotheque de widgets GUI theme WoW : panneaux, boutons, tableaux, toasts, grilles, et plus | [Lire](gui-lib/README.md) |
| **auction-house** | Hotel des Ventes complet : buyout, encheres, taxes, multi-instances, PNJ commissaire-priseur | [Lire](auction-house/README.md) |
| **mail** | Courrier joueur-a-joueur : pieces jointes, monnaie, COD, brouillons, boite aux lettres, facteur PNJ | [Lire](mail/README.md) |

## Fonctionnalites principales

- **Economie multi-devises** — devises virtuelles et physiques (liees a des items), taux de change, solde de depart configurable
- **Hotel des Ventes (HDV)** — achat immediat, encheres avec surenchere, taxes configurables, livraison par courrier, multi-instances independantes
- **Courrier** — envoi de mails avec objets, monnaie, contre-remboursement (COD), accuses de reception, expiration automatique
- **Interface WoW** — 30+ widgets themes (panneaux, tableaux, boutons, sliders, dropdowns, toasts, grilles d'inventaire)
- **Scripting KubeJS** — evenements annulables et bindings API pour chaque module
- **Permissions granulaires** — compatible LuckPerms / FTB Ranks via NeoForge PermissionAPI
- **PNJ configurables** — commissaire-priseur et facteur avec skins de joueurs personnalisables

## Demarrage rapide

### Pre-requis

- **Minecraft** 1.21.1
- **NeoForge** 21.1.x
- **Java** 21

### Build et installation

```bash
# Compiler tous les modules
./gradlew clean build

# Copier les JARs dans le dossier mods
cp economy-api/build/libs/*.jar ~/.minecraft/mods/
cp gui-lib/build/libs/*.jar ~/.minecraft/mods/
cp economy-core/build/libs/*.jar ~/.minecraft/mods/
cp auction-house/build/libs/*.jar ~/.minecraft/mods/
cp mail/build/libs/*.jar ~/.minecraft/mods/
```

### Premiers pas en jeu

```
/balance              — Voir son solde (100 Gold par defaut)
/ah                   — Ouvrir l'Hotel des Ventes
/ah sell <prix>       — Vendre l'objet en main
/mail                 — Ouvrir la boite aux lettres
/mail send <joueur> <objet>  — Envoyer un mail
```

Les blocs et oeufs d'apparition sont disponibles dans les onglets creatifs **EcoCraft** et **EcoCraft Mail**.

## Dependances optionnelles

| Mod | Usage |
|-----|-------|
| [KubeJS](https://kubejs.com/) | Scripting serveur : evenements et API pour l'economie, l'HDV et le courrier |
| [LuckPerms](https://luckperms.net/) / FTB Ranks | Permissions granulaires (taux de taxe par joueur, limites d'annonces, etc.) |

## Screenshots

<!-- Placeholder : ajoutez vos captures d'ecran ici -->
<!--
![Hotel des Ventes](docs/assets/screenshots/auction-house.png)
![Boite aux lettres](docs/assets/screenshots/mailbox.png)
![Coffre-fort](docs/assets/screenshots/vault.png)
-->

*Captures d'ecran a venir.*

## Contribuer

Les contributions sont les bienvenues. Avant de soumettre une pull request :

1. Assurez-vous que le projet compile : `./gradlew clean build`
2. Lancez les tests : `./gradlew test`
3. Tout texte affiche au joueur doit etre en francais et utiliser `Component.translatable()`
4. Le code et les commentaires restent en anglais

## Licence

*A definir.*

## Documentation

- [economy-api](economy-api/README.md) — API et interfaces
- [economy-core](economy-core/README.md) — Implementation, commandes, configuration
- [gui-lib](gui-lib/README.md) — Widgets et systeme de theme
- [auction-house](auction-house/README.md) — Hotel des Ventes
- [mail](mail/README.md) — Systeme de courrier
