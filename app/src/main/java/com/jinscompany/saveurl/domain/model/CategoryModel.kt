package com.jinscompany.saveurl.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * //todo 카테고리는 Json 형태로 String 을 저장하는 방식으로 처리 하자, 그게 더 순서를 바꾸거나 추가 및 삭제 할때 관리하기 편할 것 같음.
 * //todo 아래 CategoryModel 의 경우 필요 없는 필드는 삭제 하고 필요한 것만 추가 해서 UI Model 로 변경 해서 다시 만들자
 */
@Entity(tableName = "Category")
data class CategoryModel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "contentCnt") var contentCnt: Int = 0,
    @ColumnInfo(name = "addDate") val addDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "order") var order: Int = 0,
    @ColumnInfo(name = "isEditable") val isEditable: Boolean = true
)