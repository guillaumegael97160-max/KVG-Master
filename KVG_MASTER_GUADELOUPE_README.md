# KVG Master Guadeloupe — navigation totalement integree
Cette version de l'APK conserve le tableau de bord principal.
Le bouton ↗ lance une seconde activité WebView / Chromium **dans la meme application**,
sans demarrer l'application Chrome et sans barre d'adresse.
La seconde page affiche uniquement Retour et Actualiser sur une barre de 34dp.
L'appui long sur le logo de la page principale bascule telephone / bureau.

## Limites
WebView utilise le moteur Chromium d'Android et **ne constitue pas l'application Chrome**.
L'authentification Google peut refuser les WebView integrees pour certains comptes et
certaines autorisations. Ce comportement ne peut pas etre contourne par un habillage visuel.
Le projet ne change pas les droits Google Sheets du serveur Apps Script.

## Mise a jour
Le package est le meme que celui de la premiere version compacte.
Installer l'APK mis a jour par-dessus si la signature est identique.
