package com.jinscompany.saveurl.data.room

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jinscompany.saveurl.domain.model.UrlData

@Dao
interface BaseSaveUrlDao {

    @Query("SELECT * FROM basesaveurl ORDER BY addDate DESC")
    fun getUrlDataLatest(): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl ORDER BY addDate ASC")
    fun getUrlDataOldest(): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE isBookMark = 1 ORDER BY addDate DESC")
    fun getTargetBookMarkUrlDataLatest(): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE isBookMark = 1 ORDER BY addDate ASC")
    fun getTargetBookMarkUrlDataOldest(): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE category = :categoryName ORDER BY addDate DESC")
    fun getTargetCategoryUrlData(categoryName: String): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE category IN (:categoryNames) ORDER BY addDate DESC")
    fun getTargetCategoryUrlDataLatest(categoryNames: List<String>): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE category IN (:categoryNames) ORDER BY addDate ASC")
    fun getTargetCategoryUrlDataOldest(categoryNames: List<String>): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE siteName IN (:siteNames) ORDER BY addDate DESC")
    fun getUrlDataLatestBySites(siteNames: List<String>): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE siteName IN (:siteNames) ORDER BY addDate ASC")
    fun getUrlDataOldestBySites(siteNames: List<String>): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE isBookMark = 1 AND siteName IN (:siteNames) ORDER BY addDate DESC")
    fun getTargetBookMarkUrlDataLatestBySites(siteNames: List<String>): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE isBookMark = 1 AND siteName IN (:siteNames) ORDER BY addDate ASC")
    fun getTargetBookMarkUrlDataOldestBySites(siteNames: List<String>): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE category IN (:categories) AND siteName IN (:siteNames) ORDER BY addDate DESC")
    fun getTargetCategoryUrlDataLatestBySites(categories: List<String>, siteNames: List<String>): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE category IN (:categories) AND siteName IN (:siteNames) ORDER BY addDate ASC")
    fun getTargetCategoryUrlDataOldestBySites(categories: List<String>, siteNames: List<String>): PagingSource<Int, UrlData>





    @Query("SELECT * FROM basesaveurl ORDER BY addDate DESC")
    suspend fun get(): List<UrlData>

    @Query("SELECT * FROM basesaveurl WHERE url = :url")
    suspend fun get(url: String): UrlData?

    @Query("SELECT * FROM basesaveurl WHERE category = :categoryName ORDER BY addDate DESC")
    suspend fun getTargetCategory(categoryName: String): List<UrlData>

    @Query("SELECT * FROM basesaveurl WHERE isBookMark = 1 ORDER BY addDate DESC")
    suspend fun getTargetBookMark(): List<UrlData>

    @Query("SELECT COUNT(*) FROM basesaveurl WHERE url = :url")
    suspend fun exists(url: String): Int
    
    @Query("SELECT * FROM BaseSaveUrl \n" +
            "        WHERE title LIKE '%' || :keyword || '%' \n" +
            "        OR description LIKE '%' || :keyword || '%' \n" +
            "        OR tagList LIKE '%' || :keyword || '%'")
    fun searchAll(keyword: String): PagingSource<Int, UrlData>

    @Query("SELECT * FROM BaseSaveUrl WHERE title LIKE '%' || :keyword || '%'")
    fun searchByTitle(keyword: String): PagingSource<Int, UrlData>

    @Query("SELECT * FROM BaseSaveUrl WHERE description LIKE '%' || :keyword || '%'")
    fun searchByDescription(keyword: String): PagingSource<Int, UrlData>

    @Query("SELECT * FROM BaseSaveUrl WHERE tagList LIKE '%' || :keyword || '%'")
    fun searchByTag(keyword: String): PagingSource<Int, UrlData>

    @Query("SELECT tagList FROM BaseSaveUrl WHERE tagList IS NOT NULL")
    suspend fun getAllTagListJson(): List<String>

    @Query("SELECT DISTINCT siteName FROM BaseSaveUrl WHERE siteName IS NOT NULL AND TRIM(siteName) != ''")
    suspend fun getDistinctNonEmptySiteNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg entity: UrlData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: UrlData): Long

    @Delete
    suspend fun delete(data: UrlData): Int

    @Update
    suspend fun update(data: UrlData): Int

}