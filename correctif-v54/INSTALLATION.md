# KVG — V54 Affichage du stock en machine enregistré (lecture seule)

## Objectif
Dans « Ventes magasin hors présence », afficher **uniquement** la quantité déjà enregistrée dans KVG pour les quatre références de Mon Déo dans le magasin sélectionné. L'utilisateur compare lui-même avec le stock physique en magasin.

## Fonctionnement
- Sur KVG Master et KVG Guadeloupe, après sélection du magasin, quatre cartes indiquent **Enfant, Famille, Femme, Homme** avec leurs quantités issues du module existant « Stocks magasins » V27.
- La lecture se fait avec les autorisations habituelles, directement par `kvgV27MasterLire` ou `kvgV27GuaLire`, pour **le mois en cours** indépendamment de la période des ventes consultée.
- Donnée inconnue = **« — / Non renseigné »**, jamais `0` inventé.
- Aucune modification du stock, aucun écart calculé, aucune saisie supplémentaire, aucun report ni prime automatique.
- Le stock KVG provient du calcul/du suivi du module V27 ; ce n'est **pas** une observation physique du rayon en temps réel. Le libellé le précise.

## Installation sûre
**Deux projets Apps Script distincts :**
1. **KVG Master (`KVG_MASTER_CONTROLE_v2`)** : dans le HTML principal `KVG_SITE.html`, intégrer seulement les changements du fichier `PATCH_MASTER_V54.diff` au **script `kv26-script`** (ou remplacer uniquement le script V26 par `MODULE_V26_MASTER_V54.html`).
2. **KVG Guadeloupe Vendeuses** : dans `KVG_App.html`, intégrer `PATCH_GUADELOUPE_V54.diff` ou remplacer **uniquement** le script V26 par `MODULE_V26_GUADELOUPE_V54.html`.
3. Enregistrer et déployer une **Nouvelle version** dans chaque projet Apps Script, puis recharger l'application Android. **Aucune recompilation APK nécessaire.**

Les fichiers HTML complets sont fournis en exemples, basés respectivement sur KVG Master V50 et KVG Guadeloupe V53. **Ne remplacez pas aveuglément un HTML plus récent : utilisez les patches ciblés.** Vérifiez que les API V27 sont présentes et fonctionnent dans les deux projets avant déploiement.

## Contrôles
- Syntaxe JS du module V26 vérifiée pour Master et Guadeloupe avec Node.js.
- La section `kv26-script` est la seule portion de code modifiée dans chaque fichier.
- Aucun changement des fonctions de vente V26, d'historique ou de l'enregistrement des mouvements.
- L'affichage n'a pas été testé sur les déploiements Apps Script réels.