package com.jinscompany.saveurl.domain.importer

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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

    /** @param skippedRecords 따옴표가 짝이 안 맞아 읽지 못하고 건너뛴 줄 수 (미리보기에 표시) */
    class Result(
        val candidates: List<ImportCandidate>,
        val nonHttpCount: Int,
        val hasDates: Boolean,
        val isBackup: Boolean,
        val skippedRecords: Int = 0,
    )

    internal fun parse(
        reader: BoundedLineReader,
        maxCandidates: Int,
        nowMillis: Long = System.currentTimeMillis(),
        isCancelled: () -> Boolean = { false },
        onFound: (Int) -> Unit = {},
    ): Result {
        val records = RecordReader(reader)
        val header = records.next()?.removePrefix("\uFEFF") ?: return Result(emptyList(), 0, false, false)
        val result = if (isSaveLinkBackup(header)) parseBackup(records, maxCandidates, isCancelled, onFound)
        else parseGeneric(header, records, maxCandidates, nowMillis, isCancelled, onFound)
        return Result(result.candidates, result.nonHttpCount, result.hasDates, result.isBackup, skippedRecords = records.skipped)
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
     *
     * 따옴표는 칸의 맨 앞에 올 때만 감싸기로 본다 (`6" ruler` 처럼 칸 중간의 따옴표는 글자). 그래도 감싸기가 끝나지 않으면
     * (열린 따옴표가 [maxLines] 줄·[maxChars] 글자를 넘거나 파일 끝까지 안 닫힘) 첫 줄만 버리고 다음 줄부터 다시 읽는다.
     * 나머지 파일이 한 레코드로 합쳐져 조용히 사라지거나 메모리가 넘치지 않게 (39번 독립 리뷰 R3).
     */
    internal class RecordReader(
        private val lines: BoundedLineReader,
        private val maxLines: Int = 200,
        private val maxChars: Int = 256 * 1024,
    ) {
        var count = 0
            private set
        /** 따옴표가 깨져 건너뛴 줄 */
        var skipped = 0
            private set
        private val pushback = ArrayDeque<String>()

        private fun nextLine(): String? = pushback.removeFirstOrNull() ?: lines.readLine()

        fun next(): String? {
            while (true) {
                val first = nextLine() ?: return null
                var inQuotes = endsInQuotes(first, startInQuotes = false)
                if (!inQuotes) {
                    if (first.isBlank()) continue
                    count++
                    return first
                }
                val parts = arrayListOf(first)
                var chars = first.length
                var broken = false
                while (inQuotes) {
                    val line = nextLine()
                    if (line == null) { broken = true; break }
                    parts.add(line)
                    chars += line.length + 1
                    inQuotes = endsInQuotes(line, startInQuotes = true)
                    if (inQuotes && (parts.size > maxLines || chars > maxChars)) { broken = true; break }
                }
                if (broken) {
                    skipped++
                    for (i in parts.size - 1 downTo 1) pushback.addFirst(parts[i])
                    continue
                }
                count++
                return parts.joinToString("\n")
            }
        }
    }

    /** 이 줄이 끝날 때 따옴표 안인지 ([splitLine] 과 같은 규칙) */
    internal fun endsInQuotes(line: String, startInQuotes: Boolean): Boolean {
        var inQuotes = startInQuotes
        var fieldStart = !startInQuotes
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') i++ else inQuotes = false
                }
            } else {
                when {
                    c == '"' && fieldStart -> { inQuotes = true; fieldStart = false }
                    c == ',' -> fieldStart = true
                    else -> fieldStart = false
                }
            }
            i++
        }
        return inQuotes
    }

    internal fun splitLine(line: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var fieldStart = true
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                // 칸 맨 앞의 따옴표만 감싸기. 칸 중간의 따옴표(6" ruler)는 글자
                c == '"' && !inQuotes && fieldStart -> { inQuotes = true; fieldStart = false }
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' && inQuotes -> inQuotes = false
                c == ',' && !inQuotes -> { result.add(current.toString()); current.clear(); fieldStart = true }
                else -> { current.append(c); fieldStart = false }
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
