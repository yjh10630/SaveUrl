package com.jinscompany.saveurl.data.source

import com.jinscompany.saveurl.domain.model.UrlData

interface LocalUrlDBSource {
    suspend fun getLocalSaveDBUrlList(categoryName: String? = null): List<UrlData>
    suspend fun saveLocalDBUrl(data: UrlData): Boolean
    suspend fun deleteLocalDBUrl(data: UrlData): Boolean
    suspend fun isSavedLocalDBUrl(url: String): Boolean
    suspend fun findLocalDBUrlData(url: String): UrlData?
    suspend fun updateLocalDBUrlData(data: UrlData): Boolean
    suspend fun searchAll(keyword: String): List<UrlData>
    suspend fun searchByTitle(keyword: String): List<UrlData>
    suspend fun searchByDescription(keyword: String): List<UrlData>
    suspend fun searchByTag(keyword: String): List<UrlData>
}