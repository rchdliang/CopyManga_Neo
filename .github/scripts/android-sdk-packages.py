#!/usr/bin/env python3
"""让 CI 安装与 Gradle 版本目录一致的 Android 构建工具。"""
import argparse
from pathlib import Path
import tomllib


ROOT = Path(__file__).resolve().parents[2]


def sdk_packages(versions):
    # 两个 Android 模块当前均使用基础 API（minor = 0）。
    sdk = int(versions["androidCompileSdk"])
    if sdk < 1:
        raise ValueError("androidCompileSdk must be positive")
    return [
        f"platforms;android-{sdk}.0",
        f"build-tools;{versions['androidBuildTools']}",
        f"cmake;{versions['cmake']}",
        f"ndk;{versions['ndk']}",
    ]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--build-tools-version", action="store_true")
    args = parser.parse_args()
    with (ROOT / "gradle/libs.versions.toml").open("rb") as catalog:
        versions = tomllib.load(catalog)["versions"]
    if args.build_tools_version:
        print(versions["androidBuildTools"])
    else:
        print("\n".join(sdk_packages(versions)))


if __name__ == "__main__":
    main()
