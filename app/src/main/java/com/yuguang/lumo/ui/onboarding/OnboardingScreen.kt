package com.yuguang.lumo.ui.onboarding

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.ui.backdrop.BackdropHost
import com.yuguang.lumo.ui.components.GlassCard
import com.yuguang.lumo.ui.components.PoemLine
import com.yuguang.lumo.ui.components.PressScale
import com.yuguang.lumo.ui.components.SealBadge
import com.yuguang.lumo.ui.components.SealedSectionTitle
import com.yuguang.lumo.ui.prefs.UiPreferences
import com.yuguang.lumo.ui.theme.LocalLumoPalette
import com.yuguang.lumo.ui.theme.LocalMotionEnabled
import com.yuguang.lumo.ui.theme.LumoMotion
import com.yuguang.lumo.ui.theme.LumoType
import com.yuguang.lumo.ui.theme.LocalSkin
import com.yuguang.lumo.ui.theme.Skin
import com.yuguang.lumo.ui.theme.Scene
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.hypot

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val uiPreferences: UiPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    var deepSeekKey by mutableStateOf("")
        private set
    var doubaoKey by mutableStateOf("")
        private set
    var speechKey by mutableStateOf("")
        private set

    fun setDeepSeek(value: String) { deepSeekKey = value }
    fun setDoubao(value: String) { doubaoKey = value }
    fun setSpeech(value: String) { speechKey = value }

    fun save() {
        viewModelScope.launch {
            if (deepSeekKey.isNotBlank()) {
                val config = settingsRepository.get(ProviderId.DEEPSEEK)
                settingsRepository.save(config.copy(apiKey = deepSeekKey.trim()))
            }
            if (doubaoKey.isNotBlank() || speechKey.isNotBlank()) {
                val config = settingsRepository.get(ProviderId.DOUBAO)
                settingsRepository.save(
                    config.copy(
                        apiKey = doubaoKey.trim().ifBlank { config.apiKey },
                        speechApiKey = speechKey.trim().ifBlank { config.speechApiKey },
                    ),
                )
            }
            uiPreferences.markOnboarded()
        }
    }

    fun skip() {
        uiPreferences.markOnboarded()
    }
}

/** 三页的景、诗、印：青绿—云山—千岩，正好是一程由近及远的路。 */
private val LUMOS_PAGE_SCENES = listOf(Scene.Qinglu, Scene.Cloud, Scene.Peaks)
private val LUMOS_PAGE_POEMS = intArrayOf(
    R.string.onboarding_poem_1,
    R.string.onboarding_poem_2,
    R.string.onboarding_poem_3,
)
private val LUMOS_PAGE_SEALS = listOf("光", "山", "安")

// 弧光院：三幅同一世界观的景，配三句自己的题词与印文。
private val ARCANE_PAGE_SCENES = listOf(Scene.ArcaneCastle, Scene.ArcaneHall, Scene.ArcaneNebula)
private val ARCANE_PAGE_POEMS = intArrayOf(
    R.string.home_motto_arcane_1,
    R.string.home_motto_arcane_3,
    R.string.chat_poem_arcane_1,
)
private val ARCANE_PAGE_SEALS = listOf("星", "火", "安")

private fun pageScenes(skin: Skin): List<Scene> =
    if (skin == Skin.Arcane) ARCANE_PAGE_SCENES else LUMOS_PAGE_SCENES

private fun pagePoems(skin: Skin): IntArray =
    if (skin == Skin.Arcane) ARCANE_PAGE_POEMS else LUMOS_PAGE_POEMS

private fun pageSeals(skin: Skin): List<String> =
    if (skin == Skin.Arcane) ARCANE_PAGE_SEALS else LUMOS_PAGE_SEALS

/**
 * 画心占屏高的比例。前两页只有一行标题 + 两行说明，画心抬到 0.64，
 * 纸片高度贴着内容收 —— 空白留给山水，不留在一片空纸上。
 * 密钥页要塞三把钥匙 + 一句说明，纸片反而要让出高度（0.42），表单才不用挤着滚。
 */
private fun bandFraction(page: Int): Float =
    if (page == 2) 0.42f else 0.64f

/** 纸片顶部大圆角，压住画心的下缘。 */
private val SHEET_CORNER = 30.dp

private val ENTER_SEAL_WIDTH = 232.dp
private val ENTER_SEAL_HEIGHT = 56.dp

/**
 * 首次启动引导：三页横滑，第三页填密钥，最后一枚「启程」朱砂印。
 *
 * 版式是「上画心、下纸片」：画心只负责山与诗（可读性不靠它），
 * 标题、正文、密钥、页码、按钮、跳过全部落在下方纸片实底上 ——
 * 这样浅色与深色两套主题都成立，不必再跟山水彩块抢对比度。
 *
 * 逻辑一行没动：输入的三个字段仍写回 OnboardingViewModel，
 * 「启程」调 viewModel.save()，「跳过」调 viewModel.skip()。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val skin = LocalSkin.current
    val scenes = remember(skin) { pageScenes(skin) }
    val poems = remember(skin) { pagePoems(skin) }
    val seals = remember(skin) { pageSeals(skin) }
    val pagerState = rememberPagerState(pageCount = { scenes.size })
    val scene = scenes[pagerState.currentPage.coerceIn(0, scenes.lastIndex)]

    BackdropHost(scene = scene) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding()
                    // 只让键盘顶起内容；导航栏交给纸片自己吃掉，纸才不会在底部断开。
                    .windowInsetsPadding(WindowInsets.ime),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    // 页与页之间的相对位移：0 = 居中，±1 = 邻页。视差就挂在这个值上。
                    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    OnboardingPage(
                        scene = scenes[page],
                        poemRes = poems[page],
                        seal = seals[page],
                        offset = offset,
                        bandFraction = bandFraction(page),
                        scrollable = page == scenes.lastIndex,
                    ) {
                        when (page) {
                            0 -> PageInvitation()
                            1 -> PageIntro()
                            else -> PageKeys(viewModel = viewModel)
                        }
                    }
                }

                SheetFooter(
                    current = pagerState.currentPage,
                    onEnter = viewModel::save,
                    onSkip = viewModel::skip,
                )
            }
        }
    }
}

/**
 * 单页：一幅随滑动横移的「画心」（含竖排诗与末尾一字印）+ 下方纸片上的内容。
 *
 * 画心比内容移得多（±22dp vs ∓8dp），滑动时就有前后层次；
 * 底图 alpha 0.5，向下渐隐到纸色，纸片的圆角正好切在景的余韵上。
 */
@Composable
private fun OnboardingPage(
    scene: Scene,
    poemRes: Int,
    seal: String,
    offset: Float,
    bandFraction: Float,
    scrollable: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalLumoPalette.current
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bandShift = 22.dp * offset.coerceIn(-1.5f, 1.5f)
    val contentShift = -8.dp * offset.coerceIn(-1.5f, 1.5f)
    val sheetShape = RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val bandHeight = maxHeight * bandFraction

        // 画心：多画 26dp 好让纸片压住它的下缘，圆角处还能透出一点山水。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bandHeight + 26.dp),
        ) {
            AsyncImage(
                model = if (night) scene.nightRes else scene.dayRes,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = bandShift.toPx()
                        // 横移会露边，放大一点补回来。
                        scaleX = 1.12f
                        scaleY = 1.04f
                        alpha = 0.5f
                    },
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.62f to palette.paper.copy(alpha = 0.26f),
                            1f to palette.paper.copy(alpha = 0.62f),
                        ),
                    ),
            )
            // 诗与印：一团极淡的纸雾托住字，末字下方 8dp 落印，首尾成章。
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { translationX = contentShift.toPx() }
                    .padding(horizontal = 26.dp, vertical = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    palette.paper.copy(alpha = 0.58f),
                                    palette.paper.copy(alpha = 0f),
                                ),
                            ),
                        ),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PoemLine(
                        textRes = poemRes,
                        vertical = true,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f),
                    )
                    Spacer(Modifier.height(8.dp))
                    SealBadge(text = seal, size = 22.dp)
                }
            }
        }

        // 纸片：承载全部正文，顶部大圆角 + 一道淡金线，日夜都读得清。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = bandHeight)
                .shadow(elevation = 12.dp, shape = sheetShape, clip = false)
                .clip(sheetShape)
                .background(palette.paper),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(palette.gold.copy(alpha = 0.30f)),
            )
            if (scrollable) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(14.dp))
                    content()
                    Spacer(Modifier.height(14.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = 26.dp, end = 26.dp, top = 28.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    content()
                }
            }
        }
    }
}

/** 第一页：报个名字，一句话说清这是什么。 */
@Composable
private fun PageInvitation() {
    Text(
        text = stringResource(R.string.onboarding_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.onboarding_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** 第二页：把「钥匙放在设置里」这件事说在前面，避免第三页突然要填。 */
@Composable
private fun PageIntro() {
    Text(
        text = stringResource(R.string.keys_in_settings),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** 第三页：三把钥匙。纸片上的玻璃卡 + 玻璃化输入框，逻辑仍走 ViewModel。 */
@Composable
private fun PageKeys(viewModel: OnboardingViewModel) {
    val palette = LocalLumoPalette.current
    val fieldShape = RoundedCornerShape(14.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = palette.paperGlass.copy(alpha = 0.55f),
        unfocusedContainerColor = palette.paperGlass.copy(alpha = 0.38f),
        focusedBorderColor = palette.cinnabar.copy(alpha = 0.55f),
        unfocusedBorderColor = palette.glassBorder.copy(alpha = 0.70f),
        focusedLabelColor = palette.cinnabar,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = palette.cinnabar,
    )
    val fieldTextStyle: TextStyle = MaterialTheme.typography.bodyMedium

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        level = 0.9f,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 14.dp,
            vertical = 12.dp,
        ),
    ) {
        SealedSectionTitle(
            text = stringResource(R.string.onboarding_keys_title),
            sealText = "钥",
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = viewModel.deepSeekKey,
            onValueChange = viewModel::setDeepSeek,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.onboarding_deepseek)) },
            singleLine = true,
            shape = fieldShape,
            textStyle = fieldTextStyle,
            colors = fieldColors,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = viewModel.doubaoKey,
            onValueChange = viewModel::setDoubao,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.onboarding_doubao)) },
            singleLine = true,
            shape = fieldShape,
            textStyle = fieldTextStyle,
            colors = fieldColors,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = viewModel.speechKey,
            onValueChange = viewModel::setSpeech,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.onboarding_speech)) },
            singleLine = true,
            shape = fieldShape,
            textStyle = fieldTextStyle,
            colors = fieldColors,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
    }
    // 这句话从卡内挪到纸上：它说的是「不填也行」，属于整页的说明而不是某个字段的注解。
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * 纸片底部：页码墨点 + 「启程」朱砂印 + 一行极淡的「跳过」。
 *
 * 三件东西共用一条中线，视线从页码落到印上，不会左右乱飘。
 */
@Composable
private fun SheetFooter(
    current: Int,
    onEnter: () -> Unit,
    onSkip: () -> Unit,
) {
    val palette = LocalLumoPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.paper)
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(14.dp))
        PageDots(count = 3, current = current)
        Spacer(Modifier.height(16.dp))
        EnterSealButton(onEnter = onEnter)
        Spacer(Modifier.height(2.dp))
        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.onboarding_skip),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 页码墨点：当前页一枚 18dp 朱砂短笔，其余是 6dp 淡墨点，整体居中。 */
@Composable
private fun PageDots(count: Int, current: Int) {
    val palette = LocalLumoPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) 18.dp else 6.dp, label = "dotWidth")
            Box(
                modifier = Modifier
                    .padding(horizontal = 5.dp)
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            palette.cinnabar
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    ),
            )
        }
    }
}

/**
 * 「启程」朱砂大印：先让墨在印面上走 400ms，再真正保存。
 *
 * 400ms 的扩散是这页唯一的仪式感，也是保存前的一个缓冲 ——
 * 动效关闭时直接落到 save()，不做无意义的等待。
 */
@Composable
private fun EnterSealButton(onEnter: () -> Unit) {
    val palette = LocalLumoPalette.current
    val motionEnabled = LocalMotionEnabled.current
    val scope = rememberCoroutineScope()
    val spread = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(true) }
    val shape = RoundedCornerShape(12.dp)

    PressScale {
        Box(
            modifier = Modifier
                .width(ENTER_SEAL_WIDTH)
                .height(ENTER_SEAL_HEIGHT)
                .clip(shape)
                .background(palette.cinnabar)
                .clickable(role = Role.Button) {
                    if (!armed) return@clickable
                    armed = false
                    scope.launch {
                        if (motionEnabled) {
                            spread.snapTo(0f)
                            spread.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = LumoMotion.inkRippleMillis,
                                    easing = LumoMotion.ease,
                                ),
                            )
                        }
                        onEnter()
                    }
                }
                .drawBehind {
                    val progress = spread.value
                    if (progress <= 0.001f) return@drawBehind
                    val maxRadius = hypot(size.width, size.height) * 0.5f
                    drawCircle(
                        color = palette.sealInk.copy(alpha = 0.26f * (1f - progress)),
                        radius = maxRadius * progress,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawRect(color = palette.sealInk.copy(alpha = 0.10f * (1f - progress)))
                }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.onboarding_enter),
                style = LumoType.seal.copy(fontSize = 17.sp, letterSpacing = 4.sp),
                color = palette.sealInk,
            )
        }
    }
}
