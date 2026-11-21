# KVG Malory

Le portail web et l’application Android partagent l’interface du parcours de Malory. Malory, Vanessa et Guillaume utilisent leurs comptes KVG existants.

- `web/` contient le site statique. Publier les quatre fichiers ensemble et placer l’APK signé à côté de `index.html` pour son téléchargement.
- Les données et les autorisations sont gérées dans le service privé KVG Malory déjà déployé.
- `android/` contient l’application Android 9 ou ultérieur. Son interface est incluse dans l’APK et ses données sont chargées en ligne.

Après une connexion par mot de passe, le bouton **Activer l’empreinte** associe le compte à ce téléphone. Android valide la biométrie pour chiffrer puis déchiffrer l’accès local dans Android Keystore. Une déconnexion explicite supprime cet accès. Après un changement de mot de passe, il faut reconnecter le compte. L’accès biométrique se renouvelle par mot de passe après 30 jours.

Pour reconstruire l’APK, utiliser le JDK, la plateforme Android 35, les Build Tools 35 et `zip`. Exécuter `android/build.sh` avec les variables `ANDROID_JAR`, `BUILD_TOOLS`, `SIGNING_STORE`, `SIGNING_ALIAS` et `SIGNING_PASSWORD`. La clé de publication est conservée séparément du dépôt.
