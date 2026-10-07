package com.jinscompany.saveurl.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "Category")
data class CategoryModel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "contentCnt") var contentCnt: Int = 0,
    @ColumnInfo(name = "addDate") val addDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "order") var order: Int = 0,
    @ColumnInfo(name = "isEditable") val isEditable: Boolean = true,
) {
    @Ignore
    var isSelected: Boolean = false

    companion object {
        /**
         * 카테고리 삭제 시 해당 링크가 옮겨지는 기본 카테고리.
         * isEditable=false 로 생성되어 카테고리 편집 화면에 나오지 않음(삭제/이름 변경 불가).
         * ("전체"/"북마크" 는 DB 행이 없는 가상 필터라 링크를 옮길 대상으로 쓸 수 없음)
         */
        const val UNCATEGORIZED = "미분류"
    }
}