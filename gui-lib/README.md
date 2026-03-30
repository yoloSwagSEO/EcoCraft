# EcoCraft GUI Library

Bibliotheque de widgets GUI inspires de World of Warcraft pour NeoForge 1.21.1. Fournit un arbre de widgets complet avec rendu en profondeur, propagation d'evenements par remontee (bubbling), gestion du focus, et un theme sombre configurable.

## Mod ID

`ecocraft_gui`

## Architecture

### Arbre de widgets (WidgetTree)

Tous les widgets forment une hierarchie parent-enfants geree par `WidgetTree`. Le systeme fonctionne comme un mini-framework UI :

- **Rendu** : parcours en profondeur (depth-first), les portals sont rendus par-dessus
- **Evenements souris** : hit-test en ordre inverse (dernier enfant = au-dessus), puis remontee (bubbling) du widget cible vers la racine
- **Evenements clavier** : envoyes au widget ayant le focus, puis remontee
- **Focus** : un seul widget global peut avoir le focus a la fois
- **Portals** : widgets rendus au-dessus de tout (utilises pour les dialogues modaux, dropdowns)
- **Clipping** : les widgets avec `clipChildren` decoupent le rendu de leurs enfants a leurs limites

### Interface WidgetNode

Contrat de base pour tout widget dans l'arbre :

```java
public interface WidgetNode {
    // Structure arborescente
    WidgetNode getParent();
    List<WidgetNode> getChildren();
    void addChild(WidgetNode child);

    // Limites (coordonnees ecran absolues)
    int getX(), getY(), getWidth(), getHeight();
    boolean containsPoint(double mx, double my);

    // Rendu et evenements
    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
    boolean onMouseClicked(double mouseX, double mouseY, int button);
    boolean onKeyPressed(int keyCode, int scanCode, int modifiers);
    // ...
}
```

### BaseWidget

Classe de base abstraite implementant `WidgetNode`. Fournit :

- Gestion des enfants (ajout, suppression, reordonnancement)
- Limites (position, taille, `setBounds`)
- Visibilite, etat actif/inactif, modal, clipping
- ID optionnel pour recherche dans l'arbre (`findById`)
- Donnees utilisateur arbitraires (`setData` / `getData`)
- Navigation dans l'arbre (`findParent`, `bringToFront`, `removeFromParent`)

### EcoScreen

Classe de base pour les ecrans. Etend `Screen` de Minecraft et integre automatiquement le `WidgetTree` :

```java
public class MonEcran extends EcoScreen {
    public MonEcran() {
        super(Component.literal("Mon Ecran"));
    }

    @Override
    protected void init() {
        super.init(); // important : reinitialise l'arbre
        Theme theme = Theme.dark();

        Panel panel = new Panel(10, 10, 200, 100, theme);
        panel.setTitle(Component.literal("Titre"));
        getTree().addChild(panel);

        EcoButton btn = EcoButton.primary(20, 50, 80, 20,
            Component.literal("Cliquer"), theme, () -> {
                // action
            });
        panel.addChild(btn);
    }
}
```

## Widgets disponibles

### Conteneurs et mise en page

| Widget | Description |
|--------|-------------|
| `Panel` | Panneau avec fond theme, bordure, titre optionnel (majuscules) et separateur |
| `ScrollPane` | Conteneur defilable verticalement avec barre de defilement integree et clipping |
| `EcoGrid` | Systeme de grille 12 colonnes inspire de Bootstrap |
| `EcoRow` | Ligne dans un `EcoGrid`, contient des `EcoCol` |
| `EcoCol` | Colonne dans un `EcoRow`, dimensionnee par span (sur 12) avec alignement H/V |
| `EcoDialog` | Dialogue modal (alerte, confirmation, saisie) utilise comme portal |

### Boutons et controles

| Widget | Description |
|--------|-------------|
| `EcoButton` | Bouton theme avec variantes : `primary`, `success`, `danger`, `warning`, `ghost` |
| `EcoCycleButton` | Bouton qui cycle parmi une liste d'options (clic gauche/droit) |
| `EcoToggle` | Interrupteur ON/OFF en forme de pilule avec animation de glissement |
| `EcoCheckbox` | Case a cocher avec label optionnel |
| `EcoRadioGroup` | Groupe de boutons radio verticaux (selection unique) |
| `EcoSlider` | Curseur avec min/max/step, orientation horizontale/verticale |
| `EcoFilterTags` | Tags de filtre cliquables en forme de pilules |

### Saisie de texte

| Widget | Description |
|--------|-------------|
| `EcoEditBox` | Champ de texte monoligne personnalise (ne wrappe PAS l'EditBox Minecraft) |
| `EcoTextInput` | Champ de texte theme qui encapsule un `EcoEditBox` |
| `EcoTextArea` | Zone de texte multiligne avec defilement et selection |
| `EcoNumberInput` | Saisie numerique avec boutons +/-, validation min/max |

### Affichage de donnees

| Widget | Description |
|--------|-------------|
| `Label` | Affichage de texte simple avec alignement (gauche, centre, droite) |
| `EcoTable` | Tableau avec colonnes, tri, et trois modes : NONE, SCROLL, PAGINATED |
| `EcoStatCard` | Carte de statistique (label + valeur) avec icone et sous-titre optionnels |
| `EcoProgressBar` | Barre de progression horizontale non interactive |

### Items Minecraft

| Widget | Description |
|--------|-------------|
| `EcoItemSlot` | Affiche un `ItemStack` avec bordure coloree par rarete et tooltip au survol |
| `EcoInventoryGrid` | Grille d'inventaire joueur avec sections configurables (Main, Hotbar, Armure, etc.) |

### Listes et navigation

| Widget | Description |
|--------|-------------|
| `EcoRepeater<T>` | Liste dynamique avec ajout/suppression de lignes et defilement integre |
| `EcoTabBar` | Barre d'onglets horizontale avec style accent dore pour l'onglet actif |
| `EcoDropdown` | Menu deroulant (select) avec liste d'options qui s'ouvre en dessous |
| `EcoScrollbar` | Barre de defilement verticale autonome avec poignee draggable |

### Notifications

| Widget | Description |
|--------|-------------|
| `EcoToast` | Notification toast avec titre, message, icone, niveau (INFO/SUCCESS/WARNING/DANGER) |
| `EcoToastManager` | Singleton gerant l'affichage et l'empilement des toasts a l'ecran |
| `ToastLevel` | Enum des niveaux de notification : `INFO`, `SUCCESS`, `WARNING`, `DANGER` |
| `ToastAnimation` | Enum des animations d'entree : `SLIDE_RIGHT`, etc. |

## Systeme de theme

Le `Theme` est une palette de couleurs configurable utilisee par tous les widgets. Le theme sombre par defaut (`Theme.dark()` / `Theme.DARK`) est inspire de World of Warcraft :

- **Fonds** : noir profond a gris fonce (`bgDarkest`, `bgDark`, `bgMedium`, `bgLight`)
- **Bordures** : gris avec accent dore (`border`, `borderLight`, `borderAccent`)
- **Accent** : or WoW (`accent` = `0xFFFFD700`)
- **Texte** : blanc a gris en 5 niveaux (`textWhite`, `textLight`, `textGrey`, `textDim`, `textDark`)
- **Etats fonctionnels** : vert (succes), orange (avertissement), rouge (danger), bleu (info)
- **Raretes WoW** : commun (blanc), peu commun (vert), rare (bleu), epique (violet), legendaire (orange)
- **Etat desactive** : fond, texte et bordure assombris

Pour creer un theme personnalise :

```java
Theme custom = new Theme.Builder()
    .bgDark(0xFF1A1A2E)
    .accent(0xFFFF6B6B)
    .textWhite(0xFFFFFFFF)
    // ... autres couleurs
    .build();
```

## Utilitaires

| Classe | Description |
|--------|-------------|
| `DrawUtils` | Methodes statiques pour dessiner panneaux, separateurs, accents |
| `NumberFormatter` | Formatage de nombres avec suffixes (K, M, B) et devises |

## Dependances

| Dependance | Version |
|------------|---------|
| `economy-api` | (meme version) |
| NeoForge | 21.1.221 |

## Informations techniques

- **Java** : 21
- **NeoForge** : 21.1.221
- **Minecraft** : 1.21.1
- **Parchment** : 2024.11.17
- **Rendu** : cote client uniquement
