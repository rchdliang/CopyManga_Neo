"""Secret-free signing preparation and workflow regression checks."""
import base64
import importlib.util
from pathlib import Path
import stat
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("signing", ROOT / ".github/scripts/prepare-test-signing.py")
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class SigningPreparationTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / "workspace").mkdir()
        self.env = dict(zip(signing.REQUIRED, [base64.b64encode(b"synthetic non-key bytes").decode(),
                                              " dummy\\password:=\n", "dummy-alias", "dummy-password"]))
        self.env.update(RUNNER_TEMP=str(self.root), GITHUB_WORKSPACE=str(self.root / "workspace"))

    def test_restore_permissions_and_property_escaping(self):
        signing.prepare(self.env)
        key = self.root / "android-test-signing/release-keystore.jks"
        props = self.root / "workspace/keystore.properties"
        self.assertEqual(key.read_bytes(), b"synthetic non-key bytes")
        for path in (key, props):
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
        lines = props.read_text().splitlines()
        self.assertEqual(len(lines), 4)
        self.assertIn("storePassword=" + signing.java_property(self.env["ANDROID_KEYSTORE_PASSWORD"]), lines)
        self.assertEqual(signing.java_property("😀"), r"\ud83d\ude00")

    def test_missing_input_fails_before_writes(self):
        for name in signing.REQUIRED:
            with self.subTest(name=name), self.assertRaises(ValueError):
                signing.prepare({**self.env, name: ""})
        self.assertFalse((self.root / "android-test-signing").exists())

    def test_invalid_base64_fails_before_writes(self):
        with self.assertRaises(ValueError):
            signing.prepare({**self.env, "ANDROID_KEYSTORE_BASE64": "not base64!"})
        self.assertFalse((self.root / "android-test-signing").exists())

    def test_existing_properties_are_not_overwritten(self):
        props = self.root / "workspace/keystore.properties"
        props.write_text("existing")
        with self.assertRaises(ValueError):
            signing.prepare(self.env)
        self.assertEqual(props.read_text(), "existing")

    def test_ci_requires_opt_in_and_cleanup_before_upload(self):
        workflow = (ROOT / ".github/workflows/multiplatform-manual.yml").read_text()
        android = workflow.split("jobs:\n", 1)[1].split("  android:\n", 1)[1].split("\n  windows:", 1)[0]
        self.assertIn("-PuseReleaseSigningForTest=true --no-configuration-cache", android)
        self.assertIn("if: ${{ always() }}", android.split("Remove ephemeral signing material", 1)[1])
        self.assertLess(android.index("Remove ephemeral signing material"), android.index("Upload android-debug-apk"))
        release = (ROOT / ".github/workflows/android-ci-release.yml").read_text()
        for name in signing.REQUIRED:
            self.assertIn("secrets." + name, release)
            self.assertIn("secrets." + name, android)
        build = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn('applicationIdSuffix = ".migrationtest"', build)
        self.assertIn("require(hasReleaseSigning)", build)
        self.assertIn('getProperty("storeFile")).isFile', build)


if __name__ == "__main__":
    unittest.main()
