import importlib.util
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("sdk_packages", ROOT / ".github/scripts/android-sdk-packages.py")
sdk = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(sdk)


class AndroidSdkPackagesTest(unittest.TestCase):
    def test_catalog_upgrade_changes_all_ci_packages(self):
        versions = {
            "androidCompileSdk": "38", "androidBuildTools": "38.0.1",
            "cmake": "3.31.6", "ndk": "31.0.12345678",
        }
        self.assertEqual(sdk.sdk_packages(versions), [
            "platforms;android-38.0", "build-tools;38.0.1",
            "cmake;3.31.6", "ndk;31.0.12345678",
        ])

    def test_incomplete_catalog_does_not_silently_use_old_defaults(self):
        with self.assertRaises(KeyError):
            sdk.sdk_packages({"androidCompileSdk": "38"})

    def test_invalid_api_level_is_rejected(self):
        for value in ("0", "-1", "unknown"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                sdk.sdk_packages({"androidCompileSdk": value})


if __name__ == "__main__":
    unittest.main()
