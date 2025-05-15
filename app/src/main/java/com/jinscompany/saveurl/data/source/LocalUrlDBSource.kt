package com.jinscompany.saveurl.data.source

import androidx.paging.PagingSource
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.UrlData

interface LocalUrlDBSource {
    fun getLocalSaveDBUrlList(params: FilterParams? = null): PagingSource<Int, UrlData>
    fun searchAll(keyword: String): PagingSource<Int, UrlData>
    fun searchByTitle(keyword: String): PagingSource<Int, UrlData>
    fun searchByDescription(keyword: String): PagingSource<Int, UrlData>
    fun searchByTag(keyword: String): PagingSource<Int, UrlData>
    suspend fun saveLocalDBUrl(data: UrlData): Boolean
    suspend fun deleteLocalDBUrl(data: UrlData): Boolean
    suspend fun isSavedLocalDBUrl(url: String): Boolean
    suspend fun findLocalDBUrlData(url: String): UrlData?
    suspend fun updateLocalDBUrlData(data: UrlData): Boolean
    suspend fun getSiteNameList(): List<String>
}