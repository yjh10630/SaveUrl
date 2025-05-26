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
    @Query("SELECT * FROM trash")
    fun getPagingItemAll(): PagingSource<Int, TrashItem>
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
}