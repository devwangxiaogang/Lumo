package com.yuguang.lumo.data

/**
 * 免费池模型在界面上的短名。
 *
 * 目标是「一眼分辨」：去掉 `:free` 后缀与厂商前缀；如果尾段只剩 `free`
 * （`kilo-auto/free`、`openrouter/free` 这类自动路由），就退回斜杠前的部分，
 * 否则列表里会出现两行都叫 free，看着像重复探测。
 */
fun shortModelLabel(model: String): String {
    val withoutTag = model.removeSuffix(":free")
    val tail = withoutTag.substringAfter('/')
    return if (tail.equals("free", ignoreCase = true) || tail.isBlank()) {
        withoutTag.substringBefore('/')
    } else {
        tail
    }
}
