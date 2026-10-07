package com.jinscompany.saveurl.data.room

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.jinscompany.saveurl.data.backfill.NormalizedUrlRow
import com.jinscompany.saveurl.domain.model.CategoryCount
import com.jinscompany.saveurl.domain.model.LinkCounts
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

    @Query("SELECT * FROM basesaveurl WHERE normalizedUrl = :normalizedUrl AND normalizedUrl != '' LIMIT 1")
    suspend fun findByNormalizedUrl(normalizedUrl: String): UrlData?
    
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

    // ---- 정렬 지정 검색 (2분할 검색 화면의 필터 패널에서만 사용. 폰 검색은 위 쿼리 그대로) ----
    // oldest = 1 이면 addDate 오름차순, 0 이면 첫 정렬 키가 모두 NULL 이라 두 번째 키(addDate 내림차순)로 정렬된다.
    @Query("SELECT * FROM BaseSaveUrl WHERE (title LIKE '%' || :keyword || '%' OR description LIKE '%' || :keyword || '%' OR tagList LIKE '%' || :keyword || '%') " +
            "ORDER BY CASE WHEN :oldest = 1 THEN addDate END ASC, addDate DESC")
    fun searchAllSorted(keyword: String, oldest: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM BaseSaveUrl WHERE title LIKE '%' || :keyword || '%' ORDER BY CASE WHEN :oldest = 1 THEN addDate END ASC, addDate DESC")
    fun searchByTitleSorted(keyword: String, oldest: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM BaseSaveUrl WHERE description LIKE '%' || :keyword || '%' ORDER BY CASE WHEN :oldest = 1 THEN addDate END ASC, addDate DESC")
    fun searchByDescriptionSorted(keyword: String, oldest: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM BaseSaveUrl WHERE tagList LIKE '%' || :keyword || '%' ORDER BY CASE WHEN :oldest = 1 THEN addDate END ASC, addDate DESC")
    fun searchByTagSorted(keyword: String, oldest: Boolean): PagingSource<Int, UrlData>

    // ---- 홈 패널 요약 (2분할). 테이블을 한 번만 훑어 세 숫자를 함께 집계하고, 링크가 바뀌면 다시 내보낸다 ----
    @Query("SELECT COUNT(*) AS total, IFNULL(SUM(isBookMark), 0) AS bookmarks, " +
            "IFNULL(SUM(CASE WHEN addDate >= :since THEN 1 ELSE 0 END), 0) AS thisWeek FROM BaseSaveUrl")
    fun observeLinkCounts(since: Long): Flow<LinkCounts>

    /** 링크 수가 많은 카테고리 순 (미분류 = null/빈 값/[excluded] 제외) */
    @Query("SELECT category AS name, COUNT(*) AS count FROM BaseSaveUrl " +
            "WHERE category IS NOT NULL AND TRIM(category) != '' AND category != :excluded " +
            "GROUP BY category ORDER BY count DESC, name ASC LIMIT :limit")
    fun observeTopCategoryCounts(excluded: String, limit: Int): Flow<List<CategoryCount>>

    @Query("SELECT tagList FROM BaseSaveUrl WHERE tagList IS NOT NULL")
    suspend fun getAllTagListJson(): List<String>

    @Query("SELECT DISTINCT siteName FROM BaseSaveUrl WHERE siteName IS NOT NULL AND TRIM(siteName) != ''")
    suspend fun getDistinctNonEmptySiteNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg entity: UrlData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: UrlData): Long

    // 카테고리 이름 변경 시 해당 카테고리의 링크도 함께 변경
    @Query("UPDATE BaseSaveUrl SET category = :newName WHERE category = :oldName")
    suspend fun renameCategory(oldName: String, newName: String): Int

    // ---- normalizedUrl 백필 ----
    @Query("SELECT id, url, normalizedUrl FROM BaseSaveUrl WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun getNormalizedUrlRowsAfter(afterId: Int, limit: Int): List<NormalizedUrlRow>

    @Query("SELECT id, url, normalizedUrl FROM BaseSaveUrl WHERE normalizedUrl = '' AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun getEmptyNormalizedUrlRowsAfter(afterId: Int, limit: Int): List<NormalizedUrlRow>

    // 백필 도중 사용자가 같은 행의 url 을 수정했으면 덮어쓰지 않도록 url 도 조건에 포함
    @Query("UPDATE BaseSaveUrl SET normalizedUrl = :normalizedUrl WHERE id = :id AND url IS :url")
    suspend fun updateNormalizedUrl(id: Int, url: String?, normalizedUrl: String): Int

    @Query("UPDATE BaseSaveUrl SET isRead = 1 WHERE url = :url")
    suspend fun markAsRead(url: String)

    @Delete
    suspend fun delete(data: UrlData): Int

    @Update
    suspend fun update(data: UrlData): Int

}