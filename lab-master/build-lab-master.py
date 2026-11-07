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
        versionCode 3
        versionName '1.2.0'
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

# KVG Lab Master utilise une WebView native en plein ecran.
java = java.replace(
    "        findViewById(R.id.button_refresh).setOnClickListener(view -> reload());\n", "")
# Aucun bouton d'ouverture du navigateur ni barre d'adresse dans l'application.
java = java.replace(
    "        findViewById(R.id.button_menu).setOnClickListener(this::showMenu);\n", "")
java = java.replace(
    "        findViewById(R.id.button_browser).setOnClickListener(view -> openExternal(START_URL));\n", "")
java = java.replace(
    "        desktopMode = preferences.getBoolean(PREF_DESKTOP, isTablet);",
    "        desktopMode = false;")


# En affichage immersif, le conteneur n'ajoute pas les anciennes marges systeme.
start = java.find("        // Android 15+ applique")
end = java.find("        progressBar = findViewById", start)
if start < 0 or end < start:
    raise RuntimeError("Bloc Android insets introuvable dans MainActivity.java")
java = java[:start] + java[end:]
java = java.replace(
    "        setContentView(R.layout.activity_main);",
    "        setContentView(R.layout.activity_main);\n        enterImmersiveMode();")

immersive = """
    private void enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller =
                    getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

"""
if "    private void initializeWebView()" not in java:
    raise RuntimeError("Ancre initializeWebView introuvable")
java = java.replace("    private void initializeWebView()", immersive +
                    "    private void initializeWebView()")

# Une authentification Google n'est pas autorisee dans une WebView.
# Signaler le probleme sans ouvrir Chrome automatiquement.
old_login = """            if (host != null && (host.equalsIgnoreCase("accounts.google.com")
                    || host.toLowerCase(Locale.ROOT).endsWith(".accounts.google.com"))) {
                openExternal(uri.toString());
                return true;
            }"""
new_login = """            if (host != null && (host.equalsIgnoreCase("accounts.google.com")
                    || host.toLowerCase(Locale.ROOT).endsWith(".accounts.google.com"))) {
                pageHasError = true;
                progressBar.setVisibility(View.GONE);
                errorPanel.setVisibility(View.VISIBLE);
                Toast.makeText(this,
                        "Connexion Google requise : utilisez explicitement le navigateur ou verifiez le deploiement Apps Script.",
                        Toast.LENGTH_LONG).show();
                return true;
            }"""
if old_login not in java:
    raise RuntimeError("Ancre connexion Google introuvable")
java = java.replace(old_login, new_login)
java = java.replace(
    '        items.add(0, MENU_BROWSER, 3, "Ouvrir dans Chrome / navigateur");\n', "")
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

# Supprimer la barre d'outils de 54 dp.
# Le site gere lui-meme sa navigation et l'actualisation, sans menu Android.
layout = (root / "activity_main.xml").read_text(encoding="utf-8")
top_start = layout.find('    <LinearLayout\n        android:id="@+id/top_bar"')
top_end = layout.find('    <ProgressBar', top_start)
if top_start < 0 or top_end < top_start:
    raise RuntimeError("Barre d'outils introuvable dans activity_main.xml")
layout = layout[:top_start] + layout[top_end:]
anchor = "    </FrameLayout>\n</LinearLayout>"
# Supprimer egalement le bouton du navigateur presente dans le panneau d'erreur.
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
    <string name="menu">Options</string>
    <string name="site_unavailable">Impossible d’ouvrir la page</string>
    <string name="site_unavailable_hint">Connexion impossible. Vérifiez Internet et les autorisations de votre accès KVG, puis réessayez.</string>
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
print("KVG Lab Master Android 1.2.0: plein ecran smartphone, sans navigateur, sans lien a saisir.")
