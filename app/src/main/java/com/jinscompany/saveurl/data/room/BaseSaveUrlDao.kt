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

    // ---- 메인 목록. 잠금 카테고리 숨김은 LockSql.VISIBLE (includeLocked 는 유스케이스가 세션으로 정함) ----
    @Query("SELECT * FROM basesaveurl WHERE ${LockSql.VISIBLE} ORDER BY addDate DESC")
    fun getUrlDataLatest(includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE ${LockSql.VISIBLE} ORDER BY addDate ASC")
    fun getUrlDataOldest(includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE isBookMark = 1 AND ${LockSql.VISIBLE} ORDER BY addDate DESC")
    fun getTargetBookMarkUrlDataLatest(includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE isBookMark = 1 AND ${LockSql.VISIBLE} ORDER BY addDate ASC")
    fun getTargetBookMarkUrlDataOldest(includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE category IN (:categoryNames) AND ${LockSql.VISIBLE} ORDER BY addDate DESC")
    fun getTargetCategoryUrlDataLatest(categoryNames: List<String>, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE category IN (:categoryNames) AND ${LockSql.VISIBLE} ORDER BY addDate ASC")
    fun getTargetCategoryUrlDataOldest(categoryNames: List<String>, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE siteName IN (:siteNames) AND ${LockSql.VISIBLE} ORDER BY addDate DESC")
    fun getUrlDataLatestBySites(siteNames: List<String>, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE siteName IN (:siteNames) AND ${LockSql.VISIBLE} ORDER BY addDate ASC")
    fun getUrlDataOldestBySites(siteNames: List<String>, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE isBookMark = 1 AND siteName IN (:siteNames) AND ${LockSql.VISIBLE} ORDER BY addDate DESC")
    fun getTargetBookMarkUrlDataLatestBySites(siteNames: List<String>, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE isBookMark = 1 AND siteName IN (:siteNames) AND ${LockSql.VISIBLE} ORDER BY addDate ASC")
    fun getTargetBookMarkUrlDataOldestBySites(siteNames: List<String>, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE category IN (:categories) AND siteName IN (:siteNames) AND ${LockSql.VISIBLE} ORDER BY addDate DESC")
    fun getTargetCategoryUrlDataLatestBySites(categories: List<String>, siteNames: List<String>, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE category IN (:categories) AND siteName IN (:siteNames) AND ${LockSql.VISIBLE} ORDER BY addDate ASC")
    fun getTargetCategoryUrlDataOldestBySites(categories: List<String>, siteNames: List<String>, includeLocked: Boolean): PagingSource<Int, UrlData>

    /**
     * 안 읽음 탭 (42번 §1.5). 안 읽은 링크 + 이번 탭 진입 뒤 연 링크([keepIds], 연 링크가 눈앞에서 바로 사라지지 않게).
     * 카테고리("전체"/"북마크"/이름)·사이트·정렬은 다른 탭과 같은 규칙이고, 태그는 지금처럼 ViewModel 이 메모리에서 거른다.
     */
    @Query("SELECT * FROM basesaveurl WHERE (isRead = 0 OR id IN (:keepIds)) " +
            "AND (:allCategories = 1 OR (:onlyBookmark = 1 AND isBookMark = 1) OR category IN (:categories)) " +
            "AND (:allSites = 1 OR siteName IN (:siteNames)) AND ${LockSql.VISIBLE} " +
            "ORDER BY CASE WHEN :oldest = 1 THEN addDate END ASC, addDate DESC")
    fun getUnread(
        keepIds: List<Int>,
        allCategories: Boolean,
        onlyBookmark: Boolean,
        categories: List<String>,
        allSites: Boolean,
        siteNames: List<String>,
        oldest: Boolean,
        includeLocked: Boolean,
    ): PagingSource<Int, UrlData>

    /** 안 읽음 탭 숫자. "전체"와 같이 잠금 카테고리는 세션과 관계없이 세지 않는다 */
    @Query("SELECT COUNT(*) FROM BaseSaveUrl WHERE isRead = 0 AND ${LockSql.NOT_LOCKED}")
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE BaseSaveUrl SET isRead = :read WHERE id = :id")
    suspend fun setRead(id: Int, read: Boolean): Int

    @Query("SELECT * FROM BaseSaveUrl WHERE id = :id")
    suspend fun getById(id: Int): UrlData?

    /** 링크가 잠금 카테고리에 있는지 (그 순간의 Category 기준) */
    @Query("SELECT COUNT(*) FROM BaseSaveUrl WHERE id = :id AND NOT ${LockSql.NOT_LOCKED}")
    suspend fun isLocked(id: Int): Int

    // ---- 다시 볼 링크 카드 (42번 §5.2) ----
    /**
     * 후보: 안 읽음 + [maxAddDate] 이전 저장 + 잠금 아님(세션과 무관) + 예약 없음 + [shownBefore] 이후로 카드에 안 보임. 오래된 순 [limit] 개
     */
    @Query("SELECT * FROM BaseSaveUrl WHERE isRead = 0 AND addDate <= :maxAddDate AND ${LockSql.NOT_LOCKED} " +
            "AND url IS NOT NULL AND url != '' AND id NOT IN (SELECT linkId FROM LinkReminder) " +
            "AND (revisitShownAt IS NULL OR revisitShownAt < :shownBefore) ORDER BY addDate ASC LIMIT :limit")
    suspend fun getRevisitCandidates(maxAddDate: Long, shownBefore: Long, limit: Int): List<UrlData>

    @Query("UPDATE BaseSaveUrl SET revisitShownAt = :at WHERE id = :id")
    suspend fun setRevisitShownAt(id: Int, at: Long)

    /** 잠금 카테고리가 아닌 링크 수 (카드 조건 "링크 10개 이상") */
    @Query("SELECT COUNT(*) FROM BaseSaveUrl WHERE ${LockSql.NOT_LOCKED}")
    suspend fun countNotLocked(): Int

    // ---- 주간 정리 (42번 §4). 가져온 링크와 잠금 카테고리는 세지 않는다 ----
    @Query("SELECT COUNT(*) FROM BaseSaveUrl WHERE importBatchId IS NULL AND addDate >= :since AND ${LockSql.NOT_LOCKED}")
    suspend fun countSavedSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM BaseSaveUrl WHERE importBatchId IS NULL AND isRead = 0 AND ${LockSql.NOT_LOCKED}")
    suspend fun countUnreadDirect(): Int

    @Query("SELECT COUNT(*) FROM BaseSaveUrl WHERE importBatchId IS NULL AND ${LockSql.NOT_LOCKED}")
    suspend fun countDirect(): Int

    /** 잠금과 관계없이 전체 (테스트·내부용). 화면·내보내기는 [getAll] 을 쓴다 */
    @Query("SELECT * FROM basesaveurl ORDER BY addDate DESC")
    suspend fun get(): List<UrlData>

    /** CSV 내보내기 */
    @Query("SELECT * FROM basesaveurl WHERE ${LockSql.VISIBLE} ORDER BY addDate DESC")
    suspend fun getAll(includeLocked: Boolean): List<UrlData>

    @Query("SELECT * FROM basesaveurl WHERE url = :url")
    suspend fun get(url: String): UrlData?

    @Query("SELECT COUNT(*) FROM basesaveurl WHERE url = :url")
    suspend fun exists(url: String): Int

    @Query("SELECT * FROM basesaveurl WHERE normalizedUrl = :normalizedUrl AND normalizedUrl != '' LIMIT 1")
    suspend fun findByNormalizedUrl(normalizedUrl: String): UrlData?
    
    // ---- 검색 (잠금 카테고리 숨김은 LockSql.VISIBLE) ----
    @Query("SELECT * FROM BaseSaveUrl WHERE (title LIKE '%' || :keyword || '%' " +
            "OR description LIKE '%' || :keyword || '%' " +
            "OR tagList LIKE '%' || :keyword || '%') AND ${LockSql.VISIBLE}")
    fun searchAll(keyword: String, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM BaseSaveUrl WHERE title LIKE '%' || :keyword || '%' AND ${LockSql.VISIBLE}")
    fun searchByTitle(keyword: String, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM BaseSaveUrl WHERE description LIKE '%' || :keyword || '%' AND ${LockSql.VISIBLE}")
    fun searchByDescription(keyword: String, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM BaseSaveUrl WHERE tagList LIKE '%' || :keyword || '%' AND ${LockSql.VISIBLE}")
    fun searchByTag(keyword: String, includeLocked: Boolean): PagingSource<Int, UrlData>

    // ---- 정렬 지정 검색 (2분할 검색 화면의 필터 패널에서만 사용. 폰 검색은 위 쿼리 그대로) ----
    // oldest = 1 이면 addDate 오름차순, 0 이면 첫 정렬 키가 모두 NULL 이라 두 번째 키(addDate 내림차순)로 정렬된다.
    @Query("SELECT * FROM BaseSaveUrl WHERE (title LIKE '%' || :keyword || '%' OR description LIKE '%' || :keyword || '%' OR tagList LIKE '%' || :keyword || '%') " +
            "AND ${LockSql.VISIBLE} ORDER BY CASE WHEN :oldest = 1 THEN addDate END ASC, addDate DESC")
    fun searchAllSorted(keyword: String, oldest: Boolean, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM BaseSaveUrl WHERE title LIKE '%' || :keyword || '%' AND ${LockSql.VISIBLE} ORDER BY CASE WHEN :oldest = 1 THEN addDate END ASC, addDate DESC")
    fun searchByTitleSorted(keyword: String, oldest: Boolean, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM BaseSaveUrl WHERE description LIKE '%' || :keyword || '%' AND ${LockSql.VISIBLE} ORDER BY CASE WHEN :oldest = 1 THEN addDate END ASC, addDate DESC")
    fun searchByDescriptionSorted(keyword: String, oldest: Boolean, includeLocked: Boolean): PagingSource<Int, UrlData>

    @Query("SELECT * FROM BaseSaveUrl WHERE tagList LIKE '%' || :keyword || '%' AND ${LockSql.VISIBLE} ORDER BY CASE WHEN :oldest = 1 THEN addDate END ASC, addDate DESC")
    fun searchByTagSorted(keyword: String, oldest: Boolean, includeLocked: Boolean): PagingSource<Int, UrlData>

    // ---- 홈 패널 요약 (2분할). 테이블을 한 번만 훑어 세 숫자를 함께 집계하고, 링크가 바뀌면 다시 내보낸다 ----
    // 잠금 카테고리는 세션과 관계없이 항상 세지 않는다 ("전체" 에 섞지 않는 규칙과 같음)
    @Query("SELECT COUNT(*) AS total, IFNULL(SUM(isBookMark), 0) AS bookmarks, " +
            "IFNULL(SUM(CASE WHEN addDate >= :since THEN 1 ELSE 0 END), 0) AS thisWeek FROM BaseSaveUrl WHERE ${LockSql.NOT_LOCKED}")
    fun observeLinkCounts(since: Long): Flow<LinkCounts>

    /** 링크 수가 많은 카테고리 순 (미분류 = null/빈 값/[excluded] 제외) */
    @Query("SELECT category AS name, COUNT(*) AS count FROM BaseSaveUrl " +
            "WHERE category IS NOT NULL AND TRIM(category) != '' AND category != :excluded AND ${LockSql.NOT_LOCKED} " +
            "GROUP BY category ORDER BY count DESC, name ASC LIMIT :limit")
    fun observeTopCategoryCounts(excluded: String, limit: Int): Flow<List<CategoryCount>>

    @Query("SELECT tagList FROM BaseSaveUrl WHERE tagList IS NOT NULL AND ${LockSql.VISIBLE}")
    suspend fun getAllTagListJson(includeLocked: Boolean): List<String>

    @Query("SELECT DISTINCT siteName FROM BaseSaveUrl WHERE siteName IS NOT NULL AND TRIM(siteName) != '' AND ${LockSql.VISIBLE}")
    suspend fun getDistinctNonEmptySiteNames(includeLocked: Boolean): List<String>

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