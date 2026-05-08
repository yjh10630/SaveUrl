package com.jinscompany.saveurl.utils

import com.jinscompany.saveurl.domain.model.UrlData

sealed class CrawlResult {
    data class Success(val data: UrlData) : CrawlResult()
    data class NoMetadata(val data: UrlData) : CrawlResult()  // title 없음, url만 채워진 상태
    data object Blocked : CrawlResult()                        // 403/401 등 차단
    data object Failed : CrawlResult()                         // 완전 실패
}
