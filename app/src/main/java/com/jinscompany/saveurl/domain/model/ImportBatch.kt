package com.jinscompany.saveurl.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 링크 가져오기 한 번 (= 되돌리기 단위). 링크는 [UrlData.importBatchId] 로 이 행을 가리킨다 (Room v6).
 *
 * 대화 본문·보낸 사람 이름·원본 파일은 저장하지 않는다. [label] 은 방 이름이나 파일 이름이다.
 */
@Entity(tableName = "ImportBatch")
data class ImportBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [ImportSourceKind] 이름 (kakao, bookmark, takeout, backup, text) */
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    /** 가져오기를 누를 때 고른 개수 */
    @ColumnInfo(name = "requestedCount") val requestedCount: Int,
    /** 실제로 넣은 개수 (저장 직전 중복으로 빠진 것 제외). 진행 중에는 묶음마다 늘어난다 */
    @ColumnInfo(name = "insertedCount") val insertedCount: Int = 0,
    /** 이 가져오기가 새로 만든 카테고리. 되돌릴 때 비어 있으면 지운다 */
    @ColumnInfo(name = "createdCategories") val createdCategories: List<String> = emptyList(),
    /** [ImportBatchState] */
    @ColumnInfo(name = "state") val state: Int = ImportBatchState.IN_PROGRESS,
    /** 되돌린 시각 (되돌린 행은 30일 뒤 기록에서 숨김) */
    @ColumnInfo(name = "undoneAt") val undoneAt: Long? = null,
)

object ImportBatchState {
    const val DONE = 0
    /** 넣는 중. 앱이 이 상태로 다시 시작되면 "일부만 가져옴" */
    const val IN_PROGRESS = 1
    const val UNDONE = 2
}

/** 가져오기 기록 한 줄 + 미리보기 채우기 진행 (잠금 카테고리 링크는 세지 않는다) */
data class ImportBatchSummary(
    val id: Long,
    val source: String,
    val label: String,
    val createdAt: Long,
    val insertedCount: Int,
    val createdCategories: List<String>,
    val state: Int,
    val undoneAt: Long?,
    /** 지금 남아 있는 링크 수 (되돌리기 확인 문구의 n) */
    val linkCount: Int,
    val pendingCount: Int,
    val categoryCount: Int,
)

/** 메인 상태 카드·기록 화면의 "미리보기 채우는 중 done / total" */
data class MetaFillProgress(val pending: Int, val total: Int) {
    val done: Int get() = total - pending
    val isActive: Boolean get() = pending > 0
}
