package io.legado.app.utils

import android.content.Context

/**
 * Firebase 已禁用，此文件为空实现
 */
object FirebaseManager {

    var isEnabled: Boolean = false
        private set

    fun init(context: Context) {
        // Firebase 已禁用，不做任何操作
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        // Firebase 已禁用，不做任何操作
    }
}
