package com.jinscompany.saveurl.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

const val tutorialUrl = "https://yjh10630.github.io/MyWeb/#/SaveUrlTutorial"

// RFC 3986 에서 URL 에 쓸 수 있는 문자 (&, %, #, :, ~, + 등 포함)
private val urlRegex = Regex(
    "https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
    RegexOption.IGNORE_CASE
)

fun extractUrlFromText(text: String): String? {
    val url = urlRegex.find(text)?.value ?: return null
    // 문장 끝 구두점/닫는 괄호는 URL 에서 제외 ("...보세요 https://a.com/b." 등)
    var end = url.length
    while (end > 0) {
        val c = url[end - 1]
        val trim = when (c) {
            '.', ',', ';', ':', '!', '?', '\'' -> true
            ')' -> url.substring(0, end).count { it == '(' } < url.substring(0, end).count { it == ')' }
            else -> false
        }
        if (!trim) break
        end--
    }
    return url.substring(0, end)
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