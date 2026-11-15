package com.yuguang.lumo

import com.yuguang.lumo.data.FailureCooldown
import com.yuguang.lumo.data.ModelFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 失败冷却的纯逻辑：窗口、剔除、恢复。
 *
 * 这些规则错了会表现为「模型突然全被跳过」或「永远撞同一个坏模型」，
 * 界面上都不报错，所以必须在这里钉住。
 */
class FreeModelHealthTest {
    private val now = 1_000_000_000_000L
    private val hour = 60L * 60L * 1000L

    @Test
    fun recordKeepsOnlyLatestPerModel() {
        var failures = FailureCooldown.record(emptyList(), "a:free", now - hour, "first")
        failures = FailureCooldown.record(failures, "a:free", now, "second")
        assertEquals(1, failures.size)
        assertEquals("second", failures.single().reason)
        assertEquals(now, failures.single().failedAt)
    }

    @Test
    fun recordsAreCappedAndNewestFirst() {
        var failures = emptyList<ModelFailure>()
        repeat(FailureCooldown.MAX_ENTRIES + 10) { index ->
            failures = FailureCooldown.record(failures, "m$index:free", now - index * 1000L)
        }
        assertEquals(FailureCooldown.MAX_ENTRIES, failures.size)
        assertEquals("m0:free", failures.first().model)
    }

    @Test
    fun failureExpiresAfterWindow() {
        val failures = listOf(ModelFailure("a:free", now - FailureCooldown.WINDOW_MS - 1))
        assertFalse(FailureCooldown.isCoolingDown(failures, "a:free", now))
        assertTrue(FailureCooldown.active(failures, now).isEmpty())
    }

    @Test
    fun freshFailureIsCoolingDown() {
        val failures = listOf(ModelFailure("a:free", now - hour))
        assertTrue(FailureCooldown.isCoolingDown(failures, "a:free", now))
        assertEquals(1, FailureCooldown.active(failures, now).size)
    }

    @Test
    fun filterDropsCoolingModelsButKeepsOrder() {
        val chain = listOf("a:free", "b:free", "c:free")
        val failures = listOf(ModelFailure("b:free", now - hour))
        assertEquals(listOf("a:free", "c:free"), FailureCooldown.filter(chain, failures, now))
    }

    @Test
    fun filterNeverEmptiesTheChain() {
        // 全部冷却时必须退回原链：否则功能会「静默失效」，比报错更难排查。
        val chain = listOf("a:free", "b:free")
        val failures = chain.map { ModelFailure(it, now - hour) }
        assertEquals(chain, FailureCooldown.filter(chain, failures, now))
    }

    @Test
    fun expiredFailuresDoNotBlockTheChain() {
        val chain = listOf("a:free", "b:free")
        val failures = listOf(ModelFailure("a:free", now - FailureCooldown.WINDOW_MS - 1))
        assertEquals(chain, FailureCooldown.filter(chain, failures, now))
    }

    @Test
    fun successClearsTheCooldown() {
        val failures = listOf(ModelFailure("a:free", now - hour))
        assertTrue(FailureCooldown.clear(failures, "a:free").isEmpty())
        // 清不存在的模型不应改变列表。
        assertEquals(failures, FailureCooldown.clear(failures, "other:free"))
    }

    @Test
    fun reasonIsTruncatedToKeepPrefsSmall() {
        val long = "x".repeat(500)
        val failures = FailureCooldown.record(emptyList(), "a:free", now, long)
        assertTrue(failures.single().reason.length <= 160)
    }
}
