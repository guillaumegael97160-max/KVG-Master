# KVG Master Guadeloupe — application Android compacte
Cette branche est isolée de l'application KVG Lab Master et de l'ancienne application La Rentrée.

## Application
- Site directement chargé : https://script.google.com/macros/s/AKfycbyACsMvk1luR3c7ClDbIA-8Q1GmiF1VnYP3hLs9WigTzJbb6rk-SPeZRtHK7xHStekJGg/exec
- Une seule WebView intégrée (Chromium Android), aucune barre d'adresse et aucun onglet interne.
- Barre noire et dorée compacte : logo, Actualiser, Chrome.
- Le bouton Chrome ouvre la page en cours dans Google Chrome, avec navigateur par défaut en repli.
- Appui long sur le logo : bascule affichage téléphone/ordinateur.
- Le zoom au pincement est activé, mode PC chargé avec la largeur globale.
- L'application n'ajoute aucun droit Google Sheets. L'accès aux stocks dépend toujours du serveur Apps Script et du déploiement.

## Compilation
Dans Actions, ouvrir **Compiler APK KVG Master Guadeloupe compact**.
Récupérer l'artifact **KVG-Master-Guadeloupe-APK** ; il contient **KVG-Master-Guadeloupe.apk**.

Cette compilation de test est signée automatiquement par Android Gradle. Pour une publication Play Store, utiliser une signature de release dédiée.

## Maintenance
Les modifications du site Apps Script sont visibles sans recompiler l'application tant que son lien /exec reste inchangé.
