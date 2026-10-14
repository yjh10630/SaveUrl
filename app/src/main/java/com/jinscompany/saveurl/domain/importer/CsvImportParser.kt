package com.jinscompany.saveurl.domain.importer

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader

/**
 * CSV 가져오기.
 * - SaveLink 데이터 백업(헤더 `id,url,imageUrl,…`): 즐겨찾기·태그·읽음·카테고리·잠금 여부를 그대로 살린다.
 * - 그 밖의 CSV(Pocket·Raindrop·Instapaper 등): 열 이름으로 url / title / folder / tags / 시각을 찾는다 (순서 무관).
 *   url 열이 없으면 모든 칸에서 링크를 찾는다.
 */
object CsvImportParser {

    private val gson = Gson()
    private val listType = TypeToken.getParameterized(List::class.java, String::class.java).type

    private const val SAVELINK_HEADER_PREFIX = "id,url,imageUrl"

    fun isSaveLinkBackup(header: String): Boolean =
        header.removePrefix("\uFEFF").trim().startsWith(SAVELINK_HEADER_PREFIX)

    /** 첫 줄이 쉼표로 나뉜 열 이름처럼 보이는지 (.csv 확장자가 없을 때) */
    fun looksLikeCsvHeader(header: String): Boolean {
        val cols = splitLine(header.removePrefix("\uFEFF")).map { it.trim().lowercase() }
        return cols.size >= 2 && cols.any { it in URL_COLUMNS }
    }

    private val URL_COLUMNS = setOf("url", "link", "href", "uri", "address")
    private val TITLE_COLUMNS = setOf("title", "name")
    private val FOLDER_COLUMNS = setOf("folder", "collection")
    private val TAG_COLUMNS = setOf("tags", "tag")
    private val DATE_COLUMNS = setOf("time_added", "created", "timestamp", "date", "added", "saved", "created_at")

    class Result(val candidates: List<ImportCandidate>, val nonHttpCount: Int, val hasDates: Boolean, val isBackup: Boolean)

    fun parse(
        reader: BufferedReader,
        maxCandidates: Int,
        nowMillis: Long = System.currentTimeMillis(),
        isCancelled: () -> Boolean = { false },
        onFound: (Int) -> Unit = {},
    ): Result {
        val records = RecordReader(reader)
        val header = records.next()?.removePrefix("\uFEFF") ?: return Result(emptyList(), 0, false, false)
        return if (isSaveLinkBackup(header)) parseBackup(records, maxCandidates, isCancelled, onFound)
        else parseGeneric(header, records, maxCandidates, nowMillis, isCancelled, onFound)
    }

    private fun parseBackup(records: RecordReader, max: Int, isCancelled: () -> Boolean, onFound: (Int) -> Unit): Result {
        val out = ArrayList<ImportCandidate>()
        var nonHttp = 0
        while (out.size < max) {
            val record = records.next() ?: break
            if (records.count % 1000 == 0 && isCancelled()) throw java.util.concurrent.CancellationException()
            val cols = splitLine(record)
            if (cols.size < 11) continue
            val url = cols[1].trim()
            if (url.isEmpty()) continue
            if (!UrlExtractor.isHttpUrl(url)) { nonHttp++; continue }
            val tags: List<String> = runCatching { gson.fromJson<List<String>>(cols[6], listType) }.getOrNull().orEmpty()
            out.add(
                ImportCandidate(
                    url = url,
                    imageUrl = cols[2].ifEmpty { null },
                    siteName = cols[3].ifEmpty { null },
                    title = cols[4].ifEmpty { null },
                    description = cols[5].ifEmpty { null },
                    tags = tags.filter { it.isNotBlank() },
                    date = cols[7].trim().toLongOrNull()?.takeIf { it > 0 },
                    category = cols[8].trim().ifEmpty { null },
                    isBookmark = cols[9].trim().toBooleanStrictOrNull() ?: false,
                    isRead = cols[10].trim().toBooleanStrictOrNull() ?: false,
                    categoryLocked = cols.getOrNull(12)?.trim()?.toBooleanStrictOrNull() == true,
                )
            )
            if (out.size % 200 == 0) onFound(out.size)
        }
        return Result(out, nonHttp, hasDates = true, isBackup = true)
    }

    private fun parseGeneric(
        header: String,
        records: RecordReader,
        max: Int,
        nowMillis: Long,
        isCancelled: () -> Boolean,
        onFound: (Int) -> Unit,
    ): Result {
        val cols = splitLine(header).map { it.trim().lowercase() }
        val urlIdx = cols.indexOfFirst { it in URL_COLUMNS }
        val out = ArrayList<ImportCandidate>()
        var nonHttp = 0
        if (urlIdx < 0) {
            // 열 이름을 모르면 모든 칸에서 링크를 찾는다 (첫 줄 포함)
            var record: String? = header
            while (record != null && out.size < max) {
                if (records.count % 1000 == 0 && isCancelled()) throw java.util.concurrent.CancellationException()
                for (url in UrlExtractor.extractAll(record)) {
                    if (out.size >= max) break
                    out.add(ImportCandidate(url = url))
                }
                record = records.next()
            }
            return Result(out, 0, hasDates = false, isBackup = false)
        }
        val titleIdx = cols.indexOfFirst { it in TITLE_COLUMNS }
        val folderIdx = cols.indexOfFirst { it in FOLDER_COLUMNS }
        val tagIdx = cols.indexOfFirst { it in TAG_COLUMNS }
        val dateIdx = cols.indexOfFirst { it in DATE_COLUMNS }
        while (out.size < max) {
            val record = records.next() ?: break
            if (records.count % 1000 == 0 && isCancelled()) throw java.util.concurrent.CancellationException()
            val row = splitLine(record)
            val url = row.getOrNull(urlIdx)?.trim().orEmpty()
            if (url.isEmpty()) continue
            if (!UrlExtractor.isHttpUrl(url)) { nonHttp++; continue }
            val folder = row.getOrNull(folderIdx)?.trim().orEmpty()
            out.add(
                ImportCandidate(
                    url = url,
                    title = row.getOrNull(titleIdx)?.trim()?.ifEmpty { null },
                    folderPath = folder.split('/').map { it.trim() }.filter { it.isNotEmpty() },
                    tags = row.getOrNull(tagIdx)?.split('|', ',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                    date = row.getOrNull(dateIdx)?.let { parseDate(it, nowMillis) },
                )
            )
            if (out.size % 200 == 0) onFound(out.size)
        }
        return Result(out, nonHttp, hasDates = dateIdx >= 0, isBackup = false)
    }

    /** 초/밀리초 숫자만 (문자열 날짜 형식은 서비스마다 달라 읽지 않음 → 오늘 날짜) */
    private fun parseDate(value: String, nowMillis: Long): Long? {
        val v = value.trim().toLongOrNull() ?: return null
        val millis = if (v < 100_000_000_000L) v * 1000 else v
        return millis.takeIf { it >= 946_684_800_000L && it <= nowMillis + 86_400_000L }
    }

    /**
     * 레코드 하나씩 읽는다. 따옴표 안의 줄바꿈은 한 레코드로 합친다 (CsvBackupManager.readRecords 와 같은 규칙).
     * 파일 전체를 목록으로 올리지 않는다.
     */
    internal class RecordReader(private val reader: BufferedReader) {
        var count = 0
            private set

        fun next(): String? {
            val current = StringBuilder()
            var quoteCount = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (current.isNotEmpty() || quoteCount % 2 == 1) current.append('\n')
                current.append(line)
                quoteCount += line.count { it == '"' }
                if (quoteCount % 2 == 0) {
                    if (current.isBlank()) { current.clear(); continue }
                    count++
                    return current.toString()
                }
            }
            if (current.isBlank()) return null
            count++
            return current.toString()
        }
    }

    internal fun splitLine(line: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' && inQuotes -> inQuotes = false
                c == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
