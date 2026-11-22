package com.yuguang.lumo

import com.yuguang.lumo.data.BackupException
import com.yuguang.lumo.data.BackupLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 备份包的预算约束。
 *
 * 这些数字本身就是防线：调大之前先想清楚解压后会不会把中低端机的堆撑爆。
 */
class BackupLimitsTest {

    @Test
    fun currentVersionIsOne() {
        assertEquals(1, BackupLimits.CURRENT_VERSION)
    }

    @Test
    fun totalBudgetExceedsSingleEntryBudget() {
        // 单张图上限必须小于总量上限，否则一张图就能吃满整个包的预算。
        assertTrue(BackupLimits.MAX_ENTRY_BYTES < BackupLimits.MAX_TOTAL_BYTES)
    }

    @Test
    fun entryCountAllowsRealisticBackups() {
        // 一条消息最多 4 张图（ChatMediaStore.MAX_ATTACHMENTS），
        // 5000 个 entry 足够容纳上千条带图消息，同时挡住刷条目的攻击。
        assertTrue(BackupLimits.MAX_ENTRIES >= 4_000)
    }

    @Test
    fun indexBudgetIsSmallerThanTotal() {
        assertTrue(BackupLimits.MAX_INDEX_BYTES < BackupLimits.MAX_TOTAL_BYTES)
    }

    @Test
    fun exceptionCarriesUserReadableMessage() {
        val error = BackupException("备份来自更新版本的应用（版本 99）")
        assertTrue(error.message.orEmpty().contains("版本"))
    }

    @Test
    fun limitsStayInMegabyteRange() {
        val totalMb = BackupLimits.MAX_TOTAL_BYTES / 1024 / 1024
        if (totalMb !in 64..512) {
            fail("总量预算 $totalMb MB 超出合理区间")
        }
    }
}
