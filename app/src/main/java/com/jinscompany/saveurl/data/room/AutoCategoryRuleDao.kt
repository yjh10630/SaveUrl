package com.jinscompany.saveurl.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jinscompany.saveurl.domain.model.AutoCatEvidence
import com.jinscompany.saveurl.domain.model.AutoCatLinkRow
import com.jinscompany.saveurl.domain.model.AutoCategoryRule
import com.jinscompany.saveurl.domain.model.AutoRuleWithCategory
import kotlinx.coroutines.flow.Flow

/** 자동 분류 규칙 (36번) 과 제안·분포·기존 링크 적용에 쓰는 링크 조회 */
@Dao
interface AutoCategoryRuleDao {

    /** 규칙 + 카테고리 (거절 기록 포함). 카테고리 순서 → 사이트 순 */
    @Query("""
        SELECT r.*, c.name AS categoryName, c.isLocked AS categoryLocked, c.`order` AS categoryOrder
        FROM AutoCategoryRule r JOIN Category c ON c.id = r.categoryId
        ORDER BY c.`order` ASC, r.pattern ASC
    """)
    fun observeAllWithCategory(): Flow<List<AutoRuleWithCategory>>

    @Query("""
        SELECT r.*, c.name AS categoryName, c.isLocked AS categoryLocked, c.`order` AS categoryOrder
        FROM AutoCategoryRule r JOIN Category c ON c.id = r.categoryId
        ORDER BY c.`order` ASC, r.pattern ASC
    """)
    suspend fun getAllWithCategory(): List<AutoRuleWithCategory>

    /** 저장 화면 판정용: 켜진 규칙 중 잠금 카테고리가 아닌 것 (잠금 카테고리 이름이 저장 화면에 보이면 안 됨) */
    @Query("""
        SELECT r.*, c.name AS categoryName, c.isLocked AS categoryLocked, c.`order` AS categoryOrder
        FROM AutoCategoryRule r JOIN Category c ON c.id = r.categoryId
        WHERE r.state = 'ACTIVE' AND c.isLocked = 0
    """)
    suspend fun getApplicable(): List<AutoRuleWithCategory>

    @Query("""
        SELECT r.*, c.name AS categoryName, c.isLocked AS categoryLocked, c.`order` AS categoryOrder
        FROM AutoCategoryRule r JOIN Category c ON c.id = r.categoryId
        WHERE r.id = :id
    """)
    suspend fun getWithCategory(id: Long): AutoRuleWithCategory?

    @Query("SELECT * FROM AutoCategoryRule")
    suspend fun getAll(): List<AutoCategoryRule>

    @Query("SELECT * FROM AutoCategoryRule WHERE id = :id")
    suspend fun get(id: Long): AutoCategoryRule?

    @Query("SELECT * FROM AutoCategoryRule WHERE matchType = :matchType AND pattern = :pattern")
    suspend fun getByKey(matchType: String, pattern: String): AutoCategoryRule?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: AutoCategoryRule): Long

    @Update
    suspend fun update(rule: AutoCategoryRule): Int

    @Query("DELETE FROM AutoCategoryRule WHERE id = :id")
    suspend fun delete(id: Long): Int

    /** 카테고리를 지울 때 그 카테고리를 쓰는 규칙(거절 기록 포함)도 지운다 */
    @Query("DELETE FROM AutoCategoryRule WHERE categoryId = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Int): Int

    /** 카테고리 삭제 확인 문구의 "규칙 n개" (거절 기록은 세지 않음) */
    @Query("SELECT COUNT(*) FROM AutoCategoryRule WHERE categoryId = :categoryId AND state != 'DISMISSED'")
    suspend fun countRulesForCategory(categoryId: Int): Int

    // ---- 링크 조회 (BaseSaveUrl) ----

    /**
     * 제안 근거: 분류된 링크 전부. 가져오기로 들어온 링크(백업 복원 제외)와 잠금 카테고리 여부를 같이 읽는다.
     * 키 추출은 Kotlin 에서 한다 (RuleSuggester).
     */
    @Query("""
        SELECT u.url AS url, u.category AS category,
            (u.importBatchId IS NOT NULL AND IFNULL(b.source, '') != 'backup') AS imported,
            (u.category IN (SELECT name FROM Category WHERE isLocked = 1)) AS locked
        FROM BaseSaveUrl u LEFT JOIN ImportBatch b ON b.id = u.importBatchId
        WHERE u.category IS NOT NULL AND u.category NOT IN ('전체', '', '미분류')
    """)
    suspend fun getEvidence(): List<AutoCatEvidence>

    /** 규칙 시트 분포·기존 링크 적용 후보: 잠금 카테고리가 아닌 링크 전부 (키 매칭은 Kotlin 에서) */
    @Query("""
        SELECT id, url, title, imageUrl AS imgUrl, category FROM BaseSaveUrl
        WHERE ${LockSql.NOT_LOCKED}
        ORDER BY addDate DESC
    """)
    suspend fun getVisibleLinkRows(): List<AutoCatLinkRow>

    /**
     * 기존 링크에 적용: 미리 본 뒤 그사이 분류된 링크는 건드리지 않는다. [ids] 는 500개 이하.
     * @return 바뀐 행 수
     */
    @Query("""
        UPDATE BaseSaveUrl SET category = :category
        WHERE id IN (:ids) AND (category IS NULL OR category IN ('전체', '', '미분류'))
    """)
    suspend fun moveUnfiled(ids: List<Int>, category: String): Int

    /** 적용 되돌리기: 그사이 다른 카테고리로 옮긴 링크는 그대로 둔다. [ids] 는 500개 이하 */
    @Query("UPDATE BaseSaveUrl SET category = :previous WHERE id IN (:ids) AND category = :movedTo")
    suspend fun restoreCategory(ids: List<Int>, previous: String?, movedTo: String): Int

    /** [ids] 중 아직 분류 안 된 링크와 지금 값 (되돌리기용). [ids] 는 500개 이하 */
    @Query("""
        SELECT id, category FROM BaseSaveUrl
        WHERE id IN (:ids) AND (category IS NULL OR category IN ('전체', '', '미분류'))
    """)
    suspend fun getUnfiled(ids: List<Int>): List<LinkCategory>
}

/** 링크 id 와 그때의 카테고리 값 */
data class LinkCategory(val id: Int, val category: String?)
