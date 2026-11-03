#!/usr/bin/env python3
"""直接转发构建输出，定时报告存活状态，并记录各阶段耗时与退出码。"""
import datetime
import codecs
import json
import os
from pathlib import Path
import subprocess
import sys
import threading
import time


def run_stage(stage, command, output_dir, heartbeat_seconds=60, relay_log=None, poll_seconds=1):
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    clock = time.monotonic()
    stopped = threading.Event()
    log = open(relay_log, "rb") if relay_log else None
    decoder = codecs.getincrementaldecoder("utf-8")(errors="replace")

    def relay(final=False):
        if log:
            text = decoder.decode(log.read(), final=final)
            if text:
                print(text, end="", flush=True)

    def report(message):
        print(f"[iOS {stage}] {message}; elapsed={time.monotonic() - clock:.1f}s", flush=True)

    def heartbeat():
        next_report = clock + heartbeat_seconds
        while not stopped.wait(min(poll_seconds, heartbeat_seconds)):
            relay()
            if time.monotonic() >= next_report:
                report("still running (not a progress estimate)")
                next_report = time.monotonic() + heartbeat_seconds

    report("started")
    monitor = threading.Thread(target=heartbeat, daemon=True)
    monitor.start()
    code = 1
    try:
        # Xcode 自身输出直接透传；钩子内的 Gradle 输出从日志文件实时转发。
        code = subprocess.run(command, check=False).returncode
        if code < 0:
            code = 128 - code
        return code
    finally:
        stopped.set()
        monitor.join()
        relay(final=True)
        if log:
            log.close()
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
    log = os.environ.get("COPYMANGA_IOS_BUILD_LOG") if sys.argv[1] == "xcode" else None
    raise SystemExit(run_stage(sys.argv[1], sys.argv[2:], "build/ios-build-timings", relay_log=log))
