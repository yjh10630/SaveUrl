package com.jinscompany.saveurl.data.room

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
    suspend fun get(): List<UrlData>

    @Query("SELECT * FROM basesaveurl WHERE url = :url")
    suspend fun get(url: String): UrlData?

    @Query("SELECT * FROM basesaveurl WHERE category = :categoryName ORDER BY addDate DESC")
    suspend fun getTargetCategory(categoryName: String): List<UrlData>

    @Query("SELECT * FROM basesaveurl WHERE isBookMark = 1 ORDER BY addDate DESC")
    suspend fun getTargetBookMark(): List<UrlData>

    @Query("SELECT COUNT(*) FROM basesaveurl WHERE url = :url")
    suspend fun exists(url: String): Int
    
    // 1. title, description, tagList 에 keyword가 포함된 경우
    @Query("SELECT * FROM BaseSaveUrl \n" +
            "        WHERE title LIKE '%' || :keyword || '%' \n" +
            "        OR description LIKE '%' || :keyword || '%' \n" +
            "        OR tagList LIKE '%' || :keyword || '%'")
    suspend fun searchAll(keyword: String): List<UrlData>

    // 2. title 만 keyword가 포함된 경우
    @Query("SELECT * FROM BaseSaveUrl WHERE title LIKE '%' || :keyword || '%'")
    suspend fun searchByTitle(keyword: String): List<UrlData>

    // 3. description 만 keyword가 포함된 경우
    @Query("SELECT * FROM BaseSaveUrl WHERE description LIKE '%' || :keyword || '%'")
    suspend fun searchByDescription(keyword: String): List<UrlData>

    // 4. tagList 안에 keyword가 포함된 경우
    @Query("SELECT * FROM BaseSaveUrl WHERE tagList LIKE '%' || :keyword || '%'")
    suspend fun searchByTag(keyword: String): List<UrlData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg entity: UrlData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: UrlData): Long

    @Delete
    suspend fun delete(data: UrlData): Int

    @Update
    suspend fun update(data: UrlData): Int

}