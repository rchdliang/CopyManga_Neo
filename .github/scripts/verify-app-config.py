"""核对产物含有本次构建的协议配置；只输出通过/失败，不输出配置内容。"""
import argparse
import glob
import os
from pathlib import Path
import plistlib
import re
import sys
import subprocess
import zipfile


def verify(platform, target, expected):
    if not re.fullmatch(r"[0-9a-fA-F]{32}", expected):
        raise ValueError("COPYMANGA_TAP_STRING is missing or invalid")
    paths = [Path(path) for path in glob.glob(str(target))]
    if not paths:
        raise ValueError("Build artifact is missing")
    needle = expected.encode("ascii")
    for path in paths:
        found = False
        if platform == "android":
            with zipfile.ZipFile(path) as archive:
                found = any(needle in archive.read(name) for name in archive.namelist()
                            if re.fullmatch(r"classes\d*\.dex", name))
        elif platform == "ios":
            with zipfile.ZipFile(path) as archive:
                info = plistlib.loads(archive.read("Payload/CopyMangaNeo.app/Info.plist"))
                found = info.get("CopyMangaTapString") == expected
        elif platform == "desktop-native":
            executable = path / "CopyMangaNeo.exe" if path.is_dir() else path
            result = subprocess.run([str(executable.resolve()), "--verify-build-config"],
                                    env={**os.environ, "COPYMANGA_TAP_STRING": expected},
                                    capture_output=True, timeout=30, check=False)
            found = result.returncode == 0 and b"Packaged build configuration verified" in result.stdout
        elif platform == "desktop":
            jars = [path] if path.suffix == ".jar" else path.rglob("*.jar")
            entry = "com/rchdliang/copymanga_neo/multiplatform/app/OfficialAppBuildConfig.class"
            for jar in jars:
                with zipfile.ZipFile(jar) as archive:
                    if entry in archive.namelist() and needle in archive.read(entry):
                        found = True
                        break
        else:
            raise ValueError("Unknown platform")
        if not found:
            raise ValueError("Packaged configuration does not match the build input")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=("android", "desktop", "desktop-native", "ios"))
    parser.add_argument("target")
    args = parser.parse_args()
    try:
        verify(args.platform, args.target, os.environ.get("COPYMANGA_TAP_STRING", "").strip())
    except (ValueError, OSError, KeyError, zipfile.BadZipFile, plistlib.InvalidFileException, subprocess.TimeoutExpired):
        print("Build configuration verification failed: missing, invalid or mismatched artifact/configuration", file=sys.stderr)
        return 1
    print("Packaged build configuration verified")
    return 0


if __name__ == "__main__":
    sys.exit(main())
