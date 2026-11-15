"""Add skin-aware appearance copy (scene section title / motion / daily) to both strings.xml.

Pure-ASCII source with \\uXXXX escapes so the terminal never mangles the CJK.
"""
from pathlib import Path

ZH = {
    "appearance_scene_title_arcane": "\u5e7b\u5883",
    "appearance_scene_desc_arcane": "\u6bcf\u4e00\u9875\u90fd\u6709\u4e00\u5e45\u9701\u5f71\u4e3a\u5e95\uff0c\u4e5f\u53ef\u4ee5\u6307\u5b9a\u4e00\u5e45\u5e38\u9a7b\u3002",
    "appearance_motion_desc_arcane": "\u661f\u5c18\u7f13\u7f13\u6d6e\u52a8\u3001\u753b\u9762\u6781\u7f13\u63a8\u8fd1\u3002\u5173\u95ed\u540e\u9759\u6b62\uff0c\u66f4\u7701\u7535\u3002",
    "appearance_daily_desc_arcane": "\u6bcf\u5929\u6362\u4e00\u5e45\u5e7b\u5883\uff0c\u9875\u9762\u81ea\u5df1\u4f1a\u547c\u5438\u3002",
}
EN = {
    "appearance_scene_title_arcane": "Arcane scenes",
    "appearance_scene_desc_arcane": "Every page gets a soft magical backdrop, or you can pin one.",
    "appearance_motion_desc_arcane": "Stardust drifts and the view slowly pushes in. Off means still and cheaper on battery.",
    "appearance_daily_desc_arcane": "A new arcane scene every day, so the app keeps breathing.",
}


def patch(path: str, entries: dict[str, str]) -> None:
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    if "</resources>" not in s:
        raise SystemExit(f"{path}: missing </resources>")
    block = "".join(
        f'    <string name="{k}">{v}</string>\n' for k, v in entries.items() if f'name="{k}"' not in s
    )
    if not block:
        print(f"{path}: already patched")
        return
    s = s.replace("</resources>", block + "</resources>", 1)
    p.write_text(s, encoding="utf-8")
    bad = p.read_bytes().count(b"\xef\xbf\xbd")
    print(f"{path}: +{block.count('<string')} keys, ufffd={bad}")


patch("app/src/main/res/values/strings.xml", ZH)
patch("app/src/main/res/values-en/strings.xml", EN)
