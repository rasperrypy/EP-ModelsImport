# Cart Models

Mod NeoForge pour Minecraft 1.21.1 qui importe des modèles Blockbench (`.bbmodel`) ou des JSON de modèles de blocs Minecraft en blocs nommés plaçables.

## Prérequis et compilation

- JDK Java 21
- Windows : `gradlew.bat build`
- macOS/Linux : `./gradlew build`
- Le fichier du mod est produit dans `build/libs/`.

## Utilisation

1. Exécuter `/import-model` en tant qu’opérateur pour ouvrir le menu.
2. Saisir un nom puis déposer un fichier `.bbmodel` ou `.json` dans la fenêtre.
3. Le modèle est envoyé au serveur, validé, puis enregistré avec le monde. En solo, le serveur intégré effectue l’import et le stockage.
4. Exécuter `/give-model <nom>` pour obtenir le bloc correspondant. Les noms sont proposés par la complétion de commande.

Un exemple complet de wagon est fourni dans [`examples/test_cart.bbmodel`](examples/test_cart.bbmodel). Dépose ce fichier dans le menu, donne-lui le nom `test_cart`, puis utilise `/give-model test_cart`.

Les joueurs qui affichent les blocs de modèles doivent également avoir le mod installé. L’import sur un serveur dédié requiert les permissions opérateur.

## Formats et limites

- Taille maximale d’un fichier : 8 Mio ; maximum 4 096 cubes et 32 textures PNG intégrées (2 Mio par texture, 2 048×2 048 pixels).
- Les projets `.bbmodel` sont pris en charge pour leurs éléments cubiques et leurs textures PNG intégrées. Les géométries mesh sont refusées ; les transformations de groupes parents ne sont pas appliquées.
- Le JSON Minecraft doit contenir directement ses propres `elements`; les modèles reposant uniquement sur un parent ne sont pas pris en charge. Les références de texture externes ne sont pas téléversées et s’affichent avec la texture de remplacement.
- Les dimensions Blockbench sont conservées. Le modèle peut dépasser son bloc d’ancrage, qui n’a aucune collision physique.
