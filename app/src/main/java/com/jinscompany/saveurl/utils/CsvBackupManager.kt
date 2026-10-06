package com.jinscompany.saveurl.utils

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object CsvBackupManager {

    private val gson = Gson()
    // R8 full mode 대응: 익명 TypeToken 서브클래스 대신 getParameterized 사용
    private val listType = TypeToken.getParameterized(List::class.java, String::class.java).type

    private val header = "id,url,imageUrl,siteName,title,description,tagList,addDate,category,isBookMark,isRead,normalizedUrl"

    suspend fun export(context: Context, uri: Uri, items: List<UrlData>): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                writer.write(header)
                writer.write("\n")
                items.forEach { item ->
                    writer.write(buildCsvRow(item))
                    writer.write("\n")
                }
            }
        }
        items.size
    }

    suspend fun import(context: Context, uri: Uri): List<UrlData> = withContext(Dispatchers.IO) {
        val results = mutableListOf<UrlData>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                readRecords(reader).drop(1) // skip header
                    .forEach { record -> parseCsvRow(record)?.let { results.add(it) } }
            }
        }
        results
    }

    /**
     * 따옴표로 감싼 필드 안의 줄바꿈(설명/제목의 개행)을 하나의 레코드로 합쳐서 반환.
     * readLine() 단위로 파싱하면 개행이 포함된 행이 잘려 통째로 누락됨.
     * 이스케이프된 따옴표("")는 2개씩 세어지므로 따옴표 개수가 홀수면 필드가 아직 열려 있는 상태.
     */
    internal fun readRecords(reader: BufferedReader): List<String> {
        val records = mutableListOf<String>()
        val current = StringBuilder()
        var quoteCount = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (current.isNotEmpty() || quoteCount % 2 == 1) current.append('\n')
            current.append(line)
            quoteCount += line.count { it == '"' }
            if (quoteCount % 2 == 0) {
                records.add(current.toString())
                current.clear()
                quoteCount = 0
            }
        }
        if (current.isNotEmpty()) records.add(current.toString())
        return records
    }

    internal fun buildCsvRow(item: UrlData): String {
        val tagsJson = gson.toJson(item.tagList ?: emptyList<String>())
        return listOf(
            item.id.toString(),
            item.url.escapeCsv(),
            item.imgUrl.escapeCsv(),
            item.siteName.escapeCsv(),
            item.title.escapeCsv(),
            item.description.escapeCsv(),
            tagsJson.escapeCsv(),
            item.addDate.toString(),
            item.category.escapeCsv(),
            item.isBookMark.toString(),
            item.isRead.toString(),
            item.normalizedUrl.escapeCsv(),
        ).joinToString(",")
    }

    internal fun parseCsvRow(line: String): UrlData? {
        return try {
            val cols = splitCsvLine(line)
            if (cols.size < 11) return null
            val tagList: List<String> = try {
                gson.fromJson<List<String>>(cols[6], listType) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            val rawUrl = cols[1].ifEmpty { null }
            UrlData(
                id = cols[0].toIntOrNull() ?: 0,
                url = rawUrl,
                imgUrl = cols[2].ifEmpty { null },
                siteName = cols[3].ifEmpty { null },
                title = cols[4].ifEmpty { null },
                description = cols[5].ifEmpty { null },
                tagList = tagList,
                addDate = cols[7].toLongOrNull() ?: System.currentTimeMillis(),
                category = cols[8].ifEmpty { null },
                isBookMark = cols[9].toBooleanStrictOrNull() ?: false,
                isRead = cols[10].toBooleanStrictOrNull() ?: false,
                normalizedUrl = if (cols.size > 11 && cols[11].isNotEmpty()) cols[11]
                                else UrlNormalizer.normalize(rawUrl ?: ""),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' && inQuotes -> inQuotes = false
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun String?.escapeCsv(): String {
        if (this == null) return ""
        return if (contains(',') || contains('"') || contains('\n') || contains('\r')) {
            "\"${replace("\"", "\"\"")}\""
        } else this
    }
}
