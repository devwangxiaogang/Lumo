package com.yuguang.lumo.data.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 启动期「出过事」的记录，供设置页如实告知用户。
 *
 * 两件事都属于「用户有权知道，但不该被打断」：密钥只能留在内存、
 * 会话库迁移失败被留档重建。都没有对应的系统弹窗，所以集中在这里，
 * 由设置页的「数据」与「账户」两处读取。
 */
@Singleton
class RecoveryNotice @Inject constructor() {

    private val _databaseBackupPath = MutableStateFlow<String?>(null)
    val databaseBackupPath: StateFlow<String?> = _databaseBackupPath.asStateFlow()

    fun markDatabaseRecovered(path: String?) {
        _databaseBackupPath.value = path
    }

    fun dismissDatabaseNotice() {
        _databaseBackupPath.value = null
    }
}
