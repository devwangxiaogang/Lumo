"""把「按皮肤取景」接到 7 个调用点。

都是小改动：读 LocalSkin.current 后把 skin 传给 SceneryCatalog，
不改布局、不改参数表（唯一例外是 ConversationListScreen 里的私有 helper）。
"""

from pathlib import Path

ROOT = Path("app/src/main/java/com/yuguang/lumo/ui")


def patch(rel: str, pairs, add_import: bool = True) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    for old, new in pairs:
        assert old in text, (rel, old[:70])
        text = text.replace(old, new, 1)
    imp = "import com.yuguang.lumo.ui.theme.LocalSkin\n"
    if add_import and imp not in text:
        anchor = "import com.yuguang.lumo.ui.theme.MaterialTheme"  # placeholder never used
        # 插到第一个 com.yuguang.lumo.ui.theme.* import 之前；没有就插到最后一个 import 之后
        theme_imports = [ln for ln in text.splitlines() if ln.startswith("import com.yuguang.lumo.ui.theme.")]
        if theme_imports:
            first = sorted(theme_imports)[0]
            text = text.replace(first, imp + first, 1)
        else:
            lines = text.splitlines()
            last = max(i for i, ln in enumerate(lines) if ln.startswith("import "))
            lines.insert(last + 1, imp.rstrip("\n"))
            text = "\n".join(lines) + "\n"
    path.write_text(text, encoding="utf-8")
    print(rel, "ok")


# 1) AiApp：场景优先级里的三处取景
patch(
    "AiApp.kt",
    [
        (
            """    val scene: Scene = SceneryCatalog.byId(pickedScene)
        ?: if (dailyScene && route == Routes.Conversations) {
            SceneryCatalog.daily(today)
        } else {
            SceneryCatalog.forRoute(route)
        }""",
            """    // 皮肤只影响「取哪一组景」：手选的一幅必须属于当前皮肤，否则当作没选。
    val skin = LocalSkin.current
    val scene: Scene = SceneryCatalog.byId(pickedScene, skin)
        ?: if (dailyScene && route == Routes.Conversations) {
            SceneryCatalog.daily(today, skin)
        } else {
            SceneryCatalog.forRoute(route, skin)
        }""",
        ),
    ],
)

# 2) 对话列表：背景 + 缩略图 + 艺术条
patch(
    "list/ConversationListScreen.kt",
    [
        (
            """private fun sceneForConversation(id: Long): Scene {
    val all = SceneryCatalog.all
    val index = ((id % all.size) + all.size) % all.size
    return all[index.toInt()]
}""",
            """@Composable
private fun sceneForConversation(id: Long): Scene =
    SceneryCatalog.forConversation(id, LocalSkin.current)""",
        ),
        (
            """    BackdropHost(scene = SceneryCatalog.forRoute("conversations")) {""",
            """    BackdropHost(scene = SceneryCatalog.forRoute("conversations", LocalSkin.current)) {""",
        ),
        (
            """    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 10.dp""",
            """    // 弧光皮肤换一组底图，山水皮肤维持原来的三幅 + 擂台。
    val stripScenes = SceneryCatalog.primary(LocalSkin.current)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 10.dp""",
        ),
        ("                scene = Scene.Stream,\n", "                scene = stripScenes[0],\n"),
        ("                scene = Scene.WindBamboo,\n", "                scene = stripScenes[1],\n"),
        ("                scene = Scene.Falls,\n                width = cardWidth,\n                onClick = onVoice,",
         "                scene = stripScenes[2],\n                width = cardWidth,\n                onClick = onVoice,"),
        ("                scene = Scene.Peaks,\n", "                scene = stripScenes[3],\n"),
    ],
)

# 3) 对话页背景
patch(
    "chat/ChatScreen.kt",
    [
        (
            """    BackdropHost(scene = SceneryCatalog.forRoute("chat")) {""",
            """    BackdropHost(scene = SceneryCatalog.forRoute("chat", LocalSkin.current)) {""",
        ),
    ],
)

# 4) 翻译页记录缩略图
patch(
    "translate/TranslateScreen.kt",
    [
        (
            """    val thumb = remember(record.id) { SceneryCatalog.daily(record.createdAt / 86_400_000L) }""",
            """    val skin = LocalSkin.current
    val thumb = remember(record.id, skin) {
        SceneryCatalog.daily(record.createdAt / 86_400_000L, skin)
    }""",
        ),
    ],
)

# 5) 设置页：外观行 value + 外观页画廊过滤
patch(
    "settings/SettingsScreen.kt",
    [
        (
            """    val sceneName = SceneryCatalog.byId(sceneId)?.let { stringResource(it.titleRes) }""",
            """    val sceneName = SceneryCatalog.byId(sceneId, LocalSkin.current)
        ?.let { stringResource(it.titleRes) }""",
        ),
    ],
)

# 6) 语音页：固定「最有沉浸感」的一幅（两套皮肤各自一幅）
patch(
    "voice/VoiceChatScreen.kt",
    [
        (
            """    // 注意：SceneryCatalog.forRoute("voice") 仍是 Falls（那是「随页面」的取景），这里只改本页固定景。
    BackdropHost(scene = Scene.Qinglu) {""",
            """    // 固定一幅最有沉浸感的景：山水用青绿，弧光用天文塔。路由取景另走 forRoute，互不影响。
    BackdropHost(scene = SceneryCatalog.heroFor("voice", LocalSkin.current)) {""",
        ),
    ],
)
