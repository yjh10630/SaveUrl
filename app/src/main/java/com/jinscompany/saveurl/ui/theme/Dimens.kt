package com.jinscompany.saveurl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 간격·크기 토큰 (Stitch 디자인 기준) */
object AppDimens {
    /** 화면 좌우 여백 */
    val Gutter = 20.dp
    /** 리스트 아이템 사이 간격 */
    val ItemSpacing = 16.dp
    /** 섹션 사이 간격 */
    val SectionSpacing = 24.dp

    /** 기본 보기 썸네일 */
    val ThumbDefault = 64.dp
    /** 컴팩트 보기 썸네일 */
    val ThumbCompact = 40.dp

    val ThumbRadius = 12.dp
    val CardRadius = 16.dp
    val SheetTopRadius = 28.dp
}

object AppShapes {
    val Thumb = RoundedCornerShape(AppDimens.ThumbRadius)
    val Card = RoundedCornerShape(AppDimens.CardRadius)
    val SheetTop = RoundedCornerShape(topStart = AppDimens.SheetTopRadius, topEnd = AppDimens.SheetTopRadius)
}

internal val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(AppDimens.ThumbRadius),
    medium = RoundedCornerShape(AppDimens.CardRadius),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(AppDimens.SheetTopRadius),
)
