package com.yuguang.lumo.data.prefs

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 只在内存里持有的 [SharedPreferences]。
 *
 * 用途只有一个：本机加密存储（Keystore）不可用时的兜底。此时密钥照常可读可写，
 * 但进程结束即随内存消失——既不丢功能，也不会像明文偏好那样留在 `/data/data` 里，
 * 被云备份、设备迁移或 root 后的取证读到。
 *
 * 语义与系统实现保持一致的部分：读写、`contains`、`clear`、监听器回调。
 * 不一致的部分：没有任何持久化，`commit()` 恒为 true。
 */
class InMemorySharedPreferences : SharedPreferences {

    private val values = ConcurrentHashMap<String, Any?>()
    private val listeners = CopyOnWriteArraySet<OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = HashMap(values)

    override fun getString(key: String, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int =
        values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: OnSharedPreferenceChangeListener,
    ) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: OnSharedPreferenceChangeListener,
    ) {
        listeners -= listener
    }

    private inner class Editor : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = put(key, value)

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
            put(key, values?.toSet())

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = put(key, value)

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = put(key, value)

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = put(key, value)

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = put(key, value)

        override fun remove(key: String): SharedPreferences.Editor {
            removals += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            applyNow()
            return true
        }

        override fun apply() = applyNow()

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            pending[key] = value
            removals -= key
            return this
        }

        private fun applyNow() {
            val touched = linkedSetOf<String>()
            if (clearRequested) {
                touched += values.keys
                values.clear()
            }
            removals.forEach { key ->
                if (values.remove(key) != null) touched += key
            }
            pending.forEach { (key, value) ->
                if (value == null) {
                    if (values.remove(key) != null) touched += key
                } else {
                    values[key] = value
                    touched += key
                }
            }
            pending.clear()
            removals.clear()
            clearRequested = false
            touched.forEach { key ->
                listeners.forEach { it.onSharedPreferenceChanged(this@InMemorySharedPreferences, key) }
            }
        }
    }
}
