package com.jinscompany.saveurl.utils

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler

val globalCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e("CoroutineException", "Uncaught coroutine exception", throwable)
    CrashReporter.record(throwable)
}
