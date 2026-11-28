#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Runtime smoke test of a minified (R8) release APK on a running emulator or device.

Release builds are obfuscated and resource-shrunk, which can break code that only fails at runtime:
5.1.2 crashed when the keyboard started because of a class looked up by its Java package. This test
installs the exact APK and uses it like a person would:

1. installs it and makes it the active keyboard, which starts the keyboard service
2. opens RCBBoard's settings (skipping the onboarding) and the Appearance screen
3. opens the style picker and checks that the "Fret" style name survived resource shrinking,
   then selects Fret
4. opens a text field so the keyboard is shown, and taps and types on it
5. fails on any crash or ANR of the app, or if the app process is gone

Screenshots and the app's log are written to --out-dir for the workflow to upload.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
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

    def screen_nodes(self) -> list[ET.Element]:
        # dump to a file and pull it: more reliable than streaming the dump, and every call has a timeout
        for _ in range(3):
            self.shell("rm -f /sdcard/rcb_ui.xml", check=False)
            self.shell("uiautomator dump /sdcard/rcb_ui.xml", timeout=40, check=False)
            xml = self.shell("cat /sdcard/rcb_ui.xml", timeout=20, check=False)
            if "<hierarchy" in xml:
                return list(ET.fromstring(xml[xml.index("<hierarchy"):]).iter("node"))
            time.sleep(2)
        raise SmokeTestError("could not read the screen with uiautomator")

    def find(self, text: str) -> ET.Element | None:
        for node in self.screen_nodes():
            if node.get("text") == text or node.get("content-desc") == text:
                return node
        return None

    def tap_node(self, node: ET.Element) -> None:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
        self.shell(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")

    def tap(self, text: str, required: bool = True, wait: float = 2.0) -> bool:
        deadline = time.time() + 20
        while time.time() < deadline:
            node = self.find(text)
            if node is not None:
                self.tap_node(node)
                time.sleep(wait)
                return True
            time.sleep(1.5)
        if required:
            self.screenshot(f"missing-{re.sub(r'[^a-z0-9]+', '-', text.lower())}")
            raise SmokeTestError(f'"{text}" not found on screen')
        return False

    def screenshot(self, name: str) -> None:
        png = subprocess.run([self.adb, "exec-out", "screencap", "-p"], capture_output=True, timeout=30).stdout
        (self.out_dir / f"{name}.png").write_bytes(png)

    def screen_size(self) -> tuple[int, int]:
        match = re.search(r"(\d+)x(\d+)", self.shell("wm size"))
        if not match:
            raise SmokeTestError("could not read the screen size")
        return int(match.group(1)), int(match.group(2))


def check_alive(device: Device, package: str, step: str) -> None:
    log = device.run("logcat", "-d", "-v", "brief", timeout=60)
    crashes = [block for block in log.split("FATAL EXCEPTION")[1:] if f"Process: {package}," in block[:300]]
    anrs = [line for line in log.splitlines() if f"ANR in {package}" in line]
    if crashes or anrs:
        (device.out_dir / "logcat.txt").write_text(log, encoding="utf-8")
        detail = ("FATAL EXCEPTION" + crashes[0][:3000]) if crashes else anrs[0]
        raise SmokeTestError(f"{package} crashed or stopped responding ({step}):\n{detail}")
    if not device.shell(f"pidof {package}", check=False).strip():
        raise SmokeTestError(f"{package} is not running ({step})")


def keyboard_shown(device: Device) -> bool:
    return "mInputShown=true" in device.shell("dumpsys input_method", check=False)


def smoke_test(device: Device, apk: Path, package: str) -> None:
    ime = f"{package}/{IME_SERVICE}"
    device.wait_for_boot()
    device.shell("settings put global window_animation_scale 0", check=False)
    device.shell("settings put global transition_animation_scale 0", check=False)
    device.shell("settings put global animator_duration_scale 0", check=False)
    device.run("install", "-r", str(apk), timeout=240)
    device.run("logcat", "-c")

    # 1. the keyboard service starts as soon as it becomes the active keyboard
    device.shell(f"ime enable {ime}")
    device.shell(f"ime set {ime}")
    time.sleep(5)
    check_alive(device, package, "starting the keyboard")

    # 2. settings, skipping the onboarding of a fresh install
    device.shell(f"am start -W -n {package}/{SETTINGS_ACTIVITY}", timeout=90)
    time.sleep(3)
    device.tap("Skip", required=False)
    device.screenshot("settings")
    device.tap("Appearance")
    check_alive(device, package, "opening the settings")

    # 3. the style picker: the Fret name must survive resource shrinking
    device.tap("Style")
    device.screenshot("style-picker")
    if device.find("Fret") is None:
        raise SmokeTestError('the style picker does not show "Fret": its name was removed by resource shrinking')
    device.tap("Fret")
    check_alive(device, package, "selecting the Fret style")

    # 4. show the keyboard in the settings search field, then tap and type on it
    device.tap("Search")
    for node in device.screen_nodes():  # some Android versions only show the keyboard when the field is tapped
        if node.get("class", "").endswith("EditText"):
            device.tap_node(node)
            break
    deadline = time.time() + 20
    while not keyboard_shown(device) and time.time() < deadline:
        time.sleep(1)
    if not keyboard_shown(device):
        device.screenshot("keyboard-not-shown")
        raise SmokeTestError("the keyboard was not shown in a text field")
    width, height = device.screen_size()
    for fraction in (0.12, 0.3, 0.5, 0.7, 0.88):  # taps across a letter row near the bottom of the screen
        device.shell(f"input tap {int(width * fraction)} {int(height * 0.82)}")
        time.sleep(0.3)
    device.shell("input text rcbboard")
    time.sleep(2)
    device.screenshot("keyboard")
    check_alive(device, package, "typing")
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
    print(f"Release smoke test passed: {args.apk.name} started, showed the Fret style and the keyboard, no crash")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
