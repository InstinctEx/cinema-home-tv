#!/usr/bin/env python3
"""Install and verify Home; roll back if the device refuses the handover."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
COMPONENT = "com.gathin.tvhome/.MainActivity"


def main():
    host = os.environ.get("TV_HOST")
    if not host:
        raise SystemExit("Set TV_HOST to the TV IP address")
    serial = f"{host}:{os.environ.get('TV_PORT', '5555')}"
    adb = os.environ.get("ADB", "adb")

    def run(*args):
        result = subprocess.run([adb, "-s", serial, *args], capture_output=True, text=True,
                                timeout=120, check=True)
        output = result.stdout.strip()
        if re.search(r"(?im)^(error:|failure|exception|securityexception)", output):
            raise RuntimeError(output)
        return output

    subprocess.run([adb, "connect", serial], check=True, timeout=30)
    if run("get-state") != "device":
        raise SystemExit("TV is not connected and authorised")
    original = run("shell", "cmd", "package", "resolve-activity", "--brief", "-a",
                   "android.intent.action.MAIN", "-c", "android.intent.category.HOME").splitlines()[-1]
    if not re.fullmatch(r"[\w.]+/[\w.$]+", original):
        raise SystemExit(f"Could not record original Home: {original}")
    backup = ROOT / "backup" / ("install-" + re.sub(r"[^\w.-]", "_", serial))
    backup.mkdir(parents=True, exist_ok=True)
    record = backup / "original-home.json"
    if not record.exists():
        record.write_text(json.dumps({"serial": serial, "home": original}, indent=2) + "\n")
    apk = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "launcher/app/build/outputs/apk/debug/app-debug.apk"
    result = run("install", "-r", str(apk))
    if "Success" not in result:
        raise RuntimeError(f"Install did not report success: {result}")
    started = run("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME", "-n", COMPONENT)
    if re.search(r"(?im)^Status:\s*timeout", started):
        # Vendor input screens can briefly take over when an existing Home APK is replaced.
        started = run("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME", "-n", COMPONENT)
    if not re.search(r"(?im)^Status:\s*ok\s*$", started):
        raise RuntimeError(f"Home launch was not confirmed: {started}")
    activity = re.search(r"(?im)^Activity:\s*(\S+)", started)
    if activity and activity.group(1) not in {COMPONENT, "com.gathin.tvhome/com.gathin.tvhome.MainActivity"}:
        raise RuntimeError(f"Another activity took over the Home launch: {started}")
    print(started)
    disabled_stock = False
    try:
        if os.environ.get("DISABLE_STOCK_LAUNCHER") == "1" and original.split("/")[0] != "com.gathin.tvhome":
            run("shell", "pm", "disable-user", "--user", "0", original.split("/")[0])
            disabled_stock = True
        if original not in {COMPONENT, "com.gathin.tvhome/com.gathin.tvhome.MainActivity"}:
            run("shell", "cmd", "package", "set-home-activity", "--user", "0", COMPONENT)
        selected = run("shell", "cmd", "package", "resolve-activity", "--brief", "-a",
                       "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
        if not selected.splitlines()[-1].startswith("com.gathin.tvhome/"):
            raise RuntimeError(f"Home selection was not confirmed: {selected}. "
                               "Some vendor TVs require DISABLE_STOCK_LAUNCHER=1.")
    except Exception:
        if disabled_stock:
            run("shell", "pm", "enable", "--user", "0", original.split("/")[0])
        run("shell", "cmd", "package", "set-home-activity", "--user", "0", original)
        raise
    run("shell", "input", "keyevent", "KEYCODE_HOME")
    print(f"Home installed and selected. Stock disabled: {disabled_stock}. Recovery: {record}")


if __name__ == "__main__":
    main()
