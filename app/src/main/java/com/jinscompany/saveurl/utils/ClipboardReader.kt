package com.jinscompany.saveurl.utils

import android.content.ClipboardManager
import android.os.Build
import javax.inject.Inject

/** 클립보드에서 찾은 링크. [copiedAt] = 복사한 시각 (System.currentTimeMillis 기준, API 25 이하·모름은 null) */
data class ClipboardLink(val url: String, val copiedAt: Long?)

interface ClipboardReader {
    fun readLink(): ClipboardLink?

    fun readUrl(): String? = readLink()?.url
}

class ClipboardReaderImpl @Inject constructor(
    private val clipboardManager: ClipboardManager
) : ClipboardReader {
    override fun readLink(): ClipboardLink? {
        if (!clipboardManager.hasPrimaryClip()) return null
        val clip = clipboardManager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0)?.text?.toString() ?: return null
        val url = extractUrlFromText(text) ?: return null
        val copiedAt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) clip.description?.timestamp?.takeIf { it > 0 } else null
        return ClipboardLink(url, copiedAt)
    }
}
