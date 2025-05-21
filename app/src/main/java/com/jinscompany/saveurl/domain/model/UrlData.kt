package com.jinscompany.saveurl.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Entity(tableName = "BaseSaveUrl")
data class UrlData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "url") val url: String? = null,
    @ColumnInfo(name = "imageUrl") val imgUrl: String? = null,
    @ColumnInfo(name = "siteName") val siteName: String? = null,
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "tagList") var tagList: List<String>? = null,
    @ColumnInfo(name = "addDate") val addDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "category") var category: String? = null,
    @ColumnInfo(name = "isBookMark") var isBookMark: Boolean = false,
) {
    fun getDate(): String {
        val currentTime = Calendar.getInstance()
        val inputTime = Calendar.getInstance().apply { timeInMillis = addDate }

        val yearFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault())
        val monthDayFormat = SimpleDateFormat("MM월 dd일", Locale.getDefault())

        return when {
            // 오늘
            currentTime.get(Calendar.YEAR) == inputTime.get(Calendar.YEAR) &&
                    currentTime.get(Calendar.DAY_OF_YEAR) == inputTime.get(Calendar.DAY_OF_YEAR) -> "오늘"

            // 어제
            currentTime.get(Calendar.YEAR) == inputTime.get(Calendar.YEAR) &&
                    currentTime.get(Calendar.DAY_OF_YEAR) - 1 == inputTime.get(Calendar.DAY_OF_YEAR) -> "어제"

            // 같은 해
            currentTime.get(Calendar.YEAR) == inputTime.get(Calendar.YEAR) -> monthDayFormat.format(inputTime.time)

            // 연도가 다를 때
            else -> yearFormat.format(inputTime.time)
        }
    }

    fun mapperUrlDataToTrashItem(): TrashItem = TrashItem(
        id = id,
        url = url ?: "",
        imgUrl = imgUrl ?: "",
        siteName = siteName ?: "",
        title = title ?: "",
        description = description ?: "",
        tagList = tagList ?: emptyList(),
        addDate = addDate,
        category = category ?: "",
        isBookMark = isBookMark,
    )
}