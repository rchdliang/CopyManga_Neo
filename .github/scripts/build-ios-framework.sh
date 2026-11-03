#!/usr/bin/env bash
# 在真实 Xcode 环境中执行唯一的 Kotlin 框架构建入口。
set -euo pipefail
if [[ "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-}" == YES ]]; then exit 0; fi
cd "$(dirname "$0")/../.."
if [[ -n "${COPYMANGA_IOS_BUILD_LOG:-}" ]]; then
  python3 .github/scripts/ios-build-stage.py kotlin-framework \
    ./gradlew :shared:embedAndSignAppleFrameworkForXcode \
    --console=plain --stacktrace --profile --build-cache \
    > "$COPYMANGA_IOS_BUILD_LOG" 2>&1
else
  # 日常 Xcode/IDE 构建沿用原入口，不依赖 CI 日志设置。
  ./gradlew :shared:embedAndSignAppleFrameworkForXcode
fi
