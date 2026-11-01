"""验证 Windows 原生应用目录的 ZIP 内容、启动标记和校验文件。"""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
POWERSHELL = shutil.which("powershell.exe")


@unittest.skipUnless(os.name == "nt" and POWERSHELL, "Windows PowerShell is required")
class WindowsPortableTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.image = self.root / "app"
        self.image.mkdir()
        self.output = self.root / "output"
        self.files = {
            "CopyMangaNeo.exe": b"synthetic executable",
            "awt.dll": b"synthetic native library",
            "bin/helper.dll": b"nested native library",
            "lib/resources/data.bin": b"resources",
            ".hidden-resource": b"hidden resource",
        }
        for name, data in self.files.items():
            file = self.image / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(data)
        subprocess.run(["attrib", "+h", str(self.image / ".hidden-resource")], check=True)

    def package(self, output=None):
        return subprocess.run([
            POWERSHELL, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
            str(ROOT / ".github/scripts/build-windows-portable.ps1"),
            "-AppImage", str(self.image), "-AppVersion", "1.2.3",
            "-ArtifactVersion", "1.2.3+234", "-Architecture", "x64",
            "-OutputDirectory", str(output or self.output),
        ], capture_output=True)

    def test_complete_image_hidden_files_marker_and_checksum(self):
        result = self.package()
        self.assertEqual(result.returncode, 0, result.stderr.decode(errors="replace"))
        archive = self.output / "CopyMangaNeo_windows_1.2.3+234_x64_portable.zip"
        with zipfile.ZipFile(archive) as zipped:
            entries = {name.replace("\\", "/"): name for name in zipped.namelist()}
            for name, content in self.files.items():
                self.assertEqual(zipped.read(entries[name]), content)
            self.assertEqual(zipped.read(".nucleus-executable-type"), b"exe\n1.2.3\n")
        self.assertFalse((self.image / ".nucleus-executable-type").exists())
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        self.assertEqual(Path(str(archive) + ".sha256").read_text().strip(), digest + "  " + archive.name)
        self.assertFalse(list(self.output.glob(".portable-*.zip")))

    def test_incomplete_image_is_rejected(self):
        (self.image / "CopyMangaNeo.exe").unlink()
        self.assertNotEqual(self.package().returncode, 0)
        self.assertFalse(self.output.exists())

    def test_jvm_image_and_nested_output_are_rejected(self):
        self.assertNotEqual(self.package(self.image / "output").returncode, 0)
        self.assertFalse((self.image / "output").exists())
        runtime = self.image / "runtime/bin/server/jvm.dll"
        runtime.parent.mkdir(parents=True)
        runtime.write_bytes(b"synthetic JVM")
        self.assertNotEqual(self.package().returncode, 0)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
