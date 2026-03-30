# EcoCraft Economy Core

Implementation principale de la suite economique EcoCraft. Fournit le stockage SQLite, les commandes en jeu, le systeme de permissions, le bloc coffre-fort, et l'integration KubeJS.

## Mod ID

`ecocraft_core`

## Fonctionnalites

- **Stockage SQLite** avec migrations automatiques (support MySQL configurable)
- **Systeme multi-devises** avec devises virtuelles et physiques
- **Commandes** pour les joueurs et les administrateurs
- **Permissions** via l'API NeoForge PermissionAPI
- **Bloc coffre-fort (Vault)** pour synchroniser devises physiques et virtuelles
- **Integration KubeJS** avec evenements et bindings
- **Solde de depart configurable** pour les nouveaux joueurs

## Commandes

### Commandes joueurs

| Commande | Description | Permission |
|----------|-------------|------------|
| `/balance` | Afficher son propre solde | `ecocraft.balance` (tous) |
| `/bal` | Alias pour `/balance` | `ecocraft.balance` (tous) |
| `/balance of <joueur>` | Voir le solde d'un autre joueur (en ligne ou hors ligne) | `ecocraft.balance.others` (OP 2) |
| `/balance list` | Classement de tous les soldes | `ecocraft.balance.list` (tous) |
| `/pay <joueur> <montant>` | Envoyer de l'argent a un joueur | `ecocraft.pay` (tous) |
| `/currency list` | Lister toutes les devises disponibles | Aucune |
| `/currency convert <montant> <de> <vers>` | Convertir entre devises | `ecocraft.exchange` (tous) |

### Commandes administrateur

| Commande | Description | Permission |
|----------|-------------|------------|
| `/eco give <joueur> <montant>` | Donner de l'argent a un joueur | `ecocraft.admin.give` (OP 2) |
| `/eco take <joueur> <montant>` | Retirer de l'argent a un joueur | `ecocraft.admin.take` (OP 2) |
| `/eco set <joueur> <montant>` | Definir le solde d'un joueur | `ecocraft.admin.set` (OP 2) |

## Permissions

Toutes les permissions utilisent l'API NeoForge `PermissionAPI`. Les valeurs par defaut entre parentheses.

| Noeud | Description | Par defaut |
|-------|-------------|------------|
| `ecocraft.balance` | Voir son propre solde | `true` (tous) |
| `ecocraft.balance.others` | Voir le solde des autres joueurs | OP niveau 2 |
| `ecocraft.balance.list` | Voir le classement des soldes | `true` (tous) |
| `ecocraft.pay` | Envoyer de l'argent | `true` (tous) |
| `ecocraft.exchange` | Convertir entre devises | `true` (tous) |
| `ecocraft.admin.give` | Donner de l'argent (admin) | OP niveau 2 |
| `ecocraft.admin.take` | Retirer de l'argent (admin) | OP niveau 2 |
| `ecocraft.admin.set` | Definir un solde (admin) | OP niveau 2 |

## Configuration

Fichier de configuration NeoForge (`ecocraft_core-common.toml`) :

### Stockage

| Option | Description | Defaut |
|--------|-------------|--------|
| `storage.type` | Type de stockage (`sqlite` ou `mysql`) | `sqlite` |
| `storage.mysql.host` | Hote MySQL | `localhost` |
| `storage.mysql.port` | Port MySQL | `3306` |
| `storage.mysql.database` | Base de donnees MySQL | `ecocraft` |
| `storage.mysql.username` | Utilisateur MySQL | `root` |
| `storage.mysql.password` | Mot de passe MySQL | (vide) |

### Economie

| Option | Description | Defaut |
|--------|-------------|--------|
| `economy.defaultCurrency.id` | ID de la devise par defaut | `gold` |
| `economy.defaultCurrency.name` | Nom de la devise | `Gold` |
| `economy.defaultCurrency.symbol` | Symbole de la devise | `\u26C1` |
| `economy.defaultCurrency.decimals` | Nombre de decimales (0 a 4) | `2` |
| `economy.startingBalance` | Solde de depart pour les nouveaux joueurs | `100.0` |

### Coffre-fort

| Option | Description | Defaut |
|--------|-------------|--------|
| `vault.enabled` | Activer le bloc coffre-fort | `true` |

## Integration KubeJS

Le module fournit un plugin KubeJS avec des bindings et des evenements pour le scripting serveur.

### Bindings (`EcoEconomy`)

Disponibles dans les scripts KubeJS via l'objet global `EcoEconomy` :

```javascript
// Lire le solde d'un joueur
let balance = EcoEconomy.getBalance(player);
let balanceGems = EcoEconomy.getBalance(player, 'gems');

// Verifier si le joueur peut payer
if (EcoEconomy.canAfford(player, 500)) {
    EcoEconomy.withdraw(player, 500);
}

// Operations
EcoEconomy.deposit(player, 1000);
EcoEconomy.withdraw(player, 200);
EcoEconomy.transfer(sender, receiver, 300);
EcoEconomy.setBalance(player, 5000);
```

### Evenements (`EcocraftEvents`)

| Evenement | Description | Annulable |
|-----------|-------------|-----------|
| `EcocraftEvents.transaction` | Avant une transaction (pre) | Oui |
| `EcocraftEvents.transactionAfter` | Apres une transaction (post) | Non |
| `EcocraftEvents.balanceChanged` | Quand un solde change | Non |

Exemple de script KubeJS :

```javascript
// Bloquer les paiements superieurs a 10000
EcocraftEvents.transaction(event => {
    if (event.amount > 10000 && event.type === 'PAYMENT') {
        event.cancel();
        event.setMessage('Montant trop eleve !');
    }
});

// Logger les changements de solde
EcocraftEvents.balanceChanged(event => {
    console.log(`${event.player.name}: ${event.oldBalance} -> ${event.newBalance} (${event.cause})`);
});
```

## Dependances

| Dependance | Version | Incluse dans le JAR |
|------------|---------|---------------------|
| `economy-api` | (meme version) | Non (mod separe) |
| `gui-lib` | (meme version) | Non (mod separe) |
| `xerial:sqlite-jdbc` | 3.47.1.0 | Oui (via jarJar) |
| `KubeJS` (optionnel) | 2101.7.2+ | Non (compileOnly) |

## Informations techniques

- **Java** : 21
- **NeoForge** : 21.1.221
- **Minecraft** : 1.21.1
- **Parchment** : 2024.11.17
