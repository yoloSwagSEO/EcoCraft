# EcoCraft Economy API

Interfaces pures pour la suite economique EcoCraft. Ce module ne contient aucune implementation -- il definit uniquement les contrats que les autres modules (economy-core, auction-house, mail) et les mods tiers peuvent utiliser.

## Mod ID

`ecocraft_api`

## Interfaces disponibles

### EconomyProvider

Point d'entree principal pour toutes les operations economiques.

| Methode | Description |
|---------|-------------|
| `getAccount(UUID, Currency)` | Recupere le compte d'un joueur (cree a la demande si absent) |
| `getVirtualBalance(UUID, Currency)` | Solde virtuel (hors coffre) |
| `getVaultBalance(UUID, Currency)` | Solde en coffre |
| `deposit(UUID, BigDecimal, Currency)` | Ajouter des fonds au solde virtuel |
| `withdraw(UUID, BigDecimal, Currency)` | Retirer des fonds du solde virtuel |
| `transfer(UUID, UUID, BigDecimal, Currency)` | Transferer des fonds entre deux joueurs |
| `canAfford(UUID, BigDecimal, Currency)` | Verifier si un joueur peut se permettre un montant |

### CurrencyRegistry

Registre des devises disponibles sur le serveur.

| Methode | Description |
|---------|-------------|
| `register(Currency)` | Enregistrer une nouvelle devise |
| `getById(String)` | Recuperer une devise par son ID |
| `getDefault()` | Devise par defaut (premiere enregistree) |
| `listAll()` | Liste immutable de toutes les devises |
| `exists(String)` | Verifier si une devise existe |

### Currency

Record representant une devise. Deux types :

- **Virtuelle** : `Currency.virtual("gold", "Gold", "\u26C1", 2)` -- devise purement numerique
- **Physique** : `Currency.physical("gems", "Gems", "\u2666", 0, "minecraft:emerald")` -- liee a un item Minecraft

Champs : `id`, `name`, `symbol`, `decimals`, `physical`, `itemId`.

### ExchangeService / ExchangeRate

Service de conversion entre devises avec taux de change et frais configurables.

| Methode | Description |
|---------|-------------|
| `convert(UUID, BigDecimal, Currency, Currency)` | Convertir des fonds entre deux devises |
| `getRate(Currency, Currency)` | Obtenir le taux de change |
| `listRates()` | Lister tous les taux configures |

`ExchangeRate` calcule automatiquement la conversion en appliquant le taux et les frais.

### TransactionLog / Transaction / TransactionFilter

Acces en lecture seule a l'historique des transactions avec pagination.

```java
Page<Transaction> page = transactionLog.getHistory(
    new TransactionFilter(playerUuid, TransactionType.PAYMENT, null, null, 0, 20)
);
```

### TransactionResult

Resultat d'une operation economique : `success(Transaction)` ou `failure(String errorMessage)`.

### TransactionType

Types de transaction extensibles. Types predéfinis :

`PAYMENT`, `TAX`, `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `EXCHANGE`, `ADMIN_SET`

Les modules peuvent definir des types personnalises :

```java
TransactionType AUCTION_SALE = TransactionType.of("AUCTION_SALE");
```

### Account

Record representant le compte d'un joueur pour une devise donnee.

Champs : `owner`, `currency`, `virtualBalance`, `vaultBalance`.
Methode utile : `totalBalance()` retourne la somme des deux soldes.

### Page\<T\>

Record generique pour la pagination : `items`, `offset`, `limit`, `totalCount`.
Methodes utiles : `totalPages()`, `currentPage()`, `hasNext()`, `hasPrevious()`.

## Dependance Gradle

```groovy
dependencies {
    implementation project(':economy-api')
}
```

Pour un mod externe (via Maven local ou depot) :

```groovy
dependencies {
    implementation 'net.ecocraft:ecocraft-economy-api:0.1.0'
}
```

## Informations techniques

- **Java** : 21
- **NeoForge** : 21.1.221
- **Minecraft** : 1.21.1
- **Parchment** : 2024.11.17
- **Aucune dependance externe** -- uniquement les APIs NeoForge
