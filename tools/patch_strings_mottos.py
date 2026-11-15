"""Add three arcane home mottos (zh + en). Pure-ASCII source, \\uXXXX escapes only."""
from pathlib import Path

ZH = {
    "home_motto_arcane_1": "\u62fe\u661f\u4e3a\u70db\uff0c\u7167\u6211\u957f\u591c",
    "home_motto_arcane_2": "\u4ee5\u661f\u4f5c\u56fe\uff0c\u4ee5\u706b\u4e3a\u4e66",
    "home_motto_arcane_3": "\u957f\u591c\u672a\u5c3d\uff0c\u661f\u706b\u4e0d\u7184",
}
EN = {
    "home_motto_arcane_1": "Gather stars for candles, light my long night",
    "home_motto_arcane_2": "Charts from the stars, books from the flame",
    "home_motto_arcane_3": "The night is long, the embers keep",
}


def patch(path: str, entries: dict[str, str]) -> None:
    p = Path(path)
    s = p.read_text(encoding="utf-8")
    block = "".join(
        f'    <string name="{k}">{v}</string>\n' for k, v in entries.items() if f'name="{k}"' not in s
    )
    if not block:
        print(f"{path}: already patched")
        return
    s = s.replace("</resources>", block + "</resources>", 1)
    p.write_text(s, encoding="utf-8")
    print(f"{path}: +{block.count('<string')} keys, ufffd={p.read_bytes().count(b'\xef\xbf\xbd')}")


patch("app/src/main/res/values/strings.xml", ZH)
patch("app/src/main/res/values-en/strings.xml", EN)
