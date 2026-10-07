package com.jinscompany.saveurl.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 창 폭에 따른 화면 구성.
 *
 * - 폭 600dp 미만: 한 화면 구성 (지금 폰 UI 그대로)
 * - 600dp 이상: 2분할. 왼쪽 목록 패널 360dp, 840dp 이상이면 400dp. 오른쪽은 나머지 폭.
 */
@Immutable
data class WindowLayout(
    val isTwoPane: Boolean,
    /** 2분할일 때 왼쪽 목록 패널 폭. 한 화면 구성이면 [Dp.Unspecified] */
    val listPaneWidth: Dp,
) {
    companion object {
        /** 2분할로 바뀌는 최소 창 폭 (Material 3 medium 폭 하한) */
        const val TWO_PANE_MIN_WIDTH_DP = 600

        /** 왼쪽 패널을 넓게 쓰는 최소 창 폭 (Material 3 expanded 폭 하한) */
        const val WIDE_LIST_PANE_MIN_WIDTH_DP = 840

        val LIST_PANE_WIDTH = 360.dp
        val WIDE_LIST_PANE_WIDTH = 400.dp

        val SinglePane = WindowLayout(isTwoPane = false, listPaneWidth = Dp.Unspecified)

        /** 창 폭(dp)으로 구성을 정한다. 순수 함수라 단위 테스트 대상. */
        fun fromWidthDp(widthDp: Int): WindowLayout = when {
            widthDp >= WIDE_LIST_PANE_MIN_WIDTH_DP -> WindowLayout(isTwoPane = true, listPaneWidth = WIDE_LIST_PANE_WIDTH)
            widthDp >= TWO_PANE_MIN_WIDTH_DP -> WindowLayout(isTwoPane = true, listPaneWidth = LIST_PANE_WIDTH)
            else -> SinglePane
        }
    }
}

/**
 * 현재 창의 [WindowLayout].
 * windowSizeClass.minWidthDp 는 창 폭이 만족하는 폭 구간의 하한(0/600/840…)이라 위 기준과 그대로 맞물린다.
 */
@Composable
fun currentWindowLayout(): WindowLayout =
    WindowLayout.fromWidthDp(currentWindowAdaptiveInfo().windowSizeClass.minWidthDp)

/**
 * MainActivity 에서 [currentWindowLayout] 값을 제공한다.
 * 기본값은 한 화면 구성이라, 제공되지 않는 곳(프리뷰·테스트)에서는 폰 UI 로 동작한다.
 */
val LocalWindowLayout = compositionLocalOf { WindowLayout.SinglePane }
