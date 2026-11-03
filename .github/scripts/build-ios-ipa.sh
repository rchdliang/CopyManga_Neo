#!/usr/bin/env bash
# 从设备版 Release 应用生成待用户重签名的 IPA；不接受模拟器产物。
set -euo pipefail
cd "$(dirname "$0")/../.."
export ORG_GRADLE_PROJECT_enableAndroid=false
if [[ $# != 0 ]]; then echo "Usage: $0" >&2; exit 2; fi
config="$(mktemp)"
trap 'rm -f -- "$config"' EXIT
python3 - "$config" <<'PY'
import os, re, sys
value = os.environ.get("COPYMANGA_TAP_STRING", "").strip()
if not re.fullmatch(r"[0-9a-fA-F]{32}", value):
    raise SystemExit("COPYMANGA_TAP_STRING has an invalid format")
with open(sys.argv[1], "w", encoding="utf-8") as target:
    target.write("COPYMANGA_TAP_STRING = " + value + "\n")
PY
mkdir -p build/ios-build-logs
export COPYMANGA_IOS_BUILD_LOG="$PWD/build/ios-build-logs/kotlin-framework.log"
# 每次从空日志开始；外层计时器实时读取，避免 Xcode 缓存脚本阶段输出。
: > "$COPYMANGA_IOS_BUILD_LOG"
xcodebuild -version
# 仅由 Xcode 钩子调用一次完整框架任务，同时完成资源同步和装配。
python3 .github/scripts/ios-build-stage.py xcode \
  xcodebuild -project iosApp/iosApp.xcodeproj -scheme CopyMangaNeo \
  -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath build/ios-device -resultBundlePath build/ios-device-build.xcresult \
  -xcconfig "$config" ARCHS=arm64 ONLY_ACTIVE_ARCH=NO \
  COPYMANGA_IOS_BUILD_LOG="$COPYMANGA_IOS_BUILD_LOG" \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY= build
python3 .github/scripts/ios-build-stage.py ipa \
  python3 .github/scripts/package-ios-ipa.py \
  build/ios-device/Build/Products/Release-iphoneos/CopyMangaNeo.app build
