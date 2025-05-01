package com.jinscompany.saveurl.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

fun extractUrlFromText(text: String): String? {
    val urlRegex = Regex(
        "(https?://[a-zA-Z0-9./?=_-]+)",
        RegexOption.IGNORE_CASE
    )
    return urlRegex.find(text)?.value
}

fun ClipboardManager.checkClipboardForUrl(): String {
    val clipData: ClipData? = primaryClip

    if (clipData != null && clipData.itemCount > 0) {
        val item = clipData.getItemAt(0)
        val clipboardText = item.text?.toString()

        val url = extractUrlFromText(clipboardText ?: "")

        return if (!url.isNullOrEmpty()) {
            url
        } else {
            //CmLog.d("클립보드에 텍스트가 없습니다.")
            ""
        }
    }
    //CmLog.d("클립보드에 텍스트가 없습니다.")
    return ""
}

fun isDebuggable(context: Context): Boolean {
    var debuggable = false

    val pm = context.packageManager
    try {
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        appInfo.flags
        ApplicationInfo.FLAG_DEBUGGABLE
        debuggable = appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    } catch (e: PackageManager.NameNotFoundException) {

    }
    return debuggable
}

fun getCurrentAppVersion(context: Context): String {
    val info: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return info.versionName ?: "1.0.0"
}