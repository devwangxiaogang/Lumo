package com.yuguang.lumo.domain

import com.yuguang.lumo.domain.model.MessageStatus
import com.yuguang.lumo.domain.model.ProviderId
import kotlinx.coroutines.flow.Flow
import java.util.Locale

/** 擂台的两栏。 */
enum class ArenaSide {
    A,
    B,
}

/**
 * 一局擂台。
 *
 * 刻意不落 messages / conversations：擂台是试验，不该影响会话列表、
 * 全文索引和 token 汇总；历史只在擂台页内回看与删除。
 */
data class ArenaRun(
    val id: Long,
    val prompt: String,
    val slotA: ArenaSlot,
    val slotB: ArenaSlot,
    /** "" 未投票；"A" / "B" / "TIE" 已投票。 */
    val winner: String = "",
    val votedAt: Long = 0,
    val createdAt: Long,
)

/**
 * 单栏输出。
 *
 * [ttftMs] 只在首字写入一次，之后状态变化（DONE / FAILED）不再覆盖，
 * 否则「首字延迟」会随总耗时漂移。
 */
data class ArenaSlot(
    val provider: ProviderId,
    val model: String,
    val content: String = "",
    val reasoning: String = "",
    val status: MessageStatus = MessageStatus.STREAMING,
    val error: String = "",
    val ttftMs: Int = 0,
    val totalMs: Int = 0,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
)

/** 开一局需要的最小输入：问题 + 两侧模型。 */
data class ArenaDraft(
    val prompt: String,
    val providerA: ProviderId,
    val modelA: String,
    val providerB: ProviderId,
    val modelB: String,
)

interface ArenaRepository {
    /** 历史按时间倒序，最多 [limit] 局。 */
    fun observeRuns(limit: Int = ArenaTally.MAX_RUNS): Flow<List<ArenaRun>>

    fun observeRun(runId: Long): Flow<ArenaRun?>

    /** 建 run + 两个 STREAMING output 后立刻返回；真正的生成在 @AppScope 里跑。 */
    suspend fun start(draft: ArenaDraft): Long

    fun stop(runId: Long)

    suspend fun vote(runId: Long, winner: String)

    suspend fun delete(runId: Long)
}

/**
 * 擂台的纯逻辑：投票归一化、计时格式、token 合计、超限淘汰、状态机。
 *
 * 全部不依赖 Android，单元测试直接覆盖；界面与仓库共用同一套规则，
 * 避免「列表显示 1.2s、详情显示 1200ms」这类不一致。
 */
object ArenaTally {
    /** 与 ArenaDao.trimTo 保持一致：只保留最近 50 局。 */
    const val MAX_RUNS = 50

    const val WINNER_NONE = ""
    const val WINNER_A = "A"
    const val WINNER_B = "B"
    const val WINNER_TIE = "TIE"

    /** 把界面/旧数据里的各种写法收敛成四种存储值，认不出就当没投票。 */
    fun normalizeWinner(raw: String?): String {
        // 去空白要连内部一起删：「A 更好」「A  更好」都是同一票，
        // 只 trim 首尾会让带空格的中文标签落进 else 分支。
        val token = raw.orEmpty()
            .filterNot { it.isWhitespace() }
            .lowercase(Locale.US)
        return when {
            token == "a" || token == "side_a" || token == "a更好" || token == "a胜" -> WINNER_A
            token == "b" || token == "side_b" || token == "b更好" || token == "b胜" -> WINNER_B
            token == "tie" || token == "draw" || token == "=" || token == "平局" -> WINNER_TIE
            else -> WINNER_NONE
        }
    }

    /** 投票对应哪一栏；平局或未投票返回 null。 */
    fun winnerSide(raw: String?): ArenaSide? = when (normalizeWinner(raw)) {
        WINNER_A -> ArenaSide.A
        WINNER_B -> ArenaSide.B
        else -> null
    }

    /** 0 表示没出过正文，显示破折号而不是 0ms。 */
    fun formatTtft(ms: Int): String = when {
        ms <= 0 -> "—"
        ms < 1_000 -> "${ms}ms"
        else -> String.format(Locale.US, "%.1fs", ms / 1000.0)
    }

    fun totalTokens(promptTokens: Int, completionTokens: Int): Int =
        promptTokens.coerceAtLeast(0) + completionTokens.coerceAtLeast(0)

    fun slotTokens(slot: ArenaSlot): Int = totalTokens(slot.promptTokens, slot.completionTokens)

    /** 两栏合计：擂台页底部的对比数字。 */
    fun runTokens(run: ArenaRun): Int = slotTokens(run.slotA) + slotTokens(run.slotB)

    /** 超限淘汰：按创建时间倒序保留最近 [keep] 局。 */
    fun trim(runs: List<ArenaRun>, keep: Int = MAX_RUNS): List<ArenaRun> {
        if (keep <= 0) return emptyList()
        return runs.sortedByDescending { it.createdAt }.take(keep)
    }

    /** 只有 STREAMING 能落到终态；终态不可再被别的状态覆盖。 */
    fun canTransition(from: MessageStatus, to: MessageStatus): Boolean =
        from == MessageStatus.STREAMING && to != MessageStatus.STREAMING

    fun settleStatus(current: MessageStatus, next: MessageStatus): MessageStatus =
        if (canTransition(current, next)) next else current

    /** 首字延迟只写一次：已有值就不再让后续测量覆盖。 */
    fun ttftAfter(current: Int, measured: Int): Int =
        if (current > 0) current else measured.coerceAtLeast(0)
}
