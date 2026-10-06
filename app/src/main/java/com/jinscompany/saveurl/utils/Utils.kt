package com.jinscompany.saveurl.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

const val tutorialUrl = "https://yjh10630.github.io/MyWeb/#/SaveUrlTutorial"

/**
 * 공유 텍스트에서 첫 번째 URL 을 추출한다.
 * 기존 정규식([a-zA-Z0-9./?=_-])은 '@', '&', '%', '#', '~', '+', ':' 를 허용하지 않아
 * TikTok/Threads(@user), 쿼리스트링(&), 퍼센트 인코딩 URL 이 잘리는 문제가 있었다.
 */
fun extractUrlFromText(text: String): String? {
    val urlRegex = Regex(
        "https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
        RegexOption.IGNORE_CASE
    )
    val raw = urlRegex.find(text)?.value ?: return null
    // 문장 끝 구두점/괄호 제거
    var url = raw.trimEnd('.', ',', '!', '?', ';', ':', '\'', '"')
    if (url.endsWith(")") && url.count { it == '(' } < url.count { it == ')' }) url = url.dropLast(1)
    if (url.endsWith("]") && url.count { it == '[' } < url.count { it == ']' }) url = url.dropLast(1)
    return url
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