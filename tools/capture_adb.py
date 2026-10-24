"""Capture the current Android UI with a verified foreground package, without touching app data."""

import argparse
import json
import subprocess
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--settle", type=float, default=1.0)
    args = parser.parse_args()

    def adb(*command):
        return subprocess.check_output(["adb", "-s", args.serial, *command])

    time.sleep(args.settle)
    window = adb("shell", "dumpsys", "window").decode("utf-8", errors="replace")
    focus = next((line.strip() for line in window.splitlines() if "mCurrentFocus=" in line), "")
    if f"{args.package}/" not in focus:
        raise SystemExit(f"Wrong foreground app, screenshot aborted: {focus}")
    png = adb("exec-out", "screencap", "-p")
    if not png.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SystemExit("ADB did not return a PNG screenshot")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(png)
    metadata = {
        "serial": args.serial,
        "expected_package": args.package,
        "foreground_window": focus,
        "size": adb("shell", "wm", "size").decode().strip(),
        "density": adb("shell", "wm", "density").decode().strip(),
    }
    args.out.with_suffix(".capture.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(metadata, ensure_ascii=False))


if __name__ == "__main__":
    main()
