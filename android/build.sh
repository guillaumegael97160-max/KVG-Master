#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_JAR:?Chemin du fichier android.jar (plateforme 35)}"
: "${BUILD_TOOLS:?Dossier des Android Build Tools 35}"
: "${SIGNING_STORE:?Chemin de la clé de publication}"
: "${SIGNING_ALIAS:?Alias de la clé}"
: "${SIGNING_PASSWORD:?Mot de passe de signature}"
out="${OUTPUT_DIR:-$PWD/build}"
mkdir -p "$out/classes" "$out/dex" "$out/generated"
"$BUILD_TOOLS/aapt2" compile --dir res -o "$out/resources.zip"
"$BUILD_TOOLS/aapt2" link -o "$out/base.apk" --manifest AndroidManifest.xml -I "$ANDROID_JAR" --java "$out/generated" -A ../web "$out/resources.zip"
mapfile -t sources < <(find src "$out/generated" -name '*.java' -type f)
java -m jdk.compiler/com.sun.tools.javac.Main -source 8 -target 8 -encoding UTF-8 -bootclasspath "$BUILD_TOOLS/core-lambda-stubs.jar:$ANDROID_JAR" -d "$out/classes" "${sources[@]}"
java -m jdk.jartool/sun.tools.jar.Main --create --file "$out/classes.jar" -C "$out/classes" .
"$BUILD_TOOLS/d8" --min-api 28 --lib "$ANDROID_JAR" --output "$out/dex" "$out/classes.jar"
cp "$out/base.apk" "$out/unsigned.apk"
(cd "$out/dex" && zip -q -j "$out/unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$out/unsigned.apk" "$out/aligned.apk"
"$BUILD_TOOLS/apksigner" sign --ks "$SIGNING_STORE" --ks-key-alias "$SIGNING_ALIAS" --ks-pass env:SIGNING_PASSWORD --out "$out/KVG-Malory.apk" "$out/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$out/KVG-Malory.apk"
