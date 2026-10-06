package com.jinscompany.saveurl.data.source.linkpreview

import org.jsoup.nodes.Document
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 응답 바이트 → 문자열 디코딩.
 *
 * 순서: BOM → Content-Type charset → `<meta charset>` / `<meta http-equiv content="...charset=">`
 *       → UTF-8 엄격 디코딩 시도 → 실패하면 MS949(EUC-KR 상위 호환).
 * EUC-KR 로 선언된 페이지도 MS949 로 읽는다 (EUC-KR 에 없는 확장 한글 '똠', '햏' 등 깨짐 방지).
 */
object CharsetDecoder {

    private val META_CHARSET = Regex("""<meta[^>]+charset\s*=\s*["']?\s*([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)

    fun decode(bytes: ByteArray, contentType: String?): String {
        bomCharset(bytes)?.let { (cs, skip) -> return String(bytes, skip, bytes.size - skip, cs) }

        val declared = charsetFromContentType(contentType)
            ?: META_CHARSET.find(String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1))?.groupValues?.get(1)

        val charset = declared?.let(::lookup)
        if (charset != null) {
            if (charset == Charsets.UTF_8) {
                // 헤더는 UTF-8 이라고 하지만 실제로는 EUC-KR 인 사이트가 있다 → 엄격 디코딩으로 검증
                strictDecode(bytes, Charsets.UTF_8)?.let { return it }
                return String(bytes, korean())
            }
            return String(bytes, charset)
        }
        return strictDecode(bytes, Charsets.UTF_8) ?: String(bytes, korean())
    }

    fun charsetFromContentType(contentType: String?): String? {
        if (contentType == null) return null
        val m = Regex("charset\\s*=\\s*\"?([A-Za-z0-9_\\-]+)", RegexOption.IGNORE_CASE).find(contentType)
        return m?.groupValues?.get(1)
    }

    private fun lookup(name: String): Charset? {
        val n = name.lowercase()
        if (n in setOf("euc-kr", "euckr", "ks_c_5601-1987", "ksc5601", "cp949", "ms949", "x-windows-949", "windows-949")) {
            return korean()
        }
        return try {
            Charset.forName(name)
        } catch (e: Exception) {
            null
        }
    }

    /** MS949 → 지원 안 되면 EUC-KR */
    fun korean(): Charset = listOf("x-windows-949", "MS949", "windows-949", "EUC-KR")
        .firstNotNullOfOrNull { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8

    /**
     * UTF-8 로 "대체로" 올바른지 검사. maxBodySize 로 잘린 마지막 멀티바이트 문자 등
     * 소수의 깨진 바이트는 허용하고, EUC-KR 문서처럼 대량으로 깨지면 null.
     */
    private fun strictDecode(bytes: ByteArray, cs: Charset): String? {
        try {
            return cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            val lenient = String(bytes, cs)
            val broken = lenient.count { it == '�' }
            return if (broken <= maxOf(3, lenient.length / 50000)) lenient else null
        }
    }

    private fun bomCharset(b: ByteArray): Pair<Charset, Int>? = when {
        b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte() -> Charsets.UTF_8 to 3
        b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() -> Charsets.UTF_16BE to 2
        b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> Charsets.UTF_16LE to 2
        else -> null
    }
}

/**
 * JS / meta refresh 리다이렉트 감지.
 * 예) blog.naver.com 이 모바일 UA 에 주는 `top.location.replace('https:\/\/m.blog.naver.com\/PostView...')`
 */
object ClientRedirectDetector {

    private val JS_PATTERNS = listOf(
        Regex("""(?:top|window|self|document)?\.?location\.replace\(\s*["']([^"']+)["']\s*\)"""),
        Regex("""(?:top|window|self|document)?\.?location(?:\.href)?\s*=\s*["']([^"']+)["']"""),
    )

    /**
     * 본문이 거의 없는 "리다이렉트 전용" 페이지일 때만 리다이렉트 대상 URL 을 돌려준다.
     * (일반 페이지에 들어있는 location 코드에 끌려가지 않도록)
     */
    fun find(doc: Document, html: String, baseUrl: String): String? {
        doc.selectFirst("meta[http-equiv~=(?i)refresh]")?.attr("content")?.let { content ->
            val m = Regex("""url\s*=\s*['"]?([^'";]+)""", RegexOption.IGNORE_CASE).find(content)
            if (m != null) return LinkFetchRules.resolveLocation(baseUrl, m.groupValues[1].trim())
        }
        val hasMetaTitle = doc.selectFirst("meta[property=og:title]") != null
        val textLength = doc.body()?.text()?.length ?: 0
        if (hasMetaTitle || html.length > 6000 || textLength > 300) return null
        for (p in JS_PATTERNS) {
            val target = p.find(html)?.groupValues?.get(1) ?: continue
            val cleaned = target.replace("\\/", "/").replace("\\x3A", ":").replace("\\x2F", "/")
            if (cleaned.startsWith("http") || cleaned.startsWith("/")) {
                return LinkFetchRules.resolveLocation(baseUrl, cleaned)
            }
        }
        return null
    }
}
