# Economy V2 — Système de Devises Avancé

**Date:** 2026-03-30
**Statut:** Validé
**Impact:** economy-api, economy-core, gui-lib, auction-house, mail

---

## Résumé

Refonte du système de devises pour supporter les devises composites (PP/PO/PA/PC), physiques (liées à un item), les adaptateurs de mods tiers (Numismatics...), un vault multi-devises, un bureau de change, et des composants GUI adaptés (icônes, inputs composites).

---

## 1. Modèle de Devise

### 1.1 Types de devises

| Type | Description | Exemple |
|------|-------------|---------|
| **Virtuelle simple** | Un symbole, optionnellement des décimales | `150.00 G` |
| **Virtuelle composite** | Sous-unités avec ratios arbitraires | `1 PP 5 PO 0 PA 0 PC` |
| **Physique** | Liée à un item Minecraft, dépôt/retrait possible | Pièce d'or (item craftable) |
| **Externe** | Gérée par un mod tiers via adaptateur | Spurs (Numismatics) |

### 1.2 Stockage

Toutes les devises sont stockées en **plus petite unité** sous forme de `long` :
- `1 PP 5 PO` = `1×1000 + 5×100` = `1500` en base
- Pas de flottants, pas de risque d'arrondi
- Compatible avec tous les ratios (base 10, base 8, etc.)

### 1.3 Sous-unités (SubUnit)

```java
record SubUnit(
    String code,        // "PP", "PO", "PA", "PC"
    String name,        // "Pièce de platine"
    long multiplier,    // 1000, 100, 10, 1
    @Nullable Icon icon // icône optionnelle
)
```

Exemples :

```java
// Base 10 (WoW-style)
SubUnit("PP", "Platine",  1000, Icon.texture("ecocraft:icons/platine.png"))
SubUnit("PO", "Or",       100,  Icon.texture("ecocraft:icons/or.png"))
SubUnit("PA", "Argent",   10,   Icon.texture("ecocraft:icons/argent.png"))
SubUnit("PC", "Cuivre",   1,    Icon.texture("ecocraft:icons/cuivre.png"))

// Base 8 (Numismatics-style)
SubUnit("Sun",     "Sun",      4096, Icon.item("numismatics:sun"))
SubUnit("Crown",   "Crown",    512,  Icon.item("numismatics:crown"))
SubUnit("Cog",     "Cog",      64,   Icon.item("numismatics:cog"))
SubUnit("Sprocket","Sprocket",  16,  Icon.item("numismatics:sprocket"))
SubUnit("Bevel",   "Bevel",    8,    Icon.item("numismatics:bevel"))
SubUnit("Spur",    "Spur",     1,    Icon.item("numismatics:spur"))
```

### 1.4 API Currency étendue

```java
record Currency(
    String id,
    String name,
    String symbol,
    int decimals,             // pour devises simples (0 si composite)
    boolean physical,
    @Nullable String itemId,
    @Nullable Icon icon,
    List<SubUnit> subUnits,   // vide si devise simple
    boolean exchangeable,     // autorise le change
    BigDecimal referenceRate  // taux vers la devise de référence (1.0 = référence)
)
```

**Note :** `referenceRate` utilise `BigDecimal` pour éviter les erreurs d'arrondi sur les gros montants. Le calcul de conversion se fait en `BigDecimal`, tronqué en `long` à la fin.

### 1.5 Devise de référence

- La devise de référence est **toujours** la devise par défaut d'EcoCraft (`referenceCurrency` dans la config)
- Son `referenceRate` est toujours `1.0`
- Elle ne peut pas être changée dynamiquement — c'est l'étalon du système
- Même si les admins/moddeurs ne l'utilisent pas directement, elle sert de pivot pour tous les calculs de change

### 1.5 Formatage

`CurrencyFormatter` — prend un `long` + une `Currency` et retourne le texte formaté :

- Devise simple : `"150.00 G"` ou `"150 G"` (selon decimals)
- Devise composite : `"1 PP 5 PO"` (masque les zéros)
- Avec icônes : `"1 🪙 5 🪙"` (icônes inline si disponibles)

---

## 2. Système d'Icônes (gui-lib)

### 2.1 Sources d'icônes

```java
sealed interface Icon {
    // Texture packagée dans le mod (ResourceLocation)
    static Icon texture(String resourceLocation);

    // Item Minecraft (utilise le rendu d'item natif 16x16)
    static Icon item(String itemId);

    // Fichier externe (config folder)
    static Icon file(String path);

    // Texte fallback
    static Icon text(String symbol);
}
```

### 2.2 Widgets

| Widget | Description |
|--------|-------------|
| `EcoIcon` | Affiche une icône à une taille donnée. Source = Icon |
| `EcoImage` | Widget image riche : resize, tint, tooltip, clickable |
| `IconRegistry` | Bibliothèque d'icônes built-in + registration de customs |

### 2.3 Widget `EcoCurrencyInput`

Input composite pour les devises à sous-unités :

```
      ▲           ▲           ▲           ▲
  [  1  ] 🪙PP  [  5  ] 🪙PO  [  0  ] 🪙PA  [  0  ] 🪙PC
      ▼           ▼           ▼           ▼
```

Comportement :
- Stocke un seul `long` (valeur totale en unité de base)
- Chaque flèche ▲/▼ modifie la valeur de ±`subUnit.multiplier`
- **Conversion automatique** : 10 PO → 1 PP 0 PO (renormalise à chaque changement)
- Pour devise simple : un seul champ `[ 150.00 ] G` (fallback sur `EcoNumberInput`)
- Responder : `Consumer<Long>` (valeur en unité de base)

---

## 3. Adaptateurs Mods Tiers

### 3.1 Interface

```java
interface ExternalCurrencyAdapter {
    String modId();                              // "numismatics"
    Currency getCurrency();                       // devise à enregistrer
    long getBalance(UUID player);
    boolean withdraw(UUID player, long amount);   // en unité de base
    boolean deposit(UUID player, long amount);
    boolean canAfford(UUID player, long amount);
}
```

### 3.2 Chargement

- Conditionnel via `ModList.get().isLoaded(modId)`
- `compileOnly` dependency (pas de runtime requis)
- Auto-registration au démarrage du serveur
- Les devises externes apparaissent dans le `CurrencyRegistry` comme n'importe quelle autre

### 3.3 Adaptateurs prévus

| Mod | Statut | Notes |
|-----|--------|-------|
| Create: Numismatics | Prioritaire | 6 sous-unités, NeoForge 1.21.1 |
| Autres | Via API publique | Les devs s'intègrent eux-mêmes |
| KubeJS | Via bindings | Les modpackers créent des devises custom |

### 3.4 Source de vérité

**Règle fondamentale** : l'adaptateur ne duplique JAMAIS le solde dans notre SQLite. Il **délègue** toujours au mod tiers.

- `getBalance()` → appelle l'API du mod
- `withdraw()` → appelle l'API du mod
- `deposit()` → appelle l'API du mod

Notre `EconomyProvider` détecte la devise et route vers le bon backend :
- Devise native → SQLite interne
- Devise externe → `ExternalCurrencyAdapter` du mod

Chaque adaptateur est du **cas par cas** — chaque mod a sa propre API, ses propres contraintes. L'interface `ExternalCurrencyAdapter` est le contrat commun.

### 3.5 API publique

Les mods tiers peuvent s'intégrer sans qu'on les connaisse :

```java
// Dans le mod tiers, au server start :
EcoCraftAPI.registerCurrencyAdapter(new MyModCurrencyAdapter());
```

---

## 4. Vault Block (Guichet Bancaire Universel)

### 4.1 Double fonction

- **Terminal bancaire** : UI avec boutons dépôt/retrait pour chaque devise
- **Inventaire physique** : slots pour déposer/retirer des items-devises valorisés

### 4.2 Multi-devises

- Affiche toutes les devises disponibles (natives + externes)
- Dépôt/retrait délègue au bon backend :
  - Devise native → SQLite
  - Devise externe → `ExternalCurrencyAdapter`
- Items physiques : détecte la devise liée à l'item, convertit automatiquement

### 4.3 Items acceptés dans le vault

Le vault est une **banque**, pas un coffre. Seuls 3 types d'items sont acceptés :

| Type | Description | Exemple |
|------|-------------|---------|
| **Item monnaie** | Item dont l'ID correspond au `itemId` d'une `SubUnit` | Pièce d'or (ecocraft:gold_coin) |
| **Item dans le registry de valeurs** | Valeur fixe définie en config/BDD | `minecraft:diamond = 100 PO` |
| **Item avec tag `ecocraft:value`** | Valeur portée par le composant NBT de l'item | Tableau d'art tagué à 1000 PO |

**Tout autre item est refusé** — il rebondit dans l'inventaire du joueur.

Priorité de résolution : tag `ecocraft:value` > registry de valeurs > item monnaie.

### 4.4 Dépôt d'items

- L'item est identifié (monnaie, registry, ou tag)
- Sa valeur est calculée en unité de base de la devise concernée
- L'item est retiré de l'inventaire du joueur
- Le solde est crédité

### 4.5 Retrait (ordre de priorité)

Quand le joueur dépense ou retire du solde, le système choisit **quoi retirer** :

```
1. Solde virtuel pur (pas de pièces physiques à toucher)
2. Pièces physiques : plus petites d'abord (PC → PA → PO → PP)
3. Items valorisés (registry/tag) : moins cher d'abord
4. JAMAIS un item valorisé si sa valeur > montant restant à retirer
```

**Rendu de monnaie** : si un item de 1000 doit être retiré pour payer 900, le système :
1. Retire l'item (1000)
2. Crée des pièces physiques pour la différence (100)
3. Les pièces sont ajoutées au vault

**Prérequis** : une devise physique avec `SubUnit` doit exposer au minimum une unité de base (pièce de valeur 1) pour pouvoir rendre la monnaie. Sinon le retrait est refusé si le montant exact ne peut pas être atteint.

### 4.6 Registry de valeurs d'items

Table en BDD (ou config TOML) :

```toml
[vault.itemValues]
"minecraft:diamond" = { currency = "gold", value = 100 }
"minecraft:emerald" = { currency = "gold", value = 50 }
"minecraft:netherite_ingot" = { currency = "gold", value = 1000 }
```

Aussi modifiable par KubeJS :
```js
EcoEconomy.setItemValue("minecraft:diamond", "gold", 100);
```

### 4.7 UI

- Onglets par devise (une tab par devise disponible)
- Solde affiché avec `CurrencyFormatter` (composite/icônes)
- Boutons dépôt/retrait avec `EcoCurrencyInput`
- Zone de dépôt d'items (slots) — les items non acceptés sont rejetés
- Inventaire du vault : affiche les items valorisés stockés
- Propriétaire du vault (UUID, posé par un joueur)

---

## 5. Bureau de Change

### 5.1 Fonctionnement

- Bloc ou PNJ configurable par l'admin
- UI affiche les devises changeables avec taux
- Le joueur saisit un montant source → voit le résultat converti → confirme

### 5.2 Taux de change

- Chaque devise changeable a un **taux vers la devise de référence** (`referenceRate`)
- La devise de référence = la devise par défaut du serveur (rate = 1.0)
- Taux croisés calculés automatiquement :
  ```
  Conversion A → B = montant × (rateA / rateB)
  ```
- Override possible : taux manuel entre 2 devises spécifiques
- Frais de change configurables (global ou par paire)

### 5.3 Administration

- Taux modifiables par l'admin en jeu via la UI du bureau de change
- Persistés en base de données
- Pas de fluctuation automatique (extensible via addon futur)

### 5.4 Historique de change

- Chaque conversion est loggée dans le `TransactionLog` avec le type `EXCHANGE`
- Champs : joueur, montant source, devise source, montant cible, devise cible, taux appliqué, frais
- Consultable via `/currency history` (admin) et future UI

### 5.5 Limites de change (configurable)

- **Montant minimum** par conversion (évite le spam micro-conversions)
- **Montant maximum** par conversion
- **Limite journalière** par joueur (reset à minuit serveur)
- Toutes désactivables (0 = pas de limite)

```toml
[exchange]
globalFeePercent = 2
minAmount = 0
maxAmount = 0          # 0 = illimité
dailyLimitPerPlayer = 0  # 0 = illimité
```

### 5.6 Commandes

- `/currency convert <montant> <de> <vers>` — effectue un change
- `/currency rate <devise>` — affiche le taux
- `/currency setrate <devise> <taux>` — modifie le taux (admin)
- `/currency history` — historique des conversions (admin)

---

## 6. Impact sur les Modules Existants

### 6.1 economy-api

- `Currency` : ajout `subUnits`, `icon`, `exchangeable`, `referenceRate`
- `SubUnit` : nouveau record
- `Icon` : nouvelle interface sealed
- `ExternalCurrencyAdapter` : nouvelle interface
- `ExchangeRate` : ajout persistance

### 6.2 economy-core

- `EconomyProviderImpl` : délégation aux adaptateurs selon la devise
- `ExchangeServiceImpl` : taux persistés en SQLite, calcul croisé
- `CurrencyRegistryImpl` : supporte adaptateurs externes
- Commandes multi-devises : `/balance`, `/pay`, `/eco` avec paramètre devise
- `VaultBlock` : refonte complète (terminal + inventaire)
- Bureau de change : nouveau bloc/PNJ + UI

### 6.3 gui-lib

- `EcoIcon` : nouveau widget
- `EcoImage` : nouveau widget
- `IconRegistry` : nouveau registre
- `EcoCurrencyInput` : nouveau widget composite avec ▲/▼
- `CurrencyFormatter` : nouveau utilitaire de formatage

### 6.4 auction-house

- Chaque instance AH configurée sur une devise
- Prix affichés avec le format de la devise (composite, icônes)
- Inputs de prix utilisent `EcoCurrencyInput`

### 6.5 mail

- Currency/COD avec devise au choix
- Formatage auto dans la liste, détail, compose
- `EcoCurrencyInput` dans le compose

---

## 7. Configuration

### 7.1 TOML (ecocraft-common.toml)

```toml
[economy]
# Devise de référence (taux = 1.0)
referenceCurrency = "gold"

[economy.currencies.gold]
name = "Gold"
symbol = "G"
decimals = 2
physical = false
exchangeable = false
icon = "ecocraft:textures/icons/gold.png"

[economy.currencies.platine]
name = "Système Platine"
symbol = "PP"
decimals = 0
physical = true
exchangeable = true
referenceRate = 1.0

[[economy.currencies.platine.subUnits]]
code = "PP"
name = "Pièce de platine"
multiplier = 1000
itemId = "ecocraft:platine_coin"
icon = "ecocraft:textures/icons/platine.png"

[[economy.currencies.platine.subUnits]]
code = "PO"
name = "Pièce d'or"
multiplier = 100
itemId = "ecocraft:gold_coin"
icon = "ecocraft:textures/icons/or.png"

# etc.

[exchange]
globalFeePercent = 2
```

### 7.2 KubeJS

```js
EcoEconomy.registerCurrency({
    id: "gems",
    name: "Gemmes",
    symbol: "💎",
    exchangeable: true,
    referenceRate: 10.0,
    icon: { type: "item", value: "minecraft:diamond" }
});
```

---

## 8. Phases d'implémentation suggérées

| Phase | Contenu | Dépendances |
|-------|---------|-------------|
| **1** | API Currency V2 (subUnits, icon, exchangeable, referenceRate) | Aucune |
| **2** | gui-lib : EcoIcon, EcoImage, IconRegistry | Phase 1 |
| **3** | gui-lib : EcoCurrencyInput, CurrencyFormatter | Phase 1+2 |
| **4** | economy-core : multi-devises commandes, ExchangeService persisté | Phase 1 |
| **5** | economy-core : Vault Block refonte | Phase 1+3 |
| **6** | economy-core : Bureau de change (bloc + UI) | Phase 4+3 |
| **7** | Adaptateur Numismatics | Phase 1+4 |
| **8** | Migration AH + Mail vers Currency V2 | Phase 3+4 |
| **9** | API publique pour adaptateurs tiers | Phase 7 |

---

## 9. Hors scope (futur)

- Fluctuation automatique des taux (addon)
- MySQL backend
- Marketplace de devises entre joueurs
- Devises NFT / blockchain (lol)
