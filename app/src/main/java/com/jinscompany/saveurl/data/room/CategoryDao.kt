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

    /**
     * 카테고리 목록 + 실제 링크 수.
     * 저장된 contentCnt 컬럼은 수정 모드 카테고리 변경, 휴지통 전체 복원, CSV 가져오기 등에서 증감되지 않아
     * 실제와 어긋나므로 표시용 개수는 BaseSaveUrl 에서 매번 집계한다 (컬럼은 스키마 유지를 위해 남겨둠).
     */
    @Query("""
        SELECT c.id, c.name, COUNT(b.id) AS contentCnt, c.addDate, c.`order`, c.isEditable
        FROM Category c
        LEFT JOIN BaseSaveUrl b ON b.category = c.name
        GROUP BY c.id
        ORDER BY c.`order` ASC
    """)
    suspend fun getAllWithLinkCount(): List<CategoryModel>

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