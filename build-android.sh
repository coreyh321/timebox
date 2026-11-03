#!/usr/bin/env bash
# Builds the Time Box Android app (run by GitHub Actions)
set -euo pipefail
npm install --no-audit --no-fund
rm -rf www android && mkdir www
cp index.html manifest.json sw.js icon-192.png icon-512.png www/
npx cap add android

# exact alarms (auto-granted on Android 13+, no settings prompt)
M=android/app/src/main/AndroidManifest.xml
sed -i 's#<application#<uses-permission android:name="android.permission.USE_EXACT_ALARM" />\n    <application#' "$M"

# app icon
RES=android/app/src/main/res
rm -rf "$RES/mipmap-anydpi-v26"
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  cp "mipmap-$d.png" "$RES/mipmap-$d/ic_launcher.png"
  cp "mipmap-$d.png" "$RES/mipmap-$d/ic_launcher_round.png"
done

# home-screen widget (native code + resources)
PKG=android/app/src/main/java/io/github/coreyh321/timebox
cp MainActivity.java WidgetBridge.java TimeBoxWidget.java "$PKG/"
mkdir -p "$RES/layout" "$RES/xml" "$RES/drawable" "$RES/values"
cp timebox_widget.xml "$RES/layout/"
cp timebox_widget_info.xml "$RES/xml/"
cp timebox_widget_bg.xml "$RES/drawable/"
cp timebox_widget_styles.xml "$RES/values/"
python3 - "$M" <<'PY'
import sys
p = sys.argv[1]; s = open(p).read()
rx = """
        <receiver android:name=".TimeBoxWidget" android:exported="false">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/timebox_widget_info" />
        </receiver>
    </application>"""
assert "</application>" in s
s = s.replace("    </application>", rx.lstrip("\n"), 1)
open(p, "w").write(s)
PY

npx cap sync android

# same signing key every build, so updates install over the old app and keep your data
# (key is created on the first build and stored in a "signing-key" release)
mkdir -p ~/.android
if ! gh release download signing-key -p debug.keystore -D ~/.android 2>/dev/null; then
  keytool -genkeypair -keystore ~/.android/debug.keystore -alias androiddebugkey -keyalg RSA \
    -keysize 2048 -validity 36500 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
  gh release create signing-key ~/.android/debug.keystore --title "signing key (do not delete)" \
    --notes "Used so app updates install over the old version." --prerelease
fi
(cd android && ./gradlew assembleDebug --no-daemon -q)
cp android/app/build/outputs/apk/debug/app-debug.apk TimeBox.apk

if [ -n "${GH_TOKEN:-}" ]; then
  gh release delete latest -y --cleanup-tag || true
  gh release create latest TimeBox.apk --title "Time Box (latest)" \
    --notes "On your phone: download TimeBox.apk and open it to install or update."
fi
