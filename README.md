# La Rentrée – Mon Déo : application Android

Ce dossier contient le **projet Android complet** et le workflow **GitHub Actions** pour compiler un fichier `.apk` automatiquement.

**Lien ouvert par l'application :**

`https://script.google.com/macros/s/AKfycbyACsMvk1luR3c7ClDbIA-8Q1GmiF1VnYP3hLs9WigTzJbb6rk-SPeZRtHK7xHStekJGg/exec`

## Créer l'APK sur GitHub (sans Android Studio)

1. Décompresser `La_Rentree_Mon_Deo_GitHub.zip` sur votre ordinateur.
2. Sur GitHub, créer un dépôt, par exemple `La-Rentree-Mon-Deo` (public ou privé).
3. Ouvrir le dépôt et sélectionner **Add file > Upload files**. Transférer **le contenu du dossier décompressé**, y compris le dossier caché `.github`, à la **racine** du dépôt, puis valider avec **Commit changes**.
4. Aller dans **Actions > Compiler APK Android**. Si nécessaire, cliquer sur **Run workflow** puis choisir la branche et confirmer.
5. Attendre le résultat vert de la compilation, cliquer sur l'exécution puis, en bas, télécharger l'artefact **La-Rentree-Mon-Deo-APK**.
6. Décompresser l'artefact téléchargé : il contient **`app-debug.apk`**, à envoyer sur le téléphone ou la tablette Android pour l'installer.

Important : **ne pas déposer le ZIP non décompressé** dans le dépôt : GitHub doit trouver `app/build.gradle` et `.github/workflows/compiler-apk.yml` directement.

**Si GitHub affiche « Actions disabled » :** activer GitHub Actions pour ce dépôt depuis ses paramètres.

## Ce que fait l'application

- Affiche le site Apps Script dans l'application, avec JavaScript, stockage local et cookies.
- Affiche l'image fournie comme icône Android.
- Permet d'actualiser, revenir en arrière et partager le lien.
- Propose un **mode ordinateur** activable dans le menu `⋮`. Il est activé automatiquement sur les tablettes (grand écran).
- Permet de choisir des images et fichiers depuis les formulaires du site.
- Gère les téléchargements web HTTP(S) sur les versions récentes d'Android.
- Propose **« Ouvrir dans Chrome / navigateur »** pour les connexions Google ou les sites qui refusent WebView.

## Autorisations du site Google Apps Script

Lors de notre vérification sans compte connecté, le lien Apps Script redirige vers une **connexion Google**. Certains formulaires Google refusent la connexion dans une WebView Android.

Si vous souhaitez ouvrir le site **directement dans l'application** pour tous les utilisateurs :

1. Dans Google Apps Script : **Déployer > Gérer les déploiements > Modifier**.
2. Vérifier l'option **Qui a accès**. Si le contenu peut être public, choisir **Tout le monde** (cela inclut les personnes sans compte Google), puis enregistrer le déploiement.
3. S'assurer que l'application web n'expose pas de données réservées. Si elle doit rester privée, **ne pas modifier ses accès** : utiliser plutôt le menu « Ouvrir dans Chrome / navigateur ».

Le site dépend de son hébergement Google et d'une connexion Internet. Ce projet Android ne modifie pas le code Apps Script et ne peut pas supprimer une obligation de connexion Google.

## Modifier le nom, l'icône ou l'adresse

- Adresse du site : `app/src/main/java/com/laboratoirekvg/larentree/MainActivity.java`, constante `START_URL`.
- Nom affiché : `app/src/main/res/values/strings.xml` (`app_name`).
- Icônes : `app/src/main/res/mipmap-*/ic_launcher*.png` et `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png`.
- Image originale : `image-originale/icone-fournie.png`.

Après une modification, enregistrer les nouveaux fichiers sur GitHub. Une nouvelle compilation démarre automatiquement.

## Remarques

- APK « debug » **déjà signé pour l'installation et les essais**, sans clé de signature à configurer.
- Compatible Android 7.0 et versions plus récentes (API 24+).
- Pour publier sur Google Play, il faut générer une version **release signée** et remplir les obligations de la boutique.
- Si Android refuse une mise à jour avec une erreur de signature différente, désinstaller l'ancienne APK de test puis installer la nouvelle. **La désinstallation efface les données locales de l'application.**
- Le projet utilise Android Gradle Plugin 8.7.3, Gradle 8.9, Java 17 et compileSdk 35.


## Correction GitHub Actions (05/11/2026)

Le workflow `.github/workflows/main.yml` utilise le SDK Android preinstalle sur GitHub Actions, sans l'action `android-actions/setup-android@v3` qui pouvait echouer en cherchant le paquet obsolete `tools`.

Copiez **tout le contenu de ce dossier a la racine du depot GitHub** (pas sous un dossier supplementaire), puis ouvrez Actions > Compiler APK Android KVG > Run workflow. Si la compilation reussit, l'APK se trouve dans l'artefact `KVG-La-Rentree-APK`.
