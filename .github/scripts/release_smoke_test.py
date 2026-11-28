#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Runtime smoke test of a minified (R8) release APK on a running emulator or device.

Release builds are obfuscated and resource-shrunk, which can break code that only fails at runtime:
5.1.2 crashed when the keyboard started because of a class looked up by its Java package. This test
installs the exact APK and:

1. makes it the active keyboard, which starts the keyboard service
2. opens RCBBoard's settings
3. fails on any crash or ANR of the app, or if the app process is gone

Screenshots and the app's log are written to --out-dir for the workflow to upload.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
import time
from pathlib import Path

IME_SERVICE = "helium314.keyboard.latin.LatinIME"
SETTINGS_ACTIVITY = "helium314.keyboard.settings.SettingsActivity"


class SmokeTestError(Exception):
    pass


class Device:
    def __init__(self, adb: str, out_dir: Path):
        self.adb = adb
        self.out_dir = out_dir

    def run(self, *args: str, timeout: int = 60, check: bool = True) -> str:
        try:
            result = subprocess.run([self.adb, *args], capture_output=True, text=True, timeout=timeout)
        except subprocess.TimeoutExpired as error:
            raise SmokeTestError(f"adb {' '.join(args)} timed out after {timeout} s") from error
        if check and result.returncode:
            raise SmokeTestError(f"adb {' '.join(args)} failed: {result.stderr.strip() or result.stdout.strip()}")
        return result.stdout

    def shell(self, command: str, timeout: int = 60, check: bool = True) -> str:
        return self.run("shell", command, timeout=timeout, check=check)

    def wait_for_boot(self) -> None:
        self.run("wait-for-device", timeout=300)
        deadline = time.time() + 300
        while time.time() < deadline:
            if self.shell("getprop sys.boot_completed", check=False).strip() == "1":
                return
            time.sleep(3)
        raise SmokeTestError("device did not finish booting")

    def screenshot(self, name: str) -> None:
        png = subprocess.run([self.adb, "exec-out", "screencap", "-p"], capture_output=True, timeout=30).stdout
        (self.out_dir / f"{name}.png").write_bytes(png)

def check_alive(device: Device, package: str, step: str) -> None:
    # right after boot, the system can take a while to start the keyboard process: wait for it,
    # then look for crashes, so a process that started late and crashed is still caught
    deadline = time.time() + 30
    while not device.shell(f"pidof {package}", check=False).strip() and time.time() < deadline:
        time.sleep(2)
    log = device.run("logcat", "-d", "-v", "brief", timeout=60)
    crashes = [block for block in log.split("FATAL EXCEPTION")[1:] if f"Process: {package}," in block[:300]]
    anrs = [line for line in log.splitlines() if f"ANR in {package}" in line]
    if crashes or anrs:
        (device.out_dir / "logcat.txt").write_text(log, encoding="utf-8")
        detail = ("FATAL EXCEPTION" + crashes[0][:3000]) if crashes else anrs[0]
        raise SmokeTestError(f"{package} crashed or stopped responding ({step}):\n{detail}")
    if not device.shell(f"pidof {package}", check=False).strip():
        (device.out_dir / "logcat.txt").write_text(log, encoding="utf-8")
        raise SmokeTestError(f"{package} is not running ({step})")


def smoke_test(device: Device, apk: Path, package: str) -> None:
    # only adb commands that the x86_64 emulators of GitHub's runners handle reliably: reading the screen with
    # uiautomator and tapping on it made them freeze or shut down (the style names are checked statically by
    # test_r8_runtime_names.py)
    ime = f"{package}/{IME_SERVICE}"
    device.wait_for_boot()
    device.run("install", "-r", str(apk), timeout=240)
    device.run("logcat", "-c")

    # 1. the keyboard service starts as soon as it becomes the active keyboard (where 5.1.2 crashed)
    deadline = time.time() + 60  # a fresh install takes a moment to be registered as a keyboard
    while ime not in device.shell("ime list -a -s", check=False) and time.time() < deadline:
        time.sleep(2)
    device.shell(f"ime enable {ime}")
    device.shell(f"ime set {ime}")
    time.sleep(10)
    check_alive(device, package, "starting the keyboard")

    # 2. the settings, which load the styles, layouts and their names
    device.shell(f"am start -W -n {package}/{SETTINGS_ACTIVITY}", timeout=90)
    time.sleep(10)
    device.screenshot("settings")
    check_alive(device, package, "opening the settings")
    (device.out_dir / "logcat.txt").write_text(device.run("logcat", "-d", timeout=60), encoding="utf-8")


def save_diagnostics(device: Device) -> None:
    """best effort: the device log and keyboard state, and whether the emulator is still reachable"""
    state = subprocess.run([device.adb, "get-state"], capture_output=True, text=True, timeout=20)
    if state.returncode or "device" not in state.stdout:
        print("The emulator is no longer reachable: this is an emulator problem, not necessarily an app problem",
              file=sys.stderr)
        return
    for name, args in (("logcat.txt", ("logcat", "-d")), ("input_method.txt", ("shell", "dumpsys input_method"))):
        try:
            result = subprocess.run([device.adb, *args], capture_output=True, text=True, timeout=60)
            (device.out_dir / name).write_text(result.stdout, encoding="utf-8")
        except (subprocess.TimeoutExpired, OSError):
            pass


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--package", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--out-dir", required=True, type=Path)
    args = parser.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)
    device = Device(args.adb, args.out_dir)
    try:
        smoke_test(device, args.apk, args.package)
    except SmokeTestError as error:
        print(f"Release smoke test failed: {error}", file=sys.stderr)
        save_diagnostics(device)
        return 1
    print(f"Release smoke test passed: {args.apk.name} started the keyboard and the settings, no crash")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
