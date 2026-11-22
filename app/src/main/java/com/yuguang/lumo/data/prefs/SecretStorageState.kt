package com.yuguang.lumo.data.prefs

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 密钥存储的可用性。
 *
 * 正常情况下密钥由 Keystore 加密后落盘；个别定制 ROM、恢复出厂后的主密钥失效等场景下
 * 加密存储建不起来，此时应用退回内存存储（本次会话可用、退出即失）。
 * 设置页据此给出一条明确提示，而不是让用户以为密钥已经保存好了。
 */
@Singleton
class SecretStorageState @Inject constructor() {
    @Volatile
    var encrypted: Boolean = true
        private set

    fun markInMemoryOnly() {
        encrypted = false
    }
}
