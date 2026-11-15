package com.yuguang.lumo.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yuguang.lumo.ui.arena.ArenaScreen
import com.yuguang.lumo.ui.backdrop.BackdropHost
import com.yuguang.lumo.ui.chat.ChatScreen
import com.yuguang.lumo.ui.components.LumoNavSpec
import com.yuguang.lumo.ui.components.SkinSwitchVeil
import com.yuguang.lumo.ui.components.rememberNavSpec
import com.yuguang.lumo.ui.list.ConversationListScreen
import com.yuguang.lumo.ui.prefs.UiPreferences
import com.yuguang.lumo.ui.settings.AboutScreen
import com.yuguang.lumo.ui.settings.AppearanceScreen
import com.yuguang.lumo.ui.settings.DataSettingsScreen
import com.yuguang.lumo.ui.settings.MemoryScreen
import com.yuguang.lumo.ui.settings.ProviderSettingsScreen
import com.yuguang.lumo.ui.settings.SettingsHomeScreen
import com.yuguang.lumo.ui.settings.VoicePrefsScreen
import com.yuguang.lumo.ui.shell.AppBottomBar
import com.yuguang.lumo.ui.shell.AppSection
import com.yuguang.lumo.ui.stats.UsageStatsScreen
import com.yuguang.lumo.ui.theme.LocalSkin
import com.yuguang.lumo.ui.theme.LocalMotionEnabled
import com.yuguang.lumo.ui.theme.LumoMotion
import com.yuguang.lumo.ui.theme.Scene
import com.yuguang.lumo.ui.theme.SceneryCatalog
import com.yuguang.lumo.ui.translate.TranslateScreen
import com.yuguang.lumo.ui.voice.VoiceChatScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** 会话列表、翻译、语音、某个会话，以及设置中心（含二级页）。 */
object Routes {
    const val Conversations = "conversations"
    const val Translate = "translate"
    const val Voice = "voice"
    const val Chat = "chat/{id}"

    const val Settings = "settings"
    const val Provider = "settings/provider/{provider}"
    const val VoicePrefs = "settings/voice"
    const val Appearance = "settings/appearance"
    const val Data = "settings/data"
    const val Memory = "settings/memory"
    const val About = "settings/about"

    /** 多模型擂台：独立入口，不占底部导航。 */
    const val Arena = "arena"

    /** 用量统计：从设置页进入。 */
    const val UsageStats = "settings/stats"

    fun chat(id: Long) = "chat/$id"
    fun provider(id: String) = "settings/provider/$id"
}

/**
 * 外壳需要的两个「外观偏好」：手选场景与每日换景。
 * 只读 UiPreferences 已有的 StateFlow，不复制任何业务状态。
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    prefs: UiPreferences,
) : ViewModel() {
    val scene: StateFlow<String?> = prefs.scene
    val dailyScene: StateFlow<Boolean> = prefs.dailyScene
}

@Composable
fun AiApp(
    shareViewModel: ShareViewModel = hiltViewModel(),
    shellViewModel: ShellViewModel = hiltViewModel(),
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    // 分享内容只预填、不自动发送：既省额度，也让用户先看一眼。
    LaunchedEffect(Unit) {
        shareViewModel.handlePending { target ->
            when (target) {
                is ShareTarget.Translate -> nav.navigate(Routes.Translate)
                is ShareTarget.Chat -> nav.navigate(Routes.chat(target.conversationId))
            }
        }
    }
    val section = when (route) {
        Routes.Translate -> AppSection.Translate
        Routes.Voice -> AppSection.VoiceChat
        else -> AppSection.Chat
    }
    val showBottomBar = route == Routes.Conversations || route == Routes.Translate || route == Routes.Voice

    // 场景优先级：手选 > 首页每日换景 > 按页面。
    val pickedScene by shellViewModel.scene.collectAsStateWithLifecycle()
    val dailyScene by shellViewModel.dailyScene.collectAsStateWithLifecycle()
    val today = remember(dailyScene, route) { localEpochDay() }
    // 皮肤只影响「取哪一组景」：手选的一幅必须属于当前皮肤，否则当作没选。
    val skin = LocalSkin.current
    val scene: Scene = SceneryCatalog.byId(pickedScene, skin)
        ?: if (dailyScene && route == Routes.Conversations) {
            SceneryCatalog.daily(today, skin)
        } else {
            SceneryCatalog.forRoute(route, skin)
        }

    BackdropHost(scene = scene) {
        // 换皮肤时盖一层星尘（600ms）：让色板与文字颜色的瞬间跳变看起来像一次施法。
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            // 透明容器：整页山水由 BackdropHost 铺在下面，Scaffold 只负责排布与避让。
            containerColor = Color.Transparent,
            // 状态栏交给各页面自己的 GlassTopBar / safeDrawingPadding，这里只留底部导航
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    AppBottomBar(
                        selected = section,
                        dark = false,
                        onSelect = { nav.openSection(it) },
                    )
                }
            },
        ) { inner ->
            val spec = rememberScaleNavSpec()
            NavHost(
                navController = nav,
                startDestination = Routes.Conversations,
                modifier = Modifier.padding(inner),
                enterTransition = { spec.enter },
                exitTransition = { spec.exit },
                popEnterTransition = { spec.popEnter },
                popExitTransition = { spec.popExit },
            ) {
                composable(Routes.Conversations) {
                    ConversationListScreen(
                        onOpenChat = { id -> nav.navigate(Routes.chat(id)) },
                        onNavigate = { section -> nav.openSection(section) },
                        onOpenBackup = { nav.navigate(Routes.Data) },
                        onOpenArena = { nav.navigate(Routes.Arena) },
                    )
                }
                composable(Routes.Translate) {
                    TranslateScreen(onNavigate = { section -> nav.openSection(section) })
                }
                composable(
                    route = Routes.Chat,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) {
                    ChatScreen(
                        onBack = { nav.popBackStack() },
                        onOpenSettings = { nav.navigate(Routes.Settings) },
                        onOpenSection = { section -> nav.openSection(section) },
                        onOpenConversation = { id -> nav.navigate(Routes.chat(id)) },
                        onSeedTranslate = { text ->
                            shareViewModel.seedTranslate(text)
                            nav.navigate(Routes.Translate)
                        },
                    )
                }
                composable(Routes.Voice) {
                    VoiceChatScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.Settings) {
                    SettingsHomeScreen(
                        onBack = { nav.popBackStack() },
                        onOpenProvider = { id -> nav.navigate(Routes.provider(id.name)) },
                        onOpenVoicePrefs = { nav.navigate(Routes.VoicePrefs) },
                        onOpenAppearance = { nav.navigate(Routes.Appearance) },
                        onOpenData = { nav.navigate(Routes.Data) },
                        onOpenMemory = { nav.navigate(Routes.Memory) },
                        onOpenAbout = { nav.navigate(Routes.About) },
                        onOpenUsageStats = { nav.navigate(Routes.UsageStats) },
                    )
                }
                composable(Routes.Arena) {
                    ArenaScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.UsageStats) {
                    UsageStatsScreen(onBack = { nav.popBackStack() })
                }
                composable(
                    route = Routes.Provider,
                    arguments = listOf(navArgument("provider") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("provider").orEmpty()
                    ProviderSettingsScreen(
                        providerId = id,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.VoicePrefs) {
                    VoicePrefsScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.Appearance) {
                    AppearanceScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.Data) {
                    DataSettingsScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.Memory) {
                    MemoryScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.About) {
                    AboutScreen(onBack = { nav.popBackStack() })
                }
            }
        }
            SkinSwitchVeil()
        }
    }
}

/**
 * W2 的转场规格（淡入 + 1/16 宽横移，220/180ms）之外，再叠一层 .985→1 的极轻缩放。
 *
 * 不自己重写时长/缓动：位移与时长仍由 [rememberNavSpec] 决定，
 * 这样「行云流水」关闭时两者同时归零，不会出现缩放在放、页面已经到位的错位。
 */
@Composable
private fun rememberScaleNavSpec(): LumoNavSpec {
    val base = rememberNavSpec()
    val motion = LocalMotionEnabled.current
    return remember(base, motion) {
        val inMillis = LumoMotion.duration(LumoMotion.navEnterMillis, motion)
        val outMillis = LumoMotion.duration(LumoMotion.navExitMillis, motion)
        val enterScale = scaleIn(
            animationSpec = tween(durationMillis = inMillis, easing = LumoMotion.ease),
            initialScale = 0.985f,
        )
        val exitScale = scaleOut(
            animationSpec = tween(durationMillis = outMillis, easing = LumoMotion.ease),
            targetScale = 0.985f,
        )
        LumoNavSpec(
            enter = base.enter + enterScale,
            exit = base.exit + exitScale,
            popEnter = base.popEnter + enterScale,
            popExit = base.popExit + exitScale,
        )
    }
}

/**
 * 本地日序号（与 `LocalDate.toEpochDay()` 同一套编号）。
 *
 * minSdk 24 没有 java.time（见 `data/UsageStats.kt` 里同样的取舍），
 * 所以用 Calendar + 时区偏移折算，跨时区/夏令时都按「本地自然日」翻页。
 */
private fun localEpochDay(): Long {
    val cal = Calendar.getInstance()
    val millis = cal.timeInMillis + cal.timeZone.getOffset(cal.timeInMillis)
    return Math.floorDiv(millis, 86_400_000L)
}

private fun androidx.navigation.NavHostController.openSection(section: AppSection) {
    val route = when (section) {
        AppSection.Chat -> Routes.Conversations
        AppSection.Translate -> Routes.Translate
        AppSection.VoiceChat -> Routes.Voice
        AppSection.Settings -> Routes.Settings
    }
    if (section == AppSection.Settings) {
        navigate(route)
        return
    }
    navigate(route) {
        popUpTo(Routes.Conversations) { inclusive = false }
        launchSingleTop = true
    }
}
