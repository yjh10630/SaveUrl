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
}