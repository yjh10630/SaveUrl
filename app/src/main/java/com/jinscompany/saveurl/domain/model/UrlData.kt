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
    @ColumnInfo(name = "isRead", defaultValue = "0") var isRead: Boolean = false,
    @ColumnInfo(name = "normalizedUrl", defaultValue = "") val normalizedUrl: String = "",
    /** 링크 가져오기로 들어온 묶음 ([ImportBatch.id]). null = 직접 저장 (Room v6) */
    @ColumnInfo(name = "importBatchId") val importBatchId: Long? = null,
    /** 제목·썸네일 채우기 상태 ([MetaState]). 직접 저장한 링크는 0 (Room v6) */
    @ColumnInfo(name = "metaState", defaultValue = "0") val metaState: Int = MetaState.DONE,
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

}

/** [UrlData.metaState] 값. 가져온 링크는 저장 뒤 LinkMetaFillWorker 가 제목·썸네일을 채운다 */
object MetaState {
    /** 다 채움 (직접 저장한 링크 포함) */
    const val DONE = 0
    /** 제목·썸네일 모두 기다리는 중 */
    const val PENDING = 1
    /** 제목은 있고(북마크·백업) 썸네일만 기다리는 중. 제목은 덮어쓰지 않는다 */
    const val PENDING_IMAGE = 2
    /** 가져오지 못함. 링크를 열면 다시 시도한다 */
    const val FAILED = 3

    fun isPending(state: Int): Boolean = state == PENDING || state == PENDING_IMAGE
}
