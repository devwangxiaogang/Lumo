package com.yuguang.lumo.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 多模型擂台的一次对局。
 *
 * 刻意与 messages / conversations 分开：擂台是「试验」，不该污染会话列表、
 * 全文索引和 token 汇总；历史在擂台页内单独回看与删除。
 */
@Entity(tableName = "arena_runs")
data class ArenaRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val providerA: String,
    val modelA: String,
    val providerB: String,
    val modelB: String,
    /** "" 未投票；"A" / "B" / "TIE" 已投票。 */
    val winner: String = "",
    val votedAt: Long = 0,
    val createdAt: Long,
)

@Entity(
    tableName = "arena_outputs",
    foreignKeys = [
        ForeignKey(
            entity = ArenaRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId")],
)
data class ArenaOutputEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    /** "A" 或 "B"。 */
    val side: String,
    val content: String,
    val reasoning: String,
    val status: String,
    val error: String,
    /** 首字延迟（毫秒）；0 表示没出过正文。 */
    val ttftMs: Int = 0,
    val totalMs: Int = 0,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
)

@Dao
interface ArenaDao {
    @Query("SELECT * FROM arena_runs ORDER BY createdAt DESC LIMIT :limit")
    fun observeRuns(limit: Int): Flow<List<ArenaRunEntity>>

    @Query("SELECT * FROM arena_outputs WHERE runId = :runId ORDER BY side ASC, id ASC")
    fun observeOutputs(runId: Long): Flow<List<ArenaOutputEntity>>

    @Query("SELECT * FROM arena_runs WHERE id = :id")
    suspend fun run(id: Long): ArenaRunEntity?

    @Query("SELECT * FROM arena_outputs WHERE runId = :runId ORDER BY side ASC, id ASC")
    suspend fun outputs(runId: Long): List<ArenaOutputEntity>

    @Insert
    suspend fun insertRun(run: ArenaRunEntity): Long

    @Insert
    suspend fun insertOutput(output: ArenaOutputEntity): Long

    @Query(
        "UPDATE arena_outputs SET content = :content, reasoning = :reasoning, status = :status, " +
            "error = :error, ttftMs = :ttftMs, totalMs = :totalMs, " +
            "promptTokens = :promptTokens, completionTokens = :completionTokens WHERE id = :id",
    )
    suspend fun updateOutput(
        id: Long,
        content: String,
        reasoning: String,
        status: String,
        error: String,
        ttftMs: Int,
        totalMs: Int,
        promptTokens: Int,
        completionTokens: Int,
    )

    @Query("UPDATE arena_runs SET winner = :winner, votedAt = :votedAt WHERE id = :id")
    suspend fun vote(id: Long, winner: String, votedAt: Long)

    @Query("DELETE FROM arena_runs WHERE id = :id")
    suspend fun deleteRun(id: Long)

    /** 只保留最近 [keep] 局；级联删除 outputs。 */
    @Query("DELETE FROM arena_runs WHERE id NOT IN (SELECT id FROM arena_runs ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}
