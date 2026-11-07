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
        versionCode 1
        versionName '1.0.0'
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
(java_dir / "MainActivity.java").write_text(java, encoding="utf-8")

for source_name, target_name in [
    ("activity_main.xml", "layout/activity_main.xml"),
    ("ic_launcher.xml", "mipmap-anydpi-v26/ic_launcher.xml"),
    ("ic_launcher_round.xml", "mipmap-anydpi-v26/ic_launcher_round.xml"),
]:
    source_file = root / source_name
    if not source_file.exists():
        raise RuntimeError("Missing original Android resource " + source_name)
    target = res / target_name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_file, target)

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
    <string name="site_unavailable_hint">Vérifiez votre connexion et les autorisations du site Apps Script. Vous pouvez aussi ouvrir le site dans votre navigateur.</string>
    <string name="retry">Réessayer</string>
    <string name="open_browser">Ouvrir dans le navigateur</string>
</resources>
""", encoding="utf-8")

(res / "values/colors.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple">#B98B39</color>
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
print("KVG Lab Master: source Android assembled; independent applicationId, oil logo, and site wrapper.")
