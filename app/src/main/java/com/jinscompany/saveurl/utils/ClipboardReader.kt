package com.jinscompany.saveurl.utils

import android.content.ClipboardManager
import javax.inject.Inject

interface ClipboardReader {
    fun readUrl(): String?
}

class ClipboardReaderImpl @Inject constructor(
    private val clipboardManager: ClipboardManager
) : ClipboardReader {
    override fun readUrl(): String? {
        if (!clipboardManager.hasPrimaryClip()) return null
        if ((clipboardManager.primaryClip?.itemCount ?: 0) == 0) return null
        val text = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: return null
        return extractUrlFromText(text)
    }
}
