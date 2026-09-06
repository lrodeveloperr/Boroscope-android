#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
manifest="$project_dir/app/src/main/AndroidManifest.xml"

cd "$project_dir"
node scripts/verify_localizations.mjs

test -f "$project_dir/settings.gradle.kts"
test -f "$project_dir/app/build.gradle.kts"
test -f "$project_dir/gooduse-shell/build.gradle.kts"
test -f "$project_dir/app/src/main/java/com/worksbien/borescopedirect/MainActivity.kt"

python3 - "$project_dir" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
xml_files = list((root / "app/src/main").rglob("*.xml"))
if not xml_files:
    raise SystemExit("no XML resources found")
for path in xml_files:
    ET.parse(path)
print(f"XML OK: {len(xml_files)} files")
PY

if rg -q 'android.permission.(INTERNET|RECORD_AUDIO|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|AD_ID)' "$manifest"; then
    echo "Unexpected sensitive permission in manifest" >&2
    exit 1
fi

rg -q 'android.permission.CAMERA' "$manifest"
rg -q 'com.github.ernestp.AndroidUSBCamera:libausbc:3.6.0' "$project_dir/app/build.gradle.kts"
rg -q 'com.android.billingclient:billing-ktx:9.1.0' "$project_dir/app/build.gradle.kts"
rg -q 'com.google.android.play:review:2.0.2' "$project_dir/app/build.gradle.kts"
rg -q 'CameraProfile\(1280, 720, PixelFormat.MJPEG\)' "$project_dir/app/src/main/java"
rg -q 'CameraProfile\(640, 480, PixelFormat.YUYV\)' "$project_dir/app/src/main/java"
rg -q 'TRIAL_DURATION_MILLIS = 90_000L' "$project_dir/app/src/main/java"
rg -q 'FrameStabilityProbe' "$project_dir/app/src/main/java"
rg -q '\.recording\.mp4' "$project_dir/app/src/main/java"
rg -q 'isUsablePhoto' "$project_dir/app/src/main/java"
rg -q 'isPlayableVideo' "$project_dir/app/src/main/java"
rg -q 'R.string.report_privacy' "$project_dir/app/src/main/java"
test -f "$project_dir/CUSTOMER_LONGEVITY_AUDIT.md"
test -f "$project_dir/ADVERSARIAL_CODE_REVIEW.md"
test -f "$project_dir/html-preview/index.html"
test "$(find "$project_dir/gooduse-shell/src/main/java" -name '*.kt' | wc -l)" -eq 6

node "$project_dir/scripts/adversarial_review.mjs"

echo "Static project checks passed"
echo "Android compile/device checks require Android Studio + SDK + physical USB cameras"
