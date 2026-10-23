import hashlib
import importlib.util
import json
import plistlib
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[2] / ".github/scripts/package-ios-ipa.py"
SPEC = importlib.util.spec_from_file_location("ios_ipa", MODULE)
ipa = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ipa)

class IosIpaTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.app = Path(self.temp.name) / "CopyMangaNeo.app"
        self.app.mkdir()
        self.info = dict(CFBundleIdentifier=ipa.BUNDLE_ID, CFBundleShortVersionString="1.2.3",
                         CFBundleVersion="10", DTPlatformName="iphoneos",
                         CFBundleSupportedPlatforms=["iPhoneOS"], CFBundleExecutable="CopyMangaNeo")
        self.versions = {"COPYMANGA_VERSION_BASE": "1.2.3", "COPYMANGA_VERSION_CODE": "10"}
        (self.app / "CopyMangaNeo").write_bytes(b"fixture; not a real executable")
        for resource in ("licenses/CopyMangaNeo-GPL-3.0.txt", "licenses/kixyubook-MIT.txt", "opencc/LICENSE"):
            target = self.app / "compose-resources/app/files" / resource
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text("fixture")
        self.save()

    def save(self):
        with (self.app / "Info.plist").open("wb") as output:
            plistlib.dump(self.info, output)

    def test_device_metadata_and_resources(self):
        self.assertEqual(ipa.validate_app(self.app, self.versions, "    platform IOS\n")["CFBundleVersion"], "10")

    def test_version_properties_with_whitespace_validate_app(self):
        versions_file = Path(self.temp.name) / "version.properties"
        versions_file.write_text(
            "  # Version properties = shared with Xcode\n\n"
            "COPYMANGA_VERSION_BASE = 1.2.3\n"
            "COPYMANGA_VERSION_HOTFIX =\n"
            "COPYMANGA_VERSION_CODE=10\n", encoding="utf-8")
        versions = ipa.version_properties(versions_file)
        self.assertEqual(versions, dict(self.versions, COPYMANGA_VERSION_HOTFIX=""))
        self.assertEqual(ipa.validate_app(self.app, versions, "platform IOS\n"), self.info)

    def test_arm64_simulator_is_rejected(self):
        with self.assertRaises(ValueError):
            ipa.validate_app(self.app, self.versions, "    platform IOSSIMULATOR\n")
        self.info["CFBundleSupportedPlatforms"] = ["iPhoneSimulator"]
        self.save()
        with self.assertRaises(ValueError):
            ipa.validate_app(self.app, self.versions, "    platform IOS\n")

    def test_wrong_build_and_missing_resource_are_rejected(self):
        with self.assertRaises(ValueError):
            ipa.validate_app(self.app, dict(self.versions, COPYMANGA_VERSION_CODE="11"), "platform IOS\n")
        next(self.app.rglob("kixyubook-MIT.txt")).unlink()
        with self.assertRaises(ValueError):
            ipa.validate_app(self.app, self.versions, "platform IOS\n")

    def test_executable_path_cannot_escape_app(self):
        self.info["CFBundleExecutable"] = "../external"
        self.save()
        with self.assertRaises(ValueError):
            ipa.validate_app(self.app, self.versions, "platform IOS\n")

    @unittest.skipUnless(sys.platform == "darwin", "Requires Apple's device toolchain and ditto")
    def test_real_device_executable_packages_with_checksum_and_manifest(self):
        # 使用真正的 ARM64 设备可执行文件覆盖 vtool、lipo 和 ditto 调用。
        source = Path(self.temp.name) / "main.c"
        source.write_text("int main(void) { return 0; }\n", encoding="utf-8")
        sdk = subprocess.check_output(
            ["xcrun", "--sdk", "iphoneos", "--show-sdk-path"], text=True).strip()
        subprocess.run(["xcrun", "--sdk", "iphoneos", "clang",
                        "-target", "arm64-apple-ios15.0", "-isysroot", sdk,
                        str(source), "-o", str(self.app / "CopyMangaNeo")], check=True)
        output = Path(self.temp.name) / "output with spaces"
        with patch.object(ipa, "version_properties", return_value=self.versions):
            ipa.package(self.app, output)
        archive_path, = output.glob("*.ipa")
        with zipfile.ZipFile(archive_path) as archive:
            self.assertIsNone(archive.testzip())
            self.assertIn("Payload/CopyMangaNeo.app/CopyMangaNeo", archive.namelist())
            self.assertEqual(self.info, plistlib.loads(archive.read("Payload/CopyMangaNeo.app/Info.plist")))
        digest = hashlib.sha256(archive_path.read_bytes()).hexdigest()
        manifest = json.loads((output / "ios-device-build.json").read_text(encoding="utf-8"))
        self.assertEqual(digest, manifest["sha256"])
        self.assertEqual("arm64", manifest["architecture"])
        self.assertEqual("iphoneos", manifest["platform"])
        self.assertFalse(manifest["signed"])
        self.assertEqual(digest + "  " + archive_path.name + "\n",
                         archive_path.with_suffix(".ipa.sha256").read_text(encoding="utf-8"))

if __name__ == "__main__":
    unittest.main()
