# KVG — V51, autoriser l'empreinte des vendeuses Guadeloupe dans le Master

## Le problème observé

Le correctif V49 fournissait un pont biométrique Android + des fonctions Apps Script côté vendeuses, mais **n'ajoutait AUCUN réglage dans KVG Master**. De plus, les boutons vendeuses étaient cachés lorsqu'aucun appareil n'était associé, ou lorsque l'APK d'origine ne contenait pas le pont Android.

Ce V51 corrige ces deux points, avec un contrôle côté serveur. **Il ne suffit pas d'installer un APK.** Il faut publier les deux projets Apps Script.

## 0. Avant toute modification

- Dans Apps Script, exporte/sauvegarde les sources actuelles des projets **KVG_MASTER_CONTROLE_v2** et **KVG Guadeloupe Vendeuses**.
- N'efface PAS les feuilles existantes, les historiques, ni les identifiants et les secrets de connexion.
- Les fichiers complets fournis sont basés sur les sources connues **Master V50** et **Vendeuse V49**. Si ton site a évolué depuis, applique uniquement les fichiers de différences et les fragments, et NON les fichiers complets.
- Le module précédent **KVG_GUA_BIOMETRIE_V49.gs** doit être **REMPLACÉ** par le contenu du nouveau module V51, pas doublé.

## 1. KVG MASTER — Activation individuelle par la Direction

1. Dans le projet Apps Script **KVG_MASTER_CONTROLE_v2**, ajoute **MASTER/KVG_GUA_BIO_DIRECTION_V51.gs**.
2. Dans **KVG_SITE.html**, applique **PATCHES/PATCH_MASTER_V50_V51.diff** si ta base est exactement V50. Sinon, fais uniquement les 4 ajouts suivants :
   - Dans la liste `NAV`, immédiatement après `['rh','♙','Gestion des profils','Ressources']`, ajoute : `['biogua','☝','Empreintes vendeuses','Ressources']`.
   - Dans l'objet `TITLES`, ajoute : `biogua:['Empreintes vendeuses Guadeloupe','Activer ou retirer la connexion par empreinte pour chaque vendeuse.']`.
   - Dans le `switch(state.tab)` de `render()`, ajoute : `case 'biogua':html=kvg51BioView_();break;`.
   - Après `$('content').innerHTML=html;attachDynamic();`, ajoute `if(state.tab==='biogua')kvg51BioBind_();`, puis colle **MASTER/KVG_GUA_BIO_DIRECTION_INTERFACE_V51.html** avant `</body>`.
3. Publie une **Nouvelle version** du déploiement Web, en conservant `Exécuter en tant que : Moi` et le lien `/exec` existant.
4. Ouvre **Ressources → Empreintes vendeuses** dans KVG Master. L'écran liste les profils Guadeloupe du registre RH central. Pour chaque personne, clique **Autoriser l'empreinte**. Le code personnel et le profil doivent déjà être configurés.
5. Une autorisation retirée invalide les anciens secrets biométriques. Si tu réautorises cette personne plus tard, elle devra désactiver puis réassocier son téléphone dans `Mon profil`.

### Stockage central

Lors de la première modification, le module crée une **nouvelle feuille `KVG RH · BIOMETRIE`** dans le même classeur RH central que `KVG RH · PROFILS`. Les comptes existants, ventes et historiques ne sont pas modifiés. Ce registre contient uniquement identifiant RH, login, statut d'autorisation, numéro de génération et date de modification. **Aucune empreinte n'y est stockée.**

## 2. KVG GUADELOUPE VENDEUSES — BOUTONS ET CONTRÔLE D'AUTORISATION

1. Dans le projet Apps Script **vendeuses Guadeloupe**, remplace **le contenu du fichier existant** `KVG_GUA_BIOMETRIE_V49.gs` par celui de **GUADELOUPE/KVG_GUA_BIOMETRIE_V51.gs**. Ne conserve **PAS** les deux modules à la fois (noms de fonctions identiques). Si V49 n'existe pas, crée directement un seul fichier portant ce contenu.
2. Dans **KVG_App.html**, retire l'ancien bloc `<script>` V49 commençant par `/* KVG GUA V49` si présent. Colle **GUADELOUPE/KVG_GUA_BIO_INTERFACE_V51.html** avant le dernier `</body>`. Si la source est exactement l'ancienne V49, tu peux appliquer **PATCHES/PATCH_GUADELOUPE_V49_V51.diff** ; sinon n'écrase pas l'ensemble du HTML.
3. Déploie **Nouvelle version** dans le même déploiement Web Apps Script et vérifie que l'URL `/exec` utilisée par ton APK est inchangée.
4. Le bouton **« Se connecter avec mon empreinte »** est maintenant **visible** à l'écran de connexion, mais désactivé jusqu'à association d'un téléphone.
5. Dans **Mon profil**, le bloc **Empreinte digitale** est affiché même si l'app ne possède pas le pont Android : il explique alors pourquoi l'activation est indisponible.

## 3. APK Android — version de test déjà compilée

- Le dossier `ANDROID/` contient l'APK d'essai **KVG Guadeloupe V49**, compilé sur la branche GitHub `kvg-guadeloupe-bio-v49-20261114`.
- Il s'installe **à côté de l'application actuelle** (identifiant `com.laboratoirekvg.guadeloupe.biov49`). Il ne doit PAS remplacer l'application vendeuse officielle existante.
- Cette APK a un pont biométrique et un coffre Android Keystore. Elle n'a pas été testée sur le téléphone de chaque vendeuse.
- **Android 9+** et un capteur de biométrie forte avec une empreinte déjà ajoutée dans les réglages système sont requis. Le capteur reconnaît tout doigt enregistré sur ce téléphone, pas « une vendeuse » en particulier : utiliser un téléphone personnel, pas un smartphone partagé.
- La version officielle devra être signée avec le certificat ORIGINAL Guadeloupe ; ce correctif ne fournit pas cette clé.

## 4. Activation et test

1. Dans **KVG Master → Ressources → Empreintes vendeuses**, choisis Vanessa (ou Nadine, ou chaque autre profil) → **Autoriser l'empreinte**.
2. Sur le téléphone de la vendeuse, configure une empreinte dans **Paramètres Android → Sécurité → Empreintes digitales** si nécessaire.
3. Ouvre l'**APK d'essai V49** et connecte-toi avec son **code personnel habituel**.
4. Ouvre **Mon profil → Empreinte digitale → Activer mon empreinte sur ce téléphone** puis confirme avec le capteur Android.
5. Déconnecte-toi, sélectionne la même vendeuse et appuie sur **Se connecter avec mon empreinte**.
6. Dans le Master, **Retirer l'autorisation** : la prochaine connexion par empreinte doit être refusée. La connexion par code personnel reste disponible.

## Si tu ne vois toujours pas les boutons

- **Pas de menu « Empreintes vendeuses » dans le Master** : le `KVG_SITE.html` du MASTER n'a pas été mis à jour ou la nouvelle version n'a pas été déployée.
- **Pas de bouton empreinte sur la connexion vendeuse** : le `KVG_App.html` régional n'a pas été mis à jour / redéployé, ou l'APK pointe vers un ancien déploiement `/exec`.
- **Message « APK non compatible »** : tu as ouvert l'ancienne APK officielle ou Chrome, et non la version Android qui contient le pont biométrique.
- **Message « non autorisée »** : la Direction n'a pas activé ce compte, ou le bon projet régional n'a pas été déployé.
- **Message session expirée** : reconnexion par code requise avant de configurer l'empreinte.

**Tests** : vérification de syntaxe JavaScript de tous les scripts HTML et simulation de la sécurité inter-applications (autorisation, refus, changement de code, blocage, révocation, génération). Aucun changement n'a été effectué dans tes deux projets Apps Script déployés à distance ; seul un correctif installable est fourni.