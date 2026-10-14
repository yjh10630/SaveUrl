package com.jinscompany.saveurl.domain.importer

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.TimeZone
import java.util.concurrent.CancellationException
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * 가져올 파일 읽기 (31번 §2.2~2.3). 파일 전체를 문자열로 들고 있지 않고 줄·태그 단위로 흘려 읽는다.
 * 원본 파일은 앱 저장소로 복사하지 않고, 후보(링크·날짜·폴더)만 남긴다.
 *
 * 형식 판정 (우선순위 고정):
 * 1. zip (`PK\u0003\u0004`) → 안의 파일마다 2~6. 테이크아웃(`Takeout/`)이면 Chrome 북마크 html 만 연다
 * 2. Netscape 북마크 (`NETSCAPE-Bookmark-file-1`, `<DT><A HREF=`)
 * 3. CSV (`.csv` 이거나 첫 줄이 url 열을 가진 헤더) → SaveLink 백업 / 일반 CSV
 * 4. 카톡 대화 (앞 2줄 헤더, 또는 앞 8줄 중 2줄 이상이 카톡 시각 형식)
 * 5. 나머지 글·HTML·JSON → 링크만 찾음
 *
 * 인코딩: 앞 64 KB 를 UTF-8 엄격 모드로 읽어 보고, 실패하면 CP949(MS949)로 읽는다. BOM 은 뗀다.
 * Android 의존성 없음 (JVM 테스트).
 */
class ImportFileReader(
    private val limits: ImportLimits = ImportLimits(),
    private val timeZone: TimeZone = TimeZone.getDefault(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    /** 지금까지 찾은 링크 수 (읽는 중 화면 "링크 n개 찾음") */
    private var found = 0
    private var truncated = false

    /**
     * @param open 파일 스트림을 연다 (호출부가 닫는다)
     * @param onFound 찾은 링크 수가 늘 때 가끔 부른다
     * @throws ImportFailure 오류 화면으로 보낼 실패
     * @throws CancellationException [isCancelled] 가 true 가 되면
     */
    fun read(
        fileName: String,
        fileSize: Long?,
        input: InputStream,
        isCancelled: () -> Boolean = { false },
        onFound: (Int) -> Unit = {},
    ): ReadOutcome {
        if (fileSize != null && fileSize > limits.maxFileBytes && !fileName.endsWith(".zip", ignoreCase = true)) {
            throw ImportFailure(ImportError.TOO_BIG)
        }
        found = 0
        truncated = false
        val stream = BufferedInputStream(input, 64 * 1024)
        stream.mark(8)
        val magic = ByteArray(4)
        val magicLen = readFully(stream, magic)
        stream.reset()
        if (magicLen == 0) throw ImportFailure(ImportError.EMPTY)
        return try {
            if (magicLen == 4 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte() && magic[2] == 3.toByte() && magic[3] == 4.toByte()) {
                val sources = readZip(stream, isCancelled, onFound)
                ReadOutcome(fileName, fileSize, isZip = true, sources = sources, truncated = truncated)
            } else {
                val limited = LimitedInputStream(stream, limits.maxFileBytes)
                val source = readSingle(fileName, limited, isCancelled, onFound)
                    ?: throw ImportFailure(ImportError.UNSUPPORTED)
                if (source.candidates.isEmpty()) throw ImportFailure(ImportError.NO_LINKS)
                ReadOutcome(fileName, fileSize, isZip = false, sources = listOf(source), truncated = truncated)
            }
        } catch (e: ImportFailure) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: TooBigException) {
            throw ImportFailure(ImportError.TOO_BIG)
        } catch (e: ZipException) {
            throw ImportFailure(ImportError.UNSUPPORTED)
        } catch (e: IOException) {
            throw ImportFailure(ImportError.IO, e.message)
        }
    }

    /** 공유로 들어온 글 (EXTRA_TEXT). 카톡 형식이면 카톡으로, 아니면 링크만 */
    fun readText(text: String, label: String): ParsedSource {
        found = 0
        truncated = false
        val head = text.lineSequence().take(8).toList()
        return if (KakaoChatParser.looksLikeKakao(head)) {
            parseKakao(label, BufferedReader(StringReader(text)), { false }, {})
        } else {
            parseText(label, BufferedReader(StringReader(text)), isHtml = false, { false }, {})
        }
    }

    private fun readZip(stream: InputStream, isCancelled: () -> Boolean, onFound: (Int) -> Unit): List<ParsedSource> {
        val zip = ZipInputStream(stream)
        val sources = ArrayList<ParsedSource>()
        var entries = 0
        var totalBytes = 0L
        var readableEntries = 0
        var isTakeout = false
        var takeoutBookmarks = false
        while (true) {
            if (isCancelled()) throw CancellationException()
            val entry = zip.nextEntry ?: break
            entries++
            if (entries > limits.maxZipEntries) throw TooBigException()
            val path = entry.name.replace('\\', '/')
            if (path.startsWith("Takeout/")) isTakeout = true
            if (entry.isDirectory || path.startsWith("__MACOSX/") || path.substringAfterLast('/').startsWith(".")) continue
            val name = path.substringAfterLast('/')
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in ZIP_EXTENSIONS) continue
            // 테이크아웃은 Chrome 북마크만 연다 (방문 기록 같은 다른 데이터는 읽지 않음)
            if (path.startsWith("Takeout/") && !(path.contains("/Chrome/") && ext in setOf("html", "htm"))) continue
            readableEntries++
            val counted = ZipEntryStream(zip, limits.maxFileBytes) { delta ->
                totalBytes += delta
                if (totalBytes > limits.maxZipTotalBytes) throw TooBigException()
            }
            val source = readSingle(name, counted, isCancelled, onFound)
            if (path.startsWith("Takeout/") && source?.format == ImportFormat.BOOKMARK) {
                takeoutBookmarks = true
                sources.add(source.copy(format = ImportFormat.TAKEOUT))
            } else if (source != null) {
                sources.add(source)
            }
            if (truncated) break
        }
        if (isTakeout && !takeoutBookmarks) throw ImportFailure(ImportError.TAKEOUT_NO_BOOKMARKS)
        if (readableEntries == 0) throw ImportFailure(ImportError.ZIP_EMPTY)
        val withLinks = sources.filter { it.candidates.isNotEmpty() }
        if (withLinks.isEmpty()) throw ImportFailure(ImportError.NO_LINKS)
        return withLinks
    }

    /** 파일 하나. 바이너리·디코드 실패면 null (zip 안에서는 건너뜀) */
    private fun readSingle(name: String, raw: InputStream, isCancelled: () -> Boolean, onFound: (Int) -> Unit): ParsedSource? {
        val stream = if (raw is BufferedInputStream) raw else BufferedInputStream(raw, 64 * 1024)
        stream.mark(HEAD_BYTES + 16)
        val head = ByteArray(HEAD_BYTES)
        val headLen = readFully(stream, head)
        stream.reset()
        if (headLen == 0) return ParsedSource(ImportFormat.TEXT, name, candidates = emptyList())
        if (isBinary(head, headLen)) return null
        val charset = detectCharset(head, headLen) ?: return null
        val headText = String(head, 0, headLen, charset).removePrefix("﻿")
        val reader = BufferedReader(InputStreamReader(stream, charset), 64 * 1024)
        skipBom(reader)
        val lower = name.lowercase()
        val firstLine = headText.lineSequence().firstOrNull().orEmpty()
        return when {
            NetscapeBookmarkParser.looksLikeBookmarkFile(headText) -> parseBookmarks(name, reader, isCancelled, onFound)
            lower.endsWith(".csv") || CsvImportParser.isSaveLinkBackup(firstLine) || CsvImportParser.looksLikeCsvHeader(firstLine) ->
                parseCsv(name, reader, isCancelled, onFound)
            KakaoChatParser.looksLikeKakao(headText.lineSequence().take(8).toList()) -> parseKakao(name, reader, isCancelled, onFound)
            else -> parseText(
                name, reader,
                isHtml = headText.contains("<html", ignoreCase = true) || headText.contains("<a ", ignoreCase = true) || lower.endsWith(".html") || lower.endsWith(".htm"),
                isCancelled, onFound,
            )
        }
    }

    private fun parseKakao(name: String, reader: BufferedReader, isCancelled: () -> Boolean, onFound: (Int) -> Unit): ParsedSource {
        val parser = KakaoChatParser(timeZone, nowMillis)
        val out = ArrayList<ImportCandidate>()
        var lines = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (++lines % 500 == 0 && isCancelled()) throw CancellationException()
            if (truncated) continue
            parser.accept(line) { add(out, it, onFound) }
        }
        return ParsedSource(
            format = ImportFormat.KAKAO, fileName = name, roomName = parser.roomName(), candidates = out,
            hasDates = true, firstDate = parser.firstDate(), lastDate = parser.lastDate(),
        )
    }

    private fun parseBookmarks(name: String, reader: BufferedReader, isCancelled: () -> Boolean, onFound: (Int) -> Unit): ParsedSource {
        val parser = NetscapeBookmarkParser(nowMillis)
        val out = ArrayList<ImportCandidate>()
        parser.parse(reader, isCancelled) { if (!truncated) add(out, it, onFound) }
        return ParsedSource(
            format = ImportFormat.BOOKMARK, fileName = name, candidates = out, nonHttpCount = parser.nonHttpCount,
            hasDates = true, firstDate = parser.firstDate(), lastDate = parser.lastDate(),
        )
    }

    private fun parseCsv(name: String, reader: BufferedReader, isCancelled: () -> Boolean, onFound: (Int) -> Unit): ParsedSource {
        val result = CsvImportParser.parse(reader, maxCandidates = remaining(), nowMillis = nowMillis(), isCancelled = isCancelled) {
            onFound(found + it)
        }
        val list = result.candidates
        if (list.size >= remaining()) truncated = true
        found += list.size
        onFound(found)
        val dates = list.mapNotNull { it.date }
        return ParsedSource(
            format = if (result.isBackup) ImportFormat.BACKUP else ImportFormat.TEXT,
            fileName = name, candidates = list, nonHttpCount = result.nonHttpCount, hasDates = result.hasDates,
            firstDate = dates.minOrNull(), lastDate = dates.maxOrNull(),
        )
    }

    private fun parseText(name: String, reader: BufferedReader, isHtml: Boolean, isCancelled: () -> Boolean, onFound: (Int) -> Unit): ParsedSource {
        val out = ArrayList<ImportCandidate>()
        var lines = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (++lines % 500 == 0 && isCancelled()) throw CancellationException()
            if (truncated) continue
            for (url in UrlExtractor.extractAll(line)) {
                add(out, ImportCandidate(url = if (isHtml) HtmlText.decode(url) else url), onFound)
            }
        }
        return ParsedSource(ImportFormat.TEXT, name, candidates = out)
    }

    private fun remaining(): Int = (limits.maxCandidates - found).coerceAtLeast(0)

    private fun add(out: MutableList<ImportCandidate>, candidate: ImportCandidate, onFound: (Int) -> Unit) {
        if (found >= limits.maxCandidates) {
            truncated = true
            return
        }
        out.add(candidate)
        found++
        if (found % 200 == 0) onFound(found)
    }

    private fun skipBom(reader: BufferedReader) {
        reader.mark(1)
        if (reader.read() != 0xFEFF) reader.reset()
    }

    private class TooBigException : IOException()

    /** 읽은 바이트가 [max] 를 넘으면 [TooBigException] */
    private class LimitedInputStream(input: InputStream, private val max: Long) : FilterInputStream(input) {
        private var count = 0L
        private var markCount = 0L
        override fun read(): Int = super.read().also { if (it >= 0) bump(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bump(it.toLong()) }
        override fun mark(readlimit: Int) { super.mark(readlimit); markCount = count }
        override fun reset() { super.reset(); count = markCount }
        private fun bump(n: Long) { count += n; if (count > max) throw TooBigException() }
    }

    /** zip 항목 하나. 닫아도 zip 을 닫지 않고, 압축 푼 크기를 센다 */
    private class ZipEntryStream(
        private val zip: ZipInputStream,
        private val maxEntryBytes: Long,
        private val onBytes: (Long) -> Unit,
    ) : InputStream() {
        private var count = 0L
        override fun read(): Int = zip.read().also { if (it >= 0) bump(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = zip.read(b, off, len).also { if (it > 0) bump(it.toLong()) }
        override fun close() = Unit
        private fun bump(n: Long) {
            count += n
            if (count > maxEntryBytes) throw TooBigException()
            onBytes(n)
        }
    }

    companion object {
        private const val HEAD_BYTES = 64 * 1024
        private val ZIP_EXTENSIONS = setOf("txt", "html", "htm", "csv", "json", "md")

        private fun readFully(stream: InputStream, buf: ByteArray): Int {
            var total = 0
            while (total < buf.size) {
                val n = stream.read(buf, total, buf.size - total)
                if (n < 0) break
                total += n
            }
            return total
        }

        /** PDF·이미지·NUL 이 든 바이트는 글이 아니다 */
        internal fun isBinary(head: ByteArray, len: Int): Boolean {
            if (len >= 4 && head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() && head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte()) return true
            // UTF-16 BOM 이 아닌데 NUL 이 있으면 바이너리
            if (len >= 2 && ((head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) || (head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()))) return false
            val scan = minOf(len, 8192)
            for (i in 0 until scan) if (head[i] == 0.toByte()) return true
            return false
        }

        /**
         * UTF-8(엄격) → 안 되면 CP949. 앞부분 끝에서 잘린 멀티바이트 글자 때문에 실패하지 않도록 마지막 3바이트 안의 오류는 무시한다.
         * UTF-16 BOM 이면 UTF-16.
         */
        internal fun detectCharset(head: ByteArray, len: Int): Charset? {
            if (len >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) return Charsets.UTF_16LE
            if (len >= 2 && head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()) return Charsets.UTF_16BE
            if (isStrictUtf8(head, len)) return Charsets.UTF_8
            return koreanCharset()
        }

        private fun isStrictUtf8(head: ByteArray, len: Int): Boolean {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            // 끝에서 잘린 글자: 마지막 0~3바이트를 줄여 가며 시도
            for (cut in 0..3) {
                val n = len - cut
                if (n <= 0) break
                try {
                    decoder.reset()
                    decoder.decode(ByteBuffer.wrap(head, 0, n))
                    return true
                } catch (e: CharacterCodingException) {
                    if (len < HEAD_BYTES) return false // 파일 전체를 읽었으면 잘린 글자가 아니다
                }
            }
            return false
        }

        private fun koreanCharset(): Charset? =
            listOf("MS949", "x-windows-949", "windows-949", "EUC-KR")
                .firstNotNullOfOrNull { runCatching { Charset.forName(it) }.getOrNull() }
    }
}
