package com.yuguang.lumo.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Room
import com.yuguang.lumo.data.prefs.RecoveryNotice
import java.io.File

/**
 * 打开会话库，并在迁移失败时保住旧文件。
 *
 * 这里原来用的是 `fallbackToDestructiveMigration(true)`：任何未覆盖的迁移路径都会
 * 直接把用户的历史会话清空，而且没有任何提示。会话只存在本机、没有云端副本，
 * 这种「静默重来」是不可接受的，所以改成两步：
 *
 * 1. 迁移前体检：读 `PRAGMA user_version`，凡是「比我们认识的旧」或「比当前版本新」
 *    （降级安装、开发者预览回滚）的库先改名留档，再建新库。旧文件仍在，随时能导出。
 * 2. 迁移本身失败时（迁移 SQL 与实体不一致、文件损坏）：把整组 `ai_chat.db*`
 *    改名留档，再建一个空库，并把留档路径记到 [RecoveryNotice]。
 *
 * 兜底重建只在 provider 构造期发生一次，所以对外暴露的永远是最终那个实例。
 */
object DatabaseOpener {

    const val DB_NAME = "ai_chat.db"

    /** 有迁移脚本的最低版本；更旧的库没有迁移路径，直接留档重建。 */
    const val OLDEST_SUPPORTED = 2

    fun build(context: Context, currentVersion: Int, notice: RecoveryNotice): AppDatabase {
        val path = context.getDatabasePath(DB_NAME)
        if (needsQuarantine(path, currentVersion)) {
            val saved = quarantine(context, "unsupported")
            Log.w(TAG, "quarantined unsupported database: $saved")
            notice.markDatabaseRecovered(saved)
        }
        val first = newInstance(context)
        return try {
            // 迁移在这里才真正执行：失败会抛在启动期，而不是等用户点开某个会话。
            first.openHelper.writableDatabase
            first
        } catch (error: Throwable) {
            runCatching { first.close() }
            Log.e(TAG, "database migration failed; keeping the old file", error)
            val saved = quarantine(context, "failed")
            notice.markDatabaseRecovered(saved)
            newInstance(context).also { it.openHelper.writableDatabase }
        }
    }

    internal fun newInstance(context: Context): AppDatabase = Room
        .databaseBuilder(context, AppDatabase::class.java, DB_NAME)
        .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        // 显式不给破坏性兜底：迁移缺失就交给上面的 catch，旧文件一定留住。
        .build()

    /** 只读探测已有库的版本；没有库（首次安装）时返回 null。 */
    fun existingVersion(path: File): Int? {
        if (!path.isFile) return null
        return runCatching {
            SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.version
            }
        }.getOrNull()
    }

    fun needsQuarantine(path: File, currentVersion: Int): Boolean {
        val version = existingVersion(path) ?: return false
        // 0 表示空文件/从未写入；1 是 1.0 之前的库，没有迁移脚本。
        return version in 1 until OLDEST_SUPPORTED || version > currentVersion
    }

    /** 把整组库文件（含 -wal / -shm / -journal）改名留档，返回主文件新路径。 */
    fun quarantine(context: Context, reason: String): String? {
        val path = context.getDatabasePath(DB_NAME)
        if (!path.isFile) return null
        val target = File(path.parentFile, "$DB_NAME.$reason-${System.currentTimeMillis()}")
        return runCatching {
            path.renameTo(target)
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                File(path.path + suffix).takeIf { it.isFile }?.renameTo(File(target.path + suffix))
            }
            target.path
        }.getOrElse {
            Log.e(TAG, "cannot quarantine $path", it)
            null
        }
    }

    private const val TAG = "AiDatabase"
}
