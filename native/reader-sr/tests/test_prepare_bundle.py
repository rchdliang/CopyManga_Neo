import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location("prepare",Path(__file__).parents[1]/"prepare_bundle.py")
sr=importlib.util.module_from_spec(spec);spec.loader.exec_module(sr)
class RuntimePackagingTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.resource=self.root/"resources/reader-sr"
        platform=self.resource/"windows-x64";platform.mkdir(parents=True)
        self.binary=platform/"copymanga-sr.exe";self.binary.write_bytes(b"fixture")
        props=dict(protocol="1",platform="windows-x64",runtime="waifu2x-20250915-ncnn-c4193aad-cmp-sr1",tile="32",noise="-1",scale="2",modelSha256=sr.MODELS["scale2.0x_model.bin"],paramSha256=sr.MODELS["scale2.0x_model.param"],runtimeSha256=sr.digest(self.binary))
        (platform/"manifest.properties").write_text("".join(k+"="+v+"\n" for k,v in props.items()))
        (platform/"LLVM-LICENSE.txt").write_text("license");(platform/"COPYING").write_text("license")
        (self.resource/"NOTICE.txt").write_text("licenses"*30)
        self.pin=patch.dict(sr.RUNTIMES,{"windows-x64":sr.digest(self.binary)});self.pin.start();self.addCleanup(self.pin.stop)
    def test_valid_runtime_does_not_require_packaged_models(self):
        sr.validate(self.root,"windows-x64")
        self.assertFalse((self.resource/"models").exists())
    def test_corrupt_runtime_fails_build_preparation(self):
        self.binary.write_bytes(b"corrupted")
        with self.assertRaisesRegex(AssertionError,"runtime checksum"):sr.validate(self.root,"windows-x64")
    def test_missing_runtime_fails(self):
        self.binary.unlink()
        with self.assertRaises(OSError):sr.validate(self.root,"windows-x64")
    def test_missing_licenses_fails(self):
        (self.resource/"NOTICE.txt").unlink()
        with self.assertRaises(OSError):sr.validate(self.root,"windows-x64")
    def test_fresh_machine_without_tools_refuses_incomplete_package(self):
        with patch.object(sr.shutil,"which",return_value=None):
            with self.assertRaisesRegex(RuntimeError,"refusing an incomplete package"):
                sr.prepare(self.root/"empty",self.root/"cache","windows-x64")
if __name__=="__main__":unittest.main()
