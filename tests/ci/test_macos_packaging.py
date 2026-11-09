import hashlib
import importlib.util
from pathlib import Path
import plistlib
import tempfile
import unittest

MODULE = Path(__file__).resolve().parents[2] / ".github/scripts/collect-macos-artifacts.py"
SPEC = importlib.util.spec_from_file_location("macos_packaging", MODULE)
macos = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(macos)


class MacOSPackagingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / "app/CopyMangaNeo.app"
        self.info = {
            "CFBundleIdentifier": "com.rchdliang.copymanga_neo.desktop",
            "CFBundleShortVersionString": "1.0.40",
            "CFBundleVersion": "40",
            "CFBundleExecutable": "CopyMangaNeo",
        }
        for name in ("Contents/MacOS/CopyMangaNeo", "Contents/runtime/Contents/Home/lib/server/libjvm.dylib"):
            path = self.bundle / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"test-mach-o")
        self.config = self.bundle / "Contents/app/CopyMangaNeo.cfg"
        self.config.parent.mkdir(parents=True)
        self.config.write_text("[JavaOptions]\njava-options=-XstartOnFirstThread\n", encoding="utf-8")
        self.write_info()

    def write_info(self):
        (self.bundle / "Contents/Info.plist").write_bytes(plistlib.dumps(self.info))

    def verify(self, architectures=lambda _: ["arm64"]):
        macos.verify_bundle(self.bundle, "arm64", "1.0.40", 40, architectures)

    def test_accepts_matching_launcher_and_bundled_jvm(self):
        self.verify()

    def test_version_properties_accept_spaces_and_optional_hotfix(self):
        path = self.root / "version.properties"
        for hotfix, expected in (("", "1.0.0+200"), ("1", "1.0.0.1+200")):
            path.write_text(f"COPYMANGA_VERSION_BASE = 1.0.0\nCOPYMANGA_VERSION_HOTFIX = {hotfix}\nCOPYMANGA_VERSION_CODE = 40\n")
            self.assertEqual(("1.0.40", "40", expected), macos.version_metadata(path, "200"))

    def test_rejects_invalid_or_missing_version_before_packaging(self):
        path = self.root / "version.properties"
        for contents in ("", "COPYMANGA_VERSION_BASE = v1.0.0\n", "COPYMANGA_VERSION_BASE = 1.0.0\nCOPYMANGA_VERSION_CODE = 0\n"):
            path.write_text(contents)
            with self.assertRaises(ValueError):
                macos.version_metadata(path, "200")

    def test_rejects_wrong_launcher_or_jvm_architecture(self):
        for mismatched in ("CopyMangaNeo", "libjvm.dylib"):
            with self.subTest(binary=mismatched), self.assertRaises(ValueError):
                self.verify(lambda path: ["x86_64"] if path.name == mismatched else ["arm64"])

    def test_rejects_wrong_metadata_or_unsafe_executable(self):
        for key, value in (("CFBundleIdentifier", "other.app"), ("CFBundleVersion", "39"),
                           ("CFBundleShortVersionString", "1.0.0"), ("CFBundleExecutable", "../other")):
            original = self.info[key]
            self.info[key] = value
            self.write_info()
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.verify()
            self.info[key] = original

    def test_rejects_launcher_without_first_thread_flag(self):
        self.config.write_text("[JavaOptions]\n", encoding="utf-8")
        with self.assertRaises(ValueError):
            self.verify()

    def test_rejects_missing_bundled_jvm(self):
        (self.bundle / "Contents/runtime/Contents/Home/lib/server/libjvm.dylib").unlink()
        with self.assertRaises(ValueError):
            self.verify()

    def test_collects_both_formats_with_architecture_and_matching_checksums(self):
        for extension in ("dmg", "zip"):
            folder = self.root / extension
            folder.mkdir()
            (folder / f"app.{extension}").write_bytes(extension.encode())
        output = self.root / "installer"
        macos.collect_installers(self.root, output, "1.0.0.1+200", "arm64")
        for extension in ("dmg", "zip"):
            path = output / f"CopyMangaNeo_macos_1.0.0.1+200_arm64.{extension}"
            digest = hashlib.sha256(extension.encode()).hexdigest()
            self.assertEqual(f"{digest}  {path.name}\n", path.with_name(path.name + ".sha256").read_text())

    def test_rejects_ambiguous_or_missing_installers_before_copying(self):
        (self.root / "dmg").mkdir()
        (self.root / "dmg/a.dmg").touch()
        (self.root / "dmg/b.dmg").touch()
        output = self.root / "installer"
        with self.assertRaises(ValueError):
            macos.collect_installers(self.root, output, "1.0.0+200", "x64")
        self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
