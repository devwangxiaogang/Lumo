package com.yuguang.lumo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuguang.lumo.R
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LumoIcons
import com.yuguang.lumo.ui.theme.LumoType

/**
 * 玻璃的三种语气。
 * - [Paper]：主容器，宣纸磨砂，最常用。
 * - [Cinnabar]：朱砂强调（选中、告警、印章块）。
 * - [Celadon]：石青淡染（次级信息、可点区域）。
 */
enum class GlassTone { Paper, Cinnabar, Celadon }

/**
 * 玻璃底色透明度（level 0 = 最薄，ContrastTest 按它算最坏情形）。
 *
 * - 浅色 Paper：0.76 → 0.82。r2 真机实测卡片灰度 202.0 比背景 211.3 还暗 9 个灰阶（「陷进去」），
 *   把 level 0 从 0.62 提到 0.76，纸面才能始终压在山水的亮部之上，像蒙着一层羊皮纸。
 * - 深色 Paper：仍 0.62 → 0.80。夜山本身偏暗，加厚遮罩会把风景压没，
 *   改成「palette.paperGlass 提亮 6% + 内描边反光」来浮起，见 [glassSurface]。
 */
private fun glassAlpha(tone: GlassTone, level: Float, dark: Boolean): Float {
    val l = level.coerceIn(0f, 1f)
    return when (tone) {
        GlassTone.Paper -> if (dark) 0.62f + 0.18f * l else 0.76f + 0.06f * l
        GlassTone.Celadon -> 0.16f + 0.14f * l
        GlassTone.Cinnabar -> 0.14f + 0.14f * l
    }
}

/**
 * 磨砂宣纸玻璃：底色 + 1dp 渐变亮边 + 接触阴影，让它真正「浮」在山水上（见 [glassSurface]）。
 *
 * 刻意不做 blur：背景图在上游已经烘过柔焦，实时 `Modifier.blur` 在 API 31+ 会额外开一个
 * RenderEffect 图层，滚动时是明显的掉帧来源，收益只有一点点「更雾」。
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    tone: GlassTone = GlassTone.Paper,
    level: Float = 1f,
): Modifier = composed {
    this.clip(shape).glassSurface(shape = shape, tone = tone, level = level)
}

/**
 * 可组合的玻璃表面（不带 clip），给需要在内部再放图片/Canvas 的容器用。
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    tone: GlassTone = GlassTone.Paper,
    level: Float = 1f,
): Modifier {
    val palette = LocalLumoPalette.current
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onSurface
    val dark = scheme.background.luminance() < 0.5f
    // 浅色纸面再向白推一点（0.22）：单靠 paperGlass 的 243 灰阶压不住山水高光。
    val base = when (tone) {
        GlassTone.Paper ->
            if (dark) palette.paperGlass else lerp(palette.paperGlass, Color.White, 0.22f)
        GlassTone.Cinnabar -> palette.cinnabar
        GlassTone.Celadon -> scheme.primary
    }
    // 1dp 描边做成竖直渐变，让「上沿受光」而不是四条边一样粗：
    // 白天是 paper 白 α0.45 的高光边，夜里是 DarkInk α0.10 的纸边反光。
    val borderBrush = when (tone) {
        GlassTone.Paper -> if (dark) {
            Brush.verticalGradient(
                0f to ink.copy(alpha = 0.14f),
                0.55f to ink.copy(alpha = 0.10f),
                1f to ink.copy(alpha = 0.05f),
            )
        } else {
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.45f),
                0.45f to Color.White.copy(alpha = 0.22f),
                1f to palette.glassBorder.copy(alpha = 0.30f),
            )
        }
        GlassTone.Cinnabar -> Brush.verticalGradient(
            0f to palette.cinnabar.copy(alpha = 0.55f),
            1f to palette.cinnabar.copy(alpha = 0.30f),
        )
        GlassTone.Celadon -> Brush.verticalGradient(
            0f to scheme.primary.copy(alpha = 0.42f),
            1f to scheme.primary.copy(alpha = 0.24f),
        )
    }
    // 浮起用的接触阴影：2dp 起，level 再加 6dp。API 28 以下平台阴影会忽略 ambient/spot 颜色
    // （画成黑色），只有 API 28+ 才真的带墨色，这里可以接受——低版本本来就只剩一层淡影。
    val elevation = if (tone == GlassTone.Paper) 2.dp + 6.dp * level.coerceIn(0f, 1f) else 0.dp
    return this
        .then(
            if (elevation > 0.dp) {
                Modifier.shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = ink.copy(alpha = 0.08f),
                    spotColor = ink.copy(alpha = 0.12f),
                )
            } else {
                Modifier
            },
        )
        .background(base.copy(alpha = glassAlpha(tone, level, dark)), shape)
        .border(1.dp, borderBrush, shape)
}

/**
 * 玻璃卡片：页面里所有「浮在宣纸上的块」都该用它，而不是裸 Surface。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tone: GlassTone = GlassTone.Paper,
    level: Float = 1f,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val click = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Column(
        modifier = modifier
            .glass(shape = shape, tone = tone, level = level)
            .then(click)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * 胶囊选择片：山水场景选择、语言切换、模型筛选都用它。
 */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val palette = LocalLumoPalette.current
    val shape = RoundedCornerShape(percent = 50)
    val container = if (selected) {
        palette.cinnabar.copy(alpha = 0.16f)
    } else {
        palette.paperGlass.copy(alpha = 0.60f)
    }
    val border = if (selected) {
        palette.cinnabar.copy(alpha = 0.48f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }
    val content = if (selected) palette.cinnabar else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(shape)
            .background(container, shape)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 玻璃顶栏：56dp 高、避开状态栏、标题走衬线。
 *
 * 不用 Material 的 TopAppBar，是因为它自带一层不透明 surface 容器，
 * 会把背景山水切出一条硬边；这里只铺一层很淡的纸色。
 */
@Composable
fun GlassTopBar(
    title: String?,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val palette = LocalLumoPalette.current
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val veil = if (dark) palette.paper.copy(alpha = 0.55f) else palette.paperGlass.copy(alpha = 0.50f)

    Column(modifier = Modifier.fillMaxWidth().background(veil)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = LumoIcons.Back,
                        contentDescription = stringResource(R.string.back),
                        tint = scheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = title.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
        InkDivider()
    }
}

/**
 * 印章风格的小标题：左朱砂小方印 + 标题字。
 *
 * @param mark 方印里的单字，调用方可传「山 / 听 / 设 / 数」等，默认「印」。
 * @param sealText 旧参数名（W3 已在用），保留兼容；非 null 时优先于 [mark]。
 */
@Composable
fun SealedSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    mark: String = "印",
    sealText: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SealBadge(text = sealText ?: mark)
        Text(
            text = text,
            style = LumoType.seal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
