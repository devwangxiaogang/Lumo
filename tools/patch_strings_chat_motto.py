"""Add arcane vertical couplets for the chat welcome pane (zh + en)."""
from pathlib import Path

ZH = {
    "chat_poem_arcane_1": "\u661f\u5782\u56db\u91ce\uff0c\u706b\u7167\u957f\u5377",
}
EN = {
    "chat_poem_arcane_1": "Stars over the field, a flame on the page",
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
