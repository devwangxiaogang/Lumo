package com.yuguang.lumo

import com.yuguang.lumo.ui.chat.StreamFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 边生成边朗读的增量判定。
 *
 * 这些用例对应真实会发生的时序：增量追加、只会思考时的兜底替换、
 * 取消导致的内容回滚、以及落库版本比内存版本短。
 */
class StreamFeedTest {
    @Test
    fun `appends only the new tail`() {
        assertEquals("世界", StreamFeed.delta("你好", "你好世界"))
    }

    @Test
    fun `first feed returns whole content`() {
        assertEquals("你好", StreamFeed.delta("", "你好"))
    }

    @Test
    fun `identical content yields nothing`() {
        assertEquals("", StreamFeed.delta("你好", "你好"))
    }

    @Test
    fun `shorter but still a prefix is a rollback not new text`() {
        // flush 节流或落库覆盖可能让内容暂时变短，这不是新内容，也不能从中间截。
        assertEquals("", StreamFeed.delta("你好世界", "你好"))
    }

    @Test
    fun `replaced content is rejected`() {
        // 只有思考时，正文会被替换成兜底文案：与已喂内容毫无前缀关系。
        assertNull(StreamFeed.delta("正在生成", "模型只输出了思考过程"))
    }

    @Test
    fun `empty current after feed is a rollback`() {
        assertEquals("", StreamFeed.delta("你好", ""))
    }

    @Test
    fun `reset after replacement starts fresh`() {
        // 调用方在 null 时收尾，下一次新消息从空串重新开始。
        assertEquals("新回答", StreamFeed.delta("", "新回答"))
    }
}
