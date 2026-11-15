package com.yuguang.lumo.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yuguang.lumo.R
import com.yuguang.lumo.data.ChatExporter
import com.yuguang.lumo.data.local.ChatMediaStore
import com.yuguang.lumo.domain.ChatCommand
import com.yuguang.lumo.domain.model.ChatMessage
import com.yuguang.lumo.domain.model.Conversation
import com.yuguang.lumo.domain.model.MessageStatus
import com.yuguang.lumo.domain.model.Role
import com.yuguang.lumo.ui.localizedSummary
import com.yuguang.lumo.ui.model.ModelPickerDialog
import com.yuguang.lumo.ui.shell.AppSection
import com.yuguang.lumo.ui.components.TierBadge
import com.yuguang.lumo.domain.model.SpeechTier
import com.yuguang.lumo.domain.model.ProviderId
import kotlinx.coroutines.launch
import java.io.File
import android.content.ClipData
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.yuguang.lumo.ui.backdrop.BackdropHost
import com.yuguang.lumo.ui.components.GlassChip
import com.yuguang.lumo.ui.components.GlassTone
import com.yuguang.lumo.ui.components.GlassTopBar
import com.yuguang.lumo.ui.components.InkDots
import com.yuguang.lumo.ui.components.InkRipple
import com.yuguang.lumo.ui.components.PoemLine
import com.yuguang.lumo.ui.components.PressScale
import com.yuguang.lumo.ui.components.SealBadge
import com.yuguang.lumo.ui.components.glass
import com.yuguang.lumo.ui.theme.LocalSkin
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LumoIcons
import com.yuguang.lumo.ui.theme.Skin
import com.yuguang.lumo.ui.theme.LumoType
import com.yuguang.lumo.ui.theme.SceneryCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSection: (AppSection) -> Unit = {},
    onOpenConversation: (Long) -> Unit = {},
    onSeedTranslate: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val copyLabel = stringResource(R.string.copy_message)
    var showModels by remember { mutableStateOf(false) }
    var showPersona by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }
    var showCommands by remember { mutableStateOf(false) }
    // 1.3 分支：被编辑的用户消息（弹窗预填原文）。
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    // 无参指令（/voice、/new、/help）点选后直接执行，带参指令只回填模板等用户补全。
    val applyCommand: (String) -> Unit = { template ->
        viewModel.updateInput(template)
        if (!template.endsWith(" ")) viewModel.submit()
    }
    var exportFormat by remember { mutableStateOf(ChatExporter.Format.MARKDOWN) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var voiceMode by remember { mutableStateOf(false) }
    var voicePhase by remember { mutableStateOf(VoicePhase.Idle) }
    // 写作态：把输入框拉高到半屏，长文本一眼能看全，收起后回到常驻的三行高度。
    var composerExpanded by remember { mutableStateOf(false) }
    val speech = remember {
        SpeechInput(
            context = context,
            onText = { spoken ->
                viewModel.updateInput(spoken)
                voiceMode = false
                voicePhase = VoicePhase.Idle
            },
            onPhase = { phase ->
                voicePhase = phase
                if (phase == VoicePhase.Recognizing) voiceMode = false
            },
            onError = { message ->
                voicePhase = VoicePhase.Idle
                scope.launch { snackbar.showSnackbar(message) }
            },
            recognize = viewModel::recognize,
        )
    }
    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voiceMode = true
        } else {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.mic_for_chat)) }
        }
    }
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ChatMediaStore.MAX_ATTACHMENTS),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }
    // 导出：CreateDocument 让用户在系统文件选择器里挑位置与文件名。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(exportFormat.mime),
    ) { uri ->
        if (uri != null) viewModel.export(uri, exportFormat)
    }
    val conversationTitle = state.conversation?.title?.takeIf { it.isNotBlank() } ?: "chat"
    DisposableEffect(speech) {
        onDispose { speech.destroy() }
    }
    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.speakPrompts.collect { prompt ->
            val result = snackbar.showSnackbar(prompt.message, actionLabel = context.getString(R.string.use_local_once))
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.speakLocal(prompt.messageId, prompt.content)
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.nav.collect { event ->
            when (event) {
                is ChatNav.Translate -> onSeedTranslate(event.text)
                is ChatNav.OpenConversation -> onOpenConversation(event.id)
                ChatNav.Voice -> onOpenSection(AppSection.VoiceChat)
                ChatNav.Help -> showCommands = true
                ChatNav.PickModel -> showModels = true
            }
        }
    }
    val tail = state.messages.lastOrNull()
    var previousCount by remember { mutableStateOf(0) }
    LaunchedEffect(state.messages.size, tail?.content?.length, tail?.reasoning?.length, tail?.attachments?.size) {
        val grew = state.messages.size > previousCount
        previousCount = state.messages.size
        if (state.messages.isEmpty()) return@LaunchedEffect
        if (grew || !listState.canScrollBackward) {
            listState.animateScrollToItem(0)
        }
    }

    BackdropHost(scene = SceneryCatalog.forRoute("chat", LocalSkin.current)) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            val conversation = state.conversation
            val palette = LocalLumoPalette.current
            GlassTopBar(
                // GlassTopBar 只收字符串标题：模型 chip 因此挪进 actions，做了印面样式。
                title = conversation?.title?.takeIf { it.isNotBlank() }
                    ?: stringResource(if (conversation == null) R.string.chat_fallback else R.string.new_chat),
                onBack = onBack,
                actions = {
                    if (conversation != null) {
                        // 模型 chip：朱砂淡底 + 印面小字，读起来像盖在页眉上的一方闲章。
                        Row(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.cinnabar.copy(alpha = 0.12f))
                                .clickable { showModels = true }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                conversation.provider.localizedSummary(conversation.model),
                                style = LumoType.seal.copy(fontSize = 11.sp, letterSpacing = 0.5.sp),
                                color = palette.cinnabar,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 132.dp),
                            )
                            Icon(
                                LumoIcons.ChevronDown,
                                contentDescription = stringResource(R.string.change_model),
                                tint = palette.cinnabar,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        if (conversation.provider == ProviderId.FREE) {
                            Spacer(Modifier.width(4.dp))
                            TierBadge(tier = SpeechTier.FREE)
                        }
                    }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(LumoIcons.More, contentDescription = stringResource(R.string.more_actions))
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.persona_title)) },
                                leadingIcon = { Icon(LumoIcons.Person, contentDescription = null) },
                                onClick = {
                                    showMore = false
                                    showPersona = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.section_settings)) },
                                leadingIcon = { Icon(LumoIcons.Settings, contentDescription = null) },
                                onClick = {
                                    showMore = false
                                    onOpenSettings()
                                },
                            )
                            // 深度思考只对免费车道显示：豆包侧思考由模型默认行为决定。
                            if (state.conversation?.provider == ProviderId.FREE) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.thinking_toggle)) },
                                    leadingIcon = {
                                        Icon(
                                            if (state.conversation?.thinking == true) {
                                                LumoIcons.CheckOn
                                            } else {
                                                LumoIcons.CheckOff
                                            },
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        showMore = false
                                        viewModel.toggleThinking()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.usage_title)) },
                                leadingIcon = { Icon(LumoIcons.Chart, contentDescription = null) },
                                onClick = {
                                    showMore = false
                                    showUsage = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.auto_read_title)) },
                                leadingIcon = {
                                    Icon(
                                        if (state.autoRead) LumoIcons.CheckOn else LumoIcons.CheckOff,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showMore = false
                                    viewModel.setAutoRead(!state.autoRead)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_markdown)) },
                                leadingIcon = { Icon(LumoIcons.Doc, contentDescription = null) },
                                onClick = {
                                    showMore = false
                                    exportFormat = ChatExporter.Format.MARKDOWN
                                    exportLauncher.launch("$conversationTitle.md")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_html)) },
                                leadingIcon = { Icon(LumoIcons.Code, contentDescription = null) },
                                onClick = {
                                    showMore = false
                                    exportFormat = ChatExporter.Format.HTML
                                    exportLauncher.launch("$conversationTitle.html")
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                val tailDone = state.messages.lastOrNull()?.status == MessageStatus.DONE
                // 键盘弹起时 bottomBar 可用高度被压缩，快捷 chip 会被挤成没有字的玻璃细条：
                // 这里让它在键盘态整行淡出，把注意力留给输入本身。
                val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                AnimatedVisibility(
                    visible = !composerExpanded && !imeVisible && state.messages.isNotEmpty() &&
                        !state.generating && tailDone,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    QuickPrompts(onPick = { viewModel.applySuggestion(it, false) })
                }
                if (!composerExpanded && !state.generating && ChatCommand.looksLikeCommand(state.input)) {
                    CommandPalette(query = state.input, onPick = { template -> applyCommand(template) })
                }
                ChatComposer(
                    input = state.input,
                    pendingImages = state.pendingImages,
                    drawMode = state.drawMode,
                    generating = state.generating,
                    voiceMode = voiceMode,
                    voicePhase = voicePhase,
                    onInput = viewModel::updateInput,
                    onSend = {
                        viewModel.submit()
                        // 发送后收回常态，把屏幕还给消息区。
                        composerExpanded = false
                    },
                    onStopGeneration = viewModel::stop,
                    onShowCommands = { showCommands = true },
                    onToggleDrawMode = viewModel::toggleDrawMode,
                    onPickImages = {
                        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemoveImage = viewModel::removeImage,
                    onToggleVoiceMode = {
                        if (voiceMode) {
                            if (voicePhase != VoicePhase.Idle) speech.destroy()
                            voicePhase = VoicePhase.Idle
                            voiceMode = false
                        } else if (
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            voiceMode = true
                        } else {
                            recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onHoldStart = { speech.start(viewModel.state.value.input) },
                    onHoldEnd = { speech.stop() },
                    expanded = composerExpanded,
                    onToggleExpand = { composerExpanded = !composerExpanded },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.messages.isEmpty()) {
                WelcomePane(
                    persona = state.conversation?.systemPrompt.orEmpty(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding),
                    onSuggest = viewModel::applySuggestion,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.messages.asReversed(), key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            speaking = state.speakingId == message.id,
                            branch = state.branches[message.id],
                            onBranchPrev = { id -> viewModel.switchBranch(id) },
                            onBranchNext = { id -> viewModel.switchBranch(id) },
                            /** 用户消息可以「编辑并分支」；生成中禁用，避免和当前任务打架。 */
                            onEditBranch = if (
                                message.role == Role.USER &&
                                message.status == MessageStatus.DONE &&
                                !state.generating
                            ) {
                                { editing = message }
                            } else {
                                null
                            },
                            onRetry = viewModel::retry,
                            onSpeak = { viewModel.toggleSpeak(message) },
                            onCopy = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipData.newPlainText(copyLabel, message.content).toClipEntry(),
                                    )
                                }
                            },
                            onPreview = { previewPath = it },
                        )
                    }
                }
                if (listState.firstVisibleItemIndex > 1) {
                    FilledTonalIconButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                    ) {
                        Icon(
                            LumoIcons.ChevronDown,
                            contentDescription = stringResource(R.string.scroll_to_bottom),
                        )
                    }
                }
            }
        }
    }
    }

    val conversation = state.conversation
    if (showModels && conversation != null) {
        ModelPickerDialog(
            title = stringResource(R.string.change_provider_model, stringResource(conversation.provider.nameRes)),
            providers = listOf(conversation.provider),
            selectedProvider = conversation.provider,
            selectedId = conversation.model,
            catalogByProvider = mapOf(conversation.provider to state.catalog),
            note = stringResource(R.string.change_model_note),
            onSelect = { _, model ->
                viewModel.selectModel(model)
                showModels = false
            },
            onDismiss = { showModels = false },
        )
    }
    if (showPersona && conversation != null) {
        PersonaSheet(
            current = conversation.systemPrompt,
            onSave = {
                viewModel.updatePersona(it)
                showPersona = false
            },
            onDismiss = { showPersona = false },
        )
    }
    previewPath?.let { path ->
        ImagePreviewDialog(path = path, onDismiss = { previewPath = null })
    }
    if (showCommands) {
        CommandSheet(
            onPick = { template -> applyCommand(template) },
            onDismiss = { showCommands = false },
        )
    }
    editing?.let { target ->
        var draft by remember(target.id) { mutableStateOf(target.content) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.branch_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.branch_edit_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = draft
                        editing = null
                        viewModel.editAndSend(target.id, text)
                    },
                    enabled = draft.isNotBlank(),
                ) { Text(stringResource(R.string.branch_edit_send)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showUsage) {
        UsageSheet(
            conversation = conversation,
            messages = state.messages,
            onDismiss = { showUsage = false },
        )
    }
}

/**
 * 用量面板：按模型分组统计 token。
 *
 * 上游没给 usage 时显示「—」而不是 0，避免让人误以为「免费所以不计数」。
 */
@Composable
private fun UsageSheet(
    conversation: Conversation?,
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
) {
    val answered = messages.filter { it.role == Role.ASSISTANT && it.status == MessageStatus.DONE }
    val prompt = answered.sumOf { it.promptTokens }
    val completion = answered.sumOf { it.completionTokens }
    val free = conversation?.provider == ProviderId.FREE
    val estimated = if (free) 0.0 else estimateCost(conversation?.provider, conversation?.model, prompt, completion)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.usage_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                UsageRow(stringResource(R.string.usage_rounds), answered.size.toString())
                UsageRow(stringResource(R.string.usage_prompt), if (prompt > 0) prompt.toString() else "—")
                UsageRow(stringResource(R.string.usage_completion), if (completion > 0) completion.toString() else "—")
                UsageRow(
                    stringResource(R.string.usage_total),
                    if (prompt + completion > 0) (prompt + completion).toString() else "—",
                )
                UsageRow(
                    stringResource(R.string.usage_cost),
                    if (prompt + completion > 0) formatCost(estimated) else "—",
                )
                if (free) {
                    Text(
                        stringResource(R.string.free_train_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun UsageRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun WelcomePane(
    persona: String,
    modifier: Modifier = Modifier,
    onSuggest: (String, Boolean) -> Unit,
) {
    val summary = persona.lineSequence().firstOrNull { it.isNotBlank() }?.take(48).orEmpty()
    val explain = stringResource(R.string.suggest_explain)
    val code = stringResource(R.string.suggest_code)
    val draw = stringResource(R.string.suggest_draw)
    // 键盘弹起后内容区只剩 300dp 上下：这时先保住诗与标题，建议 chip 让位。
    // 之前 chip 被 Column 的剩余高度压成「只有玻璃底、没有字」的细条，就是这里少了判断。
    BoxWithConstraints(modifier = modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        val roomy = maxHeight >= 380.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            // 纯居中（不是偏上）：下方留白左右对称，才读得出是有意的留白。
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 还没落笔的纸上先立一句诗：溪山配「明月松间照，清泉石上流」，弧光配星与火。
            val arcaneSkin = LocalSkin.current == Skin.Arcane
            PoemLine(
                textRes = if (arcaneSkin) R.string.chat_poem_arcane_1 else R.string.onboarding_poem_2,
                vertical = true,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f),
                horizontalAlignment = Alignment.CenterHorizontally,
            )
            // 诗与标题之间 12dp 呼吸。
            Spacer(Modifier.height(if (roomy) 12.dp else 8.dp))
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (summary.isNotBlank()) {
                Text(
                    stringResource(R.string.welcome_persona, summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                )
            }
            if (roomy) {
                // 标题与人设之后留 24dp：欢迎块被 56dp 输入条解放出来的空白由这两段呼吸吸收。
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 34.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassChip(text = explain, selected = false, onClick = { onSuggest(explain, false) })
                    GlassChip(text = code, selected = false, onClick = { onSuggest(code, false) })
                    GlassChip(text = draw, selected = false, onClick = { onSuggest(draw, true) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaSheet(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultPrompt = stringResource(R.string.assistant_prompt)
    val coder = stringResource(R.string.persona_prompt_coder)
    val writer = stringResource(R.string.persona_prompt_writer)
    val tutor = stringResource(R.string.persona_prompt_tutor)
    var draft by remember(current) { mutableStateOf(current) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.persona_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.persona_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = draft == defaultPrompt, onClick = { draft = defaultPrompt }, label = { Text(stringResource(R.string.persona_preset_default)) })
                FilterChip(selected = draft == coder, onClick = { draft = coder }, label = { Text(stringResource(R.string.persona_preset_coder)) })
                FilterChip(selected = draft == writer, onClick = { draft = writer }, label = { Text(stringResource(R.string.persona_preset_writer)) })
                FilterChip(selected = draft == tutor, onClick = { draft = tutor }, label = { Text(stringResource(R.string.persona_preset_tutor)) })
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                label = { Text(stringResource(R.string.persona_custom)) },
            )
            Button(
                onClick = { onSave(draft) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            ) { Text(stringResource(R.string.persona_save)) }
        }
    }
}

/** 折叠态输入条行高：44dp + 上下各 6dp 内边距 = 56dp 的玻璃条。 */
private val COMPOSER_ROW_HEIGHT = 44.dp

/** 折叠态输入框只占一行：24dp 正是 bodyLarge 的行高。 */
private val COMPOSER_LINE_HEIGHT = 24.dp

/** 写作态输入框高度上限的下界，超过就在框内滚动。 */
private val COMPOSER_MAX_HEIGHT = 168.dp

/** 超过这个字数才显示字数，短句不需要干扰。 */
private const val WRITING_HINT_THRESHOLD = 120

@Composable
private fun ChatComposer(
    input: String,
    pendingImages: List<Uri>,
    drawMode: Boolean,
    generating: Boolean,
    voiceMode: Boolean,
    voicePhase: VoicePhase,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit,
    onShowCommands: () -> Unit,
    onToggleDrawMode: () -> Unit,
    onPickImages: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onToggleVoiceMode: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    val listening = voicePhase == VoicePhase.Listening
    val recognizing = voicePhase == VoicePhase.Recognizing
    val borderColor by animateColorAsState(
        targetValue = when {
            drawMode -> MaterialTheme.colorScheme.secondary
            listening -> MaterialTheme.colorScheme.primary
            recognizing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        },
        label = "composerBorder",
    )
    val shape = RoundedCornerShape(28.dp)

    // 写作态高度跟可视区联动：键盘弹起时按剩余高度缩放，永远给消息区留出余地。
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val visibleDp = with(density) { (windowInfo.containerSize.height - imeBottom).toDp() }
    val writingHeight = ((visibleDp * 0.62f) - 120.dp).coerceIn(COMPOSER_MAX_HEIGHT, 400.dp)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val placeholderRes = when {
        recognizing -> R.string.recognizing
        listening -> R.string.listening_ellipsis
        drawMode -> R.string.draw_hint
        else -> R.string.input_hint
    }
    val fieldStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 24.sp,
    )
    val canSend = (input.isNotBlank() || pendingImages.isNotEmpty()) && voicePhase == VoicePhase.Idle

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .glass(shape = shape, tone = GlassTone.Paper, level = 1f)
            .border(width = 1.dp, color = borderColor, shape = shape),
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            AnimatedVisibility(visible = pendingImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pendingImages.forEach { uri ->
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            IconButton(
                                onClick = { onRemoveImage(uri) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp),
                            ) {
                                Icon(
                                    LumoIcons.Close,
                                    contentDescription = stringResource(R.string.remove_image),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (drawMode) {
                Text(
                    stringResource(R.string.draw_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                )
            }

            if (expanded) {
                // 写作态：多行文本 + 完整工具行，按住说话、画笔、字数、收起都在这里。
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = writingHeight)
                        .focusRequester(focusRequester)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    enabled = !recognizing,
                    textStyle = fieldStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = Int.MAX_VALUE,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                    decorationBox = { inner ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (input.isEmpty()) {
                                Text(
                                    stringResource(placeholderRes),
                                    style = fieldStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onToggleVoiceMode,
                        enabled = voicePhase == VoicePhase.Idle,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            if (voiceMode) LumoIcons.Keyboard else LumoIcons.Mic,
                            contentDescription = stringResource(
                                if (voiceMode) R.string.switch_to_keyboard else R.string.switch_to_voice,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (voiceMode) {
                        ChatHoldPad(
                            listening = listening,
                            recognizing = recognizing,
                            onHoldStart = onHoldStart,
                            onHoldEnd = onHoldEnd,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .padding(start = 4.dp, end = 6.dp),
                        )
                    } else {
                        ComposerAction(
                            icon = LumoIcons.TextLines,
                            label = stringResource(R.string.cmd_button),
                            enabled = !generating,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onShowCommands,
                        )
                        ComposerAction(
                            icon = LumoIcons.Picture,
                            label = stringResource(R.string.attach_image),
                            enabled = !generating,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onPickImages,
                        )
                        ComposerAction(
                            icon = LumoIcons.Brush,
                            label = stringResource(R.string.draw_mode),
                            enabled = !generating,
                            tint = if (drawMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onToggleDrawMode,
                        )
                        Spacer(Modifier.weight(1f))
                        if (input.length >= WRITING_HINT_THRESHOLD) {
                            Text(
                                stringResource(R.string.composer_char_count, input.length),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                        ComposerAction(
                            icon = LumoIcons.Collapse,
                            label = stringResource(R.string.composer_collapse),
                            enabled = voicePhase == VoicePhase.Idle,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onToggleExpand,
                        )
                    }
                    ComposerSendSlot(
                        recognizing = recognizing,
                        generating = generating,
                        canSend = canSend,
                        onSend = onSend,
                        onStopGeneration = onStopGeneration,
                    )
                }
            } else {
                // 折叠态：固定 56dp 单行（44dp 行 + 6dp×2 内边距）。
                // 一行正文 + 语音 / 指令 / 图片 / 展开 + 发送印，空输入时也不留空白；
                // 多行、字数、画笔切换都挪到「展开」后的写作态。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(COMPOSER_ROW_HEIGHT)
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactAction(
                        icon = if (voiceMode) LumoIcons.Keyboard else LumoIcons.Mic,
                        label = stringResource(
                            if (voiceMode) R.string.switch_to_keyboard else R.string.switch_to_voice,
                        ),
                        enabled = voicePhase == VoicePhase.Idle,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onToggleVoiceMode,
                    )
                    if (voiceMode) {
                        ChatHoldPad(
                            listening = listening,
                            recognizing = recognizing,
                            onHoldStart = onHoldStart,
                            onHoldEnd = onHoldEnd,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .padding(start = 2.dp, end = 6.dp),
                        )
                    } else {
                        BasicTextField(
                            value = input,
                            onValueChange = onInput,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = COMPOSER_LINE_HEIGHT, max = COMPOSER_LINE_HEIGHT)
                                .padding(horizontal = 6.dp),
                            enabled = !recognizing,
                            textStyle = fieldStyle,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Default,
                            ),
                            decorationBox = { inner ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (input.isEmpty()) {
                                        Text(
                                            stringResource(placeholderRes),
                                            style = fieldStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        CompactAction(
                            icon = LumoIcons.TextLines,
                            label = stringResource(R.string.cmd_button),
                            enabled = !generating,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onShowCommands,
                        )
                        CompactAction(
                            icon = LumoIcons.Picture,
                            label = stringResource(R.string.attach_image),
                            enabled = !generating,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onPickImages,
                        )
                        // 画笔只在已经进入作画态时露出来（收尾要用它关掉），平时让位给正文。
                        if (drawMode) {
                            CompactAction(
                                icon = LumoIcons.Brush,
                                label = stringResource(R.string.draw_mode),
                                enabled = !generating,
                                tint = MaterialTheme.colorScheme.secondary,
                                onClick = onToggleDrawMode,
                            )
                        }
                        CompactAction(
                            icon = LumoIcons.Expand,
                            label = stringResource(R.string.composer_expand),
                            enabled = voicePhase == VoicePhase.Idle,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onToggleExpand,
                        )
                    }
                    ComposerSendSlot(
                        recognizing = recognizing,
                        generating = generating,
                        canSend = canSend,
                        onSend = onSend,
                        onStopGeneration = onStopGeneration,
                        compact = true,
                    )
                }
            }
        }
    }
}

/** 写作态工具按钮：统一 40dp 触控与 20dp 图标，让工具行高度稳定。 */
@Composable
private fun ComposerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 折叠态小图标：34dp。
 *
 * IconButton 会被 Material3 的最小触控尺寸兜底撑到 48dp，五个按钮一圈就把
 * 56dp 的单行条撑爆了，所以折叠态改用裸 clickable，尺寸由这里说了算。
 */
@Composable
private fun CompactAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = SemanticsRole.Button,
                onClickLabel = label,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 输入区右侧收尾位：识别中转圈 / 生成中停止 / 常态发送印，两种态共用一份。 */
@Composable
private fun ComposerSendSlot(
    recognizing: Boolean,
    generating: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit,
    compact: Boolean = false,
) {
    when {
        recognizing -> {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(if (compact) 20.dp else 22.dp),
                strokeWidth = 2.dp,
            )
        }
        generating -> {
            if (compact) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(role = SemanticsRole.Button, onClick = onStopGeneration),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        LumoIcons.Stop,
                        contentDescription = stringResource(R.string.stop_generation),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                FilledIconButton(onClick = onStopGeneration, modifier = Modifier.size(40.dp)) {
                    Icon(
                        LumoIcons.Stop,
                        contentDescription = stringResource(R.string.stop_generation),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        else -> SendSeal(onSend = onSend, enabled = canSend)
    }
}

@Composable
private fun ChatHoldPad(
    listening: Boolean,
    recognizing: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val container by animateColorAsState(
        targetValue = when {
            listening -> MaterialTheme.colorScheme.primary
            recognizing -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        },
        label = "holdPad",
    )
    val content = when {
        listening -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val label = when {
        recognizing -> stringResource(R.string.recognizing)
        listening -> stringResource(R.string.release_to_stop)
        else -> stringResource(R.string.hold_to_talk)
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .pointerInput(recognizing) {
                detectTapGestures(
                    onPress = {
                        if (recognizing) return@detectTapGestures
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHoldStart()
                        tryAwaitRelease()
                        onHoldEnd()
                    },
                )
            },
        shape = RoundedCornerShape(22.dp),
        color = container,
        contentColor = content,
        shadowElevation = if (listening) 3.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (!recognizing) {
                Icon(LumoIcons.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (listening) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    speaking: Boolean,
    branch: BranchEntry?,
    onBranchPrev: (Long) -> Unit,
    onBranchNext: (Long) -> Unit,
    onEditBranch: (() -> Unit)?,
    onRetry: () -> Unit,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onPreview: (String) -> Unit,
) {
    val isUser = message.role == Role.USER
    val failed = message.status == MessageStatus.FAILED
    val streaming = message.status == MessageStatus.STREAMING
    val markdownUrls = remember(message.content) { extractMarkdownImageUrls(message.content) }
    var menuOpen by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(message.id) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!isUser) {
                SealBadge(text = "墨", size = 26.dp)
                Spacer(Modifier.width(10.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isUser) 0.84f else 0.9f)
                    .combinedClickable(onClick = {}, onLongClick = { menuOpen = true }),
            ) {
                Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
                    if (!isUser) {
                        Text(
                            stringResource(R.string.voice_assistant),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    message.attachments.forEach { attachment ->
                        AsyncImage(
                            model = File(attachment.localPath),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPreview(attachment.localPath) },
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    markdownUrls.forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPreview(url) },
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    val thinking = streaming && message.content.isBlank()
                    val body = when {
                        message.content.isNotBlank() && streaming -> "${message.content}▍"
                        message.content.isNotBlank() -> message.content
                        message.attachments.isNotEmpty() -> ""
                        // 空正文的流式回复交给下面三滴墨，不再画一根裸光标。
                        thinking -> ""
                        else -> stringResource(R.string.empty_reply)
                    }
                    // 正在落笔：三滴墨呼吸；无障碍仍读「正在落笔」，不靠图形说话。
                    if (!isUser && thinking) {
                        val thinkingLabel = stringResource(R.string.chat_thinking)
                        Box(
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .semantics { contentDescription = thinkingLabel },
                        ) {
                            InkDots()
                        }
                    }
                    if (!isUser && message.reasoning.isNotBlank()) {
                        ReasoningBlock(message.reasoning)
                    }
                    if (body.isNotBlank()) {
                        if (isUser) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glass(
                                        shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                                        tone = GlassTone.Cinnabar,
                                        level = 1f,
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    body,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glass(
                                        shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
                                        tone = GlassTone.Paper,
                                        level = 0.9f,
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                MarkdownText(text = body, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    if (failed) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                if (message.error.isNotBlank()) {
                                    Text(
                                        message.error,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                            }
                        }
                    }
                    // 用户消息编辑分支后也要能切回来：这里对用户消息只显示切换器，不带复制/朗读。
                    if (isUser && !streaming && branch != null && branch.count > 1) {
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BranchSwitcher(
                                entry = branch,
                                onPrev = onBranchPrev,
                                onNext = onBranchNext,
                            )
                        }
                    }
                    if (!isUser && message.content.isNotBlank() && !streaming) {
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 分支切换器：只有在同一父节点下有多条助手回复时出现。
                            if (branch != null && branch.count > 1) {
                                BranchSwitcher(
                                    entry = branch,
                                    onPrev = onBranchPrev,
                                    onNext = onBranchNext,
                                )
                            }
                            IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    LumoIcons.Copy,
                                    contentDescription = stringResource(R.string.copy_message),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(onClick = onSpeak, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    if (speaking) LumoIcons.Stop else LumoIcons.Speaker,
                                    contentDescription = stringResource(if (speaking) R.string.stop_reading else R.string.read_message),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            IconButton(onClick = onRetry, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    LumoIcons.Refresh,
                                    contentDescription = stringResource(R.string.retry),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    LumoIcons.More,
                                    contentDescription = stringResource(R.string.more_actions),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onEditBranch != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.branch_edit_action)) },
                            onClick = {
                                menuOpen = false
                                onEditBranch()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_message)) },
                        onClick = {
                            menuOpen = false
                            onCopy()
                        },
                    )
                    if (!isUser) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (speaking) R.string.stop_reading else R.string.read_message)) },
                            onClick = {
                                menuOpen = false
                                onSpeak()
                            },
                        )
                    }
                    if (!isUser && !streaming && message.content.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.retry)) },
                            onClick = {
                                menuOpen = false
                                onRetry()
                            },
                        )
                    }
                }
            }
            if (isUser) {
                Spacer(Modifier.width(10.dp))
                SealBadge(text = "朱", size = 26.dp)
            }
        }
    }
}

/**
 * 分支切换器：`‹ 2/3 ›`。
 *
 * 只切 leaf，不复制消息——另一条分支的正文仍留在库里，切回去即可看到。
 */
@Composable
private fun BranchSwitcher(
    entry: BranchEntry,
    onPrev: (Long) -> Unit,
    onNext: (Long) -> Unit,
) {
    val prev = entry.previous()
    val next = entry.next()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 4.dp),
    ) {
        IconButton(
            onClick = { prev?.let(onPrev) },
            enabled = prev != null,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                LumoIcons.ChevronLeft,
                contentDescription = stringResource(R.string.branch_previous),
                tint = if (prev != null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            stringResource(R.string.branch_switcher, entry.index + 1, entry.count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(
            onClick = { next?.let(onNext) },
            enabled = next != null,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                LumoIcons.ChevronRight,
                contentDescription = stringResource(R.string.branch_next),
                tint = if (next != null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CommandPalette(query: String, onPick: (String) -> Unit) {
    val needle = query.trim().removePrefix("/").substringBefore(' ').lowercase()
    val specs = ChatCommand.specs().filter { spec ->
        needle.isEmpty() ||
            spec.trigger.removePrefix("/").startsWith(needle) ||
            spec.keywords.any { it.startsWith(needle) }
    }
    if (specs.isEmpty()) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            specs.forEach { spec ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(spec.insert) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        spec.trigger,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(spec.labelRes), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(spec.detailRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.cmd_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.cmd_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ChatCommand.specs().forEach { spec ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPick(spec.insert)
                            onDismiss()
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        spec.trigger,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(104.dp),
                    )
                    Column {
                        Text(stringResource(spec.labelRes), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(spec.detailRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPrompts(onPick: (String) -> Unit) {
    val prompts = listOf(
        stringResource(R.string.quick_simpler),
        stringResource(R.string.quick_example),
        stringResource(R.string.quick_continue),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        prompts.forEach { prompt ->
            GlassChip(text = prompt, selected = false, onClick = { onPick(prompt) })
        }
    }
}

@Composable
private fun ReasoningBlock(reasoning: String) {
    var expanded by remember { mutableStateOf(false) }
    // 推理正文是次级文字，直接压在背景上对比度不够：套一层淡玻璃再写。
    Column(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .padding(bottom = 4.dp)
            .glass(shape = RoundedCornerShape(14.dp), tone = GlassTone.Paper, level = 0.85f)
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.hide_reasoning else R.string.show_reasoning))
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
            Text(
                reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImagePreviewDialog(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = if (path.startsWith("http")) path else File(path),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .graphicsLayer { alpha = 1f },
                contentScale = ContentScale.Fit,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(LumoIcons.Close, contentDescription = stringResource(R.string.close_preview), tint = Color.White)
            }
        }
    }
}

/** 发送：一枚朱砂圆印；空输入时压到 55% 朱砂配淡印色图标，有字才「落印」。 */
@Composable
private fun SendSeal(onSend: () -> Unit, enabled: Boolean) {
    val palette = LocalLumoPalette.current
    val container by animateColorAsState(
        targetValue = if (enabled) palette.cinnabar else palette.cinnabar.copy(alpha = 0.55f),
        animationSpec = tween(200),
        label = "sendSealContainer",
    )
    val content by animateColorAsState(
        targetValue = if (enabled) {
            palette.sealInk
        } else {
            // 淡印色而不是灰：空态也要看得出「这是一枚印，只是还没按下去」。
            palette.sealInk.copy(alpha = 0.75f)
        },
        animationSpec = tween(200),
        label = "sendSealContent",
    )
    PressScale(enabled = enabled) {
        InkRipple(
            onClick = onSend,
            enabled = enabled,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(container),
        ) {
            Icon(
                LumoIcons.Send,
                contentDescription = stringResource(R.string.send),
                tint = content,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(19.dp),
            )
        }
    }
}
