#!/usr/bin/env python3
"""直接转发构建输出，定时报告存活状态，并记录各阶段耗时与退出码。"""
import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
import threading
import time


def run_stage(stage, command, output_dir, heartbeat_seconds=60):
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    clock = time.monotonic()
    stopped = threading.Event()

    def report(message):
        print(f"[iOS {stage}] {message}; elapsed={time.monotonic() - clock:.1f}s", flush=True)

    def heartbeat():
        while not stopped.wait(heartbeat_seconds):
            report("still running (not a progress estimate)")

    report("started")
    monitor = threading.Thread(target=heartbeat, daemon=True)
    monitor.start()
    code = 1
    try:
        # 继承标准输出，不把 Gradle 日志缓存在 Xcode 的脚本构建阶段。
        code = subprocess.run(command, check=False).returncode
        if code < 0:
            code = 128 - code
        return code
    finally:
        stopped.set()
        monitor.join()
        elapsed = round(time.monotonic() - clock, 3)
        output_dir = Path(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)
        (output_dir / f"{stage}.json").write_text(json.dumps({
            "stage": stage, "startedAt": started, "durationSeconds": elapsed, "exitCode": code,
        }, indent=2) + "\n", encoding="utf-8")
        report(f"finished with exit code {code}")
        summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary:
            with open(summary, "a", encoding="utf-8") as target:
                target.write(f"- iOS {stage}: {elapsed:.1f}s; exit code {code}\n")


if __name__ == "__main__":
    if len(sys.argv) < 3 or sys.argv[1] not in ("kotlin-framework", "xcode", "ipa"):
        raise SystemExit("Usage: ios-build-stage.py <kotlin-framework|xcode|ipa> <command> [args...]")
    raise SystemExit(run_stage(sys.argv[1], sys.argv[2:], "build/ios-build-timings"))
