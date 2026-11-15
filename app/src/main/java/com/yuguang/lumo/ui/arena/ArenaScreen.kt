package com.yuguang.lumo.ui.arena

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuguang.lumo.R
import com.yuguang.lumo.ui.components.GlassChip
import com.yuguang.lumo.ui.components.GlassTopBar
import com.yuguang.lumo.ui.components.glass
import com.yuguang.lumo.ui.theme.LumoIcons
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.domain.ArenaRun
import com.yuguang.lumo.domain.ArenaSide
import com.yuguang.lumo.domain.ArenaSlot
import com.yuguang.lumo.domain.ArenaTally
import com.yuguang.lumo.domain.model.MessageStatus
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.ui.chat.MarkdownText
import com.yuguang.lumo.ui.chat.estimateCost
import com.yuguang.lumo.ui.chat.formatCost
import com.yuguang.lumo.ui.localizedSummary
import com.yuguang.lumo.ui.model.ModelPickerDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 多模型擂台：同一问题两栏并排流式，结束后投票，历史可回看。
 *
 * 布局按宽度切换：宽屏左右两栏方便对照，窄屏上下堆叠保证每个字都读得清。
 */
@Composable
fun ArenaScreen(
    onBack: () -> Unit,
    viewModel: ArenaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pickerSide by remember { mutableStateOf<ArenaSide?>(null) }
    var pendingDelete by remember { mutableStateOf<ArenaRun?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.errors.collect { message -> snackbar.showSnackbar(message) }
    }

    Scaffold(
        // 透明底：山水由外壳统一铺，擂台页只在上面叠玻璃卡。
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.arena_title),
                onBack = onBack,
                actions = {
                    // 本局累计 token 挪到标题栏右侧：不占正文高度，鎏金小字提示「这一局花了多少」。
                    val shown = state.shownRun
                    if (shown != null) {
                        Text(
                            stringResource(R.string.arena_total_tokens, ArenaTally.runTokens(shown)),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLumoPalette.current.gold,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ModelSelectors(state = state, onPick = { pickerSide = it }) }

            item {
                PromptBar(
                    prompt = state.prompt,
                    running = state.running,
                    onPromptChange = viewModel::updatePrompt,
                    onStart = viewModel::start,
                    onStop = viewModel::stop,
                )
            }

            val shown = state.shownRun
            if (shown != null) {
                if (state.isViewingHistory) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.arena_viewing_history),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = viewModel::closeHistory) {
                                Text(stringResource(R.string.arena_replay))
                            }
                        }
                    }
                }
                item { RunHeader(run = shown) }
                item { SlotsBoard(run = shown) }
                item {
                    VoteRow(
                        run = shown,
                        enabled = state.canVote,
                        onVote = { winner -> viewModel.vote(shown.id, winner) },
                        onReplay = { viewModel.replay(shown) },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .glass(shape = RoundedCornerShape(14.dp), level = 1f)
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.arena_history),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.runs.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text(stringResource(R.string.arena_clear_history))
                        }
                    }
                }
            }

            if (state.runs.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.arena_history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(state.runs, key = { run -> "arena-run-" + run.id }) { run ->
                    HistoryRow(
                        run = run,
                        onClick = { viewModel.openRun(run.id) },
                        onLongClick = { pendingDelete = run },
                    )
                }
            }
        }
    }

    pickerSide?.let { side ->
        ModelPickerDialog(
            title = stringResource(if (side == ArenaSide.A) R.string.arena_model_a else R.string.arena_model_b),
            providers = ProviderId.entries,
            selectedProvider = if (side == ArenaSide.A) state.providerA else state.providerB,
            selectedId = if (side == ArenaSide.A) state.modelA else state.modelB,
            catalogByProvider = state.catalogs,
            // 生图模型在擂台里没有意义：两栏要能直接对照文字回答。
            includeImageGen = false,
            onSelect = { provider, model ->
                pickerSide = null
                viewModel.selectModel(side, provider, model)
            },
            onDismiss = { pickerSide = null },
        )
    }

    pendingDelete?.let { run ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.arena_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(run.prompt, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.arena_delete_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(run.id)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.arena_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.arena_cancel)) }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.arena_clear_history)) },
            text = { Text(stringResource(R.string.arena_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        confirmClear = false
                    },
                ) { Text(stringResource(R.string.arena_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.arena_cancel)) }
            },
        )
    }
}

@Composable
private fun ModelSelectors(state: ArenaUiState, onPick: (ArenaSide) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SelectorCard(
            label = stringResource(R.string.arena_model_a),
            provider = state.providerA,
            model = state.modelA,
            modifier = Modifier.weight(1f),
            onClick = { onPick(ArenaSide.A) },
        )
        SelectorCard(
            label = stringResource(R.string.arena_model_b),
            provider = state.providerB,
            model = state.modelB,
            modifier = Modifier.weight(1f),
            onClick = { onPick(ArenaSide.B) },
        )
    }
}

@Composable
private fun SelectorCard(
    label: String,
    provider: ProviderId,
    model: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .glass(shape = RoundedCornerShape(18.dp), level = 0.9f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            provider.localizedSummary(model),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PromptBar(
    prompt: String,
    running: Boolean,
    onPromptChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.arena_prompt_hint)) },
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(18.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (running) {
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Icon(LumoIcons.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.arena_stop))
                }
            } else {
                InkSealButton(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    icon = LumoIcons.Play,
                    label = stringResource(R.string.arena_fight),
                )
            }
        }
    }
}

/**
 * 墨押按钮：宣纸玻璃底 + 一道石青细边 + 石青字，与翻译页「开始同传」、
 * 语音页「开始对话」同一门语言。M3 的实心 Button 在夜色纸面下会变成
 * 一块薄荷色实条，最伤整套配色，所以擂台这一颗主行动改用它。
 */
@Composable
private fun InkSealButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(999.dp))
            .glass(shape = RoundedCornerShape(999.dp), level = 1f)
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .clickable(role = SemanticsRole.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent),
        )
        Spacer(Modifier.size(8.dp))
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = accent)
            Spacer(Modifier.size(6.dp))
        }
        Text(text = label, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RunHeader(run: ArenaRun) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(16.dp), level = 0.8f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                run.prompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                formatWhen(run.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 两栏：宽屏左右，窄屏上下——窄屏并排会把每行挤成两三个字。 */
@Composable
private fun SlotsBoard(run: ArenaRun) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val sideBySide = maxWidth >= 560.dp
        if (sideBySide) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SlotCard(
                    side = ArenaSide.A,
                    slot = run.slotA,
                    winner = ArenaTally.normalizeWinner(run.winner),
                    modifier = Modifier.weight(1f),
                )
                SlotCard(
                    side = ArenaSide.B,
                    slot = run.slotB,
                    winner = ArenaTally.normalizeWinner(run.winner),
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SlotCard(
                    side = ArenaSide.A,
                    slot = run.slotA,
                    winner = ArenaTally.normalizeWinner(run.winner),
                    modifier = Modifier.fillMaxWidth(),
                )
                SlotCard(
                    side = ArenaSide.B,
                    slot = run.slotB,
                    winner = ArenaTally.normalizeWinner(run.winner),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SlotCard(
    side: ArenaSide,
    slot: ArenaSlot,
    winner: String,
    modifier: Modifier = Modifier,
) {
    val won = ArenaTally.winnerSide(winner) == side
    val border = when {
        won -> MaterialTheme.colorScheme.primary
        slot.status == MessageStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .glass(shape = shape, level = if (won) 1f else 0.85f)
            .border(width = 1.dp, color = border, shape = shape),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (side == ArenaSide.A) R.string.arena_model_a else R.string.arena_model_b),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                if (won) {
                    Icon(
                        LumoIcons.Trophy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                slot.provider.localizedSummary(slot.model),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                stringResource(R.string.arena_stat_ttft, ArenaTally.formatTtft(slot.ttftMs)) +
                    " · " +
                    stringResource(R.string.arena_stat_tokens, ArenaTally.slotTokens(slot)) +
                    " · " +
                    stringResource(
                        R.string.arena_stat_cost,
                        formatCost(
                            estimateCost(slot.provider, slot.model, slot.promptTokens, slot.completionTokens),
                        ),
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            when (slot.status) {
                MessageStatus.STREAMING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.arena_waiting),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (slot.content.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        MarkdownText(text = slot.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                MessageStatus.FAILED -> {
                    Text(
                        slot.error.ifBlank { stringResource(R.string.arena_side_failed) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (slot.content.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        MarkdownText(text = slot.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                MessageStatus.DONE -> {
                    if (slot.reasoning.isNotBlank()) {
                        ReasoningToggle(slot.reasoning)
                    }
                    if (slot.content.isBlank()) {
                        Text(
                            stringResource(R.string.empty_reply),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MarkdownText(text = slot.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningToggle(reasoning: String) {
    var open by remember(reasoning) { mutableStateOf(false) }
    Column {
        TextButton(
            onClick = { open = !open },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        ) {
            Text(
                stringResource(if (open) R.string.arena_hide_reasoning else R.string.arena_show_reasoning),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (open) {
            Text(
                reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun VoteRow(
    run: ArenaRun,
    enabled: Boolean,
    onVote: (String) -> Unit,
    onReplay: () -> Unit,
) {
    val winner = ArenaTally.normalizeWinner(run.winner)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoteChip(
                label = stringResource(R.string.arena_vote_a),
                selected = winner == ArenaTally.WINNER_A,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) { onVote(ArenaTally.WINNER_A) }
            VoteChip(
                label = stringResource(R.string.arena_vote_tie),
                selected = winner == ArenaTally.WINNER_TIE,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) { onVote(ArenaTally.WINNER_TIE) }
            VoteChip(
                label = stringResource(R.string.arena_vote_b),
                selected = winner == ArenaTally.WINNER_B,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) { onVote(ArenaTally.WINNER_B) }
        }
        FilledTonalButton(onClick = onReplay, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.arena_replay))
        }
    }
}

@Composable
private fun VoteChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassChip(
        text = label,
        selected = selected,
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(run: ArenaRun, onClick: () -> Unit, onLongClick: () -> Unit) {
    val winner = ArenaTally.normalizeWinner(run.winner)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(shape = RoundedCornerShape(18.dp), level = 0.85f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                run.prompt.replace('\n', ' ').trim(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatWhen(run.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    winnerLabel(winner),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (winner == ArenaTally.WINNER_NONE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun winnerLabel(winner: String): String = when (winner) {
    ArenaTally.WINNER_A -> stringResource(R.string.arena_vote_a)
    ArenaTally.WINNER_B -> stringResource(R.string.arena_vote_b)
    ArenaTally.WINNER_TIE -> stringResource(R.string.arena_vote_tie)
    else -> stringResource(R.string.arena_history)
}

@Composable
private fun formatWhen(epoch: Long): String {
    val locale = Locale.getDefault()
    val pattern = if (isSameDay(epoch)) "HH:mm" else "MM-dd HH:mm"
    return SimpleDateFormat(pattern, locale).format(Date(epoch))
}

private fun isSameDay(epoch: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = epoch }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}
