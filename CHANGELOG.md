# Changelog

Toutes les modifications notables apportées au projet **ScryBook** seront documentées dans ce fichier.

Le format est basé sur [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
et ce projet adhère au [Versionnage Sémantique](https://semver.org/spec/v2.0.0.html).

## [0.2.6] - 2026-03-05

### Ajouté
- **Panneaux latéraux dynamiques (Paysage)** : En mode paysage, l'ouverture des personnages ou des lieux remplace désormais dynamiquement le panneau du résumé pour un meilleur gain d'espace.
- **Navigation latérale** : Ajout d'un bouton de fermeture ("X") dans les panneaux de personnages et de lieux pour revenir rapidement au résumé du chapitre.

## [0.2.5] - 2026-03-03

### Corrigé
- **Lisibilité de l'éditeur** : Correction d'un problème où le menu de correction automatique (spellcheck) s'affichait en noir sur noir.
- **Thème système** : Passage à un thème XML MD3 DayNight pour une meilleure intégration des menus système en mode sombre.

## [0.2.4] - 2026-03-03

### Ajouté
- **Barre de bas (Bottom Bar)** : Ajout d'une barre en bas sur tous les écrans pour une meilleure cohérence visuelle.
- **Optimisation Paysage** : La barre est plus haute en mode paysage pour un meilleur confort visuel sur les écrans larges.

## [0.2.3] - 2026-03-03

### Changé
- **Branding** : Mise à jour de l'identité visuelle et préparation pour le Play Store.

## [0.1.9] - 2026-02-28

### Fix
- **Conformité Google Play** : Suppression de l'autorisation "Accès à tous les fichiers" (MANAGE_EXTERNAL_STORAGE) pour respecter les politiques du Play Store. La sauvegarde "Edit in Place" repose désormais sur l'Accès aux Documents Android (SAF), ce qui est plus sûr et autorisé.

## [0.1.8] - 2026-02-28

### Ajouté
- **Modification Directe (Edit in Place)** : L'application synchronise désormais automatiquement les modifications vers le fichier d'origine, même s'il est situé dans un dossier protégé (Documents, Google Drive, etc.).
- **Accès Complet aux Fichiers** : Ajout du support de l'autorisation "Accès à tous les fichiers" (Android 11+) pour un meilleur contrôle sur vos dossiers de projets.
- **Diagnostic de Déploiement** : Amélioration des logs GitHub Actions pour faciliter le débogage de la configuration Google Play.

## [0.1.5] - 2026-02-28

### Ajouté
- **Bouton Sauvegarde Manuelle** : Ajout d'une icône de sauvegarde dans la barre de titre pour forcer l'enregistrement immédiat.
- **Support des caractères spéciaux** : Amélioration de la gestion des caractères dans le JSON de configuration du Play Store.

## [0.1.4] - 2026-02-28

### Ajouté
- **Gestionnaire de fichiers amélioré** : L'application travaille désormais sur le fichier d'origine (via résolution de chemin réel) au lieu d'utiliser des copies cache temporaires.
- **Support des fichiers externes** : Amélioration de la persistance pour les fichiers ouverts depuis Google Drive, Nextcloud, etc. via copie dans le dossier permanent `/ScryBook`.
- **Navigateur de dossiers** : Ajout d'une option "Parcourir" dans l'écran de création de projet pour choisir visuellement le dossier de destination.
- **Documentation visuelle** : Intégration de captures d'écran réelles dans le README pour un meilleur aperçu de l'interface.

### Corrigé
- **Déploiement GitHub** : Correction de l'erreur JSON dans le pipeline de déploiement Play Store.

## [0.1.3] - 2026-02-28

### Ajouté
- **Sauvegarde automatique brute** : L'éditeur enregistre maintenant toutes les 30 secondes.
- **Sauvegarde intelligente** : Enregistre automatiquement lors du changement de chapitre via le menu latéral.
- **Sauvegarde de cycle de vie** : Sauvegarde automatique quand l'application est mise en arrière-plan ou fermée.
- **Gestion des permissions** : Demande explicite des permissions de stockage pour les versions d'Android < 13.
- **Dialogue d'explication** : Ajout d'une popup expliquant pourquoi la permission de stockage est nécessaire.
- **Localisation** : Traduction des messages système en Français et Anglais.

### Corrigé
- **Lien SDK** : Correction du chemin incorrect vers le SDK Android dans les propriétés locales.
- **Stabilité** : Utilisation de verrous (Mutex) pour éviter les corruptions de fichiers lors de sauvegardes simultanées.
- **Robustesse** : Les sauvegardes se terminent même si l'interface est détruite (contexte non-annulable).

## [0.1.2] - 2026-02-27

### Ajouté
- **Fichiers récents** : Liste des 4 derniers projets ouverts sur l'écran d'accueil.
- **Automatisation** : Mise en place des GitHub Actions pour le déploiement automatique sur le Google Play Store.

## [0.1.1] - 2026-02-26

### Ajouté
- **Mode Paysage** : Support complet de l'affichage permanent du menu et du résumé sur tablettes et écrans larges.
- **Insertion d'images** : Amélioration du redimensionnement et de l'alignement des images insérées dans le texte.

## [0.1.0] - 2026-02-24

### Ajouté
- **Éditeur WYSIWYG** : Premier éditeur riche basé sur WebView avec barre de formatage.
- **Gestion de projet** : Création, ouverture et suppression de projets au format `.sb` (SQLite).
- **Structure de livre** : Gestion des chapitres, personnages et lieux.
- **Export PDF** : Première version de l'exportation du manuscrit au format PDF.

---
*Dernière mise à jour : 5 mars 2026*
