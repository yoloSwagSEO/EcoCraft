# EcoCraft Mail

Systeme de courrier joueur-a-joueur inspire de World of Warcraft pour Minecraft 1.21.1 (NeoForge).
Les joueurs peuvent s'envoyer des mails avec des pieces jointes (objets, monnaie), du contre-remboursement (COD), des accuses de reception, et plus encore.

## Fonctionnalites

- **Envoi et reception de mails** entre joueurs, avec objet, corps de message, et historique complet
- **Pieces jointes d'objets** — glissez des items depuis l'inventaire dans la vue de composition (jusqu'a 54 slots configurables)
- **Pieces jointes de monnaie** — envoyez de l'or ou d'autres devises avec vos mails
- **Contre-remboursement (COD)** — exigez un paiement avant que le destinataire puisse collecter les pieces jointes
- **Accuses de reception** — notification quand le destinataire lit votre mail (cout configurable)
- **Brouillons** — sauvegardez un mail en cours de redaction et reprenez-le plus tard
- **Historique des envois** — consultez tous les mails que vous avez envoyes (onglet "Envoyes")
- **Recherche et filtres** — filtrez par non-lu, pieces jointes, COD ; recherchez par sujet ou expediteur
- **Notifications configurables** — chat, toast, les deux, ou aucun, par type d'evenement
- **Boite aux lettres** — bloc placable dans le monde, clic droit pour ouvrir l'interface
- **Facteur (Postman NPC)** — entite stationnaire avec skin configurable, clic droit pour ouvrir la boite aux lettres
- **Mails systeme** — API pour envoyer des mails depuis d'autres modules (Hotel des Ventes, quetes, admin)
- **Expiration automatique** — les mails expirent apres N jours (configurable), les COD expires sont retournes a l'expediteur
- **Integration KubeJS** — evenements et bindings pour les scripts serveur

## Dependances

| Module | Role |
|---|---|
| `economy-api` | Interfaces d'economie (devises, transactions) |
| `economy-core` | Stockage SQLite, commandes de base, systeme de permissions |
| `gui-lib` | Widgets GUI WoW-themed (EcoScreen, EcoButton, ScrollPane, EcoToast, etc.) |

## Commandes

| Commande | Description | Permission |
|---|---|---|
| `/mail` | Ouvre l'interface de la boite aux lettres | `ecocraft.mail.command` |
| `/mail send <joueur> <objet>` | Envoie un mail texte (sans corps ni pieces jointes) | `ecocraft.mail.send` |
| `/mail admin send <joueur> <objet> <message>` | Envoie un mail systeme a un joueur | `ecocraft.mail.admin` |
| `/mail admin sendall <objet> <message>` | Envoie un mail systeme a tous les joueurs connectes | `ecocraft.mail.admin` |
| `/mail admin clear <joueur>` | Supprime tous les mails d'un joueur | `ecocraft.mail.admin` |
| `/mail admin purge` | Traite les mails expires (retour COD + suppression) | `ecocraft.mail.admin` |
| `/mail test` | Genere 7 mails de test (texte, monnaie, objets, COD, admin) | `ecocraft.mail.admin` |

## Permissions

| Noeud | Type | Defaut | Description |
|---|---|---|---|
| `ecocraft.mail.command` | Boolean | `true` | Acces a la commande `/mail` |
| `ecocraft.mail.read` | Boolean | `true` | Acces a l'interface de la boite aux lettres (bloc/NPC) |
| `ecocraft.mail.send` | Boolean | `true` | Peut envoyer des mails |
| `ecocraft.mail.attach.items` | Boolean | `true` | Peut joindre des objets aux mails |
| `ecocraft.mail.attach.currency` | Boolean | `true` | Peut joindre de la monnaie aux mails |
| `ecocraft.mail.cod` | Boolean | `true` | Peut envoyer des mails en contre-remboursement |
| `ecocraft.mail.admin` | Boolean | OP niveau 2 | Acces aux commandes admin |
| `ecocraft.mail.max_attachments` | Integer | `-1` (illimite) | Nombre max de pieces jointes par joueur (-1 = illimite) |

## Configuration

Fichier : `serverconfig/ecocraft_mail-server.toml`

| Option | Type | Defaut | Plage | Description |
|---|---|---|---|---|
| `allowPlayerMail` | Boolean | `true` | — | Active le courrier entre joueurs |
| `allowItemAttachments` | Boolean | `true` | — | Autorise les pieces jointes d'objets |
| `allowCurrencyAttachments` | Boolean | `true` | — | Autorise les pieces jointes de monnaie |
| `allowCOD` | Boolean | `true` | — | Active le contre-remboursement |
| `maxItemAttachments` | Integer | `12` | 1–54 | Nombre max de slots de pieces jointes par mail |
| `mailExpiryDays` | Integer | `30` | 1–365 | Jours avant expiration automatique d'un mail |
| `sendCost` | Long | `0` | 0–max | Cout d'envoi d'un mail (0 = gratuit) |
| `sendCostPerItem` | Long | `0` | 0–max | Cout additionnel par piece jointe (0 = gratuit) |
| `codFeePercent` | Integer | `0` | 0–100 | Pourcentage de frais sur les paiements COD |
| `allowMailboxCraft` | Boolean | `true` | — | Les joueurs peuvent crafter la boite aux lettres |
| `allowReadReceipt` | Boolean | `true` | — | Active les accuses de reception |
| `readReceiptCost` | Long | `0` | 0–max | Cout pour activer un accuse de reception (0 = gratuit) |

Toutes ces options sont egalement modifiables en jeu via l'ecran Parametres (icone engrenage dans la boite aux lettres, onglet admin).

## Utilisation en jeu

### Boite aux lettres (bloc)

- Disponible dans l'onglet creatif "EcoCraft Mail"
- Placez le bloc et faites clic droit pour ouvrir l'interface
- Commande : `/mail` ouvre aussi l'interface directement

### Facteur (Postman NPC)

- Invocation : `/summon ecocraft_mail:postman`
- Spawn egg disponible dans l'onglet creatif "EcoCraft Mail"
- Entite stationnaire, invulnerable, ne despawn jamais
- Regarde les joueurs a proximite (8 blocs)
- Clic droit ouvre la boite aux lettres
- **Skin configurable** : dans les parametres (engrenage), entrez un nom de joueur Minecraft pour appliquer son skin au facteur

### Interface de la boite aux lettres

L'interface est composee de 3 vues :

**Vue liste (Inbox/Envoyes/Brouillons)**
- 3 onglets : Boite de reception, Envoyes, Brouillons
- Ligne de statistiques : nombre de mails, non lus, objets a collecter, monnaie totale
- Barre de recherche par sujet ou expediteur
- Filtres : Tout, Non lu, Pieces jointes, COD
- Bouton "Tout collecter" — collecte tous les mails avec pieces jointes (hors COD)
- Bouton "Nouveau mail" — ouvre la vue de composition
- `Shift+Clic` sur un mail : collecte rapide si possible, sinon marque comme lu
- Indicateurs visuels : couleur de fond pour non lu, tags (COD, Or, Items)

**Vue detail**
- Sujet, expediteur, date, corps du message (scrollable)
- Panneau de pieces jointes avec icones d'items et montant de monnaie
- Bandeau COD avec boutons "Payer & Collecter" et "Retourner"
- Boutons d'action : Collecter, Supprimer, Repondre, Transferer

**Vue composition**
- Colonne gauche : destinataire, objet, corps du message (2000 caracteres max), montant monnaie, toggle COD + montant, toggle accuse de reception
- Colonne droite : grille d'inventaire (clic pour selectionner des items) + zone "A envoyer" (items selectionnes)
- Confirmation requise si le mail contient des pieces jointes, de la monnaie ou du COD (bouton "Confirmer" avec timeout de 3s)
- Affichage dynamique du cout total d'envoi
- Sauvegarde en brouillon

**Ecran parametres**
- Onglet Notifications : canal (Chat, Toast, Les deux, Aucun) par type d'evenement (nouveau mail, COD recu, mail retourne, accuse de reception)
- Onglet General (admin) : toggles et valeurs pour toutes les options de configuration, skin du facteur

## Notifications

4 types d'evenements avec canal configurable par le joueur :

| Evenement | Canal par defaut | Description |
|---|---|---|
| Nouveau mail | Chat + Toast | Un mail arrive dans la boite de reception |
| COD recu | Chat + Toast | Un paiement COD a ete effectue |
| Mail retourne | Chat + Toast | Un mail COD a ete retourne |
| Accuse de reception | Chat + Toast | Le destinataire a lu votre mail |

Canaux disponibles : `CHAT`, `TOAST`, `BOTH` (les deux), `NONE` (desactive).

## Integration KubeJS

Quand KubeJS est installe, le module expose des evenements serveur et un binding `EcoMail`.

### Evenements

Groupe : `EcocraftMailEvents`

| Evenement | Type | Description |
|---|---|---|
| `mailSending` | PRE (annulable) | Avant l'envoi d'un mail — retourner `false` annule l'envoi |
| `mailSent` | POST | Apres l'envoi d'un mail |
| `mailReceived` | POST | Quand un mail arrive dans la boite du destinataire |
| `mailCollected` | POST | Quand un joueur collecte les pieces jointes |
| `mailRead` | POST | Quand un mail est marque comme lu |
| `mailDeleted` | POST | Quand un mail est supprime |
| `codPaid` | POST | Quand un paiement COD est effectue |
| `codReturned` | POST | Quand un mail COD est retourne a l'expediteur |
| `mailExpired` | POST | Quand des mails expirent |

```js
// Exemple : bloquer les mails vers un joueur specifique
EcocraftMailEvents.mailSending(event => {
    if (event.recipientUuid === 'some-uuid') {
        event.cancel();
    }
});

// Exemple : log quand un COD est paye
EcocraftMailEvents.codPaid(event => {
    console.log(`COD paid: ${event.amount} by ${event.payerUuid}`);
});
```

### Binding `EcoMail`

| Methode | Description |
|---|---|
| `EcoMail.sendSystemMail(senderName, recipientUuid, subject, body, currencyAmount, currencyId, indestructible)` | Envoie un mail systeme, retourne l'ID du mail |
| `EcoMail.getMailsForPlayer(playerUuid)` | Liste des mails recus d'un joueur |
| `EcoMail.getSentMails(playerUuid)` | Liste des mails envoyes par un joueur |
| `EcoMail.collectMail(playerUuid, mailId)` | Collecte un mail, retourne `true`/`false` |
| `EcoMail.deleteMail(playerUuid, mailId)` | Supprime un mail, retourne `true`/`false` |
| `EcoMail.deleteAllMails(playerUuid)` | Supprime tous les mails d'un joueur (admin), retourne le nombre |
| `EcoMail.markRead(playerUuid, mailId)` | Marque un mail comme lu, retourne `true`/`false` |

```js
// Exemple : envoyer un mail de bienvenue
EcoMail.sendSystemMail(
    'Serveur',
    player.uuid,
    'Bienvenue !',
    'Bienvenue sur le serveur, voici un cadeau !',
    500,      // 500 unites de monnaie
    'gold',   // ID de la devise
    false     // pas indestructible
);
```

## Structure du module

```
mail/src/main/java/net/ecocraft/mail/
  MailMod.java                  — Point d'entree du mod
  MailServerEvents.java         — Gestion des evenements serveur
  block/                        — MailboxBlock + BlockEntity
  client/                       — Notifications (Manager, Config, Channel, EventType)
  command/                      — Commande /mail
  compat/kubejs/                — Integration KubeJS (plugin, bindings, events)
  config/                       — MailConfig (ModConfigSpec)
  data/                         — Records : Mail, Draft, MailItemAttachment
  entity/                       — PostmanEntity + PostmanRenderer
  network/                      — Payloads reseau (client <-> serveur)
  permission/                   — Noeuds de permission
  registry/                     — Registres NeoForge (blocs, entites, items)
  screen/                       — UI : MailboxScreen, ListView, DetailView, ComposeView, Settings
  service/                      — MailService (logique metier)
  storage/                      — MailStorageProvider (interface SQLite)
```

## Build

```bash
./gradlew :mail:build
./gradlew :mail:test
```
