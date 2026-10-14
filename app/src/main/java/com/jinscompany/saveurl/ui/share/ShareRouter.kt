package com.jinscompany.saveurl.ui.share

import com.jinscompany.saveurl.domain.importer.KakaoChatParser
import com.jinscompany.saveurl.domain.importer.UrlExtractor
import com.jinscompany.saveurl.utils.UrlNormalizer
import com.jinscompany.saveurl.utils.extractUrlFromText

/**
 * 공유로 들어온 것 나누기 (44번 §2, 34번 §2.1 순서 그대로). Android 의존성 없이 JVM 테스트한다.
 * 공유 글 내용은 로그·오류 보고에 남기지 않는다 (대화일 수 있음).
 */
object ShareRouter {

    sealed interface Route {
        /** 파일 → 가져오기 */
        data object ImportFiles : Route
        /** 링크가 여러 개 든 글·카톡 대화 → 가져오기 */
        data object ImportText : Route
        /** 링크 1개 → 빠른 저장 시트 */
        data class QuickSave(val url: String) : Route
        /** 빠른 저장 원격 스위치 꺼짐 → 앱의 저장 화면 */
        data class OpenSaveInApp(val url: String) : Route
        /** 가져오기가 꺼진 상태의 파일 */
        data object FileNotSupported : Route
        /** 링크를 찾지 못함 (빈 글, 링크 없는 글) */
        data object NoLink : Route
    }

    /**
     * @param isMultiple SEND_MULTIPLE
     * @param streamCount EXTRA_STREAM 개수 (파일)
     * @param text EXTRA_TEXT
     */
    fun route(
        isMultiple: Boolean,
        streamCount: Int,
        text: String?,
        importEnabled: Boolean,
        quickSaveEnabled: Boolean,
    ): Route {
        if (streamCount > 0) return if (importEnabled) Route.ImportFiles else Route.FileNotSupported
        if (isMultiple) return Route.NoLink
        val shared = text.orEmpty()
        if (shared.isBlank()) return Route.NoLink
        if (importEnabled) {
            val distinctUrls = UrlExtractor.extractAll(shared).map { UrlNormalizer.normalize(it) }.distinct()
            val looksLikeChat = KakaoChatParser.looksLikeKakao(shared.lineSequence().take(8).toList())
            if (distinctUrls.size >= 2 || (looksLikeChat && distinctUrls.isNotEmpty())) return Route.ImportText
        }
        val url = extractUrlFromText(shared)
        if (url.isNullOrEmpty()) return Route.NoLink
        return if (quickSaveEnabled) Route.QuickSave(url) else Route.OpenSaveInApp(url)
    }
}
