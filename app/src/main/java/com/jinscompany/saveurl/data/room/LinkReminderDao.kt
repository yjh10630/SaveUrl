package com.jinscompany.saveurl.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jinscompany.saveurl.domain.model.LinkReminder
import com.jinscompany.saveurl.domain.model.UpcomingReminder
import kotlinx.coroutines.flow.Flow

/** 링크 알림 예약 (42번 §3). 링크 행이 없는 예약은 이어 붙이는 쿼리에서 자연히 빠진다 */
@Dao
interface LinkReminderDao {

    /** 링크 하나에 예약 하나: 같은 linkId 면 덮어쓴다 (unique 인덱스) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: LinkReminder): Long

    @Query("SELECT * FROM LinkReminder WHERE linkId = :linkId")
    suspend fun get(linkId: Int): LinkReminder?

    @Query("SELECT * FROM LinkReminder")
    suspend fun getAll(): List<LinkReminder>

    @Query("DELETE FROM LinkReminder WHERE linkId = :linkId")
    suspend fun delete(linkId: Int): Int

    @Query("DELETE FROM LinkReminder WHERE linkId IN (:linkIds)")
    suspend fun deleteAll(linkIds: List<Int>): Int

    /** 링크가 지워진 예약 정리. 지운 linkId 를 돌려주려고 먼저 읽는다 */
    @Query("SELECT linkId FROM LinkReminder WHERE linkId NOT IN (SELECT id FROM BaseSaveUrl)")
    suspend fun getOrphanLinkIds(): List<Int>

    /** 목록 ⏰ 표시: linkId → remindAt */
    @Query("SELECT r.* FROM LinkReminder r INNER JOIN BaseSaveUrl b ON b.id = r.linkId")
    fun observeAll(): Flow<List<LinkReminder>>

    /** 링크 알림 목록·홈 "곧 올 알림" (시간 순). 잠금 여부는 지금 Category 로 계산한다 */
    @Query(
        "SELECT r.linkId AS linkId, r.remindAt AS remindAt, b.url AS url, b.title AS title, b.imageUrl AS imageUrl, " +
            "b.siteName AS siteName, b.addDate AS addDate, b.category AS category, " +
            "(b.category IS NOT NULL AND b.category IN (SELECT name FROM Category WHERE isLocked = 1)) AS locked " +
            "FROM LinkReminder r INNER JOIN BaseSaveUrl b ON b.id = r.linkId ORDER BY r.remindAt ASC"
    )
    fun observeUpcoming(): Flow<List<UpcomingReminder>>

    /** 알림을 띄울 때 (receiver) */
    @Query(
        "SELECT r.linkId AS linkId, r.remindAt AS remindAt, b.url AS url, b.title AS title, b.imageUrl AS imageUrl, " +
            "b.siteName AS siteName, b.addDate AS addDate, b.category AS category, " +
            "(b.category IS NOT NULL AND b.category IN (SELECT name FROM Category WHERE isLocked = 1)) AS locked " +
            "FROM LinkReminder r INNER JOIN BaseSaveUrl b ON b.id = r.linkId WHERE r.linkId = :linkId"
    )
    suspend fun getUpcoming(linkId: Int): UpcomingReminder?
}
