# KVG Master Guadeloupe – Accès Google « Moi uniquement »

Version de compatibilité avec un déploiement Apps Script réservé au compte propriétaire.

## Fonctionnement
- Au lancement, l'application ouvre une **Chrome Custom Tab sécurisée** dans la tâche de l'application.
- L'onglet partage la connexion Google avec Chrome installé sur le téléphone.
- Les couleurs sont noir et or, et la barre d'adresse peut se replier lors du défilement.
- Aucun WebView isolé n'est utilisé pour la connexion Google.
- Le site garde ses permissions Apps Script « Moi uniquement » et sa base de données.
- Au retour, un écran minimal permet de rouvrir KVG Master.

## Limite importante
La barre d'identification du site ne peut pas être masquée entièrement dans ce mode.
Un affichage réellement sans barre nécessiterait une TWA vérifiée par Digital Asset Links
sur un domaine dont le propriétaire contrôle la validation. Ce n'est pas possible sur
l'origine Google Apps Script script.google.com.

## Installation
Installer l'APK de test signé par GitHub Actions.
Si une ancienne version de l'application a été signée avec une autre clé de test,
Android pourra demander de la désinstaller avant installation.
Les informations restent dans les feuilles Google Sheets et sur le serveur.

## Sécurité
Aucune tentative de contournement des autorisations Google.
Un utilisateur non propriétaire n'obtient pas l'accès aux feuilles.
