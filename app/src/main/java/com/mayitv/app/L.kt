package com.mayitv.app

import android.util.Log as AndroidLog

/** 统一日志：tag=MayiTV，便于 adb logcat -s MayiTV:V 精准抓取业务链路 */
object L {
    private const val TAG = "MayiTV"
    fun d(msg: String) = AndroidLog.d(TAG, msg)
    fun i(msg: String) = AndroidLog.i(TAG, msg)
    fun w(msg: String) = AndroidLog.w(TAG, msg)
    fun e(msg: String, t: Throwable? = null): Int =
        if (t != null) AndroidLog.e(TAG, msg, t) else AndroidLog.e(TAG, msg)
}
