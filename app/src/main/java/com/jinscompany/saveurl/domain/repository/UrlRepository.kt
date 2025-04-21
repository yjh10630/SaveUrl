package com.jinscompany.saveurl.domain.repository

import com.jinscompany.saveurl.domain.model.UrlData

interface UrlRepository {
    suspend fun getUrlList(categoryName: String? = null): List<UrlData>
    suspend fun saveUrl(data: UrlData): Boolean
    suspend fun removeUrl(data: UrlData): List<UrlData>
    suspend fun parserUrl(url: String): UrlData
    suspend fun isSavedUrl(url: String): Boolean
    suspend fun findUrlData(url: String): UrlData?
    suspend fun updateUrl(data: UrlData): Boolean
    suspend fun searchAll(keyword: String): List<UrlData>
    suspend fun searchByTitle(keyword: String): List<UrlData>
    suspend fun searchByDescription(keyword: String): List<UrlData>
    suspend fun searchByTag(keyword: String): List<UrlData>
}