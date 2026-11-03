import contextlib
import importlib.util
import io
import json
import os
import re
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("ios_build_stage", ROOT / ".github/scripts/ios-build-stage.py")
stage = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(stage)
BASH = (str(Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe")
        if os.name == "nt" else shutil.which("bash"))


class StageReportingTests(unittest.TestCase):
    def test_failed_command_preserves_exit_code_and_writes_timing_without_command_or_secrets(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            summary = output / "summary.md"
            messages = io.StringIO()
            with patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(summary), "COPYMANGA_TAP_STRING": "private-value"}), contextlib.redirect_stdout(messages):
                code = stage.run_stage("kotlin-framework", [sys.executable, "-c", "import sys; sys.exit(7)"], output)
            self.assertEqual(code, 7)
            timing = json.loads((output / "kotlin-framework.json").read_text())
            self.assertEqual(timing["exitCode"], 7)
            self.assertGreaterEqual(timing["durationSeconds"], 0)
            self.assertIn("exit code 7", summary.read_text())
            self.assertNotIn("private-value", messages.getvalue() + json.dumps(timing))
            self.assertNotIn("command", timing)

    def test_quiet_command_emits_heartbeat_and_finishes_successfully(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": ""}):
            messages = io.StringIO()
            with contextlib.redirect_stdout(messages):
                code = stage.run_stage("xcode", [sys.executable, "-c", "import time; time.sleep(0.15)"], directory, heartbeat_seconds=0.03)
            self.assertEqual(code, 0)
            self.assertIn("still running (not a progress estimate)", messages.getvalue())
            self.assertEqual(json.loads((Path(directory) / "xcode.json").read_text())["exitCode"], 0)

    def test_missing_executable_is_recorded_as_failure(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": ""}):
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(OSError):
                stage.run_stage("ipa", [str(Path(directory) / "missing-executable")], directory)
            self.assertEqual(json.loads((Path(directory) / "ipa.json").read_text())["exitCode"], 1)

    def test_log_is_relayed_before_command_finishes_and_failure_drains_utf8_tail(self):
        received = threading.Event()

        class Stream(io.StringIO):
            def write(self, text):
                result = super().write(text)
                if "live output" in text:
                    received.set()
                return result

        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": ""}):
            log = Path(directory) / "framework.log"
            log.touch()

            def build(command, **kwargs):
                with log.open("ab") as output:
                    output.write(b"live output\n\xe4")
                    output.flush()
                    self.assertTrue(received.wait(3), "framework output must appear while Xcode is still running")
                    output.write(b"\xb8\xad\nlast failure\n")
                return subprocess.CompletedProcess(command, 7)

            messages = Stream()
            with patch.object(stage.subprocess, "run", side_effect=build), contextlib.redirect_stdout(messages):
                code = stage.run_stage("xcode", ["fake-xcode"], directory, relay_log=log, poll_seconds=0.01)
            self.assertEqual(code, 7)
            self.assertIn("live output\n中\nlast failure\n", messages.getvalue())
            self.assertEqual(json.loads((Path(directory) / "xcode.json").read_text())["exitCode"], 7)


@unittest.skipUnless(BASH and Path(BASH).is_file(), "Requires Bash for build orchestration tests")
class IosBuildOrchestrationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ios pipeline ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        scripts = self.root / ".github/scripts"
        scripts.mkdir(parents=True)
        shutil.copyfile(ROOT / ".github/scripts/build-ios-ipa.sh", scripts / "build-ios-ipa.sh")
        shutil.copyfile(ROOT / ".github/scripts/build-ios-framework.sh", scripts / "build-ios-framework.sh")
        project = (ROOT / "iosApp/iosApp.xcodeproj/project.pbxproj").read_text()
        phase = json.loads(re.search(r'shellScript = ("(?:\\.|[^"\\])*");', project).group(1))
        (scripts / "test-xcode-phase.sh").write_text(phase, encoding="utf-8", newline="\n")
        (self.root / "iosApp").mkdir()
        # 外部工具桩只验证调度；计时器另用真实 Python 子进程测试。
        (scripts / "package-ios-ipa.py").write_text("from pathlib import Path\nwith Path('trace.txt').open('a') as f: f.write('ipa\\n')\n")
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.write_executable(self.bin / "python3", """#!/bin/bash
if [[ "$1" == .github/scripts/ios-build-stage.py ]]; then
  echo "stage:$2" >> trace.txt
  shift 2
  exec "$@"
fi
exec """ + shlex.quote(Path(sys.executable).as_posix()) + ' "$@"\n')
        self.write_executable(self.bin / "xcodebuild", """#!/bin/bash
set -e
printf 'xcode:%s\n' "$*" >> trace.txt
if [[ "$1" == -version ]]; then exit 0; fi
export SRCROOT="$PWD/iosApp" CONFIGURATION=Release SDK_NAME=iphoneos ARCHS=arm64
# 执行项目中真实的脚本阶段；不能用空 Xcode 桩漏掉第二次 Gradle 调用。
bash .github/scripts/test-xcode-phase.sh
exit "${FAKE_XCODE_EXIT:-0}"
""")
        self.write_executable(self.root / "gradlew", """#!/bin/bash
printf 'gradle:%s\n' "$*" >> trace.txt
printf 'gradle-env:%s,%s,%s\n' "${CONFIGURATION:-}" "${SDK_NAME:-}" "${ARCHS:-}" >> trace.txt
if [[ "${FAKE_GRADLE_EXIT:-0}" != 0 ]]; then exit "$FAKE_GRADLE_EXIT"; fi
mkdir -p shared/build/bin/iosArm64/releaseFramework/CopyMangaShared.framework
touch shared/build/bin/iosArm64/releaseFramework/CopyMangaShared.framework/CopyMangaShared
""")

    def write_executable(self, path, content):
        path.write_text(content, encoding="utf-8", newline="\n")
        path.chmod(0o755)

    def run_build(self, *args, **overrides):
        env = {**os.environ, "COPYMANGA_TAP_STRING": "a" * 32,
               "PATH": str(self.bin) + os.pathsep + os.environ["PATH"], **overrides}
        return subprocess.run([BASH, ".github/scripts/build-ios-ipa.sh", *args], cwd=self.root,
                              env=env, capture_output=True, text=True, timeout=30)

    def trace(self):
        file = self.root / "trace.txt"
        return file.read_text() if file.exists() else ""

    def test_xcode_runs_the_only_full_framework_build_before_packaging(self):
        result = self.run_build()
        self.assertEqual(result.returncode, 0, result.stderr)
        trace = self.trace()
        self.assertEqual(trace.count("gradle:"), 1)
        self.assertIn(":shared:embedAndSignAppleFrameworkForXcode --console=plain --stacktrace --profile --build-cache", trace)
        self.assertIn("gradle-env:Release,iphoneos,arm64", trace)
        self.assertNotIn(":shared:linkReleaseFrameworkIosArm64", trace)
        self.assertLess(trace.index("stage:xcode"), trace.index("stage:kotlin-framework"))
        self.assertLess(trace.index("stage:kotlin-framework"), trace.index("stage:ipa"))
        self.assertIn("-configuration Release -sdk iphoneos", trace)
        self.assertIn("CODE_SIGNING_ALLOWED=NO", trace)

    def test_old_split_modes_are_rejected_before_build(self):
        for mode in ("framework", "package"):
            self.assertEqual(self.run_build(mode).returncode, 2)
        self.assertEqual(self.trace(), "")

    def test_failed_framework_or_xcode_never_packages(self):
        result = self.run_build(FAKE_GRADLE_EXIT="7")
        self.assertEqual(result.returncode, 7, result.stderr)
        self.assertIn("stage:xcode", self.trace())
        self.assertNotIn("stage:ipa", self.trace())
        result = self.run_build(FAKE_XCODE_EXIT="8")
        self.assertEqual(result.returncode, 8, result.stderr)
        self.assertIn("stage:xcode", self.trace())
        self.assertNotIn("stage:ipa", self.trace())

    def test_bad_stage_or_configuration_stops_before_compilation(self):
        self.assertEqual(self.run_build("unknown").returncode, 2)
        self.assertNotEqual(self.run_build(COPYMANGA_TAP_STRING="invalid").returncode, 0)
        self.assertEqual(self.trace(), "")

    def test_local_xcode_hook_and_ide_override_remain_supported(self):
        env = {**os.environ, "PATH": str(self.bin) + os.pathsep + os.environ["PATH"],
               "COPYMANGA_IOS_BUILD_LOG": "", "OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED": "NO"}
        result = subprocess.run([BASH, ".github/scripts/build-ios-framework.sh"], cwd=self.root,
                                env=env, capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("gradle::shared:embedAndSignAppleFrameworkForXcode\n", self.trace())
        self.assertNotIn("stage:kotlin-framework", self.trace())
        env["OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED"] = "YES"
        result = subprocess.run([BASH, ".github/scripts/build-ios-framework.sh"], cwd=self.root,
                                env=env, capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.trace().count("gradle:"), 1)


class IosWorkflowTests(unittest.TestCase):
    def test_native_cache_and_reports_are_scoped_to_ios_and_xcode_hook_is_preserved(self):
        workflow = (ROOT / ".github/workflows/multiplatform-manual.yml").read_text(encoding="utf-8")
        before, ios = workflow.split("\n  ios:\n", 1)
        self.assertNotIn("~/.konan", before)
        for value in ("runner.os", "runner.arch", "steps.xcode.outputs.build", "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties"):
            self.assertIn(value, ios)
        self.assertLess(ios.index("actions/cache/restore@"), ios.index("run: bash .github/scripts/build-ios-ipa.sh\n"))
        self.assertLess(ios.index("run: bash .github/scripts/build-ios-ipa.sh\n"), ios.index("actions/cache/save@"))
        self.assertEqual(ios.count("run: bash .github/scripts/build-ios-ipa.sh"), 1)
        self.assertIn("build/ios-build-timings/", ios)
        self.assertIn("build/reports/profile/", ios)
        self.assertIn("build/ios-build-logs/", ios)
        project = (ROOT / "iosApp/iosApp.xcodeproj/project.pbxproj").read_text()
        self.assertIn("bash .github/scripts/build-ios-framework.sh", project)


if __name__ == "__main__":
    unittest.main()
