package com.jinscompany.saveurl.domain.importer

import java.io.Reader

/**
 * 브라우저 북마크 HTML (Netscape Bookmark 형식, 31번 §1.2).
 *
 * ```
 * <DT><H3 ADD_DATE="…" PERSONAL_TOOLBAR_FOLDER="true">북마크바</H3>
 * <DL><p>
 *     <DT><A HREF="https://…" ADD_DATE="1700000000" ICON="data:…">제목</A>
 * </DL><p>
 * ```
 * 태그가 닫히지 않은 HTML 이라 DOM 대신 태그 스트림 + 폴더 스택으로 읽는다 (아이콘이 든 16 MB 파일도 한 번에 올리지 않음).
 *
 * - 루트 폴더(북마크바, 기타 북마크, 모바일 북마크 등)는 카테고리로 쓰지 않으므로 경로에서 뺀다.
 * - ADD_DATE 단위는 자릿수로 판별한다 (< 10^11 초, < 10^14 밀리초, 그 이상 마이크로초. Firefox 는 마이크로초일 수 있음).
 * - http(s) 가 아닌 링크(javascript:, chrome://, place:)는 건너뛰고 개수만 센다.
 */
class NetscapeBookmarkParser(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    var nonHttpCount = 0
        private set
    private var minDate: Long? = null
    private var maxDate: Long? = null
    fun firstDate(): Long? = minDate
    fun lastDate(): Long? = maxDate

    private class Folder(val name: String, val isRoot: Boolean)

    fun parse(reader: Reader, isCancelled: () -> Boolean = { false }, emit: (ImportCandidate) -> Unit) {
        val stack = ArrayDeque<Folder>()
        var pendingFolder: Folder? = null
        var h3Attrs: Map<String, String> = emptyMap()
        var inH3 = false
        var inA = false
        var aAttrs: Map<String, String> = emptyMap()
        val text = StringBuilder()
        val tag = StringBuilder()

        val buf = CharArray(8192)
        var inTag = false
        var quote: Char? = null
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            if (isCancelled()) throw java.util.concurrent.CancellationException()
            for (k in 0 until n) {
                val c = buf[k]
                if (!inTag) {
                    if (c == '<') {
                        inTag = true
                        tag.setLength(0)
                    } else if ((inA || inH3) && text.length < MAX_TEXT) {
                        text.append(c)
                    }
                    continue
                }
                // 태그 안: 따옴표 안의 '>' 는 태그 끝이 아니다
                if (quote != null) {
                    if (c == quote) quote = null
                    if (tag.length < MAX_TAG) tag.append(c)
                    continue
                }
                if (c == '"' || c == '\'') {
                    quote = c
                    if (tag.length < MAX_TAG) tag.append(c)
                    continue
                }
                if (c != '>') {
                    if (tag.length < MAX_TAG) tag.append(c)
                    continue
                }
                inTag = false
                val raw = tag.toString()
                when (tagName(raw)) {
                    "h3" -> { inH3 = true; text.setLength(0); h3Attrs = attributes(raw) }
                    "/h3" -> {
                        inH3 = false
                        val folderName = HtmlText.decode(text.toString()).trim()
                        // 맨 바깥(문서 루트 DL) 바로 아래 폴더만 루트 폴더 후보
                        val atRootLevel = stack.size <= 1
                        val isRoot = atRootLevel && (folderName.lowercase() in ROOT_NAMES ||
                            h3Attrs.containsKey("personal_toolbar_folder") || h3Attrs.containsKey("unfiled_bookmarks_folder"))
                        pendingFolder = Folder(folderName, isRoot)
                    }
                    "dl" -> {
                        stack.addLast(pendingFolder ?: Folder("", isRoot = true))
                        pendingFolder = null
                    }
                    "/dl" -> if (stack.isNotEmpty()) stack.removeLast()
                    "a" -> { inA = true; text.setLength(0); aAttrs = attributes(raw) }
                    "/a" -> {
                        inA = false
                        val href = aAttrs["href"]?.let(HtmlText::decode)?.trim()
                        if (!href.isNullOrEmpty()) {
                            if (UrlExtractor.isHttpUrl(href)) {
                                val date = (aAttrs["add_date"] ?: aAttrs["time_added"])?.let { toMillis(it) }
                                date?.let { d ->
                                    minDate = minDate?.let { minOf(it, d) } ?: d
                                    maxDate = maxDate?.let { maxOf(it, d) } ?: d
                                }
                                val path = stack.filter { !it.isRoot && it.name.isNotEmpty() }.map { it.name }
                                emit(
                                    ImportCandidate(
                                        url = href,
                                        title = HtmlText.decode(text.toString()).trim().takeIf { it.isNotEmpty() },
                                        date = date,
                                        folderPath = path,
                                        tags = aAttrs["tags"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                                    )
                                )
                            } else {
                                nonHttpCount++
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun tagName(raw: String): String {
        val t = raw.trimStart()
        val end = t.indexOfFirst { it.isWhitespace() }.let { if (it < 0) t.length else it }
        return t.substring(0, end).trimEnd('/').lowercase()
    }

    /** 초/밀리초/마이크로초를 자릿수로 판별해 밀리초로. 2000년 이전·미래면 null */
    internal fun toMillis(value: String): Long? {
        val v = value.trim().toLongOrNull() ?: return null
        if (v <= 0) return null
        val millis = when {
            v < 100_000_000_000L -> v * 1000
            v < 100_000_000_000_000L -> v
            else -> v / 1000
        }
        return millis.takeIf { it >= YEAR_2000_MS && it <= nowMillis() + ONE_DAY_MS }
    }

    companion object {
        private const val MAX_TEXT = 2_000
        /** 아이콘 data URI 가 큰 태그도 있으므로 넉넉히. 넘으면 뒷부분(대개 ICON)은 버린다 */
        private const val MAX_TAG = 64 * 1024
        private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
        private const val YEAR_2000_MS = 946_684_800_000L

        /** 카테고리로 만들지 않는 브라우저 루트 폴더 (소문자) */
        val ROOT_NAMES = setOf(
            "bookmarks", "bookmarks bar", "bookmarks toolbar", "bookmarks menu", "other bookmarks", "mobile bookmarks",
            "favorites bar", "favourites bar", "other favorites", "unsorted bookmarks",
            "북마크", "북마크바", "북마크 바", "북마크 메뉴", "북마크 도구 모음", "기타 북마크", "모바일 북마크",
            "즐겨찾기", "즐겨찾기 모음", "즐겨찾기 표시줄", "기타 즐겨찾기",
        )

        private val ATTR = Regex("""([A-Za-z_:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")

        internal fun attributes(rawTag: String): Map<String, String> {
            val map = HashMap<String, String>()
            for (m in ATTR.findAll(rawTag)) {
                val key = m.groupValues[1].lowercase()
                if (key == "icon" || key == "icon_uri") continue
                val value = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
                map[key] = value
            }
            return map
        }

        /** Netscape 북마크 파일인지 (앞부분으로 판정) */
        fun looksLikeBookmarkFile(head: String): Boolean =
            head.contains("NETSCAPE-Bookmark-file-1", ignoreCase = true) ||
                Regex("""<DT>\s*<A\s+HREF=""", RegexOption.IGNORE_CASE).containsMatchIn(head)
    }
}

/** HTML 엔티티 풀기 (북마크 제목·주소, 일반 HTML 의 &amp;) */
internal object HtmlText {
    private val NUMERIC = Regex("&#(x?[0-9A-Fa-f]+);")
    fun decode(s: String): String {
        if (!s.contains('&')) return s
        var out = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&apos;", "'").replace("&nbsp;", " ")
        out = NUMERIC.replace(out) { m ->
            val v = m.groupValues[1]
            val code = if (v.startsWith("x") || v.startsWith("X")) v.substring(1).toIntOrNull(16) else v.toIntOrNull()
            code?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: m.value
        }
        return out.replace("&amp;", "&")
    }
}
