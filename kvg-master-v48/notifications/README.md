# KVG Master sur Android

L’accueil utilise le site Master et sa connexion habituelle. Les onglets
Ventes, Pharmacies, Concours et Équipe répertorient neuf projets.
Les liens ouvrent le navigateur Android pour utiliser les sessions Web et les
connexions Google existantes. Un appui long modifie l’adresse de chaque projet
sur le téléphone. Les URL de Commandes Pharmacies et des concours Instagram et Carrefour
sont préremplies. Pour les six autres projets, utiliser leurs URL de production.

## Configuration des notifications

1. Dans Firebase, associer l’application Android `com.laboratoirekvg.master`.
   Placer le JSON de configuration dans `app/google-services.json` pour compiler
   localement, ou dans le secret GitHub Actions `KVG_FIREBASE_JSON` pour la CI.
   Le JSON de configuration Android ne doit pas contenir de clé de compte de service.
2. Construire la version release et la signer avec le certificat original avant
   de mettre à jour une installation existante. L’APK debug sert aux essais sur
   une installation compatible avec sa signature.
3. Dans la cloche, ouvrir « Configurer les alertes Android », autoriser les
   notifications et relever le jeton de l’appareil. Le renouveler dans la
   configuration serveur si l’appareil ou son jeton change.
4. Dans le projet Apps Script d’envoi, ajouter `KvgPush.gs`. Associer un projet
   Cloud standard, activer l’API Firebase Cloud Messaging et autoriser
   l’identité d’exécution à envoyer des messages dans le projet Firebase cible
   avec un rôle IAM approprié. Ajouter les scopes
   `https://www.googleapis.com/auth/firebase.messaging` et
   `https://www.googleapis.com/auth/script.external_request` au manifeste en
   conservant les scopes existants. Renseigner `KVG_FCM_PROJECT_ID` et
   `KVG_FCM_DEVICE_TOKEN` dans les propriétés privées du script.
5. Réautoriser le script et exécuter `kvgPushTest_` depuis Apps Script. Vérifier
   l’alerte Android en arrière-plan et son entrée dans la cloche.
6. Brancher les actions serveur réelles sur l’émetteur. Appeler
   `kvgPushProjet_(project, id, title)` après la sauvegarde, avec un identifiant
   stable pour chaque événement et un libellé générique sans données de client.
   Prévoir une reprise indépendante en cas d’échec de l’envoi pour éviter de
   rejouer l’opération métier. Le cas échéant, un script peut intégrer cette
   fonction avec sa propre identité et ses propres propriétés.

La boîte locale conserve jusqu’à 200 notifications reçues. Un clic sur l’alerte
Android ouvre la boîte, puis un clic sur l’entrée ouvre le projet.
