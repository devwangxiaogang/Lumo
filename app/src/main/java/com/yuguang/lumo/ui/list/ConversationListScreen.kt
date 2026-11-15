package com.yuguang.lumo.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yuguang.lumo.R
import com.yuguang.lumo.data.local.SearchHitRow
import com.yuguang.lumo.domain.model.Conversation
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.ui.backdrop.BackdropHost
import com.yuguang.lumo.ui.components.GlassCard
import com.yuguang.lumo.ui.components.GlassTone
import com.yuguang.lumo.ui.components.InkDivider
import com.yuguang.lumo.ui.components.InkRipple
import com.yuguang.lumo.ui.components.PoemLine
import com.yuguang.lumo.ui.components.PressScale
import com.yuguang.lumo.ui.components.SealedSectionTitle
import com.yuguang.lumo.ui.components.glass
import com.yuguang.lumo.ui.localizedSummary
import com.yuguang.lumo.ui.model.ModelPickerDialog
import com.yuguang.lumo.ui.shell.AppSection
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LumoIcons
import com.yuguang.lumo.ui.theme.LumoType
import com.yuguang.lumo.ui.theme.Scene
import com.yuguang.lumo.ui.theme.SceneryCatalog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 首页题跋用的四句诗：一天换一句，同一个方向上的人读到同一句。 */
private val HOME_POEMS = intArrayOf(
    R.string.home_poem_1,
    R.string.home_poem_2,
    R.string.home_poem_3,
    R.string.home_poem_4,
)

/** 按「第几天」取诗，不用 java.time（minSdk 24 上不必引 desugar）。 */
private fun dailyPoemRes(epochMillis: Long = System.currentTimeMillis()): Int =
    HOME_POEMS[((epochMillis / 86_400_000L) % HOME_POEMS.size).toInt().let { if (it < 0) 0 else it }]

/** 会话缩略图取哪幅景：按 id 定，同一条会话永远是同一幅，不会跳。 */
private fun sceneForConversation(id: Long): Scene {
    val all = SceneryCatalog.all
    val index = ((id % all.size) + all.size) % all.size
    return all[index.toInt()]
}

/** 深色主题下换夜景底图：跟 GlassTopBar 用同一个判据，不额外引依赖。 */
@Composable
private fun isNight(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** 对话首页：题跋、艺术卡入口、置顶/今天/更早分组，长按管理会话。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onOpenChat: (Long) -> Unit,
    onNavigate: (AppSection) -> Unit,
    onOpenBackup: () -> Unit,
    onOpenArena: () -> Unit = {},
    viewModel: ConversationListViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val catalogs by viewModel.catalogs.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.refreshStarting() }

    val poemRes = remember { dailyPoemRes() }

    val filtered = conversations.filter { conversation ->
        if (query.isBlank()) return@filter true
        val needle = query.trim()
        conversation.title.contains(needle, ignoreCase = true) ||
            conversation.preview?.contains(needle, ignoreCase = true) == true ||
            conversation.provider.localizedSummary(conversation.model).contains(needle, ignoreCase = true)
    }
    val pinned = filtered.filter { it.pinned }
    val unpinned = filtered.filter { !it.pinned }
    val today = unpinned.filter { isToday(it.updatedAt) }
    val earlier = unpinned.filterNot { isToday(it.updatedAt) }

    // 三个分组共用同一份行回调：改造前这段参数块在 LazyColumn 里抄了三遍。
    val open: (Conversation) -> Unit = { onOpenChat(it.id) }
    val togglePin: (Conversation) -> Unit = {
        viewModel.setPinned(it.id, !it.pinned)
        menuFor = null
    }
    val askRename: (Conversation) -> Unit = {
        renameTarget = it
        menuFor = null
    }
    val askDelete: (Conversation) -> Unit = {
        pendingDelete = it
        menuFor = null
    }

    BackdropHost(scene = SceneryCatalog.forRoute("conversations")) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    HomeHeader(
                        poemRes = poemRes,
                        searchOpen = searchOpen,
                        onToggleSearch = {
                            searchOpen = !searchOpen
                            if (!searchOpen) {
                                query = ""
                                viewModel.searchMessages("")
                            }
                        },
                        onOpenSettings = { onNavigate(AppSection.Settings) },
                        onOpenBackup = onOpenBackup,
                    )
                }

                if (searchOpen) {
                    item {
                        SearchField(
                            query = query,
                            onQuery = {
                                query = it
                                viewModel.searchMessages(it)
                            },
                            onClose = {
                                searchOpen = false
                                query = ""
                                viewModel.searchMessages("")
                            },
                        )
                    }
                }

                item {
                    ArtStrip(
                        onNewChat = { showCreate = true },
                        onTranslate = { onNavigate(AppSection.Translate) },
                        onVoice = { onNavigate(AppSection.VoiceChat) },
                        onArena = onOpenArena,
                    )
                }

                // 消息正文检索结果：标题命中的会话照常显示，正文命中的另起一组。
                if (query.isNotBlank()) {
                    val hits = viewModel.hits
                    item { GroupSeal(stringResource(R.string.search_in_messages), "文") }
                    if (hits.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.search_no_result),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                            )
                        }
                    } else {
                        items(hits, key = { "hit-${it.messageId}" }) { hit ->
                            SearchHitCard(hit = hit, onOpen = { onOpenChat(hit.conversationId) })
                        }
                    }
                }

                if (conversations.isEmpty()) {
                    item {
                        EmptyState(
                            onStartChat = { showCreate = true },
                            onOpenSettings = { onNavigate(AppSection.Settings) },
                        )
                    }
                } else if (filtered.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }

                if (conversations.isNotEmpty()) {
                    if (pinned.isNotEmpty()) {
                        item { GroupSeal(stringResource(R.string.conversation_group_pinned), "顶") }
                        items(pinned, key = { "pinned-${it.id}" }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                menuOpen = menuFor == conversation.id,
                                onOpen = { open(conversation) },
                                onLongPress = { menuFor = conversation.id },
                                onDismissMenu = { menuFor = null },
                                onTogglePin = { togglePin(conversation) },
                                onRename = { askRename(conversation) },
                                onDelete = { askDelete(conversation) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    if (today.isNotEmpty()) {
                        item { GroupSeal(stringResource(R.string.conversation_group_today), "今") }
                        items(today, key = { "today-${it.id}" }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                menuOpen = menuFor == conversation.id,
                                onOpen = { open(conversation) },
                                onLongPress = { menuFor = conversation.id },
                                onDismissMenu = { menuFor = null },
                                onTogglePin = { togglePin(conversation) },
                                onRename = { askRename(conversation) },
                                onDelete = { askDelete(conversation) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    if (earlier.isNotEmpty()) {
                        item { GroupSeal(stringResource(R.string.conversation_group_earlier), "旧") }
                        items(earlier, key = { "earlier-${it.id}" }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                menuOpen = menuFor == conversation.id,
                                onOpen = { open(conversation) },
                                onLongPress = { menuFor = conversation.id },
                                onDismissMenu = { menuFor = null },
                                onTogglePin = { togglePin(conversation) },
                                onRename = { askRename(conversation) },
                                onDelete = { askDelete(conversation) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.new_chat)) },
            text = { Text(stringResource(R.string.use_model, viewModel.starting.provider.localizedSummary(viewModel.starting.model))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCreate = false
                        scope.launch {
                            val id = viewModel.create(viewModel.starting.provider, viewModel.starting.model)
                            onOpenChat(id)
                        }
                    },
                ) { Text(stringResource(R.string.start)) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showCreate = false
                            showModels = true
                        },
                    ) { Text(stringResource(R.string.change_model)) }
                    TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }

    if (showModels) {
        ModelPickerDialog(
            title = stringResource(R.string.choose_model),
            providers = ProviderId.entries,
            selectedProvider = viewModel.starting.provider,
            selectedId = viewModel.starting.model,
            catalogByProvider = catalogs,
            onSelect = { provider, model ->
                showModels = false
                scope.launch {
                    val id = viewModel.create(provider, model)
                    onOpenChat(id)
                }
            },
            onDismiss = { showModels = false },
        )
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_conversation)) },
            text = {
                val title = conversation.title.ifBlank { stringResource(R.string.new_chat) }
                Text(stringResource(R.string.delete_conversation_body, title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(conversation.id)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    renameTarget?.let { conversation ->
        RenameDialog(
            initial = conversation.title,
            onConfirm = { title ->
                viewModel.rename(conversation.id, title)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

/**
 * 页首：左侧「語光」方印 + 每日一句题跋，右侧「我」玻璃圆点。
 *
 * 备份入口从原来的等宽小方块挪到「我」里：首页只留四个真正想做的事，
 * 备份/设置这类低频动作收进一枚圆点，`onOpenBackup` 能力照旧保留。
 */
@Composable
private fun HomeHeader(
    poemRes: Int,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // 两行版式：第一行印 + 诗，第二行日期 + 搜索 + 「我」。
    // 原来搜索独占一行，竖向白白吃掉 48dp，首屏少半张会话卡。
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            WordmarkSeal()
            Spacer(Modifier.width(12.dp))
            PoemLine(
                textRes = poemRes,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f),
                horizontalAlignment = Alignment.Start,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 日期是次级文字，直接落在背景上对比度不够：用主色降 alpha 0.75。
            Text(
                text = formatDay(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f),
            )
            SearchGlassButton(onOpen = onToggleSearch, size = 34.dp, active = searchOpen)
            Spacer(Modifier.width(8.dp))
            Box {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .glass(shape = CircleShape, tone = GlassTone.Paper, level = 0.55f),
                    contentAlignment = Alignment.Center,
                ) {
                    InkRipple(onClick = { menuOpen = true }, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(R.string.section_profile),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                if (menuOpen) {
                    val density = LocalDensity.current
                    // 默认 DropdownMenu 是白底直角，压在宣纸上很突兀：换成玻璃卡 + 18dp 圆角 + 细线分隔。
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(0, with(density) { 40.dp.roundToPx() }),
                        onDismissRequest = { menuOpen = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(176.dp)
                                .glass(shape = RoundedCornerShape(18.dp), tone = GlassTone.Paper, level = 1f)
                                .padding(vertical = 6.dp),
                        ) {
                            ProfileMenuRow(
                                icon = LumoIcons.Settings,
                                label = stringResource(R.string.section_settings),
                            ) {
                                menuOpen = false
                                onOpenSettings()
                            }
                            InkDivider(Modifier.padding(horizontal = 14.dp))
                            ProfileMenuRow(
                                icon = LumoIcons.Archive,
                                label = stringResource(R.string.quick_backup),
                            ) {
                                menuOpen = false
                                onOpenBackup()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 「我」菜单里的一行：墨线图标 + 标签，点完整行都算。 */
@Composable
private fun ProfileMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 「語光」二字竖排方印：6dp 圆角、朱砂底、白文。 */
@Composable
private fun WordmarkSeal(modifier: Modifier = Modifier) {
    val palette = LocalLumoPalette.current
    Box(
        modifier = modifier
            .width(28.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.cinnabar),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "語\n光",
            style = LumoType.seal.copy(fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.sp),
            color = palette.sealInk,
            textAlign = TextAlign.Center,
        )
    }
}

/** 收起的搜索：一枚玻璃放大镜，挤进日期那一行，不再独占一条。 */
@Composable
private fun SearchGlassButton(
    onOpen: () -> Unit,
    size: Dp = 40.dp,
    active: Boolean = false,
) {
    val palette = LocalLumoPalette.current
    Box(
        modifier = Modifier
            .size(size)
            .glass(shape = CircleShape, tone = GlassTone.Paper, level = 0.7f),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onOpen, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = LumoIcons.Search,
                contentDescription = stringResource(R.string.search_hint),
                tint = if (active) palette.cinnabar else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/** 展开后的搜索：玻璃胶囊 + 无边框输入，回车语义与原来一致（边输边搜）。 */
@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(shape = shape, tone = GlassTone.Paper, level = 0.9f)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LumoIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = LumoIcons.Close,
                contentDescription = stringResource(R.string.close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private val ART_CARD_HEIGHT = 120.dp

/**
 * 四张艺术卡：新对话 / 翻译 / 语音 / 擂台。
 *
 * 替代原来五个等宽小方块 —— 方块只说明「有个按钮」，卡片说明「这是一扇门」。
 * 四张按可用宽度等分，一屏放得下：早先做成横滑 + 条尾 peek，第四张（擂台）永远
 * 只露 24dp，看起来像没画完，所以改回整排。
 */
@Composable
private fun ArtStrip(
    onNewChat: () -> Unit,
    onTranslate: () -> Unit,
    onVoice: () -> Unit,
    onArena: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 10.dp
        // 392dp 屏：可用 360dp → 每张 82.5dp，字号 12sp 的标签仍放得下。
        val cardWidth = (maxWidth - gap * 3) / 4f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            ArtCard(
                icon = LumoIcons.Chat,
                label = stringResource(R.string.new_chat),
                scene = Scene.Stream,
                primary = true,
                width = cardWidth,
                onClick = onNewChat,
            )
            ArtCard(
                icon = LumoIcons.Translate,
                label = stringResource(R.string.nav_translate),
                scene = Scene.WindBamboo,
                width = cardWidth,
                onClick = onTranslate,
            )
            ArtCard(
                icon = LumoIcons.Voice,
                label = stringResource(R.string.nav_voice),
                scene = Scene.Falls,
                width = cardWidth,
                onClick = onVoice,
            )
            ArtCard(
                icon = LumoIcons.Trophy,
                label = stringResource(R.string.arena_entry),
                scene = Scene.Peaks,
                width = cardWidth,
                onClick = onArena,
            )
        }
    }
}

@Composable
private fun ArtCard(
    icon: ImageVector,
    label: String,
    scene: Scene,
    onClick: () -> Unit,
    primary: Boolean = false,
    width: Dp = 96.dp,
) {
    val palette = LocalLumoPalette.current
    val night = isNight()
    val shape = RoundedCornerShape(20.dp)
    val iconTint = if (primary) palette.cinnabar else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)

    PressScale {
        InkRipple(
            onClick = onClick,
            modifier = Modifier
                .size(width = width, height = ART_CARD_HEIGHT)
                .clip(shape),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = if (night) scene.nightRes else scene.dayRes,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.44f },
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    palette.paper.copy(alpha = 0.10f),
                                    palette.paper.copy(alpha = 0.34f),
                                    palette.paper.copy(alpha = 0.90f),
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 1.dp,
                            color = if (primary) {
                                palette.cinnabar.copy(alpha = 0.45f)
                            } else {
                                palette.glassBorder.copy(alpha = 0.55f)
                            },
                            shape = shape,
                        ),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = label,
                        style = LumoType.seal.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 一条正文命中：会话名 + 高亮片段，点击进入该会话。 */
@Composable
private fun SearchHitCard(hit: SearchHitRow, onOpen: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tone = GlassTone.Paper,
        level = 0.85f,
        onClick = onOpen,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            hit.title.ifBlank { stringResource(R.string.new_chat) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            hit.snippet.lines().joinToString(" ").trim(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** 分组标题：一枚小印 + 一道渐隐细线。 */
@Composable
private fun GroupSeal(title: String, seal: String) {
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
        SealedSectionTitle(text = title, sealText = seal)
        Spacer(Modifier.height(6.dp))
        InkDivider()
    }
}

/**
 * 会话行：玻璃卡 + 40dp 场景缩略 + 衬线标题 + 一行预览 + 时间。
 *
 * 置顶不再用实心星，改成标题前一枚朱砂小点 —— 星是「收藏」，点是「钉住」。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumoPalette.current
    val pinnedLabel = stringResource(R.string.conversation_group_pinned)
    val shape = RoundedCornerShape(20.dp)

    // 行内不再用逐条淡入：LazyColumn 每滚出一条新行都要重放 0–400ms 延迟 + 220ms 淡入，
    // 看起来就像「滑一下有几条不见了」。改用列表自己的 animateItem（插入/排序动画），
    // 滚动时内容始终是终态。
    Box(modifier = modifier) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(shape = shape, tone = GlassTone.Paper, level = 0.85f)
                    .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SceneThumb(scene = sceneForConversation(conversation.id))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (conversation.pinned) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(palette.cinnabar)
                                    .semantics { contentDescription = pinnedLabel },
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = conversation.title.ifBlank { stringResource(R.string.new_chat) },
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatTime(conversation.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val preview = conversation.preview?.let(::plainPreview).orEmpty()
                    if (preview.isNotEmpty()) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text(
                        text = conversation.provider.localizedSummary(conversation.model),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.cinnabar.copy(alpha = 0.10f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (conversation.pinned) R.string.unpin else R.string.pin))
                },
                onClick = onTogglePin,
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = onRename)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                onClick = onDelete,
            )
        }
    }
}

/** 会话缩略图：一小幅山水，圆角 12dp，压一点点纸色让它退到字后面。 */
@Composable
private fun SceneThumb(scene: Scene, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val palette = LocalLumoPalette.current
    val night = isNight()
    val shape = RoundedCornerShape(12.dp)
    Box(modifier = modifier.size(size).clip(shape)) {
        AsyncImage(
            model = if (night) scene.nightRes else scene.dayRes,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(modifier = Modifier.fillMaxSize().background(palette.paper.copy(alpha = 0.22f)))
        Box(modifier = Modifier.fillMaxSize().border(1.dp, palette.glassBorder.copy(alpha = 0.55f), shape))
    }
}

@Composable
private fun EmptyState(onStartChat: () -> Unit, onOpenSettings: () -> Unit) {
    com.yuguang.lumo.ui.components.EmptyState(
        icon = LumoIcons.Chat,
        title = stringResource(R.string.empty_title),
        body = stringResource(R.string.empty_body),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SealButton(text = stringResource(R.string.onboarding_enter), onClick = onStartChat)
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = stringResource(R.string.keys_in_settings),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** 朱砂印章按钮：用作「启程」这类一次性大动作。 */
@Composable
private fun SealButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalLumoPalette.current
    val shape = RoundedCornerShape(10.dp)
    PressScale(modifier = modifier) {
        InkRipple(
            onClick = onClick,
            modifier = Modifier
                .clip(shape)
                .background(palette.cinnabar),
        ) {
            Text(
                text = text,
                style = LumoType.seal.copy(fontSize = 13.sp, letterSpacing = 3.sp),
                color = palette.sealInk,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun isToday(epoch: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = epoch }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

/** 页首日期：不用 java.time（minSdk 24），SimpleDateFormat 足够。 */
private fun formatDay(epoch: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("M 月 d 日 · EEEE", Locale.CHINA).format(Date(epoch))

@Composable
private fun formatTime(epoch: Long): String {
    val locale = Locale.getDefault()
    val pattern = if (isToday(epoch)) "HH:mm" else "MM-dd"
    return SimpleDateFormat(pattern, locale).format(Date(epoch))
}

/**
 * 预览行的展示层清洗：会话首条常是模型原文（「### 要点清单」「> 引用」「- 列表」），
 * 直接摊在会话卡上很像漏渲染。这里逐层剥掉行首记号与行内 `**`/`__`/反引号，
 * 只影响列表展示，不动数据库里的正文。
 */
private fun plainPreview(raw: String): String {
    // 跳过空行与代码围栏行（``` 或 ```kotlin）：围栏本身没有信息，预览要落在正文上。
    var line = raw.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("```") }
        .orEmpty()
    // 行首记号可能叠着（"> - 引用里的列表项"），剥到剥不动为止。
    while (true) {
        val stripped = line
            .removePrefix("```")
            .trimStart('#', '>', '-', '*', '+', '·', ' ', '\t')
        if (stripped == line) break
        line = stripped
    }
    // 有序列表：「1. 正文」→「正文」
    val digits = line.takeWhile { it.isDigit() }
    if (digits.isNotEmpty()) {
        val marker = line.getOrNull(digits.length)
        if (marker == '.' || marker == '、' || marker == ')') {
            line = line.substring(digits.length + 1)
        }
    }
    // 行内强调、行内代码，以及正文中间冒出来的小标题井号。
    return line
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .replace("####", " · ")
        .replace("###", " · ")
        .replace("##", " · ")
        .replace("  ", " ")
        .trim()
        .trim(' ', '·')
}
