# LABORATOIRE KVG MASTER — Android noir et or

Projet Android natif distinct des applications KVG Vendeuses / Martinique / Gestion.

## Fonctions

- Logo d'application conçu depuis la **première photo** KVG MASTER.
- Diaporama de **6 images** au lancement : introduction, stocks, préparation/lots, ventes, statistiques, anticipation.
- Bouton « PASSER », transition douce, fond noir, détails or.
- Écran de chargement KVG MASTER durant l'ouverture du site.
- Site interne en **WebView plein écran immersif**, sans ouvrir une autre fenêtre Chrome.
- Bordure dorée discrète en haut, barre fine et petite commande `⟳` en haut à droite pour actualiser.
- **Appui long sur `⟳`** : permet de changer l'URL du déploiement KVG MASTER.
- Autorise l'envoi de fichiers via les formulaires, les cookies et téléchargements.
- Adresse Master persistante conservée localement.

## Important : adresse exacte du site

La bonne adresse `/exec` du déploiement **KVG MASTER** n'a pas été fournie dans ce tour. Les anciens liens présents dans le dépôt GitHub pouvaient pointer sur un autre déploiement. Pour ne pas ouvrir le mauvais centre de contrôle, `CONFIRMED_MASTER_URL` est donc vide.

Au **premier lancement**, après le diaporama, coller l'URL de KVG Master, sous la forme :
`https://script.google.com/macros/s/IDENTIFIANT/exec`

Pour préconfigurer l'APK dès sa compilation, remplacer la constante `CONFIRMED_MASTER_URL` dans `app/src/main/java/com/laboratoirekvg/master/MainActivity.java` par l'URL vérifiée.

## Compilation APK par GitHub Actions

1. Décompresser le ZIP, puis placer **tout son contenu** à la racine d'un dépôt GitHub réservé au KVG Master (ou sur une branche dédiée et adapter le workflow à la branche utilisée).
2. Dans l'onglet `Actions`, choisir **Compiler APK Laboratoire KVG MASTER Black Gold**, puis lancer `Run workflow`.
3. Lorsque l'exécution est verte, récupérer l'artifact **Laboratoire-KVG-MASTER-BlackGold-APK** et dézipper le fichier APK.

La compilation utilise Android SDK 35, Gradle 8.9, Java 17 et un APK debug signé automatiquement, Android 7+.

## Limitations honnêtes

Le déploiement Apps Script doit autoriser l'accès approprié. Les pages de connexion Google qui refusent les WebViews Android ne peuvent pas être forcées à s'ouvrir dans l'app. Les données serveur et le stock restent gérés par le site KVG Master, et non par l'APK.
