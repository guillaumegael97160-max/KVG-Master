#!/usr/bin/env python3
"""Assemble KVG Lab Master from the previously proven KVG Android source tree."""
from pathlib import Path
from PIL import Image
from io import BytesIO
import base64, shutil

root = Path(__file__).resolve().parents[1]
app = root / "app"
java_dir = app / "src/main/java/com/laboratoirekvg/labmaster"
res = app / "src/main/res"
(java_dir).mkdir(parents=True, exist_ok=True)
(res / "layout").mkdir(parents=True, exist_ok=True)
(res / "values").mkdir(parents=True, exist_ok=True)
(res / "drawable-nodpi").mkdir(parents=True, exist_ok=True)
(res / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)

(root / "settings.gradle").write_text("""pluginManagement {
  repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories { google(); mavenCentral() }
}
rootProject.name = 'KVG-Lab-Master'
include ':app'
""")
(root / "build.gradle").write_text("""plugins {
  id 'com.android.application' version '8.7.3' apply false
}
""")
(root / "gradle.properties").write_text(
    "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\n")
(app / "build.gradle").write_text("""plugins {
    id 'com.android.application'
}
android {
    namespace 'com.laboratoirekvg.labmaster'
    compileSdk 35
    defaultConfig {
        applicationId 'com.laboratoirekvg.labmaster'
        minSdk 24
        targetSdk 35
        versionCode 5
        versionName '1.4.0'
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
""")

source = root / "MainActivity.java"
if not source.is_file():
    raise RuntimeError("Missing original MainActivity.java")
java = source.read_text(encoding="utf-8")
assert "package com.laboratoirekvg.larentree;" in java
java = java.replace("package com.laboratoirekvg.larentree;",
                    "package com.laboratoirekvg.labmaster;")
java = java.replace("la_rentree_preferences", "kvg_lab_master_preferences")
java = java.replace("La Rentrée", "KVG Lab Master")

# Version smartphone : navigateur WebView interne sans aucune barre d'adresse.
# Commandes natives : titre, actualiser, ouvrir volontairement dans Chrome.
def java_swap(old, new):
    global java
    if old not in java:
        raise RuntimeError("Source MainActivity.java modifiee : " + old[:70])
    java = java.replace(old, new, 1)

java_swap(
    "        desktopMode = preferences.getBoolean(PREF_DESKTOP, isTablet);",
    "        desktopMode = false;")
java_swap(
    "        findViewById(R.id.button_menu).setOnClickListener(this::showMenu);",
    "        findViewById(R.id.button_menu).setOnClickListener(view -> openInChrome());")
java_swap(
    "        findViewById(R.id.button_browser).setOnClickListener(view -> openExternal(START_URL));\n",
    "")

# La WebView n'a pas acces a la session Google de Chrome.
# Si Google reclame OAuth, afficher l'aide plutot que basculer
# automatiquement vers Chrome.
old_auth = """            if (host != null && (host.equalsIgnoreCase("accounts.google.com")
                    || host.toLowerCase(Locale.ROOT).endsWith(".accounts.google.com"))) {
                openExternal(uri.toString());
                return true;
            }"""
new_auth = """            if (host != null && (host.equalsIgnoreCase("accounts.google.com")
                    || host.toLowerCase(Locale.ROOT).endsWith(".accounts.google.com"))) {
                pageHasError = true;
                progressBar.setVisibility(View.GONE);
                errorPanel.setVisibility(View.VISIBLE);
                return true;
            }"""
java_swap(old_auth, new_auth)

# Certains renvois de connexion sont des redirections du document principal.
java_swap(
    "                pageHasError = false;\n                errorPanel.setVisibility(View.GONE);\n                progressBar.setProgress(5);",
    """                String host = Uri.parse(url).getHost();
                if (host != null && (host.equalsIgnoreCase("accounts.google.com")
                        || host.toLowerCase(Locale.ROOT).endsWith(".accounts.google.com"))) {
                    pageHasError = true;
                    view.stopLoading();
                    progressBar.setVisibility(View.GONE);
                    errorPanel.setVisibility(View.VISIBLE);
                    return;
                }
                pageHasError = false;
                errorPanel.setVisibility(View.GONE);
                progressBar.setProgress(5);""")

# La fleche dans la barre native donne explicitement acces a Chrome.
chrome_method = """
    private void openInChrome() {
        Intent chrome = new Intent(Intent.ACTION_VIEW, Uri.parse(START_URL));
        chrome.setPackage("com.android.chrome");
        try {
            startActivity(chrome);
        } catch (ActivityNotFoundException exception) {
            openExternal(START_URL);
        }
    }

"""
java_swap("    private void openExternal(String url) {",
          chrome_method + "    private void openExternal(String url) {")
# Ne jamais ouvrir Chrome par defaut depuis les commandes du site ;
# seuls les clics sur la fleche de la barre native le declenchent.

(java_dir / "MainActivity.java").write_text(java, encoding="utf-8")

for source_name, target_name in [
    ("ic_launcher.xml", "mipmap-anydpi-v26/ic_launcher.xml"),
    ("ic_launcher_round.xml", "mipmap-anydpi-v26/ic_launcher_round.xml"),
]:
    source_file = root / source_name
    if not source_file.exists():
        raise RuntimeError("Missing original Android resource " + source_name)
    target = res / target_name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_file, target)

# Affichage integre : titre KVG Lab Master + deux commandes compactes.
layout = (root / "activity_main.xml").read_text(encoding="utf-8")
def layout_swap(old, new):
    global layout
    if old not in layout:
        raise RuntimeError("Ressource layout modifiee : " + old[:70])
    layout = layout.replace(old, new, 1)

layout_swap('android:layout_height="54dp"', 'android:layout_height="48dp"')
layout_swap('android:background="@color/purple"', 'android:background="#14110D"')
layout_swap('android:paddingStart="12dp"', 'android:paddingStart="8dp"')
layout_swap('android:layout_width="38dp"\n            android:layout_height="38dp"',
            'android:layout_width="30dp"\n            android:layout_height="30dp"')
layout_swap('android:textColor="#FFFFFF"\n            android:textSize="18sp"',
            'android:textColor="#F5D98A"\n            android:textSize="17sp"')
# Icones action a taille raisonnable : rafraichir et ouvrir Chrome.
layout_swap('android:text="↻"\n            android:textSize="29sp"',
            'android:text="↻"\n            android:textSize="26sp"')
layout_swap('android:text="⋮"\n            android:textSize="28sp"',
            'android:text="↗"\n            android:textSize="27sp"')
# Supprimer le second bouton Chrome du panneau d'erreur, inutile.
browser_button = layout.find('            <Button\n                android:id="@+id/button_browser"')
if browser_button < 0:
    raise RuntimeError("Bouton navigateur introuvable dans activity_main.xml")
browser_button_end = layout.find(" />", browser_button)
if browser_button_end < 0:
    raise RuntimeError("Balise bouton navigateur mal formee")
layout = layout[:browser_button] + layout[browser_button_end + 3:]

(res / "layout/activity_main.xml").write_text(layout, encoding="utf-8")

manifest = (root / "AndroidManifest.xml").read_text(encoding="utf-8")
manifest = manifest.replace("Theme.LaRentree", "Theme.LabMaster")
(app / "src/main/AndroidManifest.xml").write_text(manifest, encoding="utf-8")

(res / "values/strings.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">KVG Lab Master</string>
    <string name="toolbar_title">KVG Lab Master</string>
    <string name="refresh">Actualiser</string>
    <string name="menu">Ouvrir dans Chrome</string>
    <string name="site_unavailable">Impossible d’ouvrir la page</string>
    <string name="site_unavailable_hint">Vérifiez Internet puis réessayez. Si Google demande une connexion, touchez la flèche en haut à droite pour ouvrir Chrome. La connexion Chrome ne se transfère pas automatiquement dans l’application.</string>
    <string name="retry">Réessayer</string>
    <string name="open_browser">Ouvrir dans le navigateur</string>
</resources>
""", encoding="utf-8")

(res / "values/colors.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple">#B98B39</color>
    <color name="pale_lavender">#F6F2FF</color>
    <color name="launcher_background">#090706</color>
</resources>
""", encoding="utf-8")
(res / "values/styles.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.LabMaster" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:colorAccent">#DDB65B</item>
        <item name="android:statusBarColor">#090706</item>
        <item name="android:navigationBarColor">#090706</item>
        <item name="android:windowLightNavigationBar">false</item>
        <item name="android:windowActionModeOverlay">true</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
</resources>
""", encoding="utf-8")

# Authored KVG Lab Master oil-drip logo committed to GitHub as a small base64 blob.
raw = base64.b64decode((root / "lab-master/logo.webp.b64").read_text(encoding="utf-8"))
im = Image.open(BytesIO(raw)).convert("RGB")
assert im.size[0] == im.size[1]
densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for density, size in densities.items():
    d = res / ("mipmap-" + density)
    d.mkdir(parents=True, exist_ok=True)
    icon = im.resize((size, size), Image.Resampling.LANCZOS)
    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        icon.save(d / name, optimize=True)
fg = im.resize((432, 432), Image.Resampling.LANCZOS)
fg.save(res / "drawable-nodpi/ic_launcher_foreground.png", optimize=True)
print("KVG Lab Master Android 1.4.0: WebView integree, titre, Chrome sur commande et actualisation.")
