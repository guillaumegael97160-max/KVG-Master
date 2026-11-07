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
        versionCode 6
        versionName '1.5.0'
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
dependencies {
    implementation 'androidx.browser:browser:1.8.0'
}
""")

# Source native dediee au laboratoire KVG, sans WebView bloque par Google.
src_java = root / "lab-master/native/MainActivity.java"
if not src_java.is_file():
    raise RuntimeError("MainActivity natif introuvable")
shutil.copy2(src_java, java_dir / "MainActivity.java")

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

# Vue native en cas de fermeture de l'onglet Chrome integre.
src_layout = root / "lab-master/native/activity_main.xml"
if not src_layout.is_file():
    raise RuntimeError("Layout natif introuvable")
shutil.copy2(src_layout, res / "layout/activity_main.xml")

manifest = (root / "AndroidManifest.xml").read_text(encoding="utf-8")
manifest = manifest.replace("Theme.LaRentree", "Theme.LabMaster")
manifest = manifest.replace('android:exported="true"', 'android:exported="true" android:launchMode="singleTop"')
(app / "src/main/AndroidManifest.xml").write_text(manifest, encoding="utf-8")

(res / "values/strings.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">KVG Lab Master</string>
    <string name="toolbar_title">KVG Lab Master</string>
    <string name="refresh">Actualiser</string>
    <string name="menu">Ouvrir dans Chrome</string>
    <string name="site_unavailable_hint">Centre de contrôle sécurisé. Touchez le bouton pour le rouvrir avec votre session Google.</string>
    <string name="retry">Ouvrir le centre de contrôle</string>
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
print("KVG Lab Master Android 1.5.0: Chrome Custom Tab avec session Google et actualisation.")
