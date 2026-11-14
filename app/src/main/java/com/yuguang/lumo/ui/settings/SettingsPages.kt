package com.yuguang.lumo.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuguang.lumo.ui.prefs.ThemeMode
import com.yuguang.lumo.ui.prefs.UiPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.model.FreeTtsBackend
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.SpeechTier
import com.yuguang.lumo.domain.model.TtsEngine
import com.yuguang.lumo.domain.model.VoiceChoice
import com.yuguang.lumo.ui.localizedModel
import com.yuguang.lumo.ui.model.ModelPickerDialog
import com.yuguang.lumo.ui.components.TierBadge

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
            CircularProgressIndicator(modifier = Modifier.padding(padding).padding(24.dp))
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
                SettingsGroup(stringResource(R.string.free_section_title)) {
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
            SettingsGroup(stringResource(R.string.settings_provider_default_model)) {
                SettingsRow(
                    title = stringResource(R.string.default_model_label),
                    subtitle = "${provider.localizedModel(config.model)} · ${config.model}",
                    onClick = { pickingModel = true },
                )
            }
            SettingsGroup(stringResource(R.string.settings_provider_keys)) {
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
    val container = when {
        selected && tier.free -> MaterialTheme.colorScheme.tertiaryContainer
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = container,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
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
            SettingsGroup(stringResource(R.string.free_section_title)) {
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
            SettingsGroup(stringResource(R.string.settings_voice_reading)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.settings_voice_engine), style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TtsEngine.entries.forEach { engine ->
                            FilterChip(
                                selected = speech.engine == engine,
                                onClick = { viewModel.setEngine(engine) },
                                label = { Text(stringResource(engine.labelRes)) },
                            )
                        }
                    }
                    if (speech.engine == TtsEngine.DOUBAO && speech.tier == SpeechTier.PAID) {
                        Text(stringResource(R.string.settings_voice_speaker), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(VoiceChoice.FEMALE, VoiceChoice.MALE).forEach { voice ->
                                FilterChip(
                                    selected = speech.speaker == voice,
                                    onClick = { viewModel.setSpeaker(voice) },
                                    label = { Text(stringResource(voice.labelRes)) },
                                )
                            }
                        }
                    }
                }
            }
            if (speech.tier == SpeechTier.PAID) {
                SettingsGroup(stringResource(R.string.settings_realtime_voice)) {
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
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            VoiceChoice.entries.forEach { voice ->
                                FilterChip(
                                    selected = speech.liveVoice == voice,
                                    onClick = { viewModel.setLiveVoice(voice) },
                                    label = { Text(stringResource(voice.labelRes)) },
                                )
                            }
                        }
                    }
                }
            } else {
                // 免费档没有密钥可填：说明能力边界，并给一个一键自检。
                SettingsGroup(stringResource(R.string.free_section_title)) {
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
            }
        }
    }
}

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val uiPreferences: UiPreferences,
) : ViewModel() {
    val mode: StateFlow<ThemeMode> = uiPreferences.themeMode

    fun setMode(mode: ThemeMode) {
        uiPreferences.setThemeMode(mode)
    }
}

/** 外观：跟随系统 / 浅色 / 深色，切换立即生效。 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    SettingPageScaffold(
        title = stringResource(R.string.settings_section_appearance),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(stringResource(R.string.settings_theme)) {
                ThemeMode.entries.forEachIndexed { index, item ->
                    SettingsRow(
                        title = stringResource(item.labelRes),
                        value = if (item == mode) stringResource(R.string.settings_theme_current) else null,
                        valueColor = MaterialTheme.colorScheme.primary,
                        onClick = { viewModel.setMode(item) },
                    )
                    if (index < ThemeMode.entries.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

/** 模型目录刷新与会话备份。 */
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

    SettingPageScaffold(
        title = stringResource(R.string.settings_section_data),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(stringResource(R.string.refresh_models)) {
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
                }
            }
            SettingsGroup(stringResource(R.string.backup_title)) {
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
            SettingsGroup(stringResource(R.string.memory_title)) {
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
                SettingsGroup(stringResource(R.string.memory_add)) {
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
                    SettingsGroup(stringResource(R.string.memory_title)) {
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
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {    SettingPageScaffold(
        title = stringResource(R.string.settings_section_about),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(stringResource(R.string.settings_section_about)) {
                SettingsRow(title = stringResource(R.string.settings_version), value = "1.1.0")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    title = stringResource(R.string.settings_about_security),
                    subtitle = stringResource(R.string.settings_about_note),
                    value = stringResource(R.string.settings_status_ready),
                )
            }
            Text(
                stringResource(R.string.settings_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
