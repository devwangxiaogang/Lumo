package com.yuguang.lumo.ui.voice

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.model.VoiceChoice
import com.yuguang.lumo.ui.backdrop.BackdropHost
import com.yuguang.lumo.ui.chat.SpeechInput
import com.yuguang.lumo.ui.chat.VoicePhase
import com.yuguang.lumo.ui.components.glass
import com.yuguang.lumo.ui.theme.AiColors
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LumoIcons
import com.yuguang.lumo.ui.theme.LocalMotionEnabled
import com.yuguang.lumo.ui.theme.LumoType
import com.yuguang.lumo.ui.theme.Scene
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** 语音页配色随主题切换：浅色暖白 + 靛紫光晕，深色深空蓝 + 靛紫光晕。 */
private data class VoicePalette(
    val bgTop: Color,
    val bgBottom: Color,
    val glow: Color,
    val card: Color,
    val cardSoft: Color,
    val accent: Color,
    val accentDeep: Color,

    /** 石青：冷色一侧，光球与次要文字。 */
    val celadon: Color,

    /** 鎏金：只在提示与刻度上用一点。 */
    val gold: Color,

    /** 压暗层：页面自己的「更深一层墨」。 */
    val veil: Color,

    /** 宣纸盘：光球的底，比页面纸色再亮一分。 */
    val disc: Color,

    /** 盘上墨色：波形与模型名，不再用白字压色块。 */
    val discInk: Color,
    val text: Color,
    val textDim: Color,
    val online: Color,
    val border: Color,
    val isDark: Boolean,
)

@Composable
private fun rememberVoicePalette(): VoicePalette {
    val cs = MaterialTheme.colorScheme
    val lumo = LocalLumoPalette.current
    val dark = cs.background.luminance() < 0.5f
    // 墨线：波形与正文都用它，压暗一层之后依旧读得清。
    val ink = if (dark) AiColors.DarkInk else AiColors.LightInk
    val celadon = cs.primary
    val cinnabar = lumo.cinnabar
    return VoicePalette(
        // 背景交给固定的 falls 山水，这里不再自带渐变。
        bgTop = Color.Transparent,
        bgBottom = Color.Transparent,
        glow = celadon,
        card = lumo.paperGlass.copy(alpha = 0.72f),
        cardSoft = lumo.paperGlass.copy(alpha = 0.86f),
        accent = cinnabar,
        accentDeep = lerp(cinnabar, Color.Black, 0.22f),
        celadon = celadon,
        gold = lumo.gold,
        text = ink,
        textDim = if (dark) AiColors.DarkSub else AiColors.LightSub,
        online = AiColors.Success,
        border = lumo.glassBorder,
        // 青绿山水的色块比水墨重，压暗层留 0.16 才能让中央光球与按钮站住，
        // 又不至于把画压成一块灰。
        veil = ink.copy(alpha = 0.16f),
        disc = if (dark) Color(0xFF1B1915) else Color(0xFFF5EDDD),
        discInk = if (dark) Color(0xFFF2EADA) else Color(0xFF1E1B16),
        isDark = dark,
    )
}

/**
 * 语音对话页（光球 · 电话感）：
 * 顶部状态 + 中央光球 + 字幕层 + 底部按住说话，主题自适应。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatScreen(
    onBack: () -> Unit,
    viewModel: VoiceChatViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val palette = rememberVoicePalette()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var voicePhase by remember { mutableStateOf(VoicePhase.Idle) }
    var localError by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) localError = context.getString(R.string.need_mic_live)
    }
    val speech = remember {
        SpeechInput(
            context = context,
            onText = { spoken -> viewModel.sendText(spoken) },
            onPhase = { phase -> voicePhase = phase },
            onError = { message -> localError = message },
            recognize = viewModel::recognize,
        )
    }
    DisposableEffect(speech) {
        onDispose { speech.destroy() }
    }
    val speakingNow = state.holding || state.speaking || voicePhase == VoicePhase.Listening
    val level by animateFloatAsState(
        targetValue = state.amplitude,
        animationSpec = tween(120),
        label = "voiceLevel",
    )
    BackHandler {
        when {
            showTextInput -> showTextInput = false
            state.connected || state.connecting -> viewModel.stopSession()
            else -> onBack()
        }
    }
    LaunchedEffect(state.userText, state.assistantText, state.partialUserText, state.error) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    // 语音页固定在「青绿」：八张里画面最完整的一张，绢纹不糊；再自己压一层薄墨，
    // 让宣纸光球浮在山水之上，而不是和褐色绢纹糊在一起。
    // 注意：SceneryCatalog.forRoute("voice") 仍是 Falls（那是「随页面」的取景），这里只改本页固定景。
    BackdropHost(scene = Scene.Qinglu) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.veil),
        )
        // 顶部光晕，给光球一个落点
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp),
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.glow.copy(alpha = if (palette.isDark) 0.16f else 0.10f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.52f),
                    radius = size.width * 0.62f,
                ),
                radius = size.width * 0.62f,
                center = Offset(size.width / 2f, size.height * 0.52f),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 18.dp),
        ) {
            // 顶栏：标题 + 状态 + 结束
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 标题垫一小片玻璃：山水换到青绿之后画面更满，字直接压上去会花。
                Box(
                    modifier = Modifier
                        .glass(shape = RoundedCornerShape(12.dp), level = 0.9f)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.voice_chat_title),
                        color = palette.text,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.weight(1f))
                StatusChip(connected = state.connected, connecting = state.connecting, palette = palette)
                if (state.connected) {
                    IconButton(onClick = viewModel::stopSession) {
                        Icon(
                            LumoIcons.Stop,
                            contentDescription = stringResource(R.string.voice_stop),
                            tint = palette.accent.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            if (!state.connected) {
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VoiceOrb(active = false, connected = false, size = 148.dp, amplitude = level, palette = palette)
                    Spacer(Modifier.height(18.dp))
                    // 两行说明放在宣纸玻璃上：淡背景被加强后，直接压山水读不清。
                    Column(
                        modifier = Modifier
                            .glass(shape = RoundedCornerShape(20.dp), level = 0.9f)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.voice_idle_title),
                            color = palette.text,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.voice_idle_subtitle),
                            color = palette.textDim,
                            fontSize = 12.sp,
                        )
                    }
                }
                (state.error ?: localError)?.let { error ->
                    Spacer(Modifier.height(14.dp))
                    com.yuguang.lumo.ui.components.ErrorCard(
                        message = error,
                        onRetry = {
                            localError = null
                            viewModel.startSession()
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                StartButton(connecting = state.connecting, palette = palette) { viewModel.startSession() }
                Spacer(Modifier.height(12.dp))
                QuickChips(
                    state = state,
                    palette = palette,
                    onOpenSettings = { showSettings = true },
                    onToggleText = { showTextInput = !showTextInput },
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VoiceOrb(active = speakingNow, connected = true, size = 96.dp, amplitude = level, palette = palette)
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .glass(shape = RoundedCornerShape(18.dp), level = 0.9f)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            statusText(state, voicePhase),
                            color = palette.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            stringResource(R.string.voice_duration, formatDuration(state.durationSec)),
                            color = palette.textDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (state.freeTier) {
                    // 免费档常驻一行说明：逐轮对话 + 无端到端实时，避免被误当成卡顿。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .glass(shape = RoundedCornerShape(14.dp), level = 0.9f),
                    ) {
                        Text(
                            stringResource(R.string.free_voice_hint),
                            color = palette.celadon,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                } else if (state.fallback) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .glass(shape = RoundedCornerShape(14.dp), level = 0.9f),
                    ) {
                        Text(
                            stringResource(R.string.voice_fallback_hint),
                            color = palette.gold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val blank = state.userText.isBlank() && state.assistantText.isBlank() &&
                        state.partialUserText.isBlank() && !state.connecting && !state.holding &&
                        !state.speaking && voicePhase == VoicePhase.Idle
                    if (blank) {
                        item {
                            Text(
                                stringResource(R.string.voice_hold_hint_short),
                                color = palette.textDim,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    if (state.userText.isNotBlank()) {
                        item { Bubble(stringResource(R.string.voice_you), state.userText, mine = true, palette = palette) }
                    }
                    if (state.partialUserText.isNotBlank()) {
                        item {
                            Bubble(
                                stringResource(R.string.voice_you),
                                state.partialUserText,
                                mine = true,
                                streaming = true,
                                palette = palette,
                            )
                        }
                    }
                    if (state.assistantText.isNotBlank()) {
                        item { Bubble(stringResource(R.string.voice_assistant), state.assistantText, mine = false, streaming = state.speaking, palette = palette) }
                    }
                    if (state.connecting) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.accent)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.voice_connecting), color = palette.textDim, fontSize = 13.sp)
                            }
                        }
                    }
                    val error = state.error ?: localError
                    if (error != null) {
                        item {
                            com.yuguang.lumo.ui.components.ErrorCard(
                                message = error,
                                onRetry = { localError = null },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(2.dp)) }
                }
                Waveform(active = speakingNow, amplitude = level, palette = palette)
                if (showTextInput) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                stringResource(if (state.fallback) R.string.voice_type_hint else R.string.voice_greeting_hint),
                                color = palette.textDim,
                                fontSize = 13.sp,
                            )
                        },
                        maxLines = 2,
                        shape = RoundedCornerShape(20.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.text,
                            unfocusedTextColor = palette.text,
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border,
                            cursorColor = palette.accent,
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    viewModel.sendText(input)
                                    input = ""
                                },
                                enabled = input.isNotBlank(),
                            ) {
                                Icon(LumoIcons.Send, contentDescription = stringResource(R.string.send), tint = palette.accent)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                HoldButton(
                    listening = state.holding || voicePhase == VoicePhase.Listening,
                    recognizing = voicePhase == VoicePhase.Recognizing,
                    palette = palette,
                    onPress = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                            false
                        } else {
                            localError = null
                            if (state.fallback) speech.start("") else viewModel.pressStart()
                            true
                        }
                    },
                    onRelease = {
                        if (state.fallback) speech.stop() else viewModel.pressEnd()
                    },
                )
                Text(
                    stringResource(
                        if (state.holding || voicePhase == VoicePhase.Listening) R.string.voice_hold_hint
                        else R.string.voice_interrupt_hint,
                    ),
                    color = palette.textDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                QuickChips(
                    state = state,
                    palette = palette,
                    onOpenSettings = { showSettings = true },
                    onToggleText = { showTextInput = !showTextInput },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showSettings) {
        VoiceSettingsSheet(
            voice = state.voice,
            speed = state.speed,
            connected = state.connected,
            palette = palette,
            onVoice = viewModel::setVoice,
            onSpeed = viewModel::setSpeed,
            onApply = viewModel::applyAudioSettings,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun StatusChip(connected: Boolean, connecting: Boolean, palette: VoicePalette) {
    val dot = when {
        connected -> palette.online
        connecting -> palette.gold
        else -> palette.textDim
    }
    val bg = when {
        connected -> palette.online.copy(alpha = 0.14f)
        connecting -> palette.gold.copy(alpha = 0.16f)
        else -> palette.cardSoft
    }
    val label = when {
        connected -> stringResource(R.string.voice_status_connected)
        connecting -> stringResource(R.string.voice_status_connecting)
        else -> stringResource(R.string.voice_status_offline)
    }
    Box(Modifier.glass(shape = CircleShape, level = 0.9f).background(bg, CircleShape)) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, color = dot, fontSize = 11.sp)
        }
    }
}

@Composable
private fun VoiceOrb(active: Boolean, connected: Boolean, size: Dp, amplitude: Float, palette: VoicePalette) {
    val motion = LocalMotionEnabled.current
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1400), repeatMode = RepeatMode.Restart),
        label = "pulse",
    )
    val barPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "barPhase",
    )
    // 朱砂细环：待机是七分朱砂，说话时满色并随音量加粗。
    val ring = if (active && connected) palette.accent else palette.accent.copy(alpha = 0.72f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val drift = if (motion) pulse else 0.5f
            // 外圈呼吸光晕：石青 12% 起，只在发声时更亮 —— 不抢宣纸盘的墨。
            val glow = if (active && connected) {
                0.12f + 0.10f * (1f - drift) + 0.10f * amplitude
            } else {
                0.12f + 0.05f * (1f - drift)
            }
            drawCircle(palette.celadon.copy(alpha = glow), radius = r * (0.80f + 0.18f * drift))
            // 宣纸圆盘
            drawCircle(palette.disc, radius = r * 0.66f)
            drawCircle(palette.discInk.copy(alpha = 0.06f), radius = r * 0.66f, style = Stroke(width = 1.dp.toPx()))
            val ringWidth = (2f + if (active && connected) amplitude * 1.6f else 0f) * density
            drawCircle(ring, radius = r * 0.66f, style = Stroke(width = ringWidth))
            // 音量条：墨线，九道短笔
            val bars = 9
            val centerY = this.size.height * 0.67f
            for (i in 0 until bars) {
                val phase = if (motion) barPhase * 2f * PI.toFloat() + i * 0.9f else i * 0.9f
                val wave = abs(sin(phase))
                val energy = if (active) (0.45f + amplitude * 1.8f) else 0.9f
                val h = r * (0.07f + 0.19f * wave) * energy
                val x = this.size.width / 2f + (i - (bars - 1) / 2f) * r * 0.125f
                drawRoundRect(
                    color = palette.discInk.copy(alpha = if (active) 0.80f else 0.46f),
                    topLeft = Offset(x - r * 0.026f, centerY - h / 2f),
                    size = androidx.compose.ui.geometry.Size(r * 0.052f, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.026f),
                )
            }
        }
        Text(
            stringResource(R.string.voice_assistant),
            color = palette.discInk.copy(alpha = 0.92f),
            style = LumoType.poem.copy(fontSize = if (size > 120.dp) 21.sp else 15.sp),
            modifier = Modifier.offset(y = -(size * 0.11f)),
        )
    }
}

@Composable
private fun Waveform(active: Boolean, amplitude: Float, palette: VoicePalette) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1300, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "phase",
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
    ) {
        val count = 30
        val gap = size.width / count
        for (i in 0 until count) {
            val wave = abs(sin(phase * 2f * PI.toFloat() + i * 0.55f))
            val amp = if (active) (0.10f + 0.90f * wave) * (0.45f + amplitude * 1.6f) else 0.08f + 0.10f * wave
            val h = size.height * amp
            drawRoundRect(
                // 波形改用墨线：不再是一种「品牌色」，而是画在宣纸上的一道笔痕。
                color = palette.text.copy(alpha = if (active) 0.82f else 0.26f),
                topLeft = Offset(i * gap + gap * 0.2f, (size.height - h) / 2f),
                size = androidx.compose.ui.geometry.Size(gap * 0.6f, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(gap * 0.3f),
            )
        }
    }
}

@Composable
private fun Bubble(
    label: String,
    text: String,
    mine: Boolean,
    palette: VoicePalette,
    streaming: Boolean = false,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (mine) 0.84f else 0.9f)
                .pointerInput(label, text, mine) {
                    detectTapGestures(
                        onLongPress = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("voice-chat", text))
                            Toast.makeText(context, R.string.voice_copy_done, Toast.LENGTH_SHORT).show()
                        },
                    )
                },
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (mine) 20.dp else 6.dp,
                bottomEnd = if (mine) 6.dp else 20.dp,
            ),
            color = if (mine) palette.accentDeep else palette.card,
            border = if (mine) null else androidx.compose.foundation.BorderStroke(1.dp, palette.border.copy(alpha = 0.6f)),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    label,
                    color = if (mine) Color.White.copy(alpha = 0.75f) else palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (streaming) "$text ▍" else text,
                    color = if (mine) Color.White else palette.text,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StartButton(connecting: Boolean, palette: VoicePalette, onClick: () -> Unit) {
    // 墨押按钮：宣纸玻璃底 + 一圈朱砂边，不再是整宽实心色条。
    val shape = RoundedCornerShape(999.dp)
    val accent = palette.accent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .glass(shape = shape, level = if (connecting) 0.55f else 0.95f)
            .border(1.dp, accent.copy(alpha = if (connecting) 0.32f else 0.78f), shape)
            .clip(shape)
            .pointerInput(connecting) { detectTapGestures { if (!connecting) onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = accent.copy(alpha = 0.85f),
                )
                Spacer(Modifier.width(10.dp))
            } else {
                Box(Modifier.size(6.dp).background(accent, CircleShape))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                stringResource(if (connecting) R.string.voice_connecting else R.string.voice_start),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = accent,
            )
        }
    }
}

@Composable
private fun QuickChips(
    state: VoiceChatUiState,
    palette: VoicePalette,
    onOpenSettings: () -> Unit,
    onToggleText: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // 图标 + 短标签：一行能放下三个，长按说话时不用读长句。
        Chip(
            icon = LumoIcons.Voice,
            label = stringResource(state.voice.labelRes),
            palette = palette,
            onClick = onOpenSettings,
        )
        Chip(
            icon = LumoIcons.Play,
            label = stringResource(R.string.voice_settings_speed, state.speed),
            palette = palette,
            onClick = onOpenSettings,
        )
        Chip(
            icon = LumoIcons.Chat,
            label = stringResource(R.string.voice_typing),
            palette = palette,
            onClick = onToggleText,
        )
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String, palette: VoicePalette, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .glass(shape = CircleShape, level = 0.8f)
            .clip(CircleShape)
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = palette.celadon, modifier = Modifier.size(14.dp))
        Text(label, color = palette.celadon, fontSize = 12.sp)
    }
}

@Composable
private fun HoldButton(
    listening: Boolean,
    recognizing: Boolean,
    palette: VoicePalette,
    onPress: () -> Boolean,
    onRelease: () -> Unit,
) {
    // 按住说话：待按是石青描边的宣纸玻璃，按住转朱砂实印（正在录音）。
    val shape = RoundedCornerShape(999.dp)
    val accent = if (listening) palette.accent else palette.celadon
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .glass(shape = shape, level = if (listening) 0.35f else 0.95f)
            .background(if (listening) palette.accent else Color.Transparent, shape)
            .border(1.dp, accent.copy(alpha = if (listening) 1f else 0.55f), shape)
            .clip(shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (!onPress()) return@detectTapGestures
                        tryAwaitRelease()
                        onRelease()
                    },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            LumoIcons.Mic,
            contentDescription = null,
            tint = if (listening) palette.disc else palette.celadon,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(
                when {
                    listening -> R.string.release_to_stop
                    recognizing -> R.string.recognizing
                    else -> R.string.hold_to_talk
                },
            ),
            color = if (listening) palette.disc else palette.celadon,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSettingsSheet(
    voice: VoiceChoice,
    speed: Int,
    connected: Boolean,
    palette: VoicePalette,
    onVoice: (VoiceChoice) -> Unit,
    onSpeed: (Int) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.card) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.voice_settings_title), color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.voice_settings_tone), color = palette.textDim, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VoiceChoice.entries.filter { it != VoiceChoice.CLONE }.forEach { choice ->
                    val selected = voice == choice
                    Box(
                        modifier = Modifier
                            .glass(shape = RoundedCornerShape(20.dp), level = if (selected) 0.35f else 0.9f)
                            .background(if (selected) palette.accent else Color.Transparent, RoundedCornerShape(20.dp))
                            .border(
                                1.dp,
                                if (selected) palette.accent else palette.border,
                                RoundedCornerShape(20.dp),
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .pointerInput(choice) {
                                detectTapGestures {
                                    onVoice(choice)
                                    if (connected) onApply()
                                }
                            },
                    ) {
                        Text(
                            stringResource(choice.labelRes),
                            color = if (selected) palette.disc else palette.textDim,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            Text(stringResource(R.string.voice_settings_speed, speed), color = palette.textDim, fontSize = 13.sp)
            Slider(
                value = speed.toFloat(),
                onValueChange = { onSpeed(it.toInt()) },
                onValueChangeFinished = { if (connected) onApply() },
                valueRange = -50f..100f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.accent,
                    inactiveTrackColor = palette.cardSoft,
                ),
            )
            if (connected) {
                Text(stringResource(R.string.voice_settings_applied), color = palette.online, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .glass(shape = RoundedCornerShape(999.dp), level = 0.95f)
                    .border(1.dp, palette.accent.copy(alpha = 0.78f), RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.voice_settings_done), color = palette.accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun statusText(state: VoiceChatUiState, voicePhase: VoicePhase): String = when {
    state.connecting -> stringResource(R.string.voice_connecting)
    state.holding || voicePhase == VoicePhase.Listening -> stringResource(R.string.voice_listening)
    state.speaking -> stringResource(R.string.voice_speaking)
    state.fallback -> stringResource(R.string.voice_status_fallback)
    else -> stringResource(R.string.voice_status_connected_hint)
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
