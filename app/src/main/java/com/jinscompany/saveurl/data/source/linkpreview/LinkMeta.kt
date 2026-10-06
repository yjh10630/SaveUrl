package com.jinscompany.saveurl.data.source.linkpreview

import com.jinscompany.saveurl.domain.model.UrlData

/**
 * 링크 미리보기 파싱 결과 (Android 의존성 없는 순수 모델).
 *
 * @param titleFromMeta og/twitter/JSON-LD/oEmbed 처럼 "미리보기용으로 제공된" 제목이면 true.
 *                      `<title>` 태그만으로 얻은 제목은 false (봇 차단/로그인 페이지 등 오탐 가능성이 높음).
 */
data class LinkMeta(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
    val titleFromMeta: Boolean = false,
    val method: String = "",
) {
    val hasTitle: Boolean get() = !title.isNullOrBlank()

    /** 미리보기로 쓰기에 충분한지: 메타 기반 제목이 있으면 OK */
    val isGood: Boolean get() = hasTitle && titleFromMeta

    /** 점수: 제목(메타) > 이미지 > 설명 순으로 가중치 */
    val score: Int
        get() = (if (hasTitle) 4 else 0) + (if (titleFromMeta) 4 else 0) +
                (if (!imageUrl.isNullOrBlank()) 2 else 0) + (if (!description.isNullOrBlank()) 1 else 0)

    fun toUrlData(): UrlData = UrlData(
        url = url,
        title = title.orEmpty(),
        description = description.orEmpty(),
        imgUrl = imageUrl.orEmpty(),
        siteName = siteName.orEmpty(),
    )
}
