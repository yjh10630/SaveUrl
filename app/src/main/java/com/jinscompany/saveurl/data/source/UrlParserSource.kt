package com.jinscompany.saveurl.data.source

import com.jinscompany.saveurl.data.source.linkpreview.LinkMeta
import com.jinscompany.saveurl.domain.model.UrlData

interface UrlParserSource {
    suspend fun jsoupUrlParser(url: String): UrlData
    suspend fun webViewGetHtml(url: String): UrlData
    /** 가져온 링크 채우기용: 시간 예산을 짧게 둔 미리보기 수집 (WebView 폴백 없음). 실패하면 null */
    suspend fun fetchMeta(url: String, budgetMs: Long): LinkMeta?
}