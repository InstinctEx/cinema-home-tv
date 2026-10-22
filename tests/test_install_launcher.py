"""Exercise handover failures without making any ADB connection."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("install_launcher", Path(__file__).resolve().parents[1] / "tools/install_launcher.py")
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)
STOCK = "com.google.android.tvlauncher/.MainActivity"
NEW = installer.COMPONENT


class InstallTests(unittest.TestCase):
    def simulate(self, *, start_fails=False, handover_fails=False, disable_stock=True, launch_timeouts=0):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        commands = []
        home = STOCK
        starts = 0

        def adb(args, **kwargs):
            nonlocal home, starts
            if args[1] == "connect":
                return subprocess.CompletedProcess(args, 0, "connected", "")
            command = tuple(args[3:])
            commands.append(command)
            output = ""
            if command == ("get-state",): output = "device"
            elif command[0] == "install": output = "Success"
            elif command[:3] == ("shell", "am", "start"):
                starts += 1
                output = "Error: Activity did not start" if start_fails else "Status: timeout" if starts <= launch_timeouts else "Status: ok"
            elif command[:4] == ("shell", "cmd", "package", "resolve-activity"): output = home
            elif command[:4] == ("shell", "cmd", "package", "set-home-activity"):
                if command[-1] == STOCK: home = STOCK
                elif not handover_fails: home = NEW
            return subprocess.CompletedProcess(args, 0, output, "")

        environment = {"TV_HOST": "192.0.2.5", "DISABLE_STOCK_LAUNCHER": "1" if disable_stock else "0"}
        failure = None
        with patch.dict(os.environ, environment), patch.object(installer, "ROOT", root), \
                patch.object(sys, "argv", ["install_launcher.py"]), \
                patch.object(installer.subprocess, "run", side_effect=adb), contextlib.redirect_stdout(io.StringIO()):
            try:
                installer.main()
            except RuntimeError as error:
                failure = error
        record = json.loads(next(root.glob("backup/*/original-home.json")).read_text())
        return commands, failure, record, home

    def test_failed_launch_never_disables_stock(self):
        commands, error, record, home = self.simulate(start_fails=True)
        self.assertIsNotNone(error)
        self.assertFalse(any("disable-user" in c for c in commands))
        self.assertEqual(home, STOCK)
        self.assertEqual(record["home"], STOCK)

    def test_failed_handover_restores_stock(self):
        commands, error, _, home = self.simulate(handover_fails=True)
        self.assertIsNotNone(error)
        self.assertIn(("shell", "pm", "enable", "--user", "0", STOCK.split("/")[0]), commands)
        self.assertEqual(home, STOCK)

    def test_success_confirms_home_and_keeps_recovery(self):
        commands, error, record, home = self.simulate()
        self.assertIsNone(error)
        self.assertEqual(home, NEW)
        self.assertEqual(record["home"], STOCK)
        start = next(i for i, c in enumerate(commands) if c[:3] == ("shell", "am", "start"))
        disable = next(i for i, c in enumerate(commands) if "disable-user" in c)
        self.assertLess(start, disable)
        self.assertEqual(commands[-1], ("shell", "input", "keyevent", "KEYCODE_HOME"))

    def test_default_does_not_disable_stock(self):
        commands, error, _, _ = self.simulate(disable_stock=False)
        self.assertIsNone(error)
        self.assertFalse(any("disable-user" in c for c in commands))

    def test_persistent_launch_timeout_never_disables_stock(self):
        commands, error, _, home = self.simulate(launch_timeouts=2)
        self.assertIsNotNone(error)
        self.assertFalse(any("disable-user" in command for command in commands))
        self.assertEqual(home, STOCK)

    def test_temporary_vendor_takeover_retries_before_handover(self):
        commands, error, _, home = self.simulate(launch_timeouts=1)
        self.assertIsNone(error)
        self.assertEqual(home, NEW)
        self.assertEqual(sum(command[:3] == ("shell", "am", "start") for command in commands), 2)


if __name__ == "__main__":
    unittest.main()
