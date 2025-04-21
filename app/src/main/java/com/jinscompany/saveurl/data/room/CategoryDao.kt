package com.jinscompany.saveurl.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category ORDER BY `order` ASC")
    suspend fun getAll(): List<CategoryModel>

    @Query("SELECT * FROM category WHERE name = :name")
    suspend fun get(name: String): CategoryModel?

    @Query("SELECT MAX(`order`) FROM Category")
    suspend fun getMaxOrder(): Int?

    // deletedOrder 번호 이후의 데이터들을 순차적으로 가져옴
    @Query("SELECT * FROM Category WHERE `order` > :deletedOrder ORDER BY `order` ASC")
    suspend fun getCategoriesAfter(deletedOrder: Int): List<CategoryModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: CategoryModel): Long

    @Delete
    suspend fun delete(data: CategoryModel): Int

    @Update
    suspend fun update(data: CategoryModel): Int
}