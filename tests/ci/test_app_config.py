import importlib.util
from pathlib import Path
import plistlib
import tempfile
import unittest
import zipfile

MODULE = Path(__file__).resolve().parents[2] / ".github/scripts/verify-app-config.py"
SPEC = importlib.util.spec_from_file_location("app_config", MODULE)
config = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(config)


class AppConfigTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.value = "0123456789abcdef0123456789abcdef"

    def archive(self, name, entry, value):
        path = self.root / name
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(entry, value)
        return path

    def test_android_checks_dex_and_every_apk(self):
        self.archive("good.apk", "classes2.dex", b"dex\0" + self.value.encode())
        config.verify("android", self.root / "*.apk", self.value)
        self.archive("bad.apk", "assets/config.txt", self.value)
        with self.assertRaises(ValueError):
            config.verify("android", self.root / "*.apk", self.value)

    def test_desktop_checks_compiled_config_in_app_image(self):
        jar = self.archive("shared.jar", "com/rchdliang/copymanga_neo/multiplatform/app/OfficialAppBuildConfig.class",
                           b"class-fixture\0" + self.value.encode())
        config.verify("desktop", self.root, self.value)
        with self.assertRaises(ValueError):
            config.verify("desktop", jar, "f" * 32)

    def test_ios_checks_plist_value(self):
        ipa = self.archive("app.ipa", "Payload/CopyMangaNeo.app/Info.plist",
                           plistlib.dumps({"CopyMangaTapString": self.value}))
        config.verify("ios", ipa, self.value)
        with self.assertRaises(ValueError):
            config.verify("ios", ipa, "f" * 32)

    def test_missing_artifact_and_invalid_input_are_rejected(self):
        for value in ("", "not-a-key", "f" * 31, self.value):
            with self.assertRaises(ValueError):
                config.verify("android", self.root / "missing.apk", value)


if __name__ == "__main__":
    unittest.main()
