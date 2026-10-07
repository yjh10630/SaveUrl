package com.jinscompany.saveurl.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowLayoutTest {

    @Test
    fun `600dp 미만은 한 화면 구성`() {
        listOf(0, 360, 411, 599).forEach { width ->
            val layout = WindowLayout.fromWidthDp(width)
            assertFalse("width=$width", layout.isTwoPane)
            assertEquals("width=$width", Dp.Unspecified, layout.listPaneWidth)
            assertEquals(WindowLayout.SinglePane, layout)
        }
    }

    @Test
    fun `600dp 이상 840dp 미만은 2분할 + 왼쪽 360dp`() {
        listOf(600, 673, 839).forEach { width ->
            val layout = WindowLayout.fromWidthDp(width)
            assertTrue("width=$width", layout.isTwoPane)
            assertEquals("width=$width", 360.dp, layout.listPaneWidth)
        }
    }

    @Test
    fun `840dp 이상은 2분할 + 왼쪽 400dp`() {
        listOf(840, 1200, 1600).forEach { width ->
            val layout = WindowLayout.fromWidthDp(width)
            assertTrue("width=$width", layout.isTwoPane)
            assertEquals("width=$width", 400.dp, layout.listPaneWidth)
        }
    }

    @Test
    fun `WindowSizeClass 폭 하한값과 기준이 맞물린다`() {
        // windowSizeClass.minWidthDp 가 돌려주는 값(0/600/840/1200/1600) 기준
        assertFalse(WindowLayout.fromWidthDp(0).isTwoPane)
        assertEquals(360.dp, WindowLayout.fromWidthDp(600).listPaneWidth)
        assertEquals(400.dp, WindowLayout.fromWidthDp(840).listPaneWidth)
        assertEquals(400.dp, WindowLayout.fromWidthDp(1200).listPaneWidth)
    }
}
