package com.jinscompany.saveurl.utils

/**
 * 중복 감지용 URL 정규화 (BaseSaveUrl.normalizedUrl).
 *
 * - 저장 시 제거하는 추적 파라미터([TrackingParamStripper]) + 중복 감지 전용 추가 파라미터(ref, feature 등)를 무시
 * - scheme 은 https 로, host 는 소문자 + www. 제거, 포트/userinfo/fragment 제거, 경로 끝 '/' 제거
 * - 쿼리는 키 기준 정렬(같은 키는 첫 값만), 경로/값은 퍼센트 디코딩
 * - youtu.be/{id} → youtube.com/watch?v={id}
 *
 * android.net.Uri 를 쓰지 않는 순수 Kotlin 구현 (JVM 단위 테스트 가능).
 * 출력 형식을 바꾸면 [VERSION] 을 올려야 한다 → 앱 시작 시 백필이 기존 행의 normalizedUrl 을 다시 계산한다.
 */
object UrlNormalizer {

    /**
     * 정규화 알고리즘 버전.
     * 1: android.net.Uri 기반 최초 구현 (DB v4, versionCode 38 까지)
     * 2: 저장 시 추적 파라미터 정책(TrackingParamStripper) 반영, 순수 Kotlin 구현
     */
    const val VERSION = 2

    // 저장된 URL 에서는 지우지 않지만 중복 판단 시에는 무시하는 파라미터 (대소문자 구분, 기존 동작 유지)
    private val DUPLICATE_IGNORED_PARAMS = setOf(
        "gclsrc",
        "ref", "ref_src", "ref_url",
        "_ga", "_gl",
        "si",         // YouTube/Spotify 외 사이트의 share id 도 중복 판단에서는 무시
        "feature",    // YouTube
        "pp",         // YouTube
        "WT.mc_id",
        "zanpid", "origin",
    )

    fun normalize(rawUrl: String): String {
        return try {
            val url = rawUrl.trim()
            val beforeFragment = url.substringBefore('#')
            val base = beforeFragment.substringBefore('?')
            val query = if (beforeFragment.contains('?')) beforeFragment.substringAfter('?') else ""

            var host = TrackingParamStripper.extractHost(base) ?: return rawUrl
            val originalHost = host
            if (host.startsWith("www.")) host = host.removePrefix("www.")

            val afterScheme = base.substringAfter("://")
            val rawPath = if (afterScheme.contains('/')) "/" + afterScheme.substringAfter('/') else ""

            val queryParams = mutableListOf<String>()
            val path: String
            if (host == "youtu.be") {
                host = "youtube.com"
                val videoId = rawPath.trim('/').substringBefore('/')
                path = "/watch"
                if (videoId.isNotEmpty()) queryParams.add("v=${UrlCodec.decode(videoId)}")
            } else {
                path = UrlCodec.decode(rawPath).trimEnd('/')
                val firstValues = LinkedHashMap<String, String>()
                query.split('&').filter { it.isNotEmpty() }.forEach { segment ->
                    val key = UrlCodec.decode(segment.substringBefore('='))
                    val value = if (segment.contains('=')) UrlCodec.decode(segment.substringAfter('=')) else ""
                    if (key.isEmpty() || key in firstValues) return@forEach
                    if (key in DUPLICATE_IGNORED_PARAMS) return@forEach
                    if (TrackingParamStripper.isTrackingKey(key.lowercase(), originalHost)) return@forEach
                    firstValues[key] = value
                }
                firstValues.keys.sorted().forEach { key -> queryParams.add("$key=${firstValues[key]}") }
            }

            val queryString = if (queryParams.isEmpty()) "" else "?${queryParams.joinToString("&")}"
            "https://$host$path$queryString"
        } catch (e: Exception) {
            rawUrl
        }
    }
}
