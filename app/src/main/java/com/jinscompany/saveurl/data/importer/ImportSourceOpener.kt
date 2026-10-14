package com.jinscompany.saveurl.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** 고른 파일(SAF·공유 URI)의 이름·크기와 스트림. 원본은 앱 저장소로 복사하지 않는다 */
interface ImportSourceOpener {
    fun describe(uri: Uri): Pair<String, Long?>
    fun open(uri: Uri): InputStream?
}

@Singleton
class ContentResolverSourceOpener @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImportSourceOpener {
    override fun describe(uri: Uri): Pair<String, Long?> {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !c.isNull(nameIdx)) name = c.getString(nameIdx)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx).takeIf { it >= 0 }
                }
            }
        }
        return (name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file") to size
    }

    override fun open(uri: Uri): InputStream? = context.contentResolver.openInputStream(uri)
}
