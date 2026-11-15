package com.yuguang.lumo.ui.translate

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.model.FaceDirection
import com.yuguang.lumo.domain.model.FaceTurn
import com.yuguang.lumo.domain.model.SpeechTier
import com.yuguang.lumo.domain.model.freeTranslateChain
import com.yuguang.lumo.domain.model.liveLanguages
import com.yuguang.lumo.ui.components.InkDivider
import com.yuguang.lumo.ui.components.InkDots
import com.yuguang.lumo.ui.components.glass
import com.yuguang.lumo.ui.theme.LocalMotionEnabled
import com.yuguang.lumo.ui.theme.LumoIcons
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 面对面双向同传。
 *
 * 布局取「对方在上、我方在下」，因为手机通常平放在桌上：对方看到的是倒过来的屏幕，
 * 所以对方那一栏把译文放大、原文压小，两边都能只读大字。
 *
 * 方向由一句话的语言判定，判定逻辑在 [FaceDirection]；换方向需要重启同传流，
 * 因此这里只提供一键交换，不做「说着说着自动翻面」——那需要 VAD 断句，收益不抵风险。
 *
 * 引擎不是黑盒：顶部一行写明这次用的是「豆包同声传译 2.0」还是「免费链路」，
 * 点开能看清识别 / 翻译 / 朗读各走哪条模型（免费链路是 ASR + 翻译链 + TTS 三段拼的）。
 */
@Composable
fun FaceToFaceStage(
    state: TranslateUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSwap: () -> Unit,
    onToggleLock: () -> Unit,
    onSpeak: (String) -> Unit,
    onCopy: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumoPalette.current
    val running = state.facePhase == LivePhase.Listening || state.facePhase == LivePhase.Connecting
    val freeTier = state.tier == SpeechTier.FREE
    val pending = state.facePending
    val otherPending = pending?.takeIf { it.side == FaceDirection.OTHER }
    val myPending = pending?.takeIf { it.side == FaceDirection.ME }
    // 每次 partial 都会换 state：按列表引用缓存两侧的「最后一句」，避免每帧扫一遍。
    val turns = state.faceTurns
    val otherTurn = remember(turns) { turns.lastOrNull { it.side == FaceDirection.OTHER } }
    val myTurn = remember(turns) { turns.lastOrNull { it.side == FaceDirection.ME } }

    // 回调走 rememberUpdatedState + 稳定引用：父级每帧新建 lambda 时，两张卡才跳得过重组。
    val speakState by rememberUpdatedState(onSpeak)
    val copyState by rememberUpdatedState(onCopy)
    val speak = remember { { text: String -> speakState(text) } }
    val copy = remember { { text: String -> copyState(text) } }

    // 面对面时手机平放在桌上说话，屏幕熄灭会打断整段会话：只要麦克风开着就常亮。
    KeepScreenOn(enabled = running)

    var showEngine by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FaceHeader(
            from = state.faceFrom,
            to = state.faceTo,
            locked = state.faceLocked,
            running = running,
            freeTier = freeTier,
            voiceLabel = stringResource(state.liveVoice.labelRes),
            phase = state.facePhase,
            hint = if (freeTier) stringResource(R.string.face_engine_hint_free) else null,
            onOpenEngine = { showEngine = true },
            onSwap = onSwap,
            onToggleLock = onToggleLock,
        )
        FaceCard(
            title = stringResource(R.string.face_other),
            turn = otherPending ?: otherTurn,
            placeholder = stringResource(R.string.face_other_hint),
            freeTier = freeTier,
            onSpeak = speak,
            onCopy = copy,
            modifier = Modifier.weight(1f),
        )
        ControlBar(
            running = running,
            phase = state.facePhase,
            turnCount = turns.size,
            onToggleRun = { if (running) onStop() else onStart() },
            onOpenHistory = { showHistory = true },
        )
        FaceCard(
            title = stringResource(R.string.face_me),
            turn = myPending ?: myTurn,
            placeholder = stringResource(R.string.face_me_hint),
            freeTier = freeTier,
            onSpeak = speak,
            onCopy = copy,
            modifier = Modifier.weight(1f),
        )
        if (turns.isNotEmpty()) {
            // 摘要从「只读一行字」变成入口：点开能看到本次全部句子，并可朗读/复制。
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .glass(shape = RoundedCornerShape(999.dp), level = 0.9f)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(role = SemanticsRole.Button) { showHistory = true }
                    .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.face_summary, turns.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.face_open_history),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.cinnabar,
                )
                Icon(
                    LumoIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = palette.cinnabar,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
    if (showEngine) {
        EngineSheet(
            freeTier = freeTier,
            from = state.faceFrom,
            to = state.faceTo,
            voiceLabel = stringResource(state.liveVoice.labelRes),
            onDismiss = { showEngine = false },
        )
    }
    if (showHistory) {
        FaceHistorySheet(
            turns = turns,
            onSpeak = speak,
            onCopy = copy,
            onClear = {
                showHistory = false
                onClear()
            },
            onDismiss = { showHistory = false },
        )
    }
}

/** 麦克风开着就常亮：面对面是「放着说」的场景，熄屏会直接掐掉会话。 */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** 顶部：引擎徽章 + 语对。运行时只读，避免中途换语言把已经建立的流搞乱。 */
@Composable
private fun FaceHeader(
    from: String,
    to: String,
    locked: Boolean,
    running: Boolean,
    freeTier: Boolean,
    voiceLabel: String,
    phase: LivePhase,
    hint: String?,
    onOpenEngine: () -> Unit,
    onSwap: () -> Unit,
    onToggleLock: () -> Unit,
) {
    val palette = LocalLumoPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(18.dp), level = 0.9f)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(role = SemanticsRole.Button, onClick = onOpenEngine)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(palette.cinnabar, CircleShape),
                )
                Text(
                    stringResource(if (freeTier) R.string.face_engine_free else R.string.face_engine_ast),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.cinnabar,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    LumoIcons.ChevronDown,
                    contentDescription = stringResource(R.string.face_engine_detail),
                    modifier = Modifier.size(14.dp),
                    tint = palette.cinnabar,
                )
            }
            Spacer(Modifier.weight(1f))
            if (phase == LivePhase.Connecting) {
                Text(
                    stringResource(R.string.face_connecting),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            } else {
                Text(
                    voiceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                langLabel(from),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onSwap, enabled = !running, modifier = Modifier.size(36.dp)) {
                Icon(
                    LumoIcons.Swap,
                    contentDescription = stringResource(R.string.face_swap),
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                langLabel(to),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleLock, enabled = !running) {
                Text(
                    stringResource(if (locked) R.string.face_locked else R.string.face_auto),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }
    }
}

/**
 * 中间控制条：麦克风 + 状态 + 记录入口。
 *
 * 麦克风用朱砂圆印而不是 M3 的 FilledTonalIconButton——后者取 secondaryContainer，
 * 在这套色板里是一枚发黄的圆饼，跟「押印」的语义也对不上。运行中改成纸面 + 朱砂环。
 */
@Composable
private fun ControlBar(
    running: Boolean,
    phase: LivePhase,
    turnCount: Int,
    onToggleRun: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val palette = LocalLumoPalette.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                IconButton(
                    onClick = onOpenHistory,
                    enabled = turnCount > 0,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        LumoIcons.Doc,
                        contentDescription = stringResource(R.string.face_history_title),
                        modifier = Modifier.size(20.dp),
                        tint = if (turnCount > 0) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                    )
                }
            }
            SealMicButton(running = running, onClick = onToggleRun)
            Box(modifier = Modifier.weight(1f))
        }
        // 状态小字不直接压画心：垫一层 0.9f 的玻璃药丸（浅色 level-0 只有 4.46:1，不达标）
        Text(
            when (phase) {
                LivePhase.Listening -> stringResource(R.string.face_listening)
                LivePhase.Connecting -> stringResource(R.string.face_connecting)
                LivePhase.Ended -> stringResource(R.string.face_ended)
                LivePhase.Idle -> stringResource(R.string.face_idle)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .glass(shape = RoundedCornerShape(999.dp), level = 0.9f)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** 朱砂圆印麦克风：未开始时是实印，运行中是宣纸印 + 朱砂环。 */
@Composable
private fun SealMicButton(running: Boolean, onClick: () -> Unit) {
    val palette = LocalLumoPalette.current
    val motion = LocalMotionEnabled.current
    val ring = if (!motion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "faceMic")
        val value by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "faceMicRing",
        )
        value
    }
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(
                if (running) palette.paperGlass.copy(alpha = 0.92f) else palette.cinnabar,
                CircleShape,
            )
            .border(
                width = if (running) 2.dp else 1.dp,
                color = if (running) palette.cinnabar.copy(alpha = ring) else palette.sealInk.copy(alpha = 0.30f),
                shape = CircleShape,
            )
            .clickable(role = SemanticsRole.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            LumoIcons.Mic,
            contentDescription = stringResource(if (running) R.string.face_stop else R.string.face_start),
            modifier = Modifier.size(26.dp),
            tint = if (running) palette.cinnabar else palette.sealInk,
        )
    }
}

/**
 * 一张发言卡：原文小、译文大，译文可点朗读。
 *
 * 未定稿的句子不显示「…」：按缺哪一半分别说明「识别中」/「翻译中」，
 * 免费链路一段要 3–9 秒，光一个省略号会让人以为卡死了。
 */
@Composable
private fun FaceCard(
    title: String,
    turn: FaceTurn?,
    placeholder: String,
    freeTier: Boolean,
    onSpeak: (String) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumoPalette.current
    val shape = RoundedCornerShape(18.dp)
    val active = turn?.pending == true
    val borderColor = if (active) palette.cinnabar.copy(alpha = 0.55f) else palette.glassBorder
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(shape = shape, level = 0.9f)
            .border(if (active) 1.5.dp else 1.dp, borderColor, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) palette.cinnabar else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (active) {
                Spacer(Modifier.width(6.dp))
                ActiveDot()
            }
            Spacer(Modifier.weight(1f))
            val target = turn?.targetText.orEmpty()
            if (target.isNotBlank()) {
                IconButton(onClick = { onSpeak(target) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        LumoIcons.Speaker,
                        contentDescription = stringResource(R.string.face_speak),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = { onCopy(target) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        LumoIcons.Copy,
                        contentDescription = stringResource(R.string.copy_translation),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        when {
            turn == null -> Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )

            turn.targetText.isNotBlank() -> {
                Text(
                    turn.targetText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (turn.pending) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                if (turn.sourceText.isNotBlank()) {
                    Text(
                        turn.sourceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            else -> {
                if (turn.sourceText.isNotBlank()) {
                    Text(
                        turn.sourceText,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InkDots(dotSize = 5.dp, gap = 4.dp)
                    Text(
                        stringResource(
                            if (turn.sourceText.isBlank()) R.string.face_stage_recognizing
                            else R.string.face_stage_translating,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (freeTier) {
                        Text(
                            "· " + stringResource(R.string.face_latency_free),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 正在说话的小呼吸点：比整卡闪烁克制，也不乱改字重。 */
@Composable
private fun ActiveDot() {
    val palette = LocalLumoPalette.current
    val motion = LocalMotionEnabled.current
    val alpha = if (!motion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "faceActive")
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "faceActiveAlpha",
        )
        value
    }
    Box(
        modifier = Modifier
            .size(7.dp)
            .background(palette.cinnabar.copy(alpha = alpha), CircleShape)
            .semantics {
                contentDescription = ""
            },
    )
}

@Composable
private fun langLabel(code: String): String {
    val entry = liveLanguages.firstOrNull { it.code == code }
    return if (entry != null) stringResource(entry.labelRes) else code
}

/**
 * 引擎详情：把「这次到底用的哪个模型」说清楚。
 *
 * 收费档是一段端到端 AST；免费档是 ASR + 翻译链 + TTS 三段拼出来的流水线，
 * 这里把三段各自的后端与预计延迟都列出来，省得用户以为「免费」=「同一个模型但便宜」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineSheet(
    freeTier: Boolean,
    from: String,
    to: String,
    voiceLabel: String,
    onDismiss: () -> Unit,
) {
    val palette = LocalLumoPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.face_engine_detail),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            EngineRow(
                label = stringResource(R.string.face_engine_row),
                value = stringResource(if (freeTier) R.string.face_engine_free else R.string.face_engine_ast),
                accent = true,
            )
            EngineRow(
                label = stringResource(R.string.face_pair_row),
                value = "${langLabel(from)} → ${langLabel(to)}",
            )
            InkDivider(alpha = 0.5f)
            EngineRow(
                label = stringResource(R.string.face_asr_row),
                value = stringResource(if (freeTier) R.string.face_asr_free else R.string.face_asr_ast),
            )
            EngineRow(
                label = stringResource(R.string.face_translate_row),
                value = if (freeTier) {
                    stringResource(R.string.face_translate_free) + "\n" + freeTranslateChain.joinToString(" → ")
                } else {
                    stringResource(R.string.face_translate_ast)
                },
            )
            EngineRow(
                label = stringResource(R.string.face_tts_row),
                value = if (freeTier) {
                    stringResource(R.string.face_tts_free)
                } else {
                    stringResource(R.string.face_tts_ast, voiceLabel)
                },
            )
            EngineRow(
                label = stringResource(R.string.face_latency_row),
                value = stringResource(if (freeTier) R.string.face_latency_free else R.string.face_latency_ast),
                accent = freeTier,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.close), color = palette.cinnabar)
            }
        }
    }
}

@Composable
private fun EngineRow(label: String, value: String, accent: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (accent) {
                LocalLumoPalette.current.cinnabar
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 本次对话：全部句子按时间顺序，逐句可朗读 / 复制。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaceHistorySheet(
    turns: List<FaceTurn>,
    onSpeak: (String) -> Unit,
    onCopy: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalLumoPalette.current
    val time = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.face_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (turns.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.face_history_clear), color = palette.cinnabar)
                    }
                }
            }
            if (turns.isEmpty()) {
                Text(
                    stringResource(R.string.face_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(turns, key = { "${it.side}-${it.createdAt}" }) { turn ->
                        val mine = turn.side == FaceDirection.ME
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glass(shape = RoundedCornerShape(14.dp), level = 0.9f)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(if (mine) R.string.face_me else R.string.face_other),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mine) palette.cinnabar else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    time.format(Date(turn.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                if (turn.targetText.isNotBlank()) {
                                    IconButton(
                                        onClick = { onSpeak(turn.targetText) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            LumoIcons.Speaker,
                                            contentDescription = stringResource(R.string.face_speak),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { onCopy(turn.targetText) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            LumoIcons.Copy,
                                            contentDescription = stringResource(R.string.copy_translation),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                turn.targetText.ifBlank { turn.sourceText },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (turn.sourceText.isNotBlank() && turn.targetText.isNotBlank()) {
                                Text(
                                    turn.sourceText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.close), color = palette.cinnabar)
            }
        }
    }
}
