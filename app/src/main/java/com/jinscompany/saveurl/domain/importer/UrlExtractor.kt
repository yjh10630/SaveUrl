package com.jinscompany.saveurl.domain.importer

/**
 * 글 안의 링크를 모두 찾는다 (31번 §1.4). 저장 화면의 [com.jinscompany.saveurl.utils.extractUrlFromText] 와
 * 같은 문자 집합·끝 구두점 규칙에 세 가지를 더했다.
 *
 * 1. 한글 경로: `/ = ? & #` 바로 뒤에 오는 한글은 URL 로 본다 (`namu.wiki/w/카카오톡`). 다른 글자 뒤에 붙은 한글(조사)은 뺀다.
 * 2. 붙은 URL 분리: `, ; | ) ] >` 나 한글 바로 뒤에 `http(s)://` 가 오면 거기서 끊는다 (`=` 뒤는 끊지 않음: `?u=https://…`).
 * 3. `www.` 로 시작하면 `https://` 를 붙인다. 스킴 없는 `naver.me/xx` 는 잡지 않는다 (`파일.txt` 오탐 방지).
 *
 * 호스트에 점이 없는 것(localhost)과 이메일 안의 www 는 버린다. Android 의존성 없음 (JVM 테스트).
 */
object UrlExtractor {

    private val startRegex = Regex("(?i)(https?://|www\\.)")

    /** RFC 3986 문자 (기존 저장 화면 정규식과 같음) */
    private fun isUrlChar(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-._~:/?#[]@!$&'()*+,;=%"

    private fun isHangul(c: Char): Boolean = c in '가'..'힣' || c in 'ㄱ'..'ㆎ'

    private val splitBefore = ",;|)]>"

    fun extractAll(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val result = ArrayList<String>()
        var searchFrom = 0
        while (searchFrom < text.length) {
            val match = startRegex.find(text, searchFrom) ?: break
            val start = match.range.first
            // 이메일(abc@www.x.com)이나 단어 중간(xwww.)의 www 는 건너뛴다
            if (match.value.startsWith("www", ignoreCase = true) && start > 0) {
                val prev = text[start - 1]
                if (prev == '@' || prev.isLetterOrDigit() || prev == '.' || prev == '/') {
                    searchFrom = match.range.last + 1
                    continue
                }
            }
            val end = scanEnd(text, start, match.value.length)
            val raw = trimTrailing(text.substring(start, end))
            searchFrom = maxOf(end, match.range.last + 1)
            val url = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
            if (isAcceptable(url)) result.add(url)
        }
        return result
    }

    private fun scanEnd(text: String, start: Int, prefixLength: Int): Int {
        var i = start + prefixLength
        var inHangulRun = false
        while (i < text.length) {
            val c = text[i]
            // 붙은 다음 URL 에서 끊기
            if (i > start + prefixLength && (text.startsWith("http://", i, ignoreCase = true) || text.startsWith("https://", i, ignoreCase = true))) {
                val prev = text[i - 1]
                if (prev in splitBefore || isHangul(prev)) break
            }
            if (isUrlChar(c)) {
                inHangulRun = false
                i++
                continue
            }
            if (isHangul(c)) {
                val prev = text[i - 1]
                if (inHangulRun || prev in "/=?&#") {
                    inHangulRun = true
                    i++
                    continue
                }
            }
            break
        }
        return i
    }

    /** 문장 끝 구두점, 짝 없는 닫는 괄호 제거 (기존 extractUrlFromText 와 같은 규칙) */
    private fun trimTrailing(url: String): String {
        var end = url.length
        while (end > 0) {
            val c = url[end - 1]
            val trim = when (c) {
                '.', ',', ';', ':', '!', '?', '\'', '"', '|' -> true
                ')' -> url.substring(0, end).count { it == '(' } < url.substring(0, end).count { it == ')' }
                ']' -> url.substring(0, end).count { it == '[' } < url.substring(0, end).count { it == ']' }
                else -> false
            }
            if (!trim) break
            end--
        }
        return url.substring(0, end)
    }

    private fun isAcceptable(url: String): Boolean {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return false
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':')
        if (authority.contains('@')) return false
        return host.contains('.') && !host.startsWith('.') && !host.endsWith('.')
    }

    /** http(s) 링크인지 (북마크·CSV 의 javascript:, chrome:// 등은 건너뛴다) */
    fun isHttpUrl(url: String): Boolean {
        val trimmed = url.trim()
        return (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
            isAcceptable(trimmed)
    }
}
