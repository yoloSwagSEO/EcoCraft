# EcoCraft Auction House

Hotel des Ventes (HDV) style World of Warcraft pour Minecraft 1.21.1 (NeoForge). Ce module fournit un systeme d'encheres complet avec achat immediat (buyout), encheres avec surenchere, taxes configurables, livraison par courrier, et support multi-instances.

## Fonctionnalites

- **Achat immediat (Buyout)** — Mise en vente a prix fixe, achat en un clic
- **Encheres (Auction)** — Systeme d'encheres avec surenchere, historique des offres, notifications temps reel
- **Multi-instances** — Plusieurs hotels des ventes independants, chacun avec sa propre configuration (taxes, durees, modes)
- **PNJ Commissaire-priseur** — Entite configurable avec skin de joueur personnalisable, liee a une instance d'HDV
- **Systeme de taxes** — Taxe a la vente et depot configurable par instance et par joueur (via permissions)
- **Modes de livraison** — `DIRECT` (inventaire), `MAILBOX` (courrier via module mail), ou `BOTH`
- **Delais de livraison** — Delai configurable pour les achats et les objets expires
- **Notifications** — Toasts en jeu (surenchere, vente, expiration, etc.) avec file d'attente pour joueurs hors ligne
- **Colis a recuperer** — Les objets achetes/expires sont stockes en colis, recuperables via l'HDV ou `/ah collect`
- **Grand livre (Ledger)** — Historique des transactions avec details
- **Recherche et categories** — Filtrage par nom, categorie d'objet, enchantements
- **Integration KubeJS** — Evenements et API pour scripts serveur
- **Integration courrier** — Livraison automatique via le module mail (si charge)

## Commandes

| Commande | Description |
|---|---|
| `/ah` | Ouvre l'interface de l'Hotel des Ventes |
| `/ah sell <prix>` | Met en vente l'objet en main au prix indique (buyout, 24h) |
| `/ah search <terme>` | Ouvre l'HDV (pre-remplissage de recherche prevu) |
| `/ah browse <slug>` | Ouvre une instance d'HDV specifique par son slug |
| `/ah collect` | Recupere tous les colis en attente dans l'inventaire |
| `/ah admin reload` | Recharge la configuration (admin) |
| `/ah admin expire` | Force l'expiration des annonces echues (admin) |
| `/ah admin create <nom>` | Cree une nouvelle instance d'HDV (admin) |
| `/ah admin delete <slug>` | Supprime une instance (transfere les annonces vers l'HDV par defaut) (admin) |
| `/ah admin list` | Liste toutes les instances d'HDV avec le nombre d'annonces actives (admin) |
| `/ah admin rename <slug> <nouveau_nom>` | Renomme une instance d'HDV (admin) |
| `/ah testnotif <type>` | Envoie une notification de test — types: `outbid`, `auction_won`, `auction_lost`, `sale_completed`, `listing_expired` (admin) |
| `/ah testtoast` | Envoie un toast de test (admin) |
| `/ah testbids` | Injecte 5 encheres factices sur la premiere annonce de type AUCTION (admin) |

## Permissions

Toutes les permissions utilisent le prefixe `ecocraft.ah`. Compatible avec tout systeme de permissions NeoForge (LuckPerms, etc.).

### Permissions booleennes

| Noeud | Par defaut | Description |
|---|---|---|
| `ecocraft.ah.use` | `true` (tous) | Autorise l'acces a l'Hotel des Ventes |
| `ecocraft.ah.sell` | `true` (tous) | Autorise la mise en vente d'objets |
| `ecocraft.ah.bid` | `true` (tous) | Autorise les encheres |
| `ecocraft.ah.cancel` | `true` (tous) | Autorise l'annulation de ses propres annonces |
| `ecocraft.ah.admin.cancel` | OP niveau 2 | Annulation de n'importe quelle annonce |
| `ecocraft.ah.admin.settings` | OP niveau 2 | Acces a l'ecran de parametres admin |
| `ecocraft.ah.admin.reload` | OP niveau 2 | Commandes d'administration (`/ah admin`, `/ah testnotif`, etc.) |

### Permissions numeriques (Integer)

| Noeud | Par defaut | Description |
|---|---|---|
| `ecocraft.ah.max_listings` | `-1` (illimite) | Nombre maximum d'annonces actives par joueur |
| `ecocraft.ah.tax_rate` | `-1` (config HDV) | Taux de taxe personnalise (%) — `-1` utilise la valeur de l'instance |
| `ecocraft.ah.deposit_rate` | `-1` (config HDV) | Taux de depot personnalise (%) — `-1` utilise la valeur de l'instance |

## Configuration

Fichier de configuration serveur NeoForge (`ecocraft_ah-server.toml`) :

```toml
[taxes]
# Taux de taxe a la vente en pourcentage (0-100)
saleRate = 5
# Taux de depot en pourcentage (0-100)
depositRate = 2

[listings]
# Durees disponibles en heures
durations = [12, 24, 48]

[delivery]
# Mode de livraison global : DIRECT, MAILBOX, ou BOTH
deliveryMode = "DIRECT"
```

Ces valeurs servent de defaut pour l'instance HDV par defaut. Chaque instance peut ensuite etre configuree individuellement via l'ecran de parametres en jeu.

### Configuration par instance (en jeu)

Chaque instance d'HDV possede ses propres reglages modifiables via l'ecran parametres (icone engrenage, admin uniquement) :

- **Nom** de l'instance
- **Taux de taxe** et **taux de depot**
- **Durees** autorisees
- **Activer/desactiver** le buyout et les encheres
- **Destinataire des taxes** (joueur qui recoit les taxes)
- **Surcharge des permissions** de taxe (ignorer les permissions joueur)
- **Delai de livraison** pour les achats et les objets expires (en minutes)

## Integration KubeJS

Le module expose des evenements et des bindings pour les scripts KubeJS.

### Evenements

Groupe d'evenements : `EcocraftAHEvents`

| Evenement | Type | Annulable | Description |
|---|---|---|---|
| `listingCreating` | server | Oui | Avant la creation d'une annonce — `cancel()` pour bloquer |
| `listingCreated` | server | Non | Apres la creation d'une annonce |
| `buying` | server | Oui | Avant un achat — `cancel()` pour bloquer |
| `sold` | server | Non | Apres une vente (inclut les infos de taxe) |
| `bidPlacing` | server | Oui | Avant une enchere — `cancel()` pour bloquer |
| `bidPlaced` | server | Non | Apres une enchere |
| `auctionWon` | server | Non | Quand un joueur remporte une enchere |
| `auctionLost` | server | Non | Quand un joueur perd une enchere |
| `listingCancelling` | server | Oui | Avant l'annulation — `cancel()` pour bloquer |
| `listingCancelled` | server | Non | Apres l'annulation d'une annonce |
| `listingExpired` | server | Non | Quand une annonce expire |

### Exemple d'utilisation

```js
// Bloquer la vente de diamants
EcocraftAHEvents.listingCreating(event => {
    if (event.getItemId() === 'minecraft:diamond') {
        event.cancel();
        event.setMessage('Les diamants ne peuvent pas etre vendus !');
    }
});

// Logger toutes les ventes
EcocraftAHEvents.sold(event => {
    console.log(`${event.getBuyer().name.string} a achete ${event.getQuantity()}x ${event.getItemName()} pour ${event.getTotalPrice()} (taxe: ${event.getTax()})`);
});
```

### Binding API

Le binding `AHAuctions` est disponible dans les scripts :

```js
// Lister les annonces actives d'une instance
let listings = AHAuctions.getListings(ahId);

// Chercher par ID d'objet
let results = AHAuctions.getListingsByItem(ahId, 'minecraft:diamond_sword');

// Statistiques d'un joueur (totalSalesRevenue, totalPurchases, taxesPaid)
let stats = AHAuctions.getPlayerStats(player);

// Annonces actives d'un joueur
let myListings = AHAuctions.getPlayerListings(player);

// Meilleur prix pour un objet
let bestPrice = AHAuctions.getBestPrice('minecraft:diamond');

// Annuler une annonce (admin)
AHAuctions.cancelListing(listingId);
```

## Utilisation en jeu

### Mise en place

1. **Invoquer un PNJ** : `/summon ecocraft_ah:auctioneer` ou utiliser l'oeuf d'apparition dans l'onglet creatif "EcoCraft"
2. **Configurer le PNJ** : Clic droit pour ouvrir l'HDV, puis icone engrenage (admin) pour acceder aux parametres :
   - Changer le skin du PNJ (nom de joueur Minecraft)
   - Lier le PNJ a une instance d'HDV specifique
3. **Creer des instances** : `/ah admin create Mon HDV Speciale` cree une nouvelle instance avec son propre slug
4. **Vendre un objet** : Ouvrir l'HDV > onglet "Vendre" > choisir prix, type (buyout/enchere), duree
5. **Acheter** : Onglet "Acheter" > parcourir/rechercher > acheter ou encherir
6. **Recuperer ses colis** : Onglet "Mes Encheres" ou `/ah collect`

### Onglets de l'interface

- **Acheter** — Parcourir et rechercher les annonces, filtrer par categorie, acheter ou encherir
- **Vendre** — Mettre en vente un objet de l'inventaire
- **Mes Encheres** — Voir ses annonces actives, recuperer les colis
- **Grand Livre** — Historique complet des transactions
- **Notifications** — Consulter les notifications recues (configurable dans les parametres)

## Dependances

| Module | ID | Requis |
|---|---|---|
| economy-api | `ecocraft_api` | Oui — interfaces d'economie |
| economy-core | `ecocraft_core` | Oui — stockage SQLite, systeme monetaire, commandes eco |
| gui-lib | `ecocraft_gui` | Oui — widgets WoW-themed (EcoScreen, EcoTabBar, EcoButton, etc.) |
| mail | `ecocraft_mail` | Non — active la livraison par courrier si present |
| KubeJS | `kubejs` | Non — active les evenements et bindings si present |
