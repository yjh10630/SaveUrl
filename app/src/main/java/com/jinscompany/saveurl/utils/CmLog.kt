package com.jinscompany.saveurl.utils

import android.util.Log
import com.jinscompany.saveurl.SaveUrlApplication.Companion.DEBUG

object CmLog {

    private val TAG = "SaveKinkTag"

    /** Log Level Error  */
    fun e(message: String?) {
        if (DEBUG) Log.e(TAG, buildLogMsg(message))
    }

    /** Log Level Warning  */
    fun w(message: String?) {
        if (DEBUG) Log.w(TAG, buildLogMsg(message))
    }

    /** Log Level Information  */
    fun i(message: String?) {
        if (DEBUG) Log.i(TAG, buildLogMsg(message))
    }

    /** Log Level Debug  */
    fun d(message: String?) {
        if (DEBUG) Log.d(TAG, buildLogMsg(message))
    }

    /** Log Level Verbose  */
    fun v(message: String?) {
        if (DEBUG) Log.v(TAG, buildLogMsg(message))
    }


    private fun buildLogMsg(message: String?): String {
        val ste = Thread.currentThread().stackTrace[4]
        val sb = StringBuilder()
        sb.append("[")
        sb.append(ste.fileName.replace(".kt", ""))
        sb.append("::")
        sb.append(ste.methodName)
        sb.append("]")
        sb.append(message)
        return sb.toString()
    }

}