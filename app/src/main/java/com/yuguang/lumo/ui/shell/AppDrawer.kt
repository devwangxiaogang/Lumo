package com.yuguang.lumo.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yuguang.lumo.R
import com.yuguang.lumo.ui.components.GlassTone
import com.yuguang.lumo.ui.components.GlassTopBar
import com.yuguang.lumo.ui.components.glass
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LumoIcons
import com.yuguang.lumo.ui.theme.LumoType

enum class AppSection(@StringRes val labelRes: Int, val icon: ImageVector) {
    Chat(R.string.section_chat, LumoIcons.Chat),
    Translate(R.string.section_translate, LumoIcons.Translate),
    VoiceChat(R.string.section_voice, LumoIcons.Voice),
    Settings(R.string.section_settings, LumoIcons.Settings),
}

/**
 * 主界面骨架：玻璃标题栏 + 右上角「我」入口（设置中心）。
 * 一级导航在底部药丸里，抽屉不再使用。
 *
 * 容器透明：整页的山水背景由 `AiApp` 统一挂载，页面只负责「浮在山水上的玻璃层」，
 * 所以这里不能再铺一层 Material surface，否则会把背景切出硬边。
 */
@Composable
fun SectionScaffold(
    section: AppSection,
    title: String,
    onNavigate: (AppSection) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        topBar = {
            GlassTopBar(
                title = title,
                actions = {
                    actions()
                    if (section != AppSection.Settings) {
                        ProfileDot(onClick = { onNavigate(AppSection.Settings) })
                    }
                },
            )
        },
        content = content,
    )
}

/**
 * 「我」：一枚玻璃圆点，里面是朱砂题字。
 *
 * 原来用 primaryContainer 的实心圆，在一整片山水上像贴纸；玻璃圆点 + 朱砂字保持同样的
 * 可点面积（40dp）与读屏语义（内容描述就是「我」）。
 */
@Composable
private fun ProfileDot(onClick: () -> Unit) {
    val palette = LocalLumoPalette.current
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .glass(shape = CircleShape, tone = GlassTone.Paper, level = 0.8f)
                .border(1.dp, palette.cinnabar.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.section_profile),
                style = LumoType.seal,
                color = palette.cinnabar,
                maxLines = 1,
            )
        }
    }
}
