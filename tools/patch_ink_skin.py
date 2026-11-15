"""让 Ink.kt 的四个公开组件按皮肤分叉（签名一个字不改）。

- InkRipple：墨点涟漪 → 星火迸散
- InkDots：上下浮动的墨滴 → 亮度呼吸的星火
- BreathingSeal：朱砂方印 → 菱徽 + 符文环
- SealBadge：朱砂方印 → 金色菱徽（字换成四芒星）
- InkDivider：墨线 → 金线 + 中心菱点
"""

from pathlib import Path

PATH = Path("app/src/main/java/com/yuguang/lumo/ui/components/Ink.kt")
text = PATH.read_text(encoding="utf-8")


def replace(old, new):
    global text
    assert old in text, old[:90]
    text = text.replace(old, new, 1)


# ---------- imports ----------
replace(
    "import com.yuguang.lumo.ui.theme.LocalLumoPalette",
    "import com.yuguang.lumo.ui.theme.LocalLumoPalette\n"
    "import com.yuguang.lumo.ui.theme.LocalSkin\n"
    "import com.yuguang.lumo.ui.theme.Skin",
)

# ---------- InkRipple：星火迸散 ----------
replace(
    """    val motion = LocalMotionEnabled.current
    val palette = LocalLumoPalette.current
    val interaction = remember { MutableInteractionSource() }""",
    """    val motion = LocalMotionEnabled.current
    val palette = LocalLumoPalette.current
    val arcane = LocalSkin.current == Skin.Arcane
    val interaction = remember { MutableInteractionSource() }""",
)
replace(
    """                    Modifier.drawBehind {
                        val p = spread.value
                        if (p > 0.001f && p < 0.999f) {
                            val maxRadius = hypot(size.width, size.height) * 0.5f
                            drawCircle(
                                color = palette.cinnabar.copy(alpha = 0.24f * (1f - p)),
                                radius = maxRadius * p,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                    }""",
    """                    Modifier.drawBehind {
                        val p = spread.value
                        if (arcane) {
                            // 弧光学院：墨点换成星火，金环 + 十颗火星向外飞。
                            drawSparkBurst(
                                progress = p,
                                spark = palette.spark,
                                ring = palette.gold,
                            )
                        } else if (p > 0.001f && p < 0.999f) {
                            val maxRadius = hypot(size.width, size.height) * 0.5f
                            drawCircle(
                                color = palette.cinnabar.copy(alpha = 0.24f * (1f - p)),
                                radius = maxRadius * p,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                    }""",
)

# ---------- InkDots：亮度呼吸 ----------
replace(
    """    val motion = LocalMotionEnabled.current
    val progress = rememberInkDotsProgress(motion)
    Row(""",
    """    val motion = LocalMotionEnabled.current
    if (LocalSkin.current == Skin.Arcane) {
        // 星火不位移，只呼吸亮度：夜里一团火在闪，比三滴墨上下跳更「安静」。
        TwinkleDots(
            modifier = modifier,
            color = LocalLumoPalette.current.spark,
            dotSize = dotSize,
            gap = gap,
        )
        return
    }
    val progress = rememberInkDotsProgress(motion)
    Row(""",
)

# ---------- BreathingSeal：菱徽 + 符文环 ----------
replace(
    """    val motion = LocalMotionEnabled.current
    val palette = LocalLumoPalette.current
    val breath = rememberBreath(motion)
    Box(""",
    """    val motion = LocalMotionEnabled.current
    val palette = LocalLumoPalette.current
    if (LocalSkin.current == Skin.Arcane) {
        ArcaneBreathingSeal(modifier = modifier, size = size, text = text, content = content)
        return
    }
    val breath = rememberBreath(motion)
    Box(""",
)

# ---------- SealBadge：金色菱徽 ----------
replace(
    """    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = LumoType.seal.copy(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.5.sp),
            color = contentColor,
            maxLines = 1,
        )
    }
}""",
    """    if (LocalSkin.current == Skin.Arcane) {
        // 弧光学院不写字，用四芒星：同一位置、同一尺寸，语义仍是「这里的标记」。
        val palette = LocalLumoPalette.current
        DiamondBadge(
            size = size,
            brush = goldBrush(palette.gold, palette.cinnabar),
            glow = glowBrush(palette.spark),
            modifier = modifier,
        ) {
            Canvas(modifier = Modifier.size(size * 0.52f)) {
                drawFourPointStar(
                    center = Offset(this.size.width / 2f, this.size.height / 2f),
                    outerRadius = this.size.minDimension / 2f,
                    color = palette.sealInk,
                )
            }
        }
        return
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = LumoType.seal.copy(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.5.sp),
            color = contentColor,
            maxLines = 1,
        )
    }
}""",
)

# ---------- InkDivider：金线 + 菱形 ----------
replace(
    """    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        color.copy(alpha = 0f),
                        color.copy(alpha = alpha),
                        color.copy(alpha = 0f),
                    ),
                ),
            ),
    )
}""",
    """    if (LocalSkin.current == Skin.Arcane) {
        val palette = LocalLumoPalette.current
        Box(modifier = modifier.fillMaxWidth().height(9.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(9.dp)) {
                val midY = size.height / 2f
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            palette.gold.copy(alpha = 0f),
                            palette.gold.copy(alpha = alpha * 0.85f),
                            palette.gold.copy(alpha = 0f),
                        ),
                    ),
                    topLeft = Offset(0f, midY - 0.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
                )
                // 中心一颗小小的菱点：金线不是「切断」，是「穿过一枚铆钉」。
                drawFourPointStar(
                    center = Offset(size.width / 2f, midY),
                    outerRadius = 3.4.dp.toPx(),
                    color = palette.gold.copy(alpha = alpha),
                    waistRatio = 0.34f,
                )
            }
        }
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        color.copy(alpha = 0f),
                        color.copy(alpha = alpha),
                        color.copy(alpha = 0f),
                    ),
                ),
            ),
    )
}

/**
 * 弧光的「呼吸徽记」：外圈符文环慢转（12s 一圈），内芯是金色菱徽，
 * 呼吸的节奏沿用 [LumoMotion.sealBreathMillis]，与山水那枚印章同频 —— 换皮肤不该换节奏。
 */
@Composable
private fun ArcaneBreathingSeal(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    text: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val motion = LocalMotionEnabled.current
    val palette = LocalLumoPalette.current
    val breath = rememberBreath(motion)
    val rotation = rememberRuneRotation(motion)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRuneRing(
                rotationDeg = (rotation?.value ?: 0f) * 360f,
                color = palette.gold,
                radius = this.size.minDimension / 2f - 2.dp.toPx(),
            )
        }
        Box(
            modifier = Modifier
                .size(size * 0.62f)
                .graphicsLayer {
                    val p = breath?.value ?: 1f
                    val s = 0.96f + 0.06f * p
                    scaleX = s
                    scaleY = s
                    alpha = 0.86f + 0.14f * p
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRuneDiamond(
                    brush = goldBrush(palette.gold, palette.cinnabar),
                    glowBrush = glowBrush(palette.spark),
                )
            }
            if (text != null) {
                Text(
                    text = text,
                    style = LumoType.seal.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                    color = palette.sealInk,
                )
            }
            content()
        }
    }
}""",
)

# 新组件需要的 import
for new_import in [
    "import androidx.compose.foundation.Canvas",
    "import androidx.compose.foundation.layout.fillMaxSize",
    "import androidx.compose.ui.geometry.Offset",
]:
    if new_import not in text:
        anchor = "import androidx.compose.foundation.background"
        text = text.replace(anchor, new_import + "\n" + anchor, 1)

PATH.write_text(text, encoding="utf-8")
print("Ink.kt patched:", len(text.splitlines()), "lines")
