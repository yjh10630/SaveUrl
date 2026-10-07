package com.jinscompany.saveurl.utils

/**
 * 링크 저장 시 URL 에서 광고/유입 추적용 쿼리 파라미터를 제거한다.
 *
 * 정책 (보수적으로 적용):
 * - 모든 도메인: `utm_*`, fbclid, gclid, dclid, gbraid, wbraid, msclkid, igshid, igsh, mc_cid, mc_eid, _hsenc, _hsmi, yclid
 * - aliexpress.* 에서만: spm
 * - youtube.com(서브도메인 포함) / youtu.be / open.spotify.com 에서만: si
 * - 콘텐츠 식별에 쓰이는 파라미터(유튜브 v/list/t, 네이버 productNo, 쿠팡 itemId/vendorItemId 등)는 절대 제거하지 않음
 * - share_id / share_source 같이 사이트마다 의미가 다른 이름은 제거하지 않음
 *
 * 남는 파라미터는 원래 순서와 인코딩을 그대로 유지하고, fragment(#...) 도 그대로 둔다.
 * 제거할 파라미터가 없으면 입력 문자열을 그대로 반환한다.
 *
 * android.net.Uri 를 쓰지 않는 순수 Kotlin 구현이라 JVM 단위 테스트가 가능하다.
 */
object TrackingParamStripper {

    private val GLOBAL_TRACKING_KEYS = setOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid",
        "igshid", "igsh", "mc_cid", "mc_eid", "_hsenc", "_hsmi", "yclid",
    )

    fun strip(rawUrl: String): String {
        val url = rawUrl.trim()
        val hashIndex = url.indexOf('#')
        val beforeFragment = if (hashIndex >= 0) url.substring(0, hashIndex) else url
        val fragment = if (hashIndex >= 0) url.substring(hashIndex) else ""

        val queryIndex = beforeFragment.indexOf('?')
        if (queryIndex < 0) return url
        val base = beforeFragment.substring(0, queryIndex)
        val query = beforeFragment.substring(queryIndex + 1)
        if (query.isEmpty()) return url

        val host = extractHost(base) ?: return url
        val segments = query.split('&')
        val kept = segments.filterNot { segment ->
            val rawKey = segment.substringBefore('=')
            isTrackingKey(UrlCodec.decode(rawKey).lowercase(), host)
        }
        if (kept.size == segments.size) return url

        val newQuery = kept.joinToString("&")
        return buildString {
            append(base)
            if (newQuery.isNotEmpty()) append('?').append(newQuery)
            append(fragment)
        }
    }

    internal fun isTrackingKey(key: String, host: String): Boolean {
        if (key.startsWith("utm_")) return true
        if (key in GLOBAL_TRACKING_KEYS) return true
        if (key == "spm" && isAliExpress(host)) return true
        if (key == "si" && isSiShareHost(host)) return true
        return false
    }

    private fun isAliExpress(host: String): Boolean = host.split('.').contains("aliexpress")

    private fun isSiShareHost(host: String): Boolean =
        host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtu.be" || host == "open.spotify.com"

    /** `scheme://[userinfo@]host[:port]/...` 에서 소문자 host 를 추출. 형식이 아니면 null */
    internal fun extractHost(urlWithoutQuery: String): String? {
        val schemeEnd = urlWithoutQuery.indexOf("://")
        if (schemeEnd <= 0) return null
        val afterScheme = urlWithoutQuery.substring(schemeEnd + 3)
        val authority = afterScheme.substringBefore('/')
        val hostPort = authority.substringAfterLast('@')
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringBefore(']') + "]"
        } else {
            hostPort.substringBefore(':')
        }
        return host.lowercase().trimEnd('.').takeIf { it.isNotEmpty() }
    }
}

/** 퍼센트 디코딩 (UTF-8). 잘못된 시퀀스는 원문 그대로 둔다. '+' 는 공백으로 바꾸지 않는다(android.net.Uri 와 동일). */
internal object UrlCodec {
    fun decode(s: String): String {
        if (s.indexOf('%') < 0) return s
        val out = java.io.ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length && isHex(s[i + 1]) && isHex(s[i + 2])) {
                out.write((Character.digit(s[i + 1], 16) shl 4) or Character.digit(s[i + 2], 16))
                i += 3
            } else if (Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1])) {
                // 서로게이트 쌍(이모지 등)은 두 char 를 함께 인코딩
                out.write(s.substring(i, i + 2).toByteArray(Charsets.UTF_8))
                i += 2
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun isHex(c: Char) = Character.digit(c, 16) >= 0
}
