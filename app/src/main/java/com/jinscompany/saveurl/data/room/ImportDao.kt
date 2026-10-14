package com.jinscompany.saveurl.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jinscompany.saveurl.domain.model.ImportBatch
import com.jinscompany.saveurl.domain.model.ImportBatchSummary
import com.jinscompany.saveurl.domain.model.MetaFillProgress
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.flow.Flow

/** 미리보기 채우기 대상 한 줄 */
data class MetaFillRow(val id: Int, val url: String?, val title: String?, val metaState: Int)

/** 링크 가져오기 (묶음 저장·되돌리기·기록) 와 미리보기 채우기 */
@Dao
interface ImportDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: ImportBatch): Long

    @Update
    suspend fun updateBatch(batch: ImportBatch): Int

    @Query("SELECT * FROM ImportBatch WHERE id = :id")
    suspend fun getBatch(id: Long): ImportBatch?

    @Query("DELETE FROM ImportBatch WHERE id = :id")
    suspend fun deleteBatch(id: Long): Int

    /** 가져온 링크. 기존 insertAll(REPLACE) 과 달리 충돌하면 실패한다 (새 행만 넣으므로 충돌하지 않는다) */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLinks(links: List<UrlData>): List<Long>

    /** 중복 판단: 저장된 정규화 URL 전체 (시작할 때 한 번 읽어 HashSet 으로) */
    @Query("SELECT normalizedUrl FROM BaseSaveUrl WHERE normalizedUrl != ''")
    suspend fun getAllNormalizedUrls(): List<String>

    /** 정규화 값이 아직 없는 예전 행 (백필 전). 호출부가 직접 정규화한다 */
    @Query("SELECT url FROM BaseSaveUrl WHERE normalizedUrl = '' AND url IS NOT NULL")
    suspend fun getUrlsWithoutNormalized(): List<String>

    /** 저장 직전 다시 확인 (미리보기 중에 다른 경로로 같은 링크를 저장했을 수 있음). [normalized] 는 500개 이하 */
    @Query("SELECT normalizedUrl FROM BaseSaveUrl WHERE normalizedUrl IN (:normalized)")
    suspend fun findExistingNormalized(normalized: List<String>): List<String>

    @Query("DELETE FROM BaseSaveUrl WHERE importBatchId = :batchId")
    suspend fun deleteLinksOfBatch(batchId: Long): Int

    @Query("SELECT COUNT(*) FROM BaseSaveUrl WHERE category = :name")
    suspend fun countLinksInCategory(name: String): Int

    @Query("SELECT COUNT(*) FROM Trash WHERE category = :name")
    suspend fun countTrashInCategory(name: String): Int

    /** 가져오기 기록 (최신순). 되돌린 묶음은 [hideUndoneBefore] 이전에 되돌린 것을 뺀다 */
    @Query("""
        SELECT b.id, b.source, b.label, b.createdAt, b.insertedCount, b.createdCategories, b.state, b.undoneAt,
            (SELECT COUNT(*) FROM BaseSaveUrl u WHERE u.importBatchId = b.id) AS linkCount,
            (SELECT COUNT(*) FROM BaseSaveUrl u WHERE u.importBatchId = b.id AND u.metaState IN (1, 2)) AS pendingCount,
            (SELECT COUNT(DISTINCT IFNULL(u.category, '')) FROM BaseSaveUrl u WHERE u.importBatchId = b.id) AS categoryCount
        FROM ImportBatch b
        WHERE b.undoneAt IS NULL OR b.undoneAt >= :hideUndoneBefore
        ORDER BY b.createdAt DESC
    """)
    fun observeHistory(hideUndoneBefore: Long): Flow<List<ImportBatchSummary>>

    @Query("SELECT COUNT(*) FROM ImportBatch")
    fun observeBatchCount(): Flow<Int>

    /**
     * 메인 상태 카드: 아직 채울 링크가 남은 묶음들의 진행. 잠금 카테고리 링크는 세지 않는다 (홈 통계와 같은 규칙)
     */
    @Query("""
        SELECT IFNULL(SUM(CASE WHEN metaState IN (1, 2) THEN 1 ELSE 0 END), 0) AS pending, COUNT(*) AS total
        FROM BaseSaveUrl
        WHERE importBatchId IN (SELECT DISTINCT importBatchId FROM BaseSaveUrl WHERE metaState IN (1, 2) AND importBatchId IS NOT NULL)
            AND ${LockSql.NOT_LOCKED}
    """)
    fun observeFillProgress(): Flow<MetaFillProgress>

    // ---- 미리보기 채우기 (LinkMetaFillWorker) ----

    /** 채울 링크 (최신 날짜부터) */
    @Query("SELECT id, url, title, metaState FROM BaseSaveUrl WHERE metaState IN (1, 2) ORDER BY addDate DESC LIMIT :limit")
    suspend fun getPendingMeta(limit: Int): List<MetaFillRow>

    @Query("SELECT id, url, title, metaState FROM BaseSaveUrl WHERE id = :id")
    suspend fun getMetaRow(id: Int): MetaFillRow?

    @Query("SELECT COUNT(*) FROM BaseSaveUrl WHERE metaState IN (1, 2)")
    suspend fun countPendingMeta(): Int

    /**
     * 채운 결과 반영. 그사이 사용자가 고쳤으면(상태가 바뀜) 덮어쓰지 않도록 [expectedState] 일 때만.
     * 빈 값은 기존 값을 지우지 않는다.
     */
    @Query("""
        UPDATE BaseSaveUrl SET
            title = CASE WHEN :title IS NULL OR :title = '' THEN title ELSE :title END,
            imageUrl = CASE WHEN :imageUrl IS NULL OR :imageUrl = '' THEN imageUrl ELSE :imageUrl END,
            description = CASE WHEN description IS NULL OR description = '' OR description = url THEN IFNULL(:description, description) ELSE description END,
            siteName = CASE WHEN siteName IS NULL OR siteName = '' THEN IFNULL(:siteName, siteName) ELSE siteName END,
            metaState = :newState
        WHERE id = :id AND metaState = :expectedState
    """)
    suspend fun applyMeta(
        id: Int,
        expectedState: Int,
        newState: Int,
        title: String?,
        imageUrl: String?,
        description: String?,
        siteName: String?,
    ): Int
}
