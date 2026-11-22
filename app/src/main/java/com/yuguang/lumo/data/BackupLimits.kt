package com.yuguang.lumo.data

/**
 * 备份包的解压预算。
 *
 * 备份是用户从文件管理器挑进来的 ZIP，来源不可控：损坏的、别的应用伪造的、
 * 甚至是刻意构造的（高压缩比让它看起来只有几 MB，解出来几个 GB）。
 * 这里把上限集中成一处，读包时逐项校验，超限就带着可读原因失败，
 * 而不是把内存耗尽后让进程被系统杀掉。
 */
internal object BackupLimits {
    /** 备份被信任的最高版本；更高的包来自更新版本的应用，结构可能不兼容。 */
    const val CURRENT_VERSION = 1

    /** backup.json 本身的上限：纯 JSON，16 MB 已是极长的历史。 */
    const val MAX_INDEX_BYTES = 16L * 1024 * 1024

    /** 单张图片的上限：与 ChatMediaStore 的原始保留阈值同量级。 */
    const val MAX_ENTRY_BYTES = 8L * 1024 * 1024

    /** 整个包解压后的总字节上限。 */
    const val MAX_TOTAL_BYTES = 256L * 1024 * 1024

    /** entry 数量上限：一条消息最多 4 张图，这里是宽松的天花板。 */
    const val MAX_ENTRIES = 5_000

    const val INDEX_ENTRY = "backup.json"
    const val MEDIA_PREFIX = "media/"
}

/** 备份/导入过程中可以直接展示给用户的失败原因。 */
class BackupException(message: String) : Exception(message)
