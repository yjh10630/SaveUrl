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
    private val listType = object : TypeToken<List<String>>() {}.type

    private val header = "id,url,imageUrl,siteName,title,description,tagList,addDate,category,isBookMark,isRead"

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
                reader.readLine() // skip header
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    parseCsvRow(line ?: "")?.let { results.add(it) }
                }
            }
        }
        results
    }

    private fun buildCsvRow(item: UrlData): String {
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
        ).joinToString(",")
    }

    private fun parseCsvRow(line: String): UrlData? {
        return try {
            val cols = splitCsvLine(line)
            if (cols.size < 11) return null
            val tagList: List<String> = try {
                gson.fromJson(cols[6], listType)
            } catch (e: Exception) {
                emptyList()
            }
            UrlData(
                id = cols[0].toIntOrNull() ?: 0,
                url = cols[1].ifEmpty { null },
                imgUrl = cols[2].ifEmpty { null },
                siteName = cols[3].ifEmpty { null },
                title = cols[4].ifEmpty { null },
                description = cols[5].ifEmpty { null },
                tagList = tagList,
                addDate = cols[7].toLongOrNull() ?: System.currentTimeMillis(),
                category = cols[8].ifEmpty { null },
                isBookMark = cols[9].toBooleanStrictOrNull() ?: false,
                isRead = cols[10].toBooleanStrictOrNull() ?: false,
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
        return if (contains(',') || contains('"') || contains('\n')) {
            "\"${replace("\"", "\"\"")}\""
        } else this
    }
}
