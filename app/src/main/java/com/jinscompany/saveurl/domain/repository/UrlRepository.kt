package com.jinscompany.saveurl.domain.repository

import androidx.paging.PagingSource
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.UrlData

interface UrlRepository {
    fun getUrlList(categoryName: FilterParams? = null): PagingSource<Int, UrlData>
    fun searchAll(keyword: String): PagingSource<Int, UrlData>
    fun searchByTitle(keyword: String): PagingSource<Int, UrlData>
    fun searchByDescription(keyword: String): PagingSource<Int, UrlData>
    fun searchByTag(keyword: String): PagingSource<Int, UrlData>
    suspend fun removeUrl(data: UrlData): Boolean
    suspend fun saveUrl(data: UrlData): Boolean
    suspend fun parserUrl(url: String): UrlData
    suspend fun isSavedUrl(url: String): Boolean
    suspend fun findUrlData(url: String): UrlData?
    suspend fun updateUrl(data: UrlData): Boolean
}