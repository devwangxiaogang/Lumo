package com.yuguang.lumo.ui.translate

/**
 * 一次文字翻译的不可变请求。
 *
 * 之前翻译只读 `state.input` 加一个 `translating` 布尔：请求在飞的时候再输入，
 * 新的自动翻译会被 `translating` 挡掉并且不再重排；等旧请求回来，
 * 它会把旧译文写进已经改过的输入框。把「这次翻什么」固化成一个带 [generation] 的值，
 * 回写时比对代次即可丢弃过期结果。
 *
 * @param generation 单调递增的代次；只有与当前代次一致的结果才允许回写界面。
 */
data class TranslationRequest(
    val text: String,
    val source: String,
    val target: String,
    val model: String,
    val generation: Long,
    /** true 表示要写进翻译记录（手点「翻译」），false 表示自动预览。 */
    val save: Boolean,
)

/**
 * 代次分配与结果归属判定。
 *
 * 抽成纯函数是为了能单测：竞态的本质是「旧请求不许写新结果」，
 * 这一点与协程调度无关，可以脱离 Android 验证。
 */
class TranslationGeneration {
    private var current = 0L

    /** 开始一次新请求，返回它应当携带的代次。 */
    fun next(): Long {
        current += 1
        return current
    }

    /** 当前最新代次；没有请求时为 0。 */
    fun now(): Long = current

    /** 只有最新那次请求的结果可以落到界面上。 */
    fun isCurrent(generation: Long): Boolean = generation == current

    /** 取消/清空输入时作废在飞的结果。 */
    fun invalidate() {
        current += 1
    }
}
