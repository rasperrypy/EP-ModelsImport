# Cart Models

Cart Models est un mod NeoForge qui transforme des projets Blockbench et des modèles JSON Minecraft en blocs décoratifs nommés. Les modèles peuvent dépasser leur bloc d’ancrage, afficher des textures intégrées et contenir des sièges utilisables.

> **Compatibilité :** Minecraft 1.21.1 · NeoForge 21.1.265 · Java 21
> **Version publiée :** 7.0.0

## Fonctionnalités

- Import de projets Blockbench (`.bbmodel`) et de modèles Minecraft (`.json`).
- Aperçu 3D tournable avec sélection des sièges par voxel Blockbench.
- Meshes Blockbench expérimentaux activés par une case orange dans l’écran d’import.
- Textures PNG intégrées, transférées avec le modèle.
- Blocs orientés selon la direction du joueur au placement, sans collision physique.
- Contour de sélection calculé depuis la géométrie réelle du modèle et centré sur ses limites dans le bloc d’ancrage.
- Sièges configurables et verrouillage temporaire avec commande de sortie d’urgence `/unstuck`.
- Stockage des modèles avec le monde et synchronisation aux joueurs connectés.

## Installation

1. Installer Java 21.
2. Installer NeoForge pour Minecraft 1.21.1.
3. Placer le fichier JAR Cart Models dans le dossier `mods` du client et du serveur.
4. Redémarrer le jeu ou le serveur. Les joueurs doivent tous disposer du mod pour voir les modèles.

Pour compiler depuis les sources, exécuter `gradlew.bat build` sous Windows ou `./gradlew build` sous macOS/Linux. Le JAR compilé est créé dans `build/libs/`.

## Démarrage rapide

1. En jeu, un opérateur exécute `/import-model`.
2. Saisir un nom simple, puis déposer un fichier `.bbmodel` ou `.json` dans la fenêtre. L’aperçu et les statistiques s’affichent avant l’import.
3. Faire glisser la souris dans l’aperçu pour tourner le modèle. Cliquer sur un voxel Blockbench de 1×1×1 pixel pour l’ajouter ou le retirer de la liste des sièges.
4. Régler éventuellement le verrouillage des sièges en secondes. `0` conserve la sortie normale; la limite est de 3 600 secondes.
5. Cliquer sur **Importer**. Le serveur valide et enregistre le modèle.
6. Obtenir le bloc avec `/give-model <nom> [quantité]`, le placer puis interagir avec lui pour prendre un siège libre.

Un exemple de wagon complet est fourni dans [`examples/test_cart.bbmodel`](examples/test_cart.bbmodel). Pour l’essayer, l’importer sous le nom `test_cart`, puis exécuter `/give-model test_cart`.

## Commandes

| Commande | Description |
|---|---|
| `/import-model` | Ouvre l’écran d’import (opérateur requis). |
| `/give-model <nom> [quantité]` | Donne un bloc de modèle; quantité de 1 à 64. La complétion suggère les modèles importés. |
| `/list-models [page]` | Parcourt les modèles disponibles, 20 par page. Cliquer sur un nom prépare une commande `/give-model`. |
| `/model-info <nom>` | Affiche le nombre de cuboïdes, de meshes, de textures intégrées et la taille des données. |
| `/unstuck` | Quitte un siège verrouillé et téléporte le joueur au sol en X=0, Z=0 dans sa dimension actuelle. |

Importer un modèle avec un nom déjà utilisé remplace sa définition pour les prochains rendus et synchronise la nouvelle version aux joueurs connectés.

## Sièges et interaction

- Les sièges sont sélectionnés individuellement dans l’aperçu 3D; jusqu’à 64 sièges peuvent être définis par modèle.
- Interagir avec le bloc tente d’installer le joueur sur le premier siège libre.
- Sans verrouillage, s’accroupir permet de descendre.
- Avec un verrouillage actif, la sortie normale est temporairement bloquée et le temps restant est indiqué. `/unstuck` reste disponible comme sortie d’urgence.
- Le verrou est libéré à son expiration, à la mort du joueur ou si le bloc d’ancrage est retiré.
- Le modèle n’a **aucune collision physique**. Son contour de sélection suit la boîte englobante de sa géométrie, tournée selon l’orientation du bloc et sauvegardée avec celui-ci. Le contour ne remplace pas une collision et la sélection Minecraft reste limitée à la zone du bloc d’ancrage.

## Formats, limites et compatibilité

- Fichiers d’import : `.bbmodel` ou JSON de modèle Minecraft contenant directement `elements`.
- Taille maximale d’un fichier : 8 Mio.
- Jusqu’à 4 096 éléments cubiques ou mesh, 32 textures PNG intégrées et 2 Mio par texture (dimensions maximales 2 048×2 048).
- Les cubes Blockbench et les références de texture intégrées sont pris en charge.
- Les meshes Blockbench sont **expérimentaux** : activer la case orange **Meshes (instable)** avant d’importer. Leur rendu, leurs UV ou la sélection des sièges peuvent être imparfaits.
- Les transformations des groupes parents et les modèles JSON reposant uniquement sur un parent ne sont pas appliqués.
- Les textures externes ne sont pas téléversées et utilisent une texture de remplacement.
- Les modèles déjà importés restent utilisables. Les anciens modèles sans durée de verrouillage gardent une durée de `0` seconde.

## Données et dépannage

- Les modèles sont enregistrés dans les données du monde, dans `data/cartmodels_models.json`. Faire une sauvegarde du monde avant de déplacer ou modifier manuellement ce fichier.
- Si un modèle n’apparaît pas, vérifier que Cart Models est installé sur le client et le serveur et que son import s’est terminé sans message d’erreur.
- Si une texture manque, l’intégrer au fichier Blockbench avant l’import; les chemins locaux ou les références Minecraft externes ne sont pas envoyés au serveur.
- Si un mesh s’affiche mal, désactiver son option expérimentale et réimporter une version composée de cubes.
- Si un joueur reste coincé dans un siège verrouillé, exécuter `/unstuck`.
- Sur serveur dédié, l’import et le remplacement nécessitent la permission opérateur.

Les limites de transfert et le nettoyage automatique des imports interrompus protègent le serveur contre les envois incomplets ou trop volumineux.

## Développement

- Java 21 et Gradle Wrapper sont requis.
- Compilation et tests : `gradlew.bat build` (Windows) ou `./gradlew build` (macOS/Linux).
- Code source et versions : [GitHub — EP-ModelsImport](https://github.com/rasperrypy/EP-ModelsImport).
