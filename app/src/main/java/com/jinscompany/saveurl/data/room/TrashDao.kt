package com.jinscompany.saveurl.data.room

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jinscompany.saveurl.domain.model.TrashItem

@Dao
interface TrashDao {
    /** 휴지통 화면. 잠금 카테고리에서 온 항목은 [includeLocked] 일 때만 (LockSql.VISIBLE) */
    @Query("SELECT * FROM trash WHERE ${LockSql.VISIBLE}")
    fun getPagingItems(includeLocked: Boolean): PagingSource<Int, TrashItem>

    /** 전체 복원·전체 삭제 대상 = 지금 보이는 항목 */
    @Query("SELECT * FROM trash WHERE ${LockSql.VISIBLE}")
    suspend fun getVisible(includeLocked: Boolean): List<TrashItem>

    /** 잠금과 관계없이 전체 (기간 지난 항목 자동 삭제용) */
    @Query("SELECT * FROM trash")
    suspend fun getAll(): List<TrashItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: TrashItem): Long

    @Delete
    suspend fun delete(link: TrashItem): Int

    @Delete
    suspend fun deleteItems(items: List<TrashItem>): Int // 제거된 행의 갯수를 리턴

    @Query("DELETE FROM trash WHERE deleteDate < :expireTime")
    suspend fun deleteOldLinks(expireTime: Long)

    @Query("DELETE FROM trash")
    suspend fun deleteAll()

    @Query("UPDATE trash SET category = :newName WHERE category = :oldName")
    suspend fun renameCategory(oldName: String, newName: String): Int
}