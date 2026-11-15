package com.yuguang.lumo.ui.translate

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuguang.lumo.R
import com.yuguang.lumo.data.local.TranslationEntity
import com.yuguang.lumo.domain.model.SpeechLanguage
import com.yuguang.lumo.domain.model.VoiceChoice
import com.yuguang.lumo.domain.model.liveLanguages
import com.yuguang.lumo.domain.model.livePairError
import com.yuguang.lumo.domain.model.textLanguages
import com.yuguang.lumo.domain.model.translationModels
import coil.compose.AsyncImage
import com.yuguang.lumo.ui.chat.SpeechInput
import com.yuguang.lumo.ui.chat.VoicePhase
import com.yuguang.lumo.ui.components.BreathingSeal
import com.yuguang.lumo.ui.components.ErrorCard
import com.yuguang.lumo.ui.components.GlassCard
import com.yuguang.lumo.ui.components.GlassChip
import com.yuguang.lumo.ui.components.GlassTone
import com.yuguang.lumo.ui.components.InkDivider
import com.yuguang.lumo.ui.components.InkRipple
import com.yuguang.lumo.ui.components.SealedSectionTitle
import com.yuguang.lumo.ui.components.glass
import com.yuguang.lumo.ui.languageLabel
import com.yuguang.lumo.ui.shell.AppSection
import com.yuguang.lumo.ui.shell.SectionScaffold
import com.yuguang.lumo.ui.shortLanguageLabel
import com.yuguang.lumo.ui.theme.LocalSkin
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LocalMotionEnabled
import com.yuguang.lumo.ui.theme.LumoIcons
import com.yuguang.lumo.ui.theme.LumoMotion
import com.yuguang.lumo.ui.theme.LumoType
import com.yuguang.lumo.ui.theme.SceneryCatalog
import com.yuguang.lumo.ui.storedVoiceLabel
import com.yuguang.lumo.ui.translationModelLabel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
    onNavigate: (AppSection) -> Unit,
    viewModel: TranslateViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val sampleSentence = stringResource(R.string.translate_sample_sentence)
    val clipboard = LocalClipboard.current
    val clipLabel = stringResource(R.string.clip_translation)
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf<PickTarget?>(null) }
    var browse by remember { mutableStateOf<RecordBrowse?>(null) }
    val liveActive = state.livePhase == LivePhase.Connecting || state.livePhase == LivePhase.Listening
    val faceActive = state.facePhase == LivePhase.Connecting || state.facePhase == LivePhase.Listening
    val recordKind = when (state.tab) {
        TranslateTab.Live -> "LIVE"
        TranslateTab.Text -> "TEXT"
        TranslateTab.Photo -> "PHOTO"
        TranslateTab.Face -> "FACE"
    }
    val tabRecords = state.records.filter { it.kind == recordKind }
    LaunchedEffect(state.tab) { browse = null }
    LaunchedEffect(state.livePhase) {
        if (liveActive) browse = null
    }
    BackHandler(enabled = browse != null || liveActive || faceActive) {
        when {
            browse != null -> {
                browse = when (val current = browse) {
                    is RecordBrowse.Detail -> if (current.fromAll) RecordBrowse.All else null
                    else -> null
                }
            }
            faceActive -> viewModel.stopFace()
            liveActive -> viewModel.stopLive()
        }
    }
    val openRecordId = (browse as? RecordBrowse.Detail)?.id
    DisposableEffect(openRecordId) {
        onDispose { if (openRecordId != null) viewModel.stopRecordSpeech() }
    }
    var pendingTextMic by remember { mutableStateOf(false) }
    var pendingFaceMic by remember { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.selectPhoto(uri) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            !granted && pendingTextMic -> {
                pendingTextMic = false
                viewModel.denyTextMicrophone()
            }
            !granted && pendingFaceMic -> {
                pendingFaceMic = false
                viewModel.denyMicrophone()
            }
            !granted -> viewModel.denyMicrophone()
            pendingTextMic -> pendingTextMic = false
            pendingFaceMic -> {
                pendingFaceMic = false
                viewModel.startFace()
            }
            else -> viewModel.toggleLive()
        }
    }
    val view = LocalView.current
    DisposableEffect(liveActive, faceActive) {
        val window = view.context.findActivity().window
        if (liveActive || faceActive) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.leaveLive() }
    }
    SectionScaffold(
        section = AppSection.Translate,
        title = stringResource(R.string.nav_translate),
        onNavigate = { section ->
            if (section != AppSection.Translate) viewModel.leaveLive()
            onNavigate(section)
        },
        actions = {
            if (browse == null) {
                IconButton(
                    onClick = { if (!liveActive) browse = RecordBrowse.All },
                    enabled = !liveActive,
                ) {
                    Icon(LumoIcons.Clock, contentDescription = stringResource(R.string.history))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (browse == null) {
                TranslateModeSwitcher(
                    selected = state.tab,
                    onSelect = viewModel::selectTab,
                )
            }
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
            ) {
                if (browse != null) {
                    RecordBrowser(
                        browse = browse!!,
                        records = tabRecords,
                        speakingKey = state.speakingKey,
                        message = state.message,
                        offerLocalFor = state.offerLocalFor,
                        onBack = {
                            browse = when (val current = browse) {
                                is RecordBrowse.Detail -> if (current.fromAll) RecordBrowse.All else null
                                else -> null
                            }
                        },
                        onOpen = { record -> browse = RecordBrowse.Detail(record.id, fromAll = browse is RecordBrowse.All) },
                        onCopy = { text ->
                            scope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText(clipLabel, text).toClipEntry())
                            }
                        },
                        onSpeak = { record -> viewModel.speak("record:${record.id}", record.targetText) },
                        onUseLocal = { text ->
                            viewModel.speak("local-once", text, forceLocal = true)
                            viewModel.dismissMessage()
                        },
                        onDelete = viewModel::deleteRecord,
                        onRetakePhoto = {
                            // 回到图片翻译页并直接拉起选图器：用户点「重新选一张」就是想马上再拍。
                            browse = null
                            viewModel.selectTab(TranslateTab.Photo)
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onReuse = { record ->
                            viewModel.reuseTextRecord(record)
                            browse = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (state.tab == TranslateTab.Live) {
                    LiveStage(
                        state = state,
                        records = tabRecords,
                        onPickSource = { picking = PickTarget.LiveSource },
                        onPickTarget = { picking = PickTarget.LiveTarget },
                        onPickVoice = { picking = PickTarget.Voice },
                        onSwap = viewModel::swapLiveLanguages,
                        onToggle = {
                            if (liveActive) {
                                viewModel.stopLive()
                            } else {
                                permission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onCopy = { text ->
                            scope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText(clipLabel, text).toClipEntry())
                            }
                        },
                        onSpeak = { text -> viewModel.speak("live-caption", text) },
                        onSpeakRecord = { record -> viewModel.speak("record:${record.id}", record.targetText) },
                        onOpenRecord = { record -> browse = RecordBrowse.Detail(record.id, fromAll = false) },
                        onTry = {
                            // 示例句直接带到文本模式：用户不用再想「翻什么」。
                            viewModel.updateInput(sampleSentence)
                            viewModel.selectTab(TranslateTab.Text)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (state.tab == TranslateTab.Photo) {
                    PhotoStage(
                        state = state,
                        message = state.message,
                        onPick = {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onTranslate = viewModel::translatePhoto,
                        onCopy = { text ->
                            scope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText(clipLabel, text).toClipEntry())
                            }
                        },
                        onSpeak = { text -> viewModel.speak("photo-line", text) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (state.tab == TranslateTab.Face) {
                    FaceToFaceStage(
                        state = state,
                        onStart = {
                            if (ContextCompat.checkSelfPermission(view.context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.startFace()
                            } else {
                                pendingFaceMic = true
                                permission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onStop = viewModel::stopFace,
                        onSwap = viewModel::swapFace,
                        onToggleLock = viewModel::toggleFaceLock,
                        onSpeak = { text -> viewModel.speak("face-line", text) },
                        onCopy = { text ->
                            scope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText(clipLabel, text).toClipEntry())
                            }
                        },
                        onClear = viewModel::clearFaceTurns,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    TextStage(
                        state = state,
                        records = tabRecords,
                        onPickSource = { picking = PickTarget.TextSource },
                        onPickTarget = { picking = PickTarget.TextTarget },
                        onSwap = viewModel::swapTextLanguages,
                        onPickModel = { picking = PickTarget.Model },
                        onInput = viewModel::updateInput,
                        onClear = viewModel::clearInput,
                        onTranslate = { viewModel.translate(save = true) },
                        onCopy = {
                            scope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText(clipLabel, state.output).toClipEntry())
                            }
                        },
                        onSpeak = { viewModel.speak("text-output", state.output) },
                        onUseLocal = { text ->
                            viewModel.speak("local-once", text, forceLocal = true)
                            viewModel.dismissMessage()
                        },
                        onOpenRecord = { record -> browse = RecordBrowse.Detail(record.id, fromAll = false) },
                        onSpeakRecord = { record -> viewModel.speak("record:${record.id}", record.targetText) },
                        onVoicePhase = viewModel::setVoicePhase,
                        onVoiceResult = viewModel::applyVoiceInput,
                        onVoiceError = viewModel::reportVoiceError,
                        recognize = viewModel::recognize,
                        onRequestMic = {
                            pendingTextMic = true
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
    picking?.let { target ->
        OptionSheet(target = target, state = state, onDismiss = { picking = null }, viewModel = viewModel)
    }
}

/** 模式 chip 高度：44dp 够拇指扫到，也给 22dp 图标留出上下各 11dp。 */
private val MODE_CHIP_HEIGHT = 44.dp

/**
 * 四段模式：一行可横滑的玻璃 chip。
 *
 * 原来四段等分整宽，图标与文字被挤在 88dp 里；改成按内容排布 + 横滑后，
 * 每段各自拿到 22dp 图标 + 13sp 短标签的呼吸，选中态用朱砂描边加一道底印点出。
 */
@Composable
private fun TranslateModeSwitcher(
    selected: TranslateTab,
    onSelect: (TranslateTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        // 四段各用一枚墨线图标：话筒 / 两行字 / 画框 / 脸。
        Triple(TranslateTab.Live, R.string.tab_live, LumoIcons.Realtime),
        Triple(TranslateTab.Text, R.string.tab_text, LumoIcons.TextLines),
        Triple(TranslateTab.Photo, R.string.tab_photo, LumoIcons.Picture),
        Triple(TranslateTab.Face, R.string.tab_face, LumoIcons.Face),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (tab, labelRes, icon) ->
            ModeChip(
                label = stringResource(labelRes),
                icon = icon,
                selected = tab == selected,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalLumoPalette.current
    val shape = RoundedCornerShape(22.dp)
    val tint = if (selected) palette.cinnabar else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .height(MODE_CHIP_HEIGHT)
            .glass(shape = shape, level = if (selected) 0.9f else 0.72f)
            .background(
                color = if (selected) palette.cinnabar.copy(alpha = 0.10f) else Color.Transparent,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) palette.cinnabar.copy(alpha = 0.85f) else palette.glassBorder,
                shape = shape,
            )
            .clip(shape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = tint,
                maxLines = 1,
            )
        }
        // 选中：底沿一道 1.5dp 朱砂底印，像 chip 下压了半枚印。
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .width(22.dp)
                    .height(1.5.dp)
                    .background(palette.cinnabar, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun LiveStage(
    state: TranslateUiState,
    records: List<TranslationEntity>,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit,
    onPickVoice: () -> Unit,
    onSwap: () -> Unit,
    onToggle: () -> Unit,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onSpeakRecord: (TranslationEntity) -> Unit,
    onOpenRecord: (TranslationEntity) -> Unit,
    onTry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.livePhase == LivePhase.Connecting || state.livePhase == LivePhase.Listening
    // 免费档的语种边界更窄，且不支持复刻原声：状态与按钮都按免费规则判定。
    val freeTier = state.tier == com.yuguang.lumo.domain.model.SpeechTier.FREE
    val blocked = if (freeTier) {
        com.yuguang.lumo.domain.model.freeLivePairError(
            state.liveSourceLang,
            state.liveTargetLang,
            state.liveVoice,
        )
    } else {
        livePairError(state.liveSourceLang, state.liveTargetLang, state.liveVoice)
    }
    val scroll = rememberScrollState()
    val empty = state.sourceCaption.isBlank() && state.targetCaption.isBlank() &&
        state.sourcePartial.isBlank() && state.targetPartial.isBlank()
    LaunchedEffect(state.livePhase, state.sourceCaption, state.sourcePartial, state.targetCaption, state.targetPartial) {
        if (state.livePhase == LivePhase.Listening) scroll.animateScrollTo(scroll.maxValue)
        if (state.livePhase == LivePhase.Ended) scroll.scrollTo(0)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (active) {
            LiveSealStage(
                source = shortLanguageLabel(state.liveSourceLang),
                target = shortLanguageLabel(state.liveTargetLang),
                caption = stringResource(state.liveVoice.labelRes),
            )
        } else {
            // 语言与音色收进同一张卡：中文 ⇄ 英语 在左，女声小 chip 挂在卡右端。
            LanguageCard(
                source = languageLabel(state.liveSourceLang, liveLanguages),
                target = languageLabel(state.liveTargetLang, liveLanguages),
                trailing = stringResource(state.liveVoice.labelRes),
                onSource = onPickSource,
                onTarget = onPickTarget,
                onSwap = onSwap,
                onTrailing = onPickVoice,
                hint = if (state.liveVoice == VoiceChoice.CLONE) stringResource(R.string.clone_hint) else null,
            )
        }
        // 舞台吃掉标题栏与主按钮之间的全部空间：不再固定 420dp，小屏也不会把按钮挤出屏幕。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val compact = maxHeight < 380.dp
            when {
                empty && !active -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LiveStageIdle(
                        freeTier = freeTier,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                    )
                    LastRecordHint(
                        records = records,
                        onOpen = onOpenRecord,
                        onSpeak = onSpeakRecord,
                        onTry = onTry,
                    )
                }
                empty && active -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LiveEmptyHint(listening = true, freeTier = freeTier)
                }
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CaptionCard(stringResource(R.string.source_label), state.sourceCaption, state.sourcePartial, compact = true)
                    CaptionCard(stringResource(R.string.target_label), state.targetCaption, state.targetPartial, compact = false)
                    if (active) {
                        LiveWave()
                    }
                    if (state.livePhase == LivePhase.Ended && state.liveSaved) {
                        // 「已保存」这一行连同复制 / 朗读一起压在同一枚玻璃条上。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glass(shape = RoundedCornerShape(999.dp), level = 0.85f)
                                .padding(start = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.saved),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            IconButton(onClick = { onCopy(state.targetCaption) }) {
                                Icon(LumoIcons.Copy, contentDescription = stringResource(R.string.copy_translation))
                            }
                            if (state.targetCaption.isNotBlank()) {
                                IconButton(onClick = { onSpeak(state.targetCaption) }) {
                                    Icon(
                                        if (state.speakingKey == "live-caption") LumoIcons.Stop else LumoIcons.Speaker,
                                        contentDescription = stringResource(R.string.read_translation),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        state.message?.let { message ->
            // 提示与报错都用现成的 ErrorCard，不在页面上摊开一行红字。
            ErrorCard(message = message)
        }
        // 主操作贴底：状态行 12sp + ≥56dp 的墨押按钮，最后让出导航条。
        LiveStatusPill(
            phase = state.livePhase,
            hearing = state.hearing,
            blocked = if (active) null else blocked,
        )
        PrimaryActionButton(
            label = when (state.livePhase) {
                LivePhase.Connecting -> stringResource(R.string.connecting)
                LivePhase.Listening -> stringResource(R.string.end_live)
                else -> stringResource(R.string.start_live)
            },
            onClick = onToggle,
            enabled = blocked == null || active,
            stopStyle = active,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun TextStage(
    state: TranslateUiState,
    records: List<TranslationEntity>,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit,
    onSwap: () -> Unit,
    onPickModel: () -> Unit,
    onInput: (String) -> Unit,
    onClear: () -> Unit,
    onTranslate: () -> Unit,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onUseLocal: (String) -> Unit,
    onOpenRecord: (TranslationEntity) -> Unit,
    onSpeakRecord: (TranslationEntity) -> Unit,
    onVoicePhase: (VoicePhase) -> Unit,
    onVoiceResult: (String) -> Unit,
    onVoiceError: (String) -> Unit,
    recognize: (ByteArray, (String) -> Unit, (String) -> Unit) -> Unit,
    onRequestMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sampleSentence = stringResource(R.string.translate_sample_sentence)
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val latestVoiceResult by rememberUpdatedState(onVoiceResult)
    val latestVoicePhase by rememberUpdatedState(onVoicePhase)
    val latestVoiceError by rememberUpdatedState(onVoiceError)
    val latestRecognize by rememberUpdatedState(recognize)
    val speech = remember(context) {
        SpeechInput(
            context = context,
            onText = { latestVoiceResult(it) },
            onPhase = { latestVoicePhase(it) },
            onError = { latestVoiceError(it) },
            recognize = { wav, ok, fail -> latestRecognize(wav, ok, fail) },
        )
    }
    DisposableEffect(speech) {
        onDispose {
            speech.destroy()
            latestVoicePhase(VoicePhase.Idle)
        }
    }
    val listening = state.voicePhase == VoicePhase.Listening
    val recognizing = state.voicePhase == VoicePhase.Recognizing
    // 粘贴：把剪贴板里的文字接在已输入内容后面。Android 10+ 只允许前台应用读剪贴板，
    // 而这里一定是前台点按，所以直接读系统服务即可，不需要额外权限。
    val paste: () -> Unit = {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = manager?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
            .trim()
        if (text.isNotBlank()) {
            onInput(if (state.input.isBlank()) text else state.input.trimEnd() + "\n" + text)
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 语言与模型合成一张卡：模型不再单独占一行悬在画心上。
        LanguageCard(
            source = languageLabel(state.textSource, textLanguages),
            target = languageLabel(state.textTarget, textLanguages),
            trailing = translationModelLabel(state.model),
            onSource = onPickSource,
            onTarget = onPickTarget,
            onSwap = onSwap,
            onTrailing = onPickModel,
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextWorkCard(
                input = state.input,
                output = state.output,
                translating = state.translating,
                speaking = state.speakingKey == "text-output",
                voicePhase = state.voicePhase,
                onInput = onInput,
                onClear = onClear,
                onTranslate = onTranslate,
                onCopy = onCopy,
                onSpeak = onSpeak,
                onPaste = paste,
            )
            state.message?.let { message ->
                ErrorCard(message = message)
                if (state.offerLocalFor != null) {
                    TextButton(onClick = { onUseLocal(state.offerLocalFor) }) {
                        Text(stringResource(R.string.use_local_once))
                    }
                }
            }
            LastRecordHint(
                records = records,
                onOpen = onOpenRecord,
                onSpeak = onSpeakRecord,
                onTry = { onInput(sampleSentence) },
            )
        }
        // 「按住说话」贴底：和同传的主按钮同一条基线，navigationBarsPadding 让手势条不压按钮。
        HoldToTalkButton(
            listening = listening,
            recognizing = recognizing,
            enabled = !state.translating,
            onRequestMic = onRequestMic,
            onStart = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                // 把已有输入作为前缀传进去：边说边接在原句后面，不会把上一句吃掉。
                speech.start(state.input)
            },
            onStop = { speech.stop() },
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}

/**
 * 文本工作台：原文与译文合成同一张宣纸，中间一道墨线。
 *
 * 原来拆成两张卡，读的时候视线要在两块之间跳；合起来之后原文在上、译文在下，
 * 底部一排 20dp 图标管粘贴 / 清空 / 复制 / 朗读 / 发送，触达都按 48dp 给足。
 */
@Composable
private fun TextWorkCard(
    input: String,
    output: String,
    translating: Boolean,
    speaking: Boolean,
    voicePhase: VoicePhase,
    onInput: (String) -> Unit,
    onClear: () -> Unit,
    onTranslate: () -> Unit,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onPaste: () -> Unit,
) {
    val palette = LocalLumoPalette.current
    val listening = voicePhase == VoicePhase.Listening
    val recognizing = voicePhase == VoicePhase.Recognizing
    val border by animateColorAsState(
        targetValue = when {
            listening -> MaterialTheme.colorScheme.primary
            recognizing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> palette.glassBorder
        },
        label = "workCardBorder",
    )
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = shape, tone = GlassTone.Paper, level = 1f)
            .border(1.dp, border, shape),
    ) {
        // ── 原文
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.source_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (recognizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            BasicTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .padding(top = 4.dp),
                enabled = voicePhase == VoicePhase.Idle,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onTranslate() }),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text(
                                when (voicePhase) {
                                    VoicePhase.Listening -> stringResource(R.string.listening_ellipsis)
                                    VoicePhase.Recognizing -> stringResource(R.string.recognizing)
                                    VoicePhase.Idle -> stringResource(R.string.text_input_hint)
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
        }
        InkDivider(modifier = Modifier.padding(horizontal = 16.dp), alpha = 0.55f)
        // ── 译文
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.target_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (translating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .padding(top = 4.dp),
            ) {
                when {
                    output.isNotBlank() -> Text(
                        output,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp),
                    )
                    translating -> Text(
                        stringResource(R.string.translating),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    else -> Text(
                        stringResource(R.string.output_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        InkDivider(modifier = Modifier.padding(horizontal = 16.dp), alpha = 0.55f)
        // ── 操作行：图标 20dp、间距 12dp，每颗 48dp 触达
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextActionIcon(
                icon = LumoIcons.Doc,
                contentDescription = stringResource(R.string.paste_input),
                onClick = onPaste,
            )
            TextActionIcon(
                icon = LumoIcons.Close,
                contentDescription = stringResource(R.string.clear_input),
                onClick = onClear,
                enabled = input.isNotBlank(),
            )
            Spacer(modifier = Modifier.weight(1f))
            TextActionIcon(
                icon = LumoIcons.Copy,
                contentDescription = stringResource(R.string.copy_translation),
                onClick = onCopy,
                enabled = output.isNotBlank(),
            )
            TextActionIcon(
                icon = if (speaking) LumoIcons.Stop else LumoIcons.Speaker,
                contentDescription = stringResource(if (speaking) R.string.stop_reading else R.string.read_translation),
                onClick = onSpeak,
                enabled = output.isNotBlank(),
            )
            TextActionIcon(
                icon = LumoIcons.Send,
                contentDescription = stringResource(R.string.translate),
                onClick = onTranslate,
                enabled = input.isNotBlank() && !translating,
                accent = true,
                loading = translating,
            )
        }
    }
}

/** 操作行里的一颗：20dp 图标 + 48dp 触达，禁用时降到 32% 墨度，发送那颗用朱砂。 */
@Composable
private fun TextActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Boolean = false,
    loading: Boolean = false,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
        accent -> LocalLumoPalette.current.cinnabar
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = tint,
            )
        } else {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        }
    }
}

@Composable
private fun HoldToTalkButton(
    listening: Boolean,
    recognizing: Boolean,
    enabled: Boolean,
    onRequestMic: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val active = listening || recognizing
    val label = when {
        recognizing -> stringResource(R.string.recognizing)
        listening -> stringResource(R.string.release_to_stop)
        else -> stringResource(R.string.hold_to_talk)
    }
    val palette = LocalLumoPalette.current
    val ringShape = RoundedCornerShape(28.dp)
    // 朱砂环：待按是纸上一圈朱砂，按住才整颗填实。
    Box(
        modifier = modifier
            .height(56.dp)
            .glass(shape = ringShape, level = 0.9f)
            .background(if (listening) palette.cinnabar else Color.Transparent, ringShape)
            .border(
                width = 1.5.dp,
                color = palette.cinnabar.copy(alpha = if (active) 1f else 0.6f),
                shape = ringShape,
            )
            .clip(ringShape)
            .pointerInput(enabled, recognizing) {
                detectTapGestures(
                    onPress = {
                        if (!enabled || recognizing) return@detectTapGestures
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            onRequestMic()
                            return@detectTapGestures
                        }
                        onStart()
                        tryAwaitRelease()
                        onStop()
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val content = if (listening) palette.sealInk else palette.cinnabar
            if (recognizing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = palette.cinnabar,
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    LumoIcons.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = content,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 同传舞台：一枚呼吸的朱砂印作底，源/目标两枚语言印章上下叠在印心，中间一道墨线。
 * 「正在听 / 正在译」只靠呼吸与印章表达，文字压到一行状态。
 */
@Composable
private fun LiveSealStage(
    source: String,
    target: String,
    caption: String,
) {
    val palette = LocalLumoPalette.current
    val sealInk = palette.sealInk
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 呼吸环只在收音时跳；连接阶段保持静止，免得让人以为已经在录。
            BreathingSeal(size = 148.dp)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    source,
                    style = LumoType.seal.copy(fontSize = 16.sp, letterSpacing = 2.sp),
                    color = sealInk,
                    maxLines = 1,
                )
                InkDivider(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    color = sealInk,
                    alpha = 0.55f,
                )
                Text(
                    target,
                    style = LumoType.seal.copy(fontSize = 16.sp, letterSpacing = 2.sp),
                    color = sealInk,
                    maxLines = 1,
                )
            }
        }
        // 状态词挪到主按钮上方那行 12sp 药丸；印面上只留「音色」这枚小标签。
        Box(
            modifier = Modifier
                .glass(shape = RoundedCornerShape(999.dp), level = 0.85f)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 同传舞台的静止态：180dp 双层墨圈 + 中央「听」朱砂小印，圈自己缓慢呼吸。
 *
 * 高度交给父级的 weight(1f)，自己只负责在剩余空间里垂直居中；小屏（< 380dp）把副文案收掉、
 * 墨圈缩到 132dp，保证 56dp 的主按钮永远露在拇指下。
 */
@Composable
private fun LiveStageIdle(
    freeTier: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumoPalette.current
    val motion = LocalMotionEnabled.current
    val ink = MaterialTheme.colorScheme.onSurface
    val transition = rememberInfiniteTransition(label = "liveIdle")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(LumoMotion.sealBreathMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liveIdleBreath",
    )
    val wave = if (motion) breath else 0.5f
    val ring = if (compact) 132.dp else 180.dp
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(ring), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                // 双层墨圈：外圈随呼吸开合，内圈定住不动，像水面上的两圈涟漪。
                drawCircle(
                    color = ink.copy(alpha = 0.10f + 0.05f * wave),
                    radius = r * (0.96f + 0.02f * wave),
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawCircle(
                    color = ink.copy(alpha = 0.16f),
                    radius = r * 0.72f,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            BreathingSeal(size = if (compact) 52.dp else 64.dp) {
                Text(
                    text = "听",
                    style = LumoType.seal.copy(fontSize = 22.sp),
                    color = palette.sealInk,
                )
            }
        }
        Spacer(modifier = Modifier.height(if (compact) 10.dp else 14.dp))
        // 提示句放在一小片宣纸玻璃上：背景被加强后，11sp 淡字直接压山水会糊。
        Column(
            modifier = Modifier
                .glass(shape = RoundedCornerShape(18.dp), level = 0.85f)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.live_hint_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!compact) {
                Text(
                    stringResource(if (freeTier) R.string.free_live_hint else R.string.live_hint),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = if (freeTier) {
                        palette.gold
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LiveEmptyHint(listening: Boolean = false, freeTier: Boolean = false) {
    // 提示收进一张 0.9f 的玻璃卡：正文不压背景，也不再铺满整块舞台。
    Column(
        modifier = Modifier
            .glass(shape = RoundedCornerShape(20.dp), level = 0.9f)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(if (listening) R.string.hearing else R.string.live_hint_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!listening) {
            Text(
                stringResource(if (freeTier) R.string.free_live_hint else R.string.live_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (freeTier) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 主按钮上方的一行状态：未开始 / 正在连接 / 正在听。
 * 12sp 小字不能直接压山水，所以垫一枚 0.85f 的玻璃药丸。
 */
@Composable
private fun LiveStatusPill(phase: LivePhase, hearing: Boolean, blocked: Int?) {
    val palette = LocalLumoPalette.current
    val listening = phase == LivePhase.Listening && hearing
    val text = when {
        phase == LivePhase.Connecting -> stringResource(R.string.connecting)
        phase == LivePhase.Listening -> stringResource(if (hearing) R.string.hearing else R.string.quiet)
        blocked != null -> stringResource(blocked)
        else -> stringResource(R.string.live_status_idle)
    }
    val accent = when {
        blocked != null && phase == LivePhase.Idle -> palette.gold
        phase == LivePhase.Connecting || listening -> palette.cinnabar
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .glass(shape = RoundedCornerShape(999.dp), level = 0.85f)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = accent,
                maxLines = 1,
            )
        }
    }
}

/**
 * 上次记录：一块 0.9f 的玻璃卡。
 *
 * 有记录 → 译文首行（最多两行）+ 时间 + 右侧播放按钮；
 * 没记录 → 一句「对着手机说」加一枚示例句 chip，点一下把这句填进文本输入。
 * 原来的裸字贴在画心上，既不好读也不好点。
 */
@Composable
private fun LastRecordHint(
    records: List<TranslationEntity>,
    onOpen: (TranslationEntity) -> Unit,
    onSpeak: (TranslationEntity) -> Unit,
    onTry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sampleSentence = stringResource(R.string.translate_sample_sentence)
    val latest = records.firstOrNull()
    if (latest == null) {
        GlassCard(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            level = 0.9f,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.try_hint),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            GlassChip(
                text = sampleSentence,
                selected = false,
                onClick = onTry,
                modifier = Modifier.align(Alignment.Start),
            )
        }
    } else {
        val preview = latest.targetText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            .ifBlank { stringResource(R.string.no_target) }
        GlassCard(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            level = 0.9f,
            onClick = { onOpen(latest) },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.last_record, preview),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        recordWhen(latest.createdAt, withClock = true),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // 播放：40dp 玻璃圆钮，点它是朗读这条译文，点卡片其余部分是打开详情。
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .glass(shape = CircleShape, level = 0.9f)
                        .border(1.dp, LocalLumoPalette.current.cinnabar.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = { onSpeak(latest) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            LumoIcons.Play,
                            contentDescription = stringResource(R.string.read_translation),
                            modifier = Modifier.size(18.dp),
                            tint = LocalLumoPalette.current.cinnabar,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    stopStyle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 墨押按钮：整幅宣纸上压一道细边，左侧一个墨点。不用实心色条，
    // 因为「开始同传」是一句招呼，不是一声警报。
    val palette = LocalLumoPalette.current
    val celadon = MaterialTheme.colorScheme.primary
    val accent = if (stopStyle) palette.cinnabar else celadon
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .glass(shape = shape, level = if (enabled) 1f else 0.5f)
            .border(
                width = 1.dp,
                color = accent.copy(alpha = if (enabled) 0.85f else 0.30f),
                shape = shape,
            )
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        InkRipple(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (enabled) 1f else 0.35f)),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Medium,
                    color = accent.copy(alpha = if (enabled) 1f else 0.45f),
                    maxLines = 1,
                )
            }
        }
    }
}

private sealed interface RecordBrowse {
    data object All : RecordBrowse
    data class Detail(val id: Long, val fromAll: Boolean) : RecordBrowse
}

@Composable
private fun RecordBrowser(
    browse: RecordBrowse,
    records: List<TranslationEntity>,
    speakingKey: String?,
    message: String?,
    offerLocalFor: String?,
    onBack: () -> Unit,
    onOpen: (TranslationEntity) -> Unit,
    onCopy: (String) -> Unit,
    onSpeak: (TranslationEntity) -> Unit,
    onUseLocal: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onReuse: (TranslationEntity) -> Unit,
    /** 图片翻译记录里的「重新选一张」；由调用方接到选图器上。 */
    onRetakePhoto: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when (browse) {
        RecordBrowse.All -> RecordListPage(records, onBack, onOpen, modifier)
        is RecordBrowse.Detail -> {
            val record = records.firstOrNull { it.id == browse.id }
            if (record == null) {
                LaunchedEffect(browse.id) { onBack() }
            } else {
                RecordDetail(
                    record = record,
                    speaking = speakingKey == "record:${record.id}",
                    message = message,
                    offerLocalFor = offerLocalFor,
                    onBack = onBack,
                    onCopy = { onCopy(record.targetText) },
                    onSpeak = { onSpeak(record) },
                    onUseLocal = onUseLocal,
                    onDelete = { onDelete(record.id) },
                    onReuse = { onReuse(record) },
                    onRetakePhoto = if (record.kind == "PHOTO") onRetakePhoto else null,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun RecordListPage(
    records: List<TranslationEntity>,
    onBack: () -> Unit,
    onOpen: (TranslationEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) {
        records
    } else {
        records.filter { record ->
            record.sourceText.contains(query, ignoreCase = true) ||
                record.targetText.contains(query, ignoreCase = true)
        }
    }
    val palette = LocalLumoPalette.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 页内标题用印章体：和卡片上的「印」是同一套笔画，导航逻辑不动，只换外观。
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(LumoIcons.Back, contentDescription = stringResource(R.string.back))
            }
            SealedSectionTitle(text = stringResource(R.string.history))
        }
        if (records.size > SEARCH_AFTER) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_history)) },
                singleLine = true,
            )
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (filtered.isEmpty()) {
                // 空态也是一张玻璃卡：正文不直接压山水。
                GlassCard(modifier = Modifier.fillMaxWidth(), level = 0.9f) {
                    Text(
                        stringResource(if (query.isBlank()) R.string.no_history else R.string.no_search_hits),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            filtered.groupBy { recordDayLabel(it.createdAt) }.forEach { (day, items) ->
                Text(
                    day,
                    modifier = Modifier.padding(start = 4.dp),
                    style = LumoType.seal.copy(fontSize = 13.sp),
                    color = palette.cinnabar,
                )
                items.forEach { record -> RecordSummary(record, onOpen) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordDetail(
    record: TranslationEntity,
    speaking: Boolean,
    message: String?,
    offerLocalFor: String?,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onUseLocal: (String) -> Unit,
    onDelete: () -> Unit,
    onReuse: () -> Unit,
    /** 图片翻译记录专用的「重新选图」出口；非图片来源时为 null。 */
    onRetakePhoto: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var confirming by remember(record.id) { mutableStateOf(false) }
    val languages = if (record.kind == "LIVE" || record.kind == "FACE") liveLanguages else textLanguages
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 详情顶部也是一张玻璃卡：时间 / 语言 / 音色三行小字不能直接压山水。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(shape = RoundedCornerShape(20.dp), level = 0.9f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(LumoIcons.Back, contentDescription = stringResource(R.string.back))
            }
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(recordWhen(record.createdAt, withClock = true), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${languageLabel(record.sourceLang, languages)} → ${languageLabel(record.targetLang, languages)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    when (record.kind) {
                        "LIVE" -> storedVoiceLabel(record.option)
                        "PHOTO" -> record.option
                        "FACE" -> stringResource(
                            if (record.option == "ME") R.string.face_me else R.string.face_other,
                        )
                        else -> translationModelLabel(record.option)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 图片翻译只存文字：原图在识别完成后就被删掉了，
            // 这里说清楚，并提供「重新选一张」的出口，避免用户以为图丢了是 bug。
            if (record.kind == "PHOTO") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(shape = RoundedCornerShape(12.dp), level = 0.8f),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.photo_record_no_image),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (onRetakePhoto != null) {
                            TextButton(
                                onClick = onRetakePhoto,
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            ) { Text(stringResource(R.string.photo_record_retake)) }
                        }
                    }
                }
            }
            CaptionCard(stringResource(R.string.source_label), record.sourceText.ifBlank { stringResource(R.string.no_source) }, "", compact = true)
            CaptionCard(stringResource(R.string.target_label), record.targetText.ifBlank { stringResource(R.string.no_target) }, "", compact = false)
        }
        message?.let {
            ErrorCard(message = it)
            if (offerLocalFor != null) {
                TextButton(onClick = { onUseLocal(offerLocalFor) }) { Text(stringResource(R.string.use_local_once)) }
            }
        }
        if (confirming) {
            Text(stringResource(R.string.delete_record_confirm))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (record.kind == "TEXT") {
                    TextButton(onClick = onReuse) { Text(stringResource(R.string.fill_input)) }
                }
                TextButton(onClick = onCopy, enabled = record.targetText.isNotBlank()) {
                    Text(stringResource(R.string.copy_translation))
                }
                if (record.targetText.isNotBlank()) {
                    TextButton(onClick = onSpeak) {
                        Text(stringResource(if (speaking) R.string.stop_reading else R.string.read_translation))
                    }
                }
                TextButton(onClick = { confirming = true }) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun RecordSummary(record: TranslationEntity, onOpen: (TranslationEntity) -> Unit) {
    val pair = "${shortLanguageLabel(record.sourceLang)} → ${shortLanguageLabel(record.targetLang)}"
    val palette = LocalLumoPalette.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // 每条记录带一幅「那天的山」缩略图：翻历史先认画，再读字。
    val skin = LocalSkin.current
    val thumb = remember(record.id, skin) {
        SceneryCatalog.daily(record.createdAt / 86_400_000L, skin)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(20.dp), level = 0.9f)
            .clickable { onOpen(record) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = if (dark) thumb.nightRes else thumb.dayRes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 44.dp, height = 58.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier.glass(shape = RoundedCornerShape(8.dp), level = 0.8f),
                    ) {
                        Text(
                            when (record.kind) {
                                "LIVE" -> stringResource(R.string.tab_live)
                                "PHOTO" -> stringResource(R.string.tab_photo)
                                "FACE" -> stringResource(R.string.tab_face)
                                else -> stringResource(R.string.tab_text)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        recordWhen(record.createdAt, withClock = false),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        pair,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    record.targetText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                        .ifBlank { stringResource(R.string.no_target) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    record.sourceText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                        .ifBlank { stringResource(R.string.no_source) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                LumoIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun recordWhen(createdAt: Long, withClock: Boolean): String {
    val locale = LocalConfiguration.current.locales[0]
    val record = Calendar.getInstance().apply { timeInMillis = createdAt }
    val today = Calendar.getInstance()
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DATE, -1) }
    val clock = SimpleDateFormat("HH:mm", locale).format(Date(createdAt))
    val chinese = locale.language == "zh"
    return when {
        sameDay(record, today) -> clock
        sameDay(record, yesterday) -> if (withClock) stringResource(R.string.yesterday_time, clock) else stringResource(R.string.yesterday)
        withClock -> SimpleDateFormat(if (chinese) "MM-dd HH:mm" else "MMM d, HH:mm", locale).format(Date(createdAt))
        else -> SimpleDateFormat(if (chinese) "MM-dd" else "MMM d", locale).format(Date(createdAt))
    }
}

@Composable
private fun recordDayLabel(createdAt: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val record = Calendar.getInstance().apply { timeInMillis = createdAt }
    val today = Calendar.getInstance()
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DATE, -1) }
    val chinese = locale.language == "zh"
    return when {
        sameDay(record, today) -> stringResource(R.string.today)
        sameDay(record, yesterday) -> stringResource(R.string.yesterday)
        record.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
            SimpleDateFormat(if (chinese) "M月d日" else "MMM d", locale).format(Date(createdAt))
        else -> SimpleDateFormat(if (chinese) "yyyy年M月d日" else "MMM d, yyyy", locale).format(Date(createdAt))
    }
}

private fun sameDay(left: Calendar, right: Calendar): Boolean =
    left.get(Calendar.YEAR) == right.get(Calendar.YEAR) &&
        left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR)

private const val SEARCH_AFTER = 6

@Composable
private fun LanguageCard(
    source: String,
    target: String,
    trailing: String?,
    onSource: () -> Unit,
    onTarget: () -> Unit,
    onSwap: (() -> Unit)?,
    onTrailing: (() -> Unit)?,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val palette = LocalLumoPalette.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(shape = shape, level = 0.9f)
            .border(1.dp, palette.glassBorder, shape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageChip(source, onSource, Modifier.weight(1f))
            if (onSwap != null) {
                Box(
                    modifier = Modifier
                        .glass(shape = CircleShape, tone = GlassTone.Paper, level = 0.7f)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            shape = CircleShape,
                        ),
                ) {
                    IconButton(onClick = onSwap, modifier = Modifier.size(40.dp)) {
                        Icon(
                            LumoIcons.Swap,
                            contentDescription = stringResource(R.string.swap_languages),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            LanguageChip(target, onTarget, Modifier.weight(1f))
            if (trailing != null && onTrailing != null) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(palette.glassBorder),
                )
                Spacer(modifier = Modifier.width(6.dp))
                SettingPill(label = trailing, onClick = onTrailing)
            }
        }
        if (hint != null) {
            Text(
                hint,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 卡右端的小设置 chip：朱砂淡底 + 细边 + 一个小箭头，40dp 高、触达按拇指给足。 */
@Composable
private fun SettingPill(label: String, onClick: () -> Unit) {
    val palette = LocalLumoPalette.current
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .widthIn(max = 100.dp)
            .clip(shape)
            .background(palette.cinnabar.copy(alpha = 0.10f), shape)
            .border(1.dp, palette.cinnabar.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.cinnabar,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            LumoIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = palette.cinnabar,
        )
    }
}

@Composable
private fun LanguageChip(label: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CaptionCard(title: String, committed: String, partial: String, compact: Boolean) {
    // 源文是纸白、译文带一层石青：不看标题也知道哪一半是哪一半。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(
                shape = RoundedCornerShape(22.dp),
                tone = if (compact) GlassTone.Paper else GlassTone.Celadon,
                level = if (compact) 0.85f else 1f,
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (compact) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            if (committed.isNotBlank()) {
                Text(
                    committed,
                    modifier = Modifier.padding(top = 8.dp),
                    style = if (compact) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                )
            }
            if (partial.isNotBlank()) {
                Text(
                    partial,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/** 同传时的动态声波。 */
@Composable
private fun LiveWave() {
    val stroke = LocalLumoPalette.current.cinnabar
    val transition = rememberInfiniteTransition(label = "liveWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "liveWavePhase",
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(top = 4.dp),
    ) {
        val count = 32
        val gap = size.width / count
        for (i in 0 until count) {
            val wave = kotlin.math.abs(kotlin.math.sin(phase * 2f * Math.PI.toFloat() + i * 0.6f))
            val height = size.height * (0.2f + 0.8f * wave)
            drawRoundRect(
                // 同传声波也回到朱砂：不再是那块「品牌蓝」。
                color = stroke.copy(alpha = 0.55f),
                topLeft = Offset(i * gap + gap * 0.2f, (size.height - height) / 2f),
                size = Size(gap * 0.6f, height),
                cornerRadius = CornerRadius(gap * 0.3f),
            )
        }
    }
}

private enum class PickTarget { TextSource, TextTarget, LiveSource, LiveTarget, Model, Voice }

private val preferredTextCodes = listOf("zh", "en", "ja", "ko")
private val preferredLiveCodes = listOf("zh", "en", "ja", "ko")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSheet(
    target: PickTarget,
    state: TranslateUiState,
    viewModel: TranslateViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var custom by remember { mutableStateOf("") }
    var showCustom by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (target) {
                PickTarget.Model -> {
                    Text(stringResource(R.string.translation_model), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    translationModels.forEach { option ->
                        OptionRow(
                            label = stringResource(option.labelRes),
                            detail = option.id,
                            selected = state.model == option.id,
                            onClick = {
                                viewModel.selectModel(option.id)
                                onDismiss()
                            },
                        )
                    }
                    TextButton(onClick = { showCustom = !showCustom }) {
                        Text(stringResource(R.string.other_model))
                    }
                    if (showCustom) {
                        Text(
                            stringResource(R.string.other_model_detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = custom,
                            onValueChange = { custom = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.other_model)) },
                        )
                        Button(
                            onClick = {
                                viewModel.selectModel(custom)
                                onDismiss()
                            },
                            enabled = custom.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.use_this_id)) }
                    }
                }
                PickTarget.Voice -> {
                    Text(stringResource(R.string.choose_voice), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    VoiceChoice.entries.forEach { voice ->
                        OptionRow(
                            label = stringResource(voice.labelRes),
                            detail = null,
                            selected = state.liveVoice == voice,
                            onClick = {
                                viewModel.selectLiveVoice(voice)
                                onDismiss()
                            },
                        )
                    }
                }
                else -> {
                    val source = target == PickTarget.TextSource || target == PickTarget.LiveSource
                    val languages = when (target) {
                        PickTarget.TextSource -> listOf(SpeechLanguage("", R.string.lang_auto)) + preferLanguages(textLanguages, preferredTextCodes)
                        PickTarget.TextTarget -> preferLanguages(textLanguages, preferredTextCodes)
                        else -> preferLanguages(
                            liveLanguages.filter { language ->
                                source || (language.code != "yue-CN" && language.code != "sh-CN")
                            },
                            preferredLiveCodes,
                        )
                    }
                    val selected = when (target) {
                        PickTarget.TextSource -> state.textSource
                        PickTarget.TextTarget -> state.textTarget
                        PickTarget.LiveSource -> state.liveSourceLang
                        else -> state.liveTargetLang
                    }
                    languages.forEach { language ->
                        OptionRow(
                            label = stringResource(language.labelRes),
                            detail = null,
                            selected = language.code == selected,
                            onClick = {
                                when (target) {
                                    PickTarget.TextSource, PickTarget.TextTarget ->
                                        viewModel.selectTextLanguage(source, language.code)
                                    else -> viewModel.selectLiveLanguage(source, language.code)
                                }
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) {
            Icon(LumoIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun preferLanguages(languages: List<SpeechLanguage>, preferred: List<String>): List<SpeechLanguage> {
    val head = preferred.mapNotNull { code -> languages.firstOrNull { it.code == code } }
    val rest = languages.filter { it.code !in preferred }
    return head + rest
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error(getString(R.string.activity_missing))
}
