"""Add arcane credits line for the About page (zh + en)."""
from pathlib import Path

ZH = {
    "credits_source_arcane": "\u80cc\u666f\u7d20\u6750\uff1a\u7a0b\u5e8f\u5316\u751f\u6210\u7684\u539f\u521b\u56fe\uff08\u672a\u4f7f\u7528\u4efb\u4f55\u5546\u6807\u6216\u5f71\u89c6\u7d20\u6750\uff09",
}
EN = {
    "credits_source_arcane": "Backdrops: original artwork generated procedurally (no trademarks or film assets used)",
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
