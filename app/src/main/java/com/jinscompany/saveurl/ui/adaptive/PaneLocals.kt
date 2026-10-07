package com.jinscompany.saveurl.ui.adaptive

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 2분할의 오른쪽 패널 안(NavHost)에 그려지는 화면이면 true.
 * 하위 화면 앱바가 ← 대신 ✕(패널 닫기)를 오른쪽에 두는 데 쓴다. 폰(한 화면)에서는 항상 false.
 */
val LocalInRightPane = staticCompositionLocalOf { false }
