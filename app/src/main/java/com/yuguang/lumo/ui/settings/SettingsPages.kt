package com.yuguang.lumo.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuguang.lumo.ui.prefs.ThemeMode
import com.yuguang.lumo.ui.prefs.UiPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import coil.compose.AsyncImage
import com.yuguang.lumo.BuildConfig
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.model.FreeTtsBackend
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.SpeechTier
import com.yuguang.lumo.domain.model.TtsEngine
import com.yuguang.lumo.domain.model.VoiceChoice
import com.yuguang.lumo.ui.localizedModel
import com.yuguang.lumo.ui.model.ModelPickerDialog
import com.yuguang.lumo.ui.components.AnimatedEntrance
import com.yuguang.lumo.ui.components.GlassChip
import com.yuguang.lumo.ui.components.InkDivider
import com.yuguang.lumo.ui.components.LoadingState
import com.yuguang.lumo.ui.components.SealBadge
import com.yuguang.lumo.ui.components.SealedSectionTitle
import com.yuguang.lumo.ui.components.TierBadge
import com.yuguang.lumo.ui.components.glass
import com.yuguang.lumo.ui.theme.AiColors
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LumoType
import com.yuguang.lumo.ui.theme.Scene
import com.yuguang.lumo.ui.theme.SceneryCatalog

/** 单个服务商的接入信息与默认模型。 */
@Composable
fun ProviderSettingsScreen(
    providerId: String,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val provider = ProviderId.entries.firstOrNull { it.name == providerId } ?: ProviderId.DEEPSEEK
    val catalogs by viewModel.catalogs.collectAsStateWithLifecycle()
    val config = viewModel.configs?.get(provider)
    var pickingModel by remember { mutableStateOf(false) }
    var showUrl by remember { mutableStateOf(false) }

    SettingPageScaffold(
        title = stringResource(provider.nameRes),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (config == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingState()
            }
            return@SettingPageScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!provider.requiresKey) {
                // 免密钥车道：说明来源与限制，并把「刷新目录」放最前。
                SettingsGroup(stringResource(R.string.free_section_title), mark = "设") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.free_no_key_needed),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (provider == ProviderId.FREE) {
                            Text(
                                stringResource(R.string.free_train_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                stringResource(R.string.custom_endpoint_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = viewModel::refreshModels,
                            enabled = !viewModel.refreshing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (viewModel.refreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(stringResource(R.string.refresh_models))
                        }
                        if (provider == ProviderId.CUSTOM) {
                            Button(
                                onClick = { viewModel.testConnection(provider) },
                                enabled = config.baseUrl.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.test_connection)) }
                        }
                    }
                }
            }
            SettingsGroup(stringResource(R.string.settings_provider_default_model), mark = "设") {
                SettingsRow(
                    title = stringResource(R.string.default_model_label),
                    subtitle = "${provider.localizedModel(config.model)} · ${config.model}",
                    onClick = { pickingModel = true },
                )
            }
            SettingsGroup(stringResource(R.string.settings_provider_keys), mark = "设") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = config.apiKey,
                        onValueChange = { viewModel.update(config.copy(apiKey = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                stringResource(
                                    if (provider.requiresKey) R.string.api_key else R.string.api_key_optional,
                                ),
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                    TextButton(onClick = { showUrl = !showUrl }) {
                        Text(stringResource(if (showUrl) R.string.hide_endpoint else R.string.show_endpoint))
                    }
                    if (showUrl) {
                        OutlinedTextField(
                            value = config.baseUrl,
                            onValueChange = { viewModel.update(config.copy(baseUrl = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.base_url)) },
                            singleLine = true,
                        )
                    }
                    Button(
                        onClick = { viewModel.save(provider) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.save_provider, stringResource(provider.nameRes)))
                    }
                }
            }
            Text(
                stringResource(R.string.settings_about_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (pickingModel) {
        ModelPickerDialog(
            title = stringResource(R.string.provider_default_model, stringResource(provider.nameRes)),
            providers = listOf(provider),
            selectedProvider = provider,
            selectedId = config?.model.orEmpty(),
            catalogByProvider = mapOf(provider to catalogs[provider].orEmpty()),
            note = stringResource(R.string.default_model_note),
            onSelect = { _, model ->
                config?.let { viewModel.update(it.copy(model = model)) }
                pickingModel = false
            },
            onDismiss = { pickingModel = false },
        )
    }
}

/**
 * 档位选择卡：收费（豆包）与免费链路在这里二选一。
 *
 * 免费卡用 tertiary 色描边 + 「免费」胶囊，和收费卡一眼就能分开；
 * 选中态同时用背景色与描边双重区分，不依赖单一颜色。
 */
@Composable
private fun TierCard(
    tier: SpeechTier,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = if (tier.free) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    // 玻璃卡代替实心 Surface：选中态靠「描边 + 淡色底」双重表达，叠在山水上仍能一眼分辨。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = shape, level = if (selected) 1f else 0.7f)
            .background(
                color = if (selected) accent.copy(alpha = 0.10f) else Color.Transparent,
                shape = shape,
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else LocalLumoPalette.current.glassBorder,
                shape = shape,
            )
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(tier.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                )
                if (tier.free) {
                    Spacer(Modifier.width(6.dp))
                    TierBadge(tier = tier)
                }
            }
            Text(
                stringResource(
                    if (tier.free) R.string.tier_free_subtitle else R.string.tier_paid_subtitle,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 两段式分段控件：一条玻璃胶囊装下所有选项，选中段用朱砂淡淡铺一层。
 * 用于「引擎」这类只有两档、且选项文字都短的设置，避免每个选项占一行。
 */
@Composable
fun SegmentPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(shape = shape, level = 0.9f)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .then(
                        if (selected) {
                            Modifier.background(LocalLumoPalette.current.cinnabar.copy(alpha = 0.16f))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        LocalLumoPalette.current.cinnabar
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** 朗读引擎、音色与实时语音凭证。 */
@Composable
fun VoicePrefsScreen(    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val speech = viewModel.speech
    val config = viewModel.configs?.get(ProviderId.DOUBAO)

    SettingPageScaffold(
        title = stringResource(R.string.settings_section_voice),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 档位卡放在最前：这是「收费 / 免费」两套链路的唯一开关。
            SettingsGroup(stringResource(R.string.free_section_title), mark = "设") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpeechTier.entries.forEach { tier ->
                        TierCard(
                            tier = tier,
                            selected = speech.tier == tier,
                            onClick = { viewModel.setTier(tier) },
                        )
                    }
                    if (speech.tier == SpeechTier.FREE) {
                        Text(
                            stringResource(R.string.tier_free_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.free_backend_label),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FreeTtsBackend.entries.forEach { backend ->
                                FilterChip(
                                    selected = speech.freeTtsBackend == backend,
                                    onClick = { viewModel.setFreeTtsBackend(backend) },
                                    label = { Text(stringResource(backend.labelRes)) },
                                )
                            }
                        }
                    }
                }
            }
            SettingsGroup(stringResource(R.string.settings_voice_reading), mark = "听") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.settings_voice_engine), style = MaterialTheme.typography.labelLarge)
                    // 引擎只有两档：一行分段控件收官，不再每个选项占一行。
                    SegmentPicker(
                        options = TtsEngine.entries.map { stringResource(it.labelRes) },
                        selectedIndex = TtsEngine.entries.indexOf(speech.engine),
                        onSelect = { index -> viewModel.setEngine(TtsEngine.entries[index]) },
                    )
                    InkDivider(alpha = 0.45f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.auto_read_title),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                stringResource(R.string.auto_read_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = speech.autoRead, onCheckedChange = viewModel::setAutoRead)
                    }
                    InkDivider(alpha = 0.45f)
                    // 自动标题属于「生成行为」而不是语音能力，但和边生成边朗读同属省心开关，放同一组。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.stats_auto_title),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                stringResource(R.string.stats_auto_title_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = viewModel.autoTitleEnabled,
                            onCheckedChange = viewModel::setAutoTitle,
                        )
                    }
                    if (speech.engine == TtsEngine.DOUBAO && speech.tier == SpeechTier.PAID) {
                        Text(stringResource(R.string.settings_voice_speaker), style = MaterialTheme.typography.labelLarge)
                        // 音色是二选一：一行 GlassChip 就够，不用一个选项一行。
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(VoiceChoice.FEMALE, VoiceChoice.MALE).forEach { voice ->
                                GlassChip(
                                    text = stringResource(voice.labelRes),
                                    selected = speech.speaker == voice,
                                    onClick = { viewModel.setSpeaker(voice) },
                                )
                            }
                        }
                    }
                }
            }
            if (speech.tier == SpeechTier.PAID) {
                SettingsGroup(stringResource(R.string.settings_realtime_voice), mark = "听") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_realtime_keys_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (config == null) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            OutlinedTextField(
                                value = config.speechApiKey,
                                onValueChange = { viewModel.update(config.copy(speechApiKey = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.speech_api_key)) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                supportingText = { Text(stringResource(R.string.speech_api_key_help)) },
                            )
                            OutlinedTextField(
                                value = config.speechAppId,
                                onValueChange = { viewModel.update(config.copy(speechAppId = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.speech_app_id)) },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = config.speechAccessToken,
                                onValueChange = { viewModel.update(config.copy(speechAccessToken = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.speech_access_token)) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                supportingText = { Text(stringResource(R.string.speech_legacy_help)) },
                            )
                            Button(
                                onClick = { viewModel.save(ProviderId.DOUBAO) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.save_provider, stringResource(ProviderId.DOUBAO.nameRes)))
                            }
                        }
                        Text(stringResource(R.string.settings_live_voice), style = MaterialTheme.typography.labelLarge)
                        // 实时语音音色（女声 / 男声 / 复刻原声）也收成一行。
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VoiceChoice.entries.forEach { voice ->
                                GlassChip(
                                    text = stringResource(voice.labelRes),
                                    selected = speech.liveVoice == voice,
                                    onClick = { viewModel.setLiveVoice(voice) },
                                )
                            }
                        }
                    }
                }
            } else {
                // 免费档没有密钥可填：说明能力边界，并给一个一键自检。
                SettingsGroup(stringResource(R.string.free_section_title), mark = "设") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.free_no_key_needed),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.free_voice_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.free_asr_experimental),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.free_quota_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = viewModel::runSelfTest,
                            enabled = !viewModel.selfTesting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (viewModel.selfTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(
                                stringResource(
                                    if (viewModel.selfTesting) R.string.free_self_test_running
                                    else R.string.free_self_test,
                                ),
                            )
                        }
                        viewModel.selfTestResults.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    if (item.ok) "✓" else "✕",
                                    color = statusColor(item.ok),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    item.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.ok) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                            }
                        }
                    }
                }
                // 模型健康（冷却中的免费模型 / 一键重置）搬到数据页的维护段：
                // 它修的是模型可用性，属于维护，不属于语音设置。
            }
        }
    }
}

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val uiPreferences: UiPreferences,
) : ViewModel() {
    val mode: StateFlow<ThemeMode> = uiPreferences.themeMode

    /** null = 随页面；否则是手选场景的 id。 */
    val scene: StateFlow<String?> = uiPreferences.scene
    val dailyScene: StateFlow<Boolean> = uiPreferences.dailyScene
    val motion: StateFlow<Boolean> = uiPreferences.motion

    fun setMode(mode: ThemeMode) {
        uiPreferences.setThemeMode(mode)
    }

    fun setScene(id: String?) = uiPreferences.setScene(id)

    fun setDailyScene(enabled: Boolean) = uiPreferences.setDailyScene(enabled)

    fun setMotion(enabled: Boolean) = uiPreferences.setMotion(enabled)
}

/** 外观：山水画廊（场景 / 行云流水 / 每日换景）+ 明暗三段，改动即时生效。 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val pickedSceneId by viewModel.scene.collectAsStateWithLifecycle()
    val dailyScene by viewModel.dailyScene.collectAsStateWithLifecycle()
    val motion by viewModel.motion.collectAsStateWithLifecycle()
    val picked = SceneryCatalog.byId(pickedSceneId)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    SettingPageScaffold(
        title = stringResource(R.string.settings_section_appearance),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SealedSectionTitle(stringResource(R.string.appearance_scene_title), mark = "山")
                Text(
                    stringResource(R.string.appearance_scene_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                // 「随页面」置顶：不手选时，每页用自己的场景（会话=云、翻译=风竹、语音=瀑…）。
                FollowPageRow(
                    selected = picked == null,
                    onClick = { viewModel.setScene(null) },
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    items(SceneryCatalog.all, key = { it.id }) { scene ->
                        SceneCard(
                            scene = scene,
                            selected = picked?.id == scene.id,
                            dark = dark,
                            onClick = { viewModel.setScene(scene.id) },
                        )
                    }
                }
            }
            // 两个动效开关：都是「当场生效」，所以放在场景下面，改完立刻能看到画面变化。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(shape = RoundedCornerShape(22.dp), level = 0.9f),
            ) {
                ToggleRow(
                    title = stringResource(R.string.appearance_motion),
                    desc = stringResource(R.string.appearance_motion_desc),
                    checked = motion,
                    onCheckedChange = viewModel::setMotion,
                )
                InkDivider(alpha = 0.45f)
                ToggleRow(
                    title = stringResource(R.string.appearance_daily),
                    desc = stringResource(R.string.appearance_daily_desc),
                    checked = dailyScene,
                    onCheckedChange = viewModel::setDailyScene,
                )
            }
            SettingsGroup(stringResource(R.string.settings_theme), mark = "山") {
                ThemeMode.entries.forEachIndexed { index, item ->
                    SettingsRow(
                        title = stringResource(item.labelRes),
                        value = if (item == mode) stringResource(R.string.settings_theme_current) else null,
                        valueColor = MaterialTheme.colorScheme.primary,
                        onClick = { viewModel.setMode(item) },
                    )
                    if (index < ThemeMode.entries.lastIndex) {
                        InkDivider(alpha = 0.45f)
                    }
                }
            }
            Text(
                stringResource(R.string.settings_theme_instant),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** 一张山水卡：120×200dp，按主题取昼/夜资源，选中态是朱砂描边 + 右上角印。 */
@Composable
private fun SceneCard(
    scene: Scene,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalLumoPalette.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(200.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) palette.cinnabar else palette.glassBorder,
                shape = shape,
            ),
    ) {
        AsyncImage(
            model = if (dark) scene.nightRes else scene.dayRes,
            contentDescription = stringResource(scene.titleRes),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 底部只留一层很淡的收边：题款自己带纸片，不靠压黑。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.62f to palette.scrimBottom.copy(alpha = 0.10f),
                        1f to palette.scrimBottom.copy(alpha = 0.34f),
                    ),
                ),
        )
        // 题款做成内嵌小纸片：山水有深有浅、有昼有夜，白字压图必然有一半读不出来；
        // 纸片自带底色，墨色题款在任何一张图上都成立。
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.paper.copy(alpha = 0.82f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(scene.titleRes),
                style = LumoType.poem.copy(fontSize = 13.sp, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (selected) {
            SealBadge(
                text = "选",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                size = 20.dp,
            )
        }
    }
}

/** 「随页面」：不手选场景时的默认项。 */
@Composable
private fun FollowPageRow(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalLumoPalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = shape, level = if (selected) 1f else 0.85f)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) palette.cinnabar else palette.glassBorder,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 区块顶部已经有一句说明，这行不再重复；只留标题 + 右侧「当前」。
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.scene_follow_page),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (selected) {
            Text(
                stringResource(R.string.settings_theme_current),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 开关行：整行可点，右手边的 Switch 只是指示器。 */
@Composable
private fun ToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            // 轨道用固定石青：深色主题里 colorScheme.primary 会偏薄荷，和山水不对味。
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFFF6E9),
                checkedTrackColor = Color(0xFF4E8791),
                checkedBorderColor = Color(0xFF4E8791),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            ),
        )
    }
}


/** 数据页：备份 / 模型维护 两段常规操作，危险操作单独一段朱砂描边 + 二次确认。 */
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importBackup(uri) }
    val memories = viewModel.memories
    var confirmClear by remember { mutableStateOf(false) }

    SettingPageScaffold(
        title = stringResource(R.string.settings_section_data),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedEntrance(index = 0) {
                SettingsGroup(stringResource(R.string.backup_title), mark = "备") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.backup_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { exportLauncher.launch("chat-backup.zip") },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.backup_export)) }
                            Button(
                                onClick = { importLauncher.launch(arrayOf("application/zip")) },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.backup_import)) }
                        }
                    }
                }
            }
            AnimatedEntrance(index = 1) {
                SettingsGroup(stringResource(R.string.settings_data_section_maintenance), mark = "修") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.refresh_models_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = viewModel::refreshModels,
                            enabled = !viewModel.refreshing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (viewModel.refreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp).size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(stringResource(R.string.refresh_models))
                        }
                        InkDivider(alpha = 0.45f)
                        // 免费链路的模型健康从语音页搬来：冷却中的模型在这里看、在这里清。
                        Text(
                            stringResource(R.string.free_health_title),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (viewModel.modelFailures.isEmpty()) {
                            Text(
                                stringResource(R.string.free_health_none),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            viewModel.modelFailures.forEach { failure ->
                                Text(
                                    stringResource(
                                        R.string.free_health_item,
                                        com.yuguang.lumo.data.shortModelLabel(failure.model),
                                        failure.reason.ifBlank {
                                            stringResource(R.string.free_health_cooldown)
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = viewModel::resetModelHealth) {
                                Text(stringResource(R.string.free_health_reset))
                            }
                        }
                        Text(
                            stringResource(R.string.free_health_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            AnimatedEntrance(index = 2) {
                DangerSection(
                    memoryCount = memories.size,
                    onRequestClear = { confirmClear = true },
                )
            }
        }
    }
    if (confirmClear) {
        // 二次确认：破坏性动作不接受一次点击，正文写明「不可恢复」。
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_danger_clear_memory)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_danger_clear_memory_message,
                        memories.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clearAllMemories()
                    },
                ) {
                    Text(
                        stringResource(R.string.settings_danger_confirm),
                        color = LocalLumoPalette.current.cinnabar,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * 危险段：单独一段、朱砂描边，和上面的常规操作在视觉上分开。
 * 说明小字仍落在 0.9 的玻璃面上。
 */
@Composable
private fun DangerSection(
    memoryCount: Int,
    onRequestClear: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SealedSectionTitle(stringResource(R.string.settings_danger_section), mark = "危")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = shape, level = 0.9f)
                .border(
                    width = 1.dp,
                    color = LocalLumoPalette.current.cinnabar.copy(alpha = 0.55f),
                    shape = shape,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = memoryCount > 0, onClick = onRequestClear)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.settings_danger_clear_memory),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalLumoPalette.current.cinnabar,
                    )
                    Text(
                        stringResource(R.string.settings_danger_clear_memory_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.settings_memory_count, memoryCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 长期记忆：总开关 + 条目列表。默认关闭。 */
@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    var draft by remember { mutableStateOf("") }
    SettingPageScaffold(
        title = stringResource(R.string.memory_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(stringResource(R.string.memory_title), mark = "数") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.memory_enable), style = MaterialTheme.typography.bodyLarge)
                        }
                        androidx.compose.material3.Switch(
                            checked = viewModel.memoryOn,
                            onCheckedChange = { viewModel.setMemoryEnabled(it) },
                        )
                    }
                    Text(
                        stringResource(R.string.memory_enable_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (viewModel.memoryOn) {
                SettingsGroup(stringResource(R.string.memory_add), mark = "数") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.memory_add_hint)) },
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                viewModel.addMemory(draft)
                                draft = ""
                            },
                            enabled = draft.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.memory_add)) }
                    }
                }
                val memories = viewModel.memories
                if (memories.isEmpty()) {
                    Text(
                        stringResource(R.string.memory_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                } else {
                    SettingsGroup(stringResource(R.string.memory_title), mark = "数") {
                        memories.forEachIndexed { index, memory ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(memory.content, style = MaterialTheme.typography.bodyMedium)
                                }
                                androidx.compose.material3.Switch(
                                    checked = memory.enabled,
                                    onCheckedChange = { viewModel.toggleMemory(memory.id, it) },
                                )
                                TextButton(onClick = { viewModel.deleteMemory(memory.id) }) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                            if (index < memories.lastIndex) {
                                InkDivider(alpha = 0.45f)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 关于：版本 / 隐私与安全 / 素材出处 / 开源许可，一屏四行，不再重复。 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    SettingPageScaffold(
        title = stringResource(R.string.settings_section_about),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedEntrance(index = 0) {
                SettingsGroup(stringResource(R.string.settings_section_about)) {
                    SettingsRow(
                        title = stringResource(R.string.settings_version),
                        value = BuildConfig.VERSION_NAME,
                    )
                    InkDivider(alpha = 0.45f)
                    SettingsRow(
                        title = stringResource(R.string.settings_about_privacy),
                        subtitle = stringResource(R.string.settings_about_note),
                        value = stringResource(R.string.settings_status_ready),
                    )
                    InkDivider(alpha = 0.45f)
                    // 素材署名从页脚那块独立玻璃搬进来：原来同一句出处写了两遍。
                    SettingsRow(
                        title = stringResource(R.string.settings_about_credits),
                        subtitle = stringResource(R.string.credits_source),
                    )
                    InkDivider(alpha = 0.45f)
                    SettingsRow(
                        title = stringResource(R.string.settings_about_license),
                        value = stringResource(R.string.settings_about_license_value),
                    )
                }
            }
            AnimatedEntrance(index = 1) {
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
