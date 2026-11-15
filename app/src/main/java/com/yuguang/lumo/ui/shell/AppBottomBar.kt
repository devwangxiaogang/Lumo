package com.yuguang.lumo.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuguang.lumo.R
import com.yuguang.lumo.ui.components.glass
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LocalMotionEnabled
import com.yuguang.lumo.ui.theme.LumoIcons
import com.yuguang.lumo.ui.theme.LumoMotion
import com.yuguang.lumo.ui.theme.LumoType

/**
 * 悬浮玻璃药丸导航：对话 / 翻译 / 语音。
 *
 * 三个语义层都保留：图标形状（墨线）、8sp 题款标签、选中态的「石青淡底 + 朱砂墨点」。
 * 药丸浮在山水之上（16dp 外边距 + 导航栏避让），不再用整条不透明 Surface 把画面切出硬边。
 *
 * [dark] 保留是为了兼容语音页的深色场景：给了深色语境时药丸再压一层墨色，
 * 免得浅色纸玻璃在夜色场景里「发亮」。
 */
@Composable
fun AppBottomBar(
    selected: AppSection,
    dark: Boolean = false,
    onSelect: (AppSection) -> Unit,
) {
    val palette = LocalLumoPalette.current
    val scheme = MaterialTheme.colorScheme
    val items = listOf(
        Triple(AppSection.Chat, LumoIcons.Chat, R.string.nav_chat),
        Triple(AppSection.Translate, LumoIcons.Translate, R.string.nav_translate),
        Triple(AppSection.VoiceChat, LumoIcons.Voice, R.string.nav_voice),
    )
    val shape = RoundedCornerShape(28.dp)
    val veil = if (dark) palette.paper.copy(alpha = 0.30f) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = shape, level = 1f)
                .background(veil, shape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (section, icon, labelRes) ->
                NavPill(
                    icon = icon,
                    label = stringResource(labelRes),
                    active = section == selected,
                    activeTint = scheme.primary,
                    inactiveTint = scheme.onSurfaceVariant,
                    onClick = { if (section != selected) onSelect(section) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 单个导航项：石青淡底 + 1.12 放大 + 底部朱砂墨点。
 *
 * 图标与标签始终存在，选中只改变「浓度」，不改变排版，所以切换时不会有跳动；
 * contentDescription 用标签文案，读屏与可见文字一致。
 */
@Composable
private fun NavPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeTint: Color,
    inactiveTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionEnabled.current
    val palette = LocalLumoPalette.current
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (active) 1.12f else 1f,
        animationSpec = tween(
            durationMillis = LumoMotion.duration(LumoMotion.navEnterMillis, motion),
            easing = LumoMotion.ease,
        ),
        label = "navScale",
    )
    val dot by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = LumoMotion.duration(LumoMotion.navExitMillis, motion),
            easing = LumoMotion.ease,
        ),
        label = "navDot",
    )
    val tint = if (active) activeTint else inactiveTint

    Column(
        modifier = modifier
            .clickable(role = Role.Tab, onClick = onClick)
            .background(
                color = if (active) scheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        Text(
            text = label,
            style = LumoType.seal.copy(fontSize = 8.sp, lineHeight = 10.sp, letterSpacing = 1.sp),
            color = tint,
            maxLines = 1,
        )
        // 朱砂墨点：选中才有，高度恒定，避免药丸高度随选择跳动。
        Box(
            modifier = Modifier
                .size(3.dp)
                .graphicsLayer { alpha = dot }
                .background(palette.cinnabar, CircleShape),
        )
    }
}
