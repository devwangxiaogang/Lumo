package com.yuguang.lumo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuguang.lumo.R
import com.yuguang.lumo.ui.theme.LumoIcons
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LumoType

/** 空状态轮换用的诗句（复用已有键，不新增文案）。 */
private val POEMS = intArrayOf(
    R.string.home_poem_1,
    R.string.home_poem_2,
    R.string.home_poem_3,
    R.string.home_poem_4,
)

/**
 * 统一空状态：一幅淡墨山水空镜 + 一行诗 + 标题/说明/操作。
 *
 * 签名保持不变（W3 已有的调用无需改动）；变化只在视觉：不再是一枚彩色圆形图标，
 * 而是一张「什么都没有的山水」，让空状态本身也是画面的一部分。
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val palette = LocalLumoPalette.current
    // 同一句标题永远配同一句诗，不随重组跳字。
    val poemRes = POEMS[(title.hashCode() and 0x7FFF_FFFF) % POEMS.size]

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EmptyLandscapeVignette()
        PoemLine(textRes = poemRes)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        actions()
        SealBadge(text = "空", color = palette.cinnabar.copy(alpha = 0.55f), size = 16.dp)
    }
}

/**
 * 淡墨空镜：三道山脊 + 一枚月。全部是低透明度墨色，不抢正文。
 * 尺寸写死 120dp 高，避免在长短不一的空状态里跳来跳去。
 */
@Composable
private fun EmptyLandscapeVignette(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val gold = LocalLumoPalette.current.gold
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val w = size.width
        val h = size.height

        fun ridge(baseY: Float, amp: Float, color: Color) {
            val path = Path().apply {
                moveTo(0f, h)
                lineTo(0f, baseY + amp * 0.2f)
                quadraticTo(w * 0.16f, baseY - amp, w * 0.34f, baseY - amp * 0.35f)
                quadraticTo(w * 0.52f, baseY + amp * 0.30f, w * 0.68f, baseY - amp * 0.75f)
                quadraticTo(w * 0.84f, baseY - amp * 1.45f, w, baseY - amp * 0.45f)
                lineTo(w, h)
                close()
            }
            drawPath(path = path, color = color)
        }

        ridge(h * 0.68f, h * 0.24f, ink.copy(alpha = 0.05f))
        ridge(h * 0.82f, h * 0.26f, ink.copy(alpha = 0.09f))
        ridge(h * 0.96f, h * 0.16f, ink.copy(alpha = 0.13f))
        drawCircle(
            color = gold.copy(alpha = 0.20f),
            radius = h * 0.10f,
            center = Offset(w * 0.78f, h * 0.22f),
        )
    }
}

/**
 * 统一错误卡片：朱砂小印 + 文案 + 重试。
 *
 * 签名保持不变；底色从整块 errorContainer 换成「朱砂淡染的玻璃」，
 * 这样错误提示也能浮在山水上而不切开背景。
 */
@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val palette = LocalLumoPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(16.dp), tone = GlassTone.Cinnabar, level = 0.6f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SealBadge(text = "!", color = palette.cinnabar, size = 18.dp)
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (onRetry != null) {
            IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = LumoIcons.Refresh,
                    contentDescription = stringResource(R.string.retry),
                    tint = palette.cinnabar,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * 统一加载态：一枚呼吸的朱砂印，印里三滴墨在动。
 *
 * 替代散落各页的 CircularProgressIndicator —— 转圈是「机器在忙」，
 * 墨滴是「笔正在落」，和这个应用的语气一致。
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val palette = LocalLumoPalette.current
    Column(
        modifier = modifier.padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            BreathingSeal(size = 54.dp) {
                InkDots(color = palette.sealInk, dotSize = 4.dp, gap = 4.dp)
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = LumoType.seal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
