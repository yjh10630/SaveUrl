package com.jinscompany.saveurl.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 링크별 "나중에 알림" 예약 (42번 §3.1, Room v8). 링크 하나에 예약 하나, 알림을 띄우면 행을 지운다 ("예정" = 행이 있음).
 *
 * 링크 행과 외래 키로 묶지 않는다: BaseSaveUrl 은 REPLACE 로 다시 넣는 경로가 있어 CASCADE 면 예약이 함께 지워질 수 있다.
 * 대신 링크를 지우는 경로(휴지통·영구 삭제)에서 예약을 지우고, 목록·알림은 링크와 이어 붙여 읽으며,
 * 앱 시작·재예약 때 링크가 없는 행을 정리한다 (휴지통에서 같은 id 로 복원돼도 알림이 되살아나지 않게).
 */
@Entity(
    tableName = "LinkReminder",
    indices = [Index(value = ["linkId"], unique = true), Index(value = ["remindAt"])],
)
data class LinkReminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "linkId") val linkId: Int,
    /** 절대 시각 epoch ms. 시간대가 바뀌어도 같은 순간에 울린다 (§2.4) */
    @ColumnInfo(name = "remindAt") val remindAt: Long,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    /** 만든 선택지 ([ReminderPreset] 키). 문구·나중 분석용 */
    @ColumnInfo(name = "preset") val preset: String,
)

/** 예약된 알림 + 링크 (목록·홈 패널·알림 내용). 잠금 여부는 그 순간 Category 테이블로 판단한다 */
data class UpcomingReminder(
    val linkId: Int,
    val remindAt: Long,
    val url: String?,
    val title: String?,
    val imageUrl: String?,
    val siteName: String?,
    val addDate: Long,
    val category: String?,
    val locked: Boolean,
)
