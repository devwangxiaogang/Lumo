"""往两个 strings.xml 追加「皮肤」相关键（中英成对）。

注意：本文件必须保持纯 ASCII 源码，非 ASCII 一律用 \\uXXXX 转义 ——
Windows 终端 heredoc 会把中文写成 U+FFFD。
"""

from pathlib import Path

ZH = {
    "skin_lumo": "\u5c71\u6c34",                      # 山水
    "skin_lumo_blurb": "\u5ba3\u7eb8\u4e0e\u53e4\u753b",  # 宣纸与古画
    "skin_arcane": "\u5f27\u5149\u5b66\u9662",        # 弧光学院
    "skin_arcane_blurb": "\u661f\u7a79\u4e0e\u70db\u706b",  # 星穹与烛火
    "appearance_skin_title": "\u76ae\u80a4",          # 皮肤
    "appearance_skin_desc": "\u4e24\u5957\u76ae\u80a4\u5404\u6709\u4e03\u5e45\u666f\uff0c\u5207\u6362\u540e\u684c\u9762\u56fe\u6807\u4e5f\u4f1a\u8ddf\u7740\u6362\u3002",
    # 两套皮肤各有八幅景，切换后桌面图标也会跟着换。
    "skin_icon_switched": "\u5df2\u6362\u6210\u300c%1$s\u300d\u98ce\u683c\uff0c\u684c\u9762\u56fe\u6807\u5982\u679c\u6ca1\u5237\u65b0\uff0c\u56de\u684c\u9762\u6ed1\u4e00\u4e0b\u5c31\u597d\u3002",
    # 已换成「%1$s」风格，桌面图标如果没刷新，回桌面滑一下就好。
    "scene_arcane_castle": "\u661f\u591c\u57ce\u5821",   # 星夜城堡
    "scene_arcane_hall": "\u70db\u706b\u957f\u5385",      # 烛火长厅
    "scene_arcane_forest": "\u7981\u6797\u8424\u706b",    # 禁林萤火
    "scene_arcane_lake": "\u955c\u6e56\u661f\u5f71",     # 镜湖星影
    "scene_arcane_tower": "\u5929\u6587\u5854",          # 天文塔
    "scene_arcane_potions": "\u9b54\u836f\u95f4",        # 魔药间
    "scene_arcane_library": "\u6d6e\u7a7a\u4e66\u9601",  # 浮空书阁
    "scene_arcane_nebula": "\u661f\u4e91\u6781\u5149",   # 星云极光
}

EN = {
    "skin_lumo": "Ink &amp; Landscape",
    "skin_lumo_blurb": "Rice paper and old masters",
    "skin_arcane": "Arcanum",
    "skin_arcane_blurb": "Starlit halls and candlelight",
    "appearance_skin_title": "Skin",
    "appearance_skin_desc": "Two skins, eight scenes each. Switching also swaps the launcher icon.",
    "skin_icon_switched": "Switched to %1$s. If the home screen icon looks stale, swipe back and forth.",
    "scene_arcane_castle": "Castle Under Stars",
    "scene_arcane_hall": "Candlelit Hall",
    "scene_arcane_forest": "Fireflies in the Wood",
    "scene_arcane_lake": "Mirror Lake",
    "scene_arcane_tower": "Astronomy Tower",
    "scene_arcane_potions": "Potion Room",
    "scene_arcane_library": "Floating Library",
    "scene_arcane_nebula": "Nebula Veil",
}


def patch(path: Path, table: dict) -> None:
    text = path.read_text(encoding="utf-8")
    assert text.rstrip().endswith("</resources>"), path
    body = "".join(
        '    <string name="%s">%s</string>\n' % (key, value) for key, value in table.items()
    )
    text = text.rstrip()[: -len("</resources>")] + body + "</resources>\n"
    path.write_text(text, encoding="utf-8")
    assert b"\xef\xbf\xbd" not in path.read_bytes(), "mojibake in " + str(path)
    print(path, "->", len(table), "keys")


patch(Path("app/src/main/res/values/strings.xml"), ZH)
patch(Path("app/src/main/res/values-en/strings.xml"), EN)
