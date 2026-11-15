package com.yuguang.lumo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuguang.lumo.BuildConfig
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.SpeechTier
import com.yuguang.lumo.domain.model.TtsEngine
import com.yuguang.lumo.ui.components.AnimatedEntrance
import com.yuguang.lumo.ui.components.GlassTopBar
import com.yuguang.lumo.ui.components.InkDivider
import com.yuguang.lumo.ui.components.SealedSectionTitle
import com.yuguang.lumo.ui.components.glass
import com.yuguang.lumo.ui.theme.LumoIcons
import com.yuguang.lumo.ui.theme.AiColors
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.SceneryCatalog

/**
 * 设置中心：4 组 7 行 + 一条独立的「关于」。
 *
 * 整合原则——同一个目的地的入口只留一条：语音的三项（实时语音 / 音色 / 引擎）都进
 * VoicePrefs，合成一行并把「引擎 · 音色」写在 value 里；用量统计提到标题栏图标；
 * 「刷新模型」下沉到数据页的维护段。每行只留 名字 + 状态值 + 箭头，重复的副标题改由
 * 组标题下的说明行统一交代。
 */
@Composable
fun SettingsHomeScreen(
    onBack: () -> Unit,
    onOpenProvider: (ProviderId) -> Unit,
    onOpenVoicePrefs: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenData: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenAbout: () -> Unit,
    // 统计页路由由 lead 在 AiApp 接线；默认空实现让参数保持向后兼容。
    onOpenUsageStats: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    appearance: AppearanceViewModel = hiltViewModel(),
) {
    val configs = viewModel.configs
    val speech = viewModel.speech
    val themeMode by appearance.mode.collectAsStateWithLifecycle()
    val sceneId by appearance.scene.collectAsStateWithLifecycle()
    // 场景名空 = 随页面，跟着聊天页的画册轮换。
    val sceneName = SceneryCatalog.byId(sceneId)?.let { stringResource(it.titleRes) }
        ?: stringResource(R.string.scene_follow_page)
    val freeCount = viewModel.catalogs.value[ProviderId.FREE]?.size ?: 0
    // 引擎 · 音色：免费链路没有音色可选，只报链路名，不露出一项不生效的设置。
    val voiceValue = when {
        speech.tier == SpeechTier.FREE -> stringResource(R.string.tier_free)
        speech.engine == TtsEngine.DOUBAO ->
            "${stringResource(speech.engine.labelRes)} · ${stringResource(speech.speaker.labelRes)}"

        else -> stringResource(speech.engine.labelRes)
    }
    Scaffold(
        // 透明容器 + 玻璃标题栏：山水背景由 AiApp 统一铺底，这里只负责叠玻璃分组。
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.section_settings),
                onBack = onBack,
                actions = {
                    // 用量统计从主页列表提到标题栏：一个图标入口，省掉一整行。
                    IconButton(onClick = onOpenUsageStats) {
                        Icon(
                            LumoIcons.Chart,
                            contentDescription = stringResource(R.string.stats_entry_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                // 页脚（豆包语音 · 方舟 · DeepSeek）要能从手势条上让开。
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AnimatedEntrance(index = 0) {
                SettingsGroup(
                    title = stringResource(R.string.settings_section_account),
                    mark = "设",
                    // 三行共用一个说明，替掉原来每行重复的「默认模型与 API Key」。
                    note = stringResource(R.string.settings_account_note),
                ) {
                    configs?.get(ProviderId.DEEPSEEK)?.let { config ->
                        SettingsRow(
                            title = stringResource(ProviderId.DEEPSEEK.nameRes),
                            value = stringResource(
                                if (config.apiKey.isNotBlank()) R.string.settings_status_configured
                                else R.string.settings_status_missing,
                            ),
                            valueColor = statusColor(config.apiKey.isNotBlank()),
                            onClick = { onOpenProvider(ProviderId.DEEPSEEK) },
                        )
                        InkDivider(alpha = 0.45f)
                    }
                    configs?.get(ProviderId.DOUBAO)?.let { config ->
                        SettingsRow(
                            title = stringResource(ProviderId.DOUBAO.nameRes),
                            value = stringResource(
                                if (config.apiKey.isNotBlank()) R.string.settings_status_configured
                                else R.string.settings_status_missing,
                            ),
                            valueColor = statusColor(config.apiKey.isNotBlank()),
                            onClick = { onOpenProvider(ProviderId.DOUBAO) },
                        )
                    }
                    InkDivider(alpha = 0.45f)
                    // 免费车道单独一行：不需要 Key，状态显示可用模型数。
                    configs?.get(ProviderId.FREE)?.let { _ ->
                        SettingsRow(
                            title = stringResource(ProviderId.FREE.nameRes),
                            value = if (freeCount > 0) {
                                stringResource(R.string.free_models_available, freeCount)
                            } else {
                                // 目录还没拉回来时不谎报状态，留一个占位符。
                                "—"
                            },
                            valueColor = statusColor(true),
                            onClick = { onOpenProvider(ProviderId.FREE) },
                        )
                    }
                }
            }
            AnimatedEntrance(index = 1) {
                // 实时语音 / 朗读音色 / 朗读引擎三项同去 VoicePrefs：只留一条独立玻璃行，
                // 不再为一行内容占一个分组标题（分组标题只给有 2 行以上的块）。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(shape = RoundedCornerShape(22.dp), level = 0.9f),
                ) {
                    SettingsRow(
                        title = stringResource(R.string.settings_section_voice),
                        value = voiceValue,
                        onClick = onOpenVoicePrefs,
                    )
                }
            }
            AnimatedEntrance(index = 2) {
                SettingsGroup(stringResource(R.string.settings_section_appearance), mark = "山") {
                    // 主题 + 当前场景：两个设置同一页，用 · 拼一行。
                    SettingsRow(
                        title = stringResource(R.string.settings_theme),
                        value = "${stringResource(themeMode.labelRes)} · $sceneName",
                        onClick = onOpenAppearance,
                    )
                }
            }
            AnimatedEntrance(index = 3) {
                SettingsGroup(stringResource(R.string.settings_section_data_backup), mark = "数") {
                    SettingsRow(
                        title = stringResource(R.string.memory_title),
                        value = stringResource(
                            if (viewModel.memoryOn) R.string.settings_status_on
                            else R.string.settings_status_off,
                        ),
                        valueColor = statusColor(viewModel.memoryOn),
                        onClick = onOpenMemory,
                    )
                    InkDivider(alpha = 0.45f)
                    // 备份 / 刷新模型 / 危险操作都在数据页里，这里只留一个入口。
                    SettingsRow(
                        title = stringResource(R.string.settings_row_backup),
                        value = "ZIP",
                        onClick = onOpenData,
                    )
                }
            }
            AnimatedEntrance(index = 4) {
                // 「关于」只剩一个入口，不再占一个分组：独立一条玻璃行，版本号直接给在右侧。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(shape = RoundedCornerShape(22.dp), level = 0.9f),
                ) {
                    SettingsRow(
                        title = stringResource(R.string.settings_section_about),
                        value = BuildConfig.VERSION_NAME,
                        onClick = onOpenAbout,
                    )
                }
            }
            AnimatedEntrance(index = 5) {
                Text(
                    stringResource(R.string.settings_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    mark: String = "印",
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 每个分区的印不同：设 / 听 / 山 / 数，一眼能认出走到哪一块。
        SealedSectionTitle(title, mark = mark)
        // 一块磨砂宣纸：分组的边界靠玻璃厚度与描边，而不是靠实心 surface 把背景切断。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = RoundedCornerShape(22.dp), level = 0.9f),
        ) {
            // 说明行放在卡内：次级小字必须落在 level ≥ 0.8 的磨砂面上才够清楚。
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 10.dp,
                    ),
                )
                InkDivider(alpha = 0.45f)
            }
            content()
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 触控区不小于 48dp：行内只留名字与状态值，靠高度兜住点击命中率。
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            // 长模型名不许撑破行：单行 + 省略号 + 上限 140dp，右侧对齐。
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
        if (onClick != null) {
            Icon(
                LumoIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun statusColor(ok: Boolean): Color = if (ok) {
    AiColors.Success
} else {
    LocalLumoPalette.current.cinnabar
}

@Composable
fun SettingPageScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = snackbarHost,
        topBar = { GlassTopBar(title = title, onBack = onBack) },
        content = content,
    )
}
