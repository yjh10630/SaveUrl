package com.jinscompany.saveurl.data.source

import com.jinscompany.saveurl.domain.model.UrlData

interface UrlParserSource {
    suspend fun jsoupUrlParser(url: String): UrlData
    suspend fun webViewGetHtml(url: String): UrlData
}