"""Capture Arena / UsageStats / settings-sub pages by text-driven taps.

Usage: python tools/capture_extra.py [--serial XXX] [--theme light|dark]
Writes docs/smoke/extra-<theme>-<page>.png
"""
import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = "D:/localProgram/AIdemo"
PKG = "com.yuguang.lumo"


def adb(serial, *args, capture=False):
    cmd = ["adb", "-s", serial, *args]
    if capture:
        return subprocess.run(cmd, capture_output=True)
    return subprocess.run(cmd, capture_output=True)


def sh(serial, *args):
    return adb(serial, "shell", *args, capture=True).stdout.decode("utf-8", "replace")


def dump(serial):
    sh(serial, "uiautomator", "dump", "/sdcard/ui.xml")
    adb(serial, "pull", "/sdcard/ui.xml", "C:/tmp/ui.xml", capture=True)
    try:
        return ET.parse("C:/tmp/ui.xml").getroot()
    except Exception:
        return None


def find(root, text, exact=False):
    for node in root.iter("node"):
        t = node.get("text") or ""
        d = node.get("content-desc") or ""
        hay = t + "|" + d
        if (exact and (t == text or d == text)) or (not exact and text in hay):
            b = re.findall(r"\d+", node.get("bounds", ""))
            if len(b) == 4:
                x1, y1, x2, y2 = map(int, b)
                return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap(serial, xy):
    adb(serial, "shell", "input", "tap", str(xy[0]), str(xy[1]), capture=True)
    time.sleep(1.0)


def shot(serial, name):
    sh(serial, "screencap", "-p", "/sdcard/_x.png")
    path = f"{ROOT}/docs/smoke/{name}.png"
    adb(serial, "pull", "/sdcard/_x.png", path, capture=True)
    print("shot ->", name)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", default="55QCS4Z5VGGU8HLV")
    ap.add_argument("--theme", default="light")
    a = ap.parse_args()
    s = a.serial
    sh(s, "input", "keyevent", "KEYCODE_WAKEUP")
    sh(s, "wm", "dismiss-keyguard")
    sh(s, "am", "force-stop", PKG)
    sh(s, "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(3.0)

    root = dump(s)
    if root is None:
        print("dump failed"); sys.exit(1)

    # 1) 擂台：横滑艺术条后点第四张卡
    strip = find(root, "擂台") or find(root, "Arena")
    if strip is None:
        # 滑动艺术条：在卡片区域横向拖动
        adb(s, "shell", "input", "swipe", "900", "530", "200", "530", "300", capture=True)
        time.sleep(0.8)
        root = dump(s)
        strip = find(root, "擂台") or find(root, "Arena")
    if strip:
        tap(s, strip)
        time.sleep(1.6)
        shot(s, f"extra-{a.theme}-arena")
        sh(s, "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1.2)
    else:
        print("arena entry not found")

    # 2) 设置 → 使用统计
    root = dump(s)
    me = find(root, "我")
    if me:
        tap(s, me)
        time.sleep(0.8)
        root = dump(s)
        settings = find(root, "设置")
        if settings:
            tap(s, settings)
            time.sleep(1.2)
            shot(s, f"extra-{a.theme}-settings-top")
            # 向下滚到数据分区
            for _ in range(3):
                adb(s, "shell", "input", "swipe", "540", "1900", "540", "900", "300", capture=True)
                time.sleep(0.5)
            root = dump(s)
            stats = find(root, "统计") or find(root, "使用统计")
            if stats:
                tap(s, stats)
                time.sleep(1.6)
                shot(s, f"extra-{a.theme}-stats")
                print("stats captured")
            else:
                shot(s, f"extra-{a.theme}-settings-bottom")
                print("stats entry not found")


if __name__ == "__main__":
    main()
