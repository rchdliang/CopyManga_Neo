#!/usr/bin/env bash
# 从设备版 Release 应用生成待用户重签名的 IPA；不接受模拟器产物。
set -euo pipefail
cd "$(dirname "$0")/../.."
export ORG_GRADLE_PROJECT_enableAndroid=false
config="$(mktemp)"
trap 'rm -f -- "$config"' EXIT
python3 - "$config" <<'PY'
import os, re, sys
value = os.environ.get("COPYMANGA_TAP_STRING", "").strip()
if value and not re.fullmatch(r"[0-9a-fA-F]{32}", value):
    raise SystemExit("COPYMANGA_TAP_STRING has an invalid format")
with open(sys.argv[1], "w", encoding="utf-8") as target:
    target.write("COPYMANGA_TAP_STRING = " + value + "\n")
PY
xcodebuild -version
xcodebuild -project iosApp/iosApp.xcodeproj -scheme CopyMangaNeo \
  -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath build/ios-device -resultBundlePath build/ios-device-build.xcresult \
  -xcconfig "$config" ARCHS=arm64 ONLY_ACTIVE_ARCH=NO \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY= build
python3 .github/scripts/package-ios-ipa.py \
  build/ios-device/Build/Products/Release-iphoneos/CopyMangaNeo.app build
