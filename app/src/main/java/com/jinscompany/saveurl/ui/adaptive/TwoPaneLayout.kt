package com.jinscompany.saveurl.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.main.MainListPane
import com.jinscompany.saveurl.ui.main.MainListScreen
import com.jinscompany.saveurl.ui.navigation.Navigation
import com.jinscompany.saveurl.ui.navigation.baseRoute
import com.jinscompany.saveurl.ui.search.SearchListPane
import com.jinscompany.saveurl.ui.setting.AppSettingScreen
import com.jinscompany.saveurl.ui.theme.AppTheme

/** 왼쪽 목록 패널을 갖는 "구역"의 시작 화면. 백스택에서 가장 위에 있는 이 화면이 왼쪽 패널 내용을 정한다. */
private val SECTION_ROOT_ROUTES = setOf(Navigation.Routes.MAIN, Navigation.Routes.SEARCH, Navigation.Routes.APP_SETTING)

/**
 * 2분할 레이아웃 (폭 600dp 이상).
 *
 * ```
 * ┌ 왼쪽 목록 (360dp / 840dp 이상 400dp) ┬ 오른쪽 패널 (나머지, surface 톤) ┐
 * │ 구역 시작 화면의 목록                 │ NavHost (현재 목적지의 패널 내용)  │
 * ├──────────────────────────────────────┴──────────────────────────────────┤
 * │ 하단 전체 폭 적응형 배너 1개                                              │
 * └──────────────────────────────────────────────────────────────────────────┘
 * ```
 * - 구역: 백스택에서 가장 위의 메인/검색/설정 항목. 왼쪽은 그 항목의 ViewModel 로 목록을 그린다
 *   (메인 위에 저장 화면이 열려도 왼쪽 메인 목록은 그대로 유지).
 * - 패널 구분은 배경 톤 차이(왼쪽 background / 오른쪽 surface)로만 한다.
 * - 뒤로가기는 NavHost 가 처리한다: 오른쪽에 하위 화면이 열려 있으면 닫혀서 기본 패널로, 기본 상태면
 *   액티비티의 "두 번 눌러 종료"로 넘어간다 (폰과 같은 백스택).
 */
@Composable
fun TwoPaneLayout(
    navController: NavHostController,
    windowLayout: WindowLayout,
    mainListState: LazyListState,
    rightPane: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    val backStack by navController.currentBackStack.collectAsState()
    val entries = backStack.filter { it.destination !is NavGraph }
    val top = entries.lastOrNull()
    val sectionRoot = entries.lastOrNull { it.baseRoute() in SECTION_ROOT_ROUTES }
    val listStateHolder = rememberSaveableStateHolder()

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // 하단 내비게이션 바 여백은 아래 광고 바가 맡는다 (패널 안 Scaffold 가 다시 띄우지 않게)
                .consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        ) {
            Box(modifier = Modifier.width(windowLayout.listPaneWidth).fillMaxHeight()) {
                if (sectionRoot != null) {
                    listStateHolder.SaveableStateProvider(key = sectionRoot.id) {
                        SectionListPane(
                            sectionRoot = sectionRoot,
                            top = top,
                            navController = navController,
                            mainListState = mainListState,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(colors.surface)
            ) {
                CompositionLocalProvider(LocalInRightPane provides true) {
                    rightPane()
                }
            }
        }
        AdBannerBar(hostedByTwoPane = true)
    }
}

@Composable
private fun SectionListPane(
    sectionRoot: NavBackStackEntry,
    top: NavBackStackEntry?,
    navController: NavHostController,
    mainListState: LazyListState,
) {
    // 백스택 항목이 CREATED 이상일 때만 그 항목에 묶인 ViewModel 을 만든다 (복원 직후 저장 상태가 준비되기 전 접근 방지)
    val lifecycleState by sectionRoot.lifecycle.currentStateAsState()
    if (!lifecycleState.isAtLeast(Lifecycle.State.CREATED)) return

    val rightPaneRoute = top?.baseRoute()
    CompositionLocalProvider(LocalViewModelStoreOwner provides sectionRoot) {
        when (sectionRoot.baseRoute()) {
            Navigation.Routes.MAIN -> MainListScreen(
                navController = navController,
                listState = mainListState,
                pane = MainListPane(
                    rightPaneShowsHome = rightPaneRoute == Navigation.Routes.MAIN,
                    // 저장/편집 패널이 열려 있으면 그 URL 의 행을 선택 표시한다 (저장된 링크 = 편집)
                    selectedUrl = top?.takeIf { rightPaneRoute == Navigation.Routes.SAVE_LINK }?.arguments?.getString("url"),
                    scrollToTop = sectionRoot.arguments?.getBoolean("scrollToTop") ?: false,
                ),
            )
            Navigation.Routes.SEARCH -> SearchListPane(
                onBack = { navController.popBackStack(Navigation.Routes.SEARCH, inclusive = true) },
            )
            Navigation.Routes.APP_SETTING -> AppSettingScreen(
                navController = navController,
                isListPane = true,
                selectedPaneRoute = rightPaneRoute,
            )
        }
    }
}
