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
    fun getUrlData(): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE category = :categoryName ORDER BY addDate DESC")
    fun getTargetCategoryUrlData(categoryName: String): PagingSource<Int, UrlData>

    @Query("SELECT * FROM basesaveurl WHERE isBookMark = 1 ORDER BY addDate DESC")
    fun getTargetBookMarkUrlData(): PagingSource<Int, UrlData>


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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg entity: UrlData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: UrlData): Long

    @Delete
    suspend fun delete(data: UrlData): Int

    @Update
    suspend fun update(data: UrlData): Int

}