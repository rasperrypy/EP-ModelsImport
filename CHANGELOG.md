# Changelog

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
