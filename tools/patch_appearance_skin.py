"""外观页加「皮肤」选择器，并让场景画廊跟着皮肤走。

选择器放在最上面：换皮肤比换一幅画动得更大，应该是这一页第一个决定。
切换时同时改图标（IconSwitcher），并给一次星尘扫过的过场。
"""

from pathlib import Path

PATH = Path("app/src/main/java/com/yuguang/lumo/ui/settings/SettingsPages.kt")
text = PATH.read_text(encoding="utf-8")


def replace(old, new):
    global text
    assert old in text, old[:90]
    text = text.replace(old, new, 1)


# 1) ViewModel：皮肤 + 图标切换
replace(
    """    val dailyScene: StateFlow<Boolean> = uiPreferences.dailyScene
    val motion: StateFlow<Boolean> = uiPreferences.motion

    fun setMode(mode: ThemeMode) {""",
    """    val dailyScene: StateFlow<Boolean> = uiPreferences.dailyScene
    val motion: StateFlow<Boolean> = uiPreferences.motion

    /** 当前皮肤；换皮肤会顺带把桌面图标换掉（见 [setSkin]）。 */
    val skin: StateFlow<Skin> = uiPreferences.skin

    fun setMode(mode: ThemeMode) {""",
)
replace(
    """    fun setScene(id: String?) = uiPreferences.setScene(id)""",
    """    /**
     * 换皮肤 = 换色板 + 换八幅景 + 换桌面图标。
     *
     * 图标切换放在这里而不是外层：皮肤只有这一个入口，两件事必须同时发生，
     * 否则会出现「界面是弧光、图标还是朱砂印」的半套状态。
     */
    fun setSkin(skin: Skin) {
        if (uiPreferences.skin.value == skin) return
        uiPreferences.setSkin(skin)
        viewModelScope.launch { IconSwitcher.applyAsync(app, skin) }
    }

    fun setScene(id: String?) = uiPreferences.setScene(id)""",
)

# 2) 屏幕：读皮肤 + 顶部选择器 + 画廊过滤
replace(
    """    val motion by viewModel.motion.collectAsStateWithLifecycle()
    val picked = SceneryCatalog.byId(pickedSceneId)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f""",
    """    val motion by viewModel.motion.collectAsStateWithLifecycle()
    val skin by viewModel.skin.collectAsStateWithLifecycle()
    val picked = SceneryCatalog.byId(pickedSceneId, skin)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val context = LocalContext.current
    val skinLabel = stringResource(skin.labelRes)""",
)
replace(
    """            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SealedSectionTitle(stringResource(R.string.appearance_scene_title), mark = "山")""",
    """            // 皮肤是这一页最上面那个决定：它一换，色板、八幅景、桌面图标一起换。
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SealedSectionTitle(stringResource(R.string.appearance_skin_title), mark = "皮")
                Text(
                    stringResource(R.string.appearance_skin_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Skin.entries.forEach { item ->
                        SkinCard(
                            skin = item,
                            selected = item == skin,
                            dark = dark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.setSkin(item)
                                // 图标在部分桌面要等下一次回到桌面才刷新，先给一句话交代。
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.skin_icon_switched, skinLabelOf(context, item)),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SealedSectionTitle(stringResource(R.string.appearance_scene_title), mark = "山")""",
)
replace(
    """                    items(SceneryCatalog.all, key = { it.id }) { scene ->""",
    """                    // 画廊只列当前皮肤的八幅：混着放会让「这幅画属于哪套皮肤」变得难判断。
                    items(SceneryCatalog.allFor(skin), key = { it.id }) { scene ->""",
)

# 3) SkinCard + 取名字的小工具（贴在对 SceneCard 定义之前）
anchor = "@Composable\nprivate fun SceneCard("
skin_card = '''/**
 * 皮肤卡：左边一小块「材质预览」（用该皮肤的第一幅景 + 自己的纸色玻璃），
 * 右边名字与一句说明。不做真实截图 —— 预览图会随皮肤变，截死了反而误导。
 */
@Composable
private fun SkinCard(
    skin: Skin,
    selected: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalLumoPalette.current
    val preview = SceneryCatalog.allFor(skin).first()
    val night = runCatching { MaterialTheme.colorScheme.background.luminance() < 0.5f }.getOrDefault(dark)
    Box(
        modifier = modifier
            .height(104.dp)
            .clip(RoundedCornerShape(18.dp))
            .glass(shape = RoundedCornerShape(18.dp), level = if (selected) 1f else 0.85f)
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        // 左半：一幅该皮肤的景，右侧渐隐到玻璃底上。
        AsyncImage(
            model = if (night) preview.nightRes else preview.dayRes,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.52f),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.52f)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, palette.paperGlass.copy(alpha = 0.92f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.52f)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(skin.labelRes),
                style = LumoType.seal.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = stringResource(skin.blurbRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        if (selected) {
            // 选中：右上角一枚朱砂（弧光下是金）小圆点，比整卡描边安静。
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(palette.cinnabar),
            )
        }
    }
}

/** 取皮肤名字给 toast 用（Compose 的 stringResource 不能在回调里调）。 */
private fun skinLabelOf(context: android.content.Context, skin: Skin): String =
    context.getString(skin.labelRes)

''' + anchor
text = text.replace(anchor, skin_card, 1)

# 4) imports
for imp in [
    "import android.widget.Toast",
    "import androidx.compose.foundation.layout.fillMaxHeight",
    "import androidx.compose.foundation.layout.fillMaxWidth",
    "import androidx.compose.material3.MaterialTheme",
    "import androidx.compose.ui.graphics.Brush",
    "import androidx.compose.ui.graphics.Color",
    "import androidx.compose.ui.layout.ContentScale",
    "import androidx.compose.ui.platform.LocalContext",
    "import androidx.compose.ui.semantics.Role",
    "import coil.compose.AsyncImage",
    "import com.yuguang.lumo.ui.theme.IconSwitcher",
    "import com.yuguang.lumo.ui.theme.LumoType",
    "import com.yuguang.lumo.ui.theme.Skin",
]:
    if imp not in text:
        lines = text.splitlines()
        last = max(i for i, line in enumerate(lines) if line.startswith("import "))
        lines.insert(last + 1, imp)
        text = "\n".join(lines) + "\n"

assert "viewModelScope" in text
if "import androidx.lifecycle.viewModelScope" not in text:
    text = text.replace(
        "import androidx.lifecycle.viewmodel.compose.viewModel",
        "import androidx.lifecycle.viewModelScope\nimport androidx.lifecycle.viewmodel.compose.viewModel",
        1,
    )

PATH.write_text(text, encoding="utf-8")
print("appearance patched:", len(text.splitlines()), "lines")
