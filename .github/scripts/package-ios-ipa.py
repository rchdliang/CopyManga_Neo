"""验证实际设备包、资源和版本，再用 ditto 保留包权限封装 IPA。"""
import hashlib
import json
import plistlib
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

BUNDLE_ID = "com.rchdliang.copymanga-neo"

def version_properties(path):
    return dict(tuple(part.strip() for part in line.split("=", 1))
                for line in path.read_text(encoding="utf-8").splitlines()
                if "=" in line and not line.lstrip().startswith("#"))

def validate_app(app, versions, macho_info):
    with (app / "Info.plist").open("rb") as source:
        info = plistlib.load(source)
    expected = {
        "CFBundleIdentifier": BUNDLE_ID,
        "CFBundleShortVersionString": versions["COPYMANGA_VERSION_BASE"].strip(),
        "CFBundleVersion": versions["COPYMANGA_VERSION_CODE"].strip(),
        "DTPlatformName": "iphoneos",
    }
    for key, value in expected.items():
        if str(info.get(key, "")) != value:
            raise ValueError("iOS app metadata does not match: " + key)
    if info.get("CFBundleSupportedPlatforms") != ["iPhoneOS"]:
        raise ValueError("A device app is required; simulator apps cannot be packaged")
    if not re.search(r"(?m)^\s*platform IOS\s*$", macho_info) or "IOSSIMULATOR" in macho_info:
        raise ValueError("Mach-O must target iOS devices")
    executable = info.get("CFBundleExecutable")
    if not isinstance(executable, str) or Path(executable).name != executable or not (app / executable).is_file():
        raise ValueError("Missing or invalid application executable")
    resources = list(app.rglob("*"))
    for suffix in ("licenses/CopyMangaNeo-GPL-3.0.txt", "licenses/kixyubook-MIT.txt", "opencc/LICENSE"):
        if not any(p.is_file() and p.as_posix().endswith(suffix) for p in resources):
            raise ValueError("Missing Compose resource: " + suffix)
    return info

def package(app, output):
    app, output = app.resolve(), output.resolve()
    versions = version_properties(Path("version.properties"))
    executable = app / "CopyMangaNeo"
    info_text = subprocess.check_output(["xcrun", "vtool", "-show-build", str(executable)], text=True)
    subprocess.run(["xcrun", "lipo", str(executable), "-verify_arch", "arm64"], check=True)
    info = validate_app(app, versions, info_text)
    output.mkdir(parents=True, exist_ok=True)
    version = versions["COPYMANGA_VERSION_BASE"].strip()
    hotfix = versions.get("COPYMANGA_VERSION_HOTFIX", "").strip()
    if hotfix:
        version += "." + hotfix
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){2,3}", version):
        raise ValueError("Invalid release version")
    ipa = output / ("CopyMangaNeo-v" + version + "-ios-arm64-unsigned.ipa")
    with tempfile.TemporaryDirectory() as temporary:
        payload = Path(temporary) / "Payload"
        payload.mkdir()
        subprocess.run(["ditto", str(app), str(payload / app.name)], check=True)
        subprocess.run(["ditto", "-c", "-k", "--keepParent", str(payload), str(ipa)], check=True)
    with zipfile.ZipFile(ipa) as archive:
        if "Payload/CopyMangaNeo.app/Info.plist" not in archive.namelist():
            raise ValueError("IPA Payload layout is invalid")
        if archive.testzip() is not None:
            raise ValueError("IPA archive integrity failure")
    digest = hashlib.sha256(ipa.read_bytes()).hexdigest()
    ipa.with_suffix(".ipa.sha256").write_text(digest + "  " + ipa.name + "\n", encoding="utf-8")
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    (output / "ios-device-build.json").write_text(json.dumps({
        "commit": commit, "bundleId": BUNDLE_ID, "version": version,
        "build": info["CFBundleVersion"], "platform": "iphoneos", "architecture": "arm64",
        "signed": False, "sha256": digest, "artifact": ipa.name,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(ipa.name)

if __name__ == "__main__":
    package(Path(sys.argv[1]), Path(sys.argv[2]))
