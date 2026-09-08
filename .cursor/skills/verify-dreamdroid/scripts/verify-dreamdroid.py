#!/usr/bin/env python3
"""Drive the dreamDroid googleDebug APK on a connected Android device via adb."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "net.reichholf.dreamdroid.debug"
LAUNCHER = f"{PACKAGE}/net.reichholf.dreamdroid.activities.TabbedNavigationActivity"
STATE_DIR = Path(__file__).resolve().parents[1] / "artifacts"
PID_FILE = STATE_DIR / "session.pid"
DUMP_REMOTE = "/sdcard/dreamdroid-verify-dump.xml"
REPO_ROOT = Path(__file__).resolve().parents[4]


def find_adb() -> str:
    env = os.environ.get("ADB")
    if env:
        return env
    local = REPO_ROOT / "local.properties"
    if local.exists():
        for line in local.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1].replace("\\\\", "\\").replace("\\:", ":")
                candidate = Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
                if candidate.exists():
                    return str(candidate)
    return "adb.exe" if os.name == "nt" else "adb"


ADB = find_adb()


def run_adb(args: list[str], check: bool = True, capture: bool = True) -> subprocess.CompletedProcess[str]:
    serial = os.environ.get("ANDROID_SERIAL")
    cmd = [ADB]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    return subprocess.run(
        cmd,
        check=check,
        capture_output=capture,
        text=True,
        encoding="utf-8",
        errors="replace",
    )


def require_device() -> str:
    out = run_adb(["devices"]).stdout
    ready = []
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            ready.append(parts[0])
    if not ready:
        print("doctor: no adb device in 'device' state", file=sys.stderr)
        print(out, file=sys.stderr)
        sys.exit(1)
    serial = os.environ.get("ANDROID_SERIAL")
    if serial and serial not in ready:
        print(f"doctor: ANDROID_SERIAL={serial} is not connected", file=sys.stderr)
        sys.exit(1)
    return serial or ready[0]


def parse_dump(xml_text: str) -> list[dict[str, str]]:
    nodes = []
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as exc:
        raise SystemExit(f"ui dump is not valid XML: {exc}") from exc
    for el in root.iter("node"):
        nodes.append(dict(el.attrib))
    return nodes


def bounds_center(bounds: str) -> tuple[int, int]:
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        raise SystemExit(f"unparseable bounds: {bounds}")
    l, t, r, b = map(int, m.groups())
    return (l + r) // 2, (t + b) // 2


def dump_ui() -> list[dict[str, str]]:
    run_adb(["shell", "uiautomator", "dump", DUMP_REMOTE], check=False)
    pulled = run_adb(["exec-out", "cat", DUMP_REMOTE], check=False)
    if pulled.returncode != 0 or not pulled.stdout.strip():
        raise SystemExit("failed to dump UI hierarchy")
    return parse_dump(pulled.stdout)


def match_node(nodes: list[dict[str, str]], text=None, desc=None, resource_id=None) -> dict[str, str]:
    for n in nodes:
        if text is not None and text not in (n.get("text") or ""):
            continue
        if desc is not None and desc not in (n.get("content-desc") or ""):
            continue
        if resource_id is not None and n.get("resource-id") != resource_id:
            continue
        return n
    raise SystemExit(
        f"no node matched text={text!r} desc={desc!r} resource-id={resource_id!r}"
    )


def cmd_doctor(_: argparse.Namespace) -> None:
    serial = require_device()
    pkg = run_adb(["shell", "pm", "path", PACKAGE], check=False)
    if pkg.returncode != 0 or not pkg.stdout.strip():
        print(f"doctor: {PACKAGE} is not installed (install googleDebug first)", file=sys.stderr)
        sys.exit(1)
    pid = run_adb(["shell", "pidof", PACKAGE], check=False).stdout.strip()
    focus = run_adb(["shell", "dumpsys", "window"], check=False).stdout
    focused = ""
    for line in focus.splitlines():
        if "mCurrentFocus" in line or "mFocusedApp" in line:
            focused = line.strip()
            if PACKAGE in focused:
                break
    version = run_adb(
        ["shell", "dumpsys", "package", PACKAGE],
        check=False,
    ).stdout
    version_name = ""
    for line in version.splitlines():
        if "versionName=" in line:
            version_name = line.strip()
            break
    print(f"serial={serial}")
    print(f"package={PACKAGE}")
    print(version_name or "versionName=unknown")
    print(f"pid={pid or 'not running'}")
    print(focused or "focus=unknown")
    if PACKAGE not in (focused or ""):
        print("doctor: debug package is not in the foreground", file=sys.stderr)
        sys.exit(1)
    if not pid:
        print("doctor: debug package has no pid", file=sys.stderr)
        sys.exit(1)


def cmd_launch(args: argparse.Namespace) -> None:
    require_device()
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    if args.clear_data:
        run_adb(["shell", "pm", "clear", PACKAGE], check=False)
    started = run_adb(
        ["shell", "am", "start", "-W", "-n", LAUNCHER, "-a", "android.intent.action.MAIN"],
        check=False,
    )
    if started.returncode != 0:
        print(started.stdout + started.stderr, file=sys.stderr)
        raise SystemExit("am start failed")
    deadline = time.time() + args.timeout
    pid = ""
    while time.time() < deadline:
        pid = run_adb(["shell", "pidof", PACKAGE], check=False).stdout.strip().split()
        pid = pid[0] if pid else ""
        if pid:
            break
        time.sleep(0.4)
    if not pid:
        raise SystemExit("app did not start")
    PID_FILE.write_text(pid, encoding="utf-8")
    print(f"started {LAUNCHER} pid={pid}")


def cmd_dump(args: argparse.Namespace) -> None:
    nodes = dump_ui()
    xml_text = run_adb(["exec-out", "cat", DUMP_REMOTE]).stdout
    if args.path:
        path = Path(args.path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(xml_text, encoding="utf-8")
        print(str(path))
    else:
        for n in nodes:
            print(
                f"{n.get('class','')} text={n.get('text')!r} desc={n.get('content-desc')!r} id={n.get('resource-id')!r} bounds={n.get('bounds')}"
            )


def cmd_screenshot(args: argparse.Namespace) -> None:
    path = Path(args.path)
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = subprocess.run(
        [ADB] + (["-s", os.environ["ANDROID_SERIAL"]] if os.environ.get("ANDROID_SERIAL") else []) + ["exec-out", "screencap", "-p"],
        check=True,
        capture_output=True,
    )
    path.write_bytes(raw.stdout)
    print(str(path))


def cmd_tap(args: argparse.Namespace) -> None:
    node = match_node(dump_ui(), text=args.text, desc=args.desc, resource_id=args.resource_id)
    x, y = bounds_center(node["bounds"])
    run_adb(["shell", "input", "tap", str(x), str(y)])
    print(f"tapped {x},{y} text={node.get('text')!r} id={node.get('resource-id')!r}")


def cmd_wait_text(args: argparse.Namespace) -> None:
    deadline = time.time() + args.timeout
    last = ""
    while time.time() < deadline:
        nodes = dump_ui()
        last = " | ".join((n.get("text") or n.get("content-desc") or "") for n in nodes if n.get("text") or n.get("content-desc"))
        for n in nodes:
            hay = f"{n.get('text','')} {n.get('content-desc','')}"
            if args.text in hay:
                print(f"found {args.text!r}")
                return
        time.sleep(0.6)
    print(last, file=sys.stderr)
    raise SystemExit(f"timed out waiting for {args.text!r}")


def cmd_contains(args: argparse.Namespace) -> None:
    nodes = dump_ui()
    for n in nodes:
        hay = f"{n.get('text','')} {n.get('content-desc','')}"
        if args.text in hay:
            print(f"contains {args.text!r}")
            return
    raise SystemExit(f"UI dump does not contain {args.text!r}")


def cmd_back(_: argparse.Namespace) -> None:
    run_adb(["shell", "input", "keyevent", "KEYCODE_BACK"])
    print("KEYCODE_BACK")


def cmd_cleanup(_: argparse.Namespace) -> None:
    pid = PID_FILE.read_text(encoding="utf-8").strip() if PID_FILE.exists() else ""
    current = run_adb(["shell", "pidof", PACKAGE], check=False).stdout.strip().split()
    if pid and pid in current:
        run_adb(["shell", "kill", pid], check=False)
    run_adb(["shell", "am", "force-stop", PACKAGE], check=False)
    run_adb(["shell", "rm", "-f", DUMP_REMOTE], check=False)
    if PID_FILE.exists():
        PID_FILE.unlink()
    print(f"stopped {PACKAGE}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("doctor")
    p_launch = sub.add_parser("launch")
    p_launch.add_argument("--clear-data", action="store_true")
    p_launch.add_argument("--timeout", type=float, default=30)
    p_dump = sub.add_parser("dump")
    p_dump.add_argument("--path")
    p_shot = sub.add_parser("screenshot")
    p_shot.add_argument("--path", required=True)
    p_tap = sub.add_parser("tap")
    p_tap.add_argument("--text")
    p_tap.add_argument("--desc")
    p_tap.add_argument("--resource-id")
    p_wait = sub.add_parser("wait-text")
    p_wait.add_argument("text")
    p_wait.add_argument("--timeout", type=float, default=20)
    p_has = sub.add_parser("contains")
    p_has.add_argument("text")
    sub.add_parser("back")
    sub.add_parser("cleanup")

    args = parser.parse_args()
    if args.cmd == "tap" and not (args.text or args.desc or args.resource_id):
        parser.error("tap requires --text, --desc, or --resource-id")
    {
        "doctor": cmd_doctor,
        "launch": cmd_launch,
        "dump": cmd_dump,
        "screenshot": cmd_screenshot,
        "tap": cmd_tap,
        "wait-text": cmd_wait_text,
        "contains": cmd_contains,
        "back": cmd_back,
        "cleanup": cmd_cleanup,
    }[args.cmd](args)


if __name__ == "__main__":
    main()
