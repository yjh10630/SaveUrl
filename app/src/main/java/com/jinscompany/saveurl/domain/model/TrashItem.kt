package com.jinscompany.saveurl.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Trash")
data class TrashItem(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "imageUrl") val imgUrl: String,
    @ColumnInfo(name = "siteName") val siteName: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "tagList") val tagList: List<String>,
    @ColumnInfo(name = "addDate") val addDate: Long,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "isBookMark") val isBookMark: Boolean,
    @ColumnInfo(name = "deleteDate") val deleteDate: Long = System.currentTimeMillis(),
    /** 휴지통으로 갈 때의 읽음 값. 복원하면 그대로 돌려준다 (Room v8. 기존 행은 1 = 읽음) */
    @ColumnInfo(name = "isRead", defaultValue = "1") val isRead: Boolean = true,
)
