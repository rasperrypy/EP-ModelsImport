# Cart Models

Mod NeoForge pour Minecraft 1.21.1 qui importe des modèles Blockbench (`.bbmodel`) ou des JSON de modèles de blocs Minecraft en blocs nommés plaçables.

## Prérequis et compilation

- JDK Java 21
- Windows : `gradlew.bat build`
- macOS/Linux : `./gradlew build`
- Le fichier du mod est produit dans `build/libs/`.

## Utilisation

1. Exécuter `/import-model` en tant qu’opérateur pour ouvrir le menu.
2. Saisir un nom puis déposer un seul fichier `.bbmodel` ou `.json` dans la fenêtre. Un aperçu et les statistiques du modèle apparaissent avant l’envoi, sur un voile sombre translucide sans flou afin de garder l’interface nette. Le flou global des menus est temporairement désactivé pour ce panneau, puis restauré à sa valeur précédente lorsque tu le quittes.
3. Faire glisser la souris dans l’aperçu 3D pour tourner le modèle. Survoler un voxel Blockbench de 1×1×1 pixel puis cliquer pour le définir ou le retirer comme siège, sans regrouper les voxels en blocs Minecraft de 16×16×16 pixels. Les voxels sélectionnés sont surlignés ; jusqu’à 64 sièges peuvent être définis. Dans le jeu, cliquer droit sur le bloc pour prendre une place libre et s’accroupir pour descendre.
4. Le modèle est envoyé au serveur, validé, puis enregistré avec le monde. En solo, le serveur intégré effectue l’import et le stockage.
5. Exécuter `/give-model <nom> [quantité]` pour obtenir le bloc correspondant (quantité de 1 à 64). Les noms sont proposés par la complétion de commande.
6. Utiliser `/list-models [page]` pour parcourir les modèles importés, 20 par page.
7. Utiliser `/model-info <nom>` pour consulter le nombre de cuboïdes et de meshes, les textures et la taille stockée.

Importer à nouveau un modèle sous un nom existant le remplace et actualise son rendu sur les clients connectés.
Les blocs orientent automatiquement le modèle selon la direction du joueur lors de la pose. Dans `/list-models`, cliquer sur un nom prépare la commande `/give-model` correspondante.

Un exemple complet de wagon est fourni dans [`examples/test_cart.bbmodel`](examples/test_cart.bbmodel). Dépose ce fichier dans le menu, donne-lui le nom `test_cart`, puis utilise `/give-model test_cart`.

Les joueurs qui affichent les blocs de modèles doivent également avoir le mod installé. L’import sur un serveur dédié requiert les permissions opérateur.

Les transferts interrompus sont automatiquement nettoyés ; le serveur limite également le nombre et la mémoire des imports simultanés.

## Formats et limites

- Taille maximale d’un fichier : 8 Mio ; maximum 4 096 éléments cubiques ou mesh et 32 textures PNG intégrées (2 Mio par texture, 2 048×2 048 pixels).
- Les projets `.bbmodel` prennent en charge les cubes et les textures PNG intégrées. Les meshes Blockbench sont expérimentaux : coche l’option orange « Meshes (instable) » dans le menu d’import pour les activer ; ils peuvent s’afficher ou se sélectionner incorrectement. Les transformations de groupes parents ne sont pas appliquées.
- Le JSON Minecraft doit contenir directement ses propres `elements`; les modèles reposant uniquement sur un parent ne sont pas pris en charge. Les références de texture externes ne sont pas téléversées et s’affichent avec la texture de remplacement.
- Les dimensions Blockbench sont conservées. Le modèle peut dépasser son bloc d’ancrage, qui n’a aucune collision physique.
