# Changelog

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
- Le menu d’import n’applique plus le flou d’arrière-plan au texte et à l’aperçu ; un voile sombre translucide conserve le contraste tout en laissant le monde visible.

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
