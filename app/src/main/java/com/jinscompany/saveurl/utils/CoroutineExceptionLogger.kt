package com.jinscompany.saveurl.utils

import android.util.Log
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineExceptionHandler

val globalCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e("CoroutineException", "Uncaught coroutine exception", throwable)
    Firebase.crashlytics.recordException(throwable)
}
