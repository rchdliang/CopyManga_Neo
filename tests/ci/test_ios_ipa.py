import importlib.util
import plistlib
import tempfile
import unittest
from pathlib import Path

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

if __name__ == "__main__":
    unittest.main()
