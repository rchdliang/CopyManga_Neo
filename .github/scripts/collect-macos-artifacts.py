"""验证 Nucleus/Tao macOS 应用目录，再收集分架构 DMG/ZIP 与校验值。"""
import argparse
import hashlib
import importlib.util
from pathlib import Path
import plistlib
import re
import shutil
import subprocess
import os


def binary_architectures(path):
    return subprocess.check_output(["/usr/bin/lipo", "-archs", str(path)], text=True).strip().split()


def version_metadata(properties_file, count):
    properties = dict((key.strip(), value.strip()) for key, value in
                      (line.split("=", 1) for line in properties_file.read_text(encoding="utf-8").splitlines()
                       if "=" in line and not line.lstrip().startswith("#")))
    base = properties.get("COPYMANGA_VERSION_BASE", "")
    hotfix = properties.get("COPYMANGA_VERSION_HOTFIX", "")
    build_number = properties.get("COPYMANGA_VERSION_CODE", "")
    if (not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", base)
            or hotfix and not re.fullmatch(r"[0-9]+", hotfix)
            or not re.fullmatch(r"[1-9][0-9]*", build_number)
            or not re.fullmatch(r"[1-9][0-9]*", str(count))):
        raise ValueError("Invalid version.properties or Git commit count")
    artifact_version = base + (f".{hotfix}" if hotfix else "") + f"+{count}"
    return base.rsplit(".", 1)[0] + "." + build_number, build_number, artifact_version


def verify_bundle(bundle, architecture, package_version, build_number, inspect_arch=binary_architectures):
    info = plistlib.loads((bundle / "Contents/Info.plist").read_bytes())
    if info.get("CFBundleIdentifier") != "com.rchdliang.copymanga_neo.desktop":
        raise ValueError("Unexpected macOS bundle identifier")
    if info.get("CFBundleShortVersionString") != package_version or str(info.get("CFBundleVersion")) != str(build_number):
        raise ValueError("macOS bundle version does not match version.properties")
    executable_name = info.get("CFBundleExecutable", "")
    if not executable_name or Path(executable_name).name != executable_name or "/" in executable_name or "\\" in executable_name:
        raise ValueError("Invalid bundle executable")
    executable = bundle / "Contents/MacOS" / executable_name
    libraries = list((bundle / "Contents/runtime").rglob("libjvm.dylib"))
    if not executable.is_file() or len(libraries) != 1:
        raise ValueError("macOS app must include its launcher and one JVM")
    required_arch = {"x64": "x86_64", "arm64": "arm64"}[architecture]
    for binary in [executable, libraries[0]]:
        if required_arch not in inspect_arch(binary):
            raise ValueError("macOS launcher/JVM architecture mismatch")
    configs = list((bundle / "Contents/app").glob("*.cfg"))
    if len(configs) != 1 or "java-options=-XstartOnFirstThread" not in configs[0].read_text(encoding="utf-8"):
        raise ValueError("Tao macOS launcher must start on the first thread")


def collect_installers(binary_root, output, version, architecture):
    sources = []
    for extension in ("dmg", "zip"):
        matches = list((binary_root / extension).glob(f"*.{extension}"))
        if len(matches) != 1:
            raise ValueError(f"Expected exactly one macOS {extension} installer")
        sources.append(matches[0])
    output.mkdir(parents=True, exist_ok=True)
    for source in sources:
        target = output / f"CopyMangaNeo_macos_{version}_{architecture}{source.suffix}"
        shutil.copy2(source, target)
        with target.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        target.with_name(target.name + ".sha256").write_text(f"{digest}  {target.name}\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--architecture", required=True, choices=("x64", "arm64"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    count = subprocess.check_output(["git", "rev-list", "--count", "HEAD"], cwd=root, text=True).strip()
    package_version, build_number, version = version_metadata(root / "version.properties", count)
    binary_root = root / "desktopApp/build/compose/binaries/main"
    bundle = binary_root / "app/CopyMangaNeo.app"
    verify_bundle(bundle, args.architecture, package_version, build_number)
    spec = importlib.util.spec_from_file_location("app_config", root / ".github/scripts/verify-app-config.py")
    config = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(config)
    config.verify("desktop", bundle, os.environ.get("COPYMANGA_TAP_STRING", "").strip())
    collect_installers(binary_root, root / "desktopApp/build/installer", version, args.architecture)
    print(f"Verified macOS {args.architecture} bundle and collected DMG/ZIP")


if __name__ == "__main__":
    main()
