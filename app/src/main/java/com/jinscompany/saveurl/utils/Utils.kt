package com.jinscompany.saveurl.utils

import android.content.ClipData
import android.content.ClipboardManager

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