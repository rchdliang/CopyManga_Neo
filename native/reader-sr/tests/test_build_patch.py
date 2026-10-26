import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('sr_build', Path(__file__).parents[1] / 'build.py')
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class WindowsPatchTests(unittest.TestCase):
    def test_crlf_patch_matches_pinned_lf_source_and_index(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', root], check=True)
            subprocess.run(['git', 'config', 'core.autocrlf', 'false'], cwd=root, check=True)
            source = root / 'source.txt'
            source.write_bytes(b'before\n')
            subprocess.run(['git', 'add', 'source.txt'], cwd=root, check=True)
            patch = root / 'fix.patch'
            patch.write_bytes(b'diff --git a/source.txt b/source.txt\r\n'
                              b'--- a/source.txt\r\n+++ b/source.txt\r\n'
                              b'@@ -1 +1 @@\r\n-before\r\n+after\r\n')
            build.apply_cpu_patch(root, patch)
            build.apply_cpu_patch(root, patch, cached=True, env=dict(os.environ))
            self.assertEqual(source.read_bytes(), b'after\n')
            indexed = subprocess.check_output(['git', 'show', ':source.txt'], cwd=root)
            self.assertEqual(source.read_bytes(), indexed)


if __name__ == '__main__':
    unittest.main()
