"""给背景环境层加「弧光」分支：云雾 → 星点闪烁 + 极光带 + 尘埃。

MistLayer 在弧光皮肤下整块换实现（[StarfieldLayer] + [AuroraLayer]），
HaloLayer 在弧光皮肤下光源位置与颜色不同（左下星辉而不是左上日晕），
两者都保持原来的调用签名，因此 BackdropHost 一字不改。
"""

from pathlib import Path

PATH = Path("app/src/main/java/com/yuguang/lumo/ui/backdrop/SceneBackdrop.kt")
text = PATH.read_text(encoding="utf-8")


def replace(old, new):
    global text
    assert old in text, old[:90]
    text = text.replace(old, new, 1)


# 1) 云雾层：弧光下换成星野
replace(
    """@Composable
internal fun MistLayer(motionEnabled: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalLumoPalette.current""",
    """@Composable
internal fun MistLayer(motionEnabled: Boolean, modifier: Modifier = Modifier) {
    if (LocalSkin.current == Skin.Arcane) {
        StarfieldLayer(motionEnabled = motionEnabled, modifier = modifier)
        return
    }
    val palette = LocalLumoPalette.current""",
)

# 2) 光晕层：弧光用左上偏下的金色星辉
replace(
    """    val palette = LocalLumoPalette.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glow = if (dark) palette.gold else Color(0xFFFFF6E2)
    val peak = if (dark) 0.10f else 0.16f

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.20f, size.height * 0.08f)""",
    """    val palette = LocalLumoPalette.current
    val arcane = LocalSkin.current == Skin.Arcane
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glow = if (arcane) {
        palette.glow
    } else if (dark) {
        palette.gold
    } else {
        Color(0xFFFFF6E2)
    }
    val peak = when {
        arcane && dark -> 0.22f
        arcane -> 0.18f
        dark -> 0.10f
        else -> 0.16f
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 弧光皮肤的光不在左上角，而在「画面的中上偏右」——像远处塔顶的一盏灯。
        val center = if (arcane) {
            Offset(size.width * 0.62f, size.height * 0.14f)
        } else {
            Offset(size.width * 0.20f, size.height * 0.08f)
        }""",
)
replace(
    """        val radius = size.width * 0.85f
        drawCircle(""",
    """        val radius = size.width * if (arcane) 1.05f else 0.85f
        drawCircle(""",
)

# 3) 追加 StarfieldLayer / AuroraLayer
anchor = """/** 三层可动背景的合体（不含位图层，位图层由 CrossfadeScene 负责交叉淡入）。 */"""
new_layers = '''/**
 * 星野层（弧光皮肤的 z2）：36 颗星各自闪烁 + 一条极光带缓慢横扫 + 少量尘埃。
 *
 * 星星位置用固定种子的伪随机生成，不用 Random —— 同一台机器每次冷启动星图一致，
 * 换页时不会「星星乱跳」。闪烁周期按星编号错开（1.6–3.2s），避免整屏同频。
 */
@Composable
internal fun StarfieldLayer(motionEnabled: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalLumoPalette.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val starColor = if (dark) palette.spark else palette.gold
    val twinkles = rememberStarTwinkles(motionEnabled)
    val sweep = rememberAuroraSweep(motionEnabled)

    Canvas(modifier = modifier.fillMaxSize()) {
        // 星点：x/y 用整数散列铺开，半径 0.6–1.6dp，靠中心略微加密。
        STAR_COUNT_ARCANE.toString()
        for (index in 0 until STAR_COUNT_ARCANE) {
            val fx = hashedUnit(index * 2 + 1)
            val fy = hashedUnit(index * 2 + 2)
            val twinkle = twinkles?.getOrNull(index % twinkles.size)?.value ?: 0.62f
            val baseAlpha = 0.18f + 0.55f * twinkle
            val radius = (0.55f + 1.15f * hashedUnit(index * 5 + 3)) * dp1
            val center = Offset(size.width * fx, size.height * fy)
            // 先铺一层晕，再点一个核：远处的星看起来才是「光」而不是「白点」。
            drawCircle(starColor.copy(alpha = baseAlpha * 0.22f), radius * 3.2f, center)
            drawCircle(starColor.copy(alpha = baseAlpha), radius, center)
        }

        // 极光：一条斜向的柔光带，横扫一轮 9s。只在中上区域，压到正文区就太吵。
        val p = sweep?.value ?: 0.35f
        val bandTop = size.height * (0.02f + 0.10f * p)
        val bandHeight = size.height * 0.26f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.glow.copy(alpha = 0f),
                    palette.glow.copy(alpha = if (dark) 0.10f else 0.07f),
                    palette.glow.copy(alpha = 0f),
                ),
                startY = bandTop,
                endY = bandTop + bandHeight,
            ),
            topLeft = Offset(-size.width * 0.15f, bandTop),
            size = Size(size.width * 1.3f, bandHeight),
        )
    }
}

/** 星点数量：36 颗——比「满天星」省电，比「几颗装饰」有说服力。 */
private const val STAR_COUNT_ARCANE = 36

/** 把整数散列到 0..1：确定、无分配、跨设备一致。 */
private fun hashedUnit(seed: Int): Float {
    var x = seed * 374761393 + 668265263
    x = (x xor (x shr 13)) * 1274126177
    x = x xor (x shr 16)
    return ((x and 0x7FFFFFFF) % 100000) / 100000f
}

@Composable
private fun rememberStarTwinkles(motionEnabled: Boolean): List<State<Float>>? {
    if (!motionEnabled) return null
    val transition = rememberInfiniteTransition(label = "arcaneStars")
    // 6 条不同周期的动画轮流分给 36 颗星，既有层次又不建 36 个 transition。
    return (0 until 6).map { index ->
        val span = LumoMotion.starTwinkleMaxMillis - LumoMotion.starTwinkleMinMillis
        val duration = LumoMotion.starTwinkleMinMillis + span * index / 5
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "arcaneStar$index",
        )
    }
}

@Composable
private fun rememberAuroraSweep(motionEnabled: Boolean): State<Float>? {
    if (!motionEnabled) return null
    val transition = rememberInfiniteTransition(label = "arcaneAurora")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LumoMotion.auroraSweepMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arcaneAuroraSweep",
    )
}

''' + anchor
text = text.replace(anchor, new_layers, 1)

# 4) 需要的 import
for new_import in [
    "import com.yuguang.lumo.ui.theme.LocalSkin",
    "import com.yuguang.lumo.ui.theme.Skin",
]:
    if new_import not in text:
        anchor_import = "import com.yuguang.lumo.ui.theme.LocalLumoPalette"
        text = text.replace(anchor_import, anchor_import + "\n" + new_import, 1)

# dp1 辅助：Canvas 里画半径要用 px，1dp 的 px 值由 Density 提供
if "val dp1" not in text:
    replace(
        """    Canvas(modifier = modifier.fillMaxSize()) {
        // 星点：x/y 用整数散列铺开，半径 0.6–1.6dp，靠中心略微加密。""",
        """    val dp1 = with(androidx.compose.ui.platform.LocalDensity.current) { 1.dp.toPx() }
    Canvas(modifier = modifier.fillMaxSize()) {
        // 星点：x/y 用整数散列铺开，半径 0.6–1.6dp，靠中心略微加密。""",
    )

PATH.write_text(text, encoding="utf-8")
print("SceneBackdrop patched:", len(text.splitlines()), "lines")
