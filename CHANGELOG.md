# Changelog

## 7.0.0

### Contour de sélection centré sur le modèle
- Le contour sélectionnable suit désormais les limites de la géométrie des cubes et des meshes au lieu de couvrir systématiquement tout le bloc d’ancrage.
- Les rotations des cubes et l’orientation du bloc sont prises en compte ; les modèles déjà placés conservent leurs données.
- La collision physique reste désactivée.

### Documentation et qualité de vie
- README réorganisé avec installation, prise en main, commandes, sièges, limites, compatibilité et dépannage.
- Ajout de tests sur les limites, rotations, meshes et centrage du contour.

## 6.5.0

### Verrouillage temporaire des sièges
- Durée de verrouillage configurable par modèle lors de l’import, de 0 à 3 600 secondes ; 0 conserve la sortie normale.
- Le joueur peut forcer une sortie d’urgence avec `/unstuck`, qui le téléporte en X=0, Z=0 au niveau du sol de sa dimension.
- Le verrouillage est synchronisé avec les clients et libéré automatiquement à son expiration, à la mort du joueur ou si le bloc d’ancrage disparaît.

## 6.0.0

### Support expérimental des meshes Blockbench
- Le panneau d’import propose une case orange « Meshes (instable) » pour importer les éléments mesh en plus des cubes.
- Les meshes activés sont affichés dans l’aperçu 3D et en jeu ; le survol et la sélection de sièges s’appuient sur les faces du mesh.
- Les commandes d’information du modèle indiquent le nombre de meshes.
- Les modèles cubiques existants restent compatibles ; les meshes sont refusés à l’import tant que l’option expérimentale n’est pas activée.

## 5.6.0

### Sièges par voxel du modèle
- Chaque cellule de 1×1×1 pixel Blockbench peut être sélectionnée séparément ; les sièges ne sont plus regroupés par blocs de 16×16×16 pixels Minecraft.
- Les sièges précis sont placés à la hauteur du voxel choisi.
- La taille des voxels de siège est enregistrée avec les modèles ; les modèles déjà sauvegardés conservent leur interprétation historique.

### Flou du menu d’import
- Désactivation temporaire du réglage global de flou des menus pendant l’import, puis restauration de sa valeur précédente lorsque l’écran est quitté.
- Le voile sombre translucide conserve le contraste sans flouter les textes ni l’aperçu.

### Compatibilité
- Minecraft 1.21.1, NeoForge 21.1.265 et Java 21.

## 5.5.2

### Première correction du flou
- Le réglage global de flou des menus Minecraft est temporairement désactivé pendant l’ouverture du panneau d’import.
- La valeur choisie par le joueur est restaurée en quittant l’écran ou en ouvrant un autre écran ; les préférences graphiques restent inchangées.
- Cette correction ne couvrait pas tous les cas ; le réglage est maintenant également contrôlé pendant le rendu en 5.6.0.

### Compatibilité
- Minecraft 1.21.1, NeoForge 21.1.265 et Java 21.

## 5.5.1

### Correction du flou
- Le menu d’import ne fait plus appel au fond transparent de Minecraft et dessine un voile translucide ; le réglage global du flou est désormais également désactivé temporairement en 5.5.2.

### Compatibilité
- Minecraft 1.21.1, NeoForge 21.1.265 et Java 21.

## 5.5.0

### Aperçu 3D interactif
- L’aperçu du modèle est rendu en perspective et peut être tourné librement en glissant la souris, comme dans Blockbench.
- Le voxel sous le curseur est surligné ; un clic le définit ou le retire comme siège, avec une sélection visible dans l’aperçu 3D.
- La sélection suit le rayon de la caméra afin de choisir le voxel visible sous le curseur, y compris lorsque les sièges se chevauchent à l’écran.
- Les instructions de rotation et de sélection sont traduites en français et en anglais.

### Compatibilité
- Minecraft 1.21.1, NeoForge 21.1.265 et Java 21.

## 5.1.0

### Corrections
- Première tentative de remplacement du flou d’arrière-plan du menu d’import ; correction complétée en 5.5.1 car le fond transparent de Minecraft continuait à appliquer le flou.

### Compatibilité
- Minecraft 1.21.1, NeoForge 21.1.265 et Java 21.

## 5.0.0

### Sièges sélectionnables
- Sélection de voxels de 16×16×16 pixels directement sur l’aperçu du modèle ; les marqueurs distinguent les voxels sélectionnés.
- Jusqu’à 64 emplacements par modèle, stockés avec les données importées et synchronisés avec les clients.
- Clic droit sur le bloc du modèle pour prendre le premier siège libre ; accroupissement pour descendre.
- Les sièges suivent l’orientation du bloc et plusieurs joueurs peuvent occuper les différents sièges.
- Les modèles sans siège restent inchangés et affichent un message explicatif à l’interaction.
- Validation serveur des coordonnées et de leur chevauchement avec la géométrie du modèle.
- Les ancres invisibles de siège disparaissent lorsqu’elles sont inoccupées ou que leur bloc support est retiré.

### Compatibilité
- Minecraft 1.21.1, NeoForge 21.1.265 et Java 21.
- Les anciens modèles sont lus sans modification et n’ont aucun siège par défaut.

## 4.0.0

### Nouveautés et confort d’utilisation
- Aperçu isométrique du modèle directement dans le panneau d’import, avant tout envoi au serveur.
- Bouton de rotation de l’aperçu pour inspecter les formes sous plusieurs angles.
- Affichage du nombre de cuboïdes, textures intégrées et octets normalisés.
- Validation complète du fichier côté client avant de démarrer le transfert.
- Lecture, décodage UTF-8 et validation hors du thread graphique pour éviter les freezes.
- Nom proposé automatiquement, nettoyé des caractères interdits, avec valeur de secours sûre.
- Sélection multi-fichiers refusée explicitement au lieu d’ignorer silencieusement les fichiers suivants.
- Boutons Importer/Effacer/Tourner adaptés à l’état de préparation et de transfert.
- Import lançable avec Entrée lorsque le modèle est prêt.
- Confirmation avant de remplacer un modèle déjà synchronisé sur le client.
- Les fichiers invalides affichent maintenant la cause de validation.
- Effacement de la sélection et libération de la texture d’aperçu quand le panneau se ferme.
- Textes du panneau d’import traduits en français et en anglais.

### Compatibilité
- Minecraft 1.21.1, NeoForge 21.1.265 et Java 21.
- Aucun changement au format des données des mondes.

## 3.5.0

### Corrections
- Les transferts de modèles incomplets expirent après deux minutes, libérant leur mémoire même si le client se déconnecte.
- Le réassembleur limite désormais les transferts simultanés et la mémoire cumulée, et rejette les métadonnées de chunks impossibles.
- Un échec d’écriture du fichier de données annule maintenant la modification en mémoire au lieu de laisser un modèle non sauvegardé disponible jusqu’au redémarrage.

### Compatibilité
- Minecraft 1.21.1, NeoForge 21.1.265 et Java 21.

## 3.0.0

### Ajouts
- Les blocs de modèle s’orientent selon la direction du joueur lors de leur pose.
- `/model-info <nom>` affiche les cuboïdes, textures intégrées et la taille des données stockées.
- Les noms de `/list-models` sont cliquables et préparent `/give-model`.

### Compatibilité
- Minecraft 1.21.1, NeoForge 21.1.265 et Java 21.

## 2.0.0

### Ajouts
- `/list-models [page]` affiche les modèles importés par pages de 20.
- `/give-model <nom> [quantité]` permet de recevoir jusqu’à 64 blocs en une commande.
- Messages de commandes traduits en anglais et en français.

### Corrections
- Le cache client traite désormais les noms de modèles sans tenir compte de la casse, comme le stockage serveur. Réimporter un modèle avec une casse différente met ainsi à jour les blocs déjà placés.
- Le résultat d’import indique clairement quand un modèle existant a été remplacé.

### Compatibilité
- Minecraft 1.21.1, NeoForge 21.1.265 et Java 21.
