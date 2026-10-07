package com.jinscompany.saveurl.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jinscompany.saveurl.ui.adaptive.LocalWindowLayout
import com.jinscompany.saveurl.ui.adaptive.TwoPaneLayout
import com.jinscompany.saveurl.ui.add_category.EditCategoryScreen
import com.jinscompany.saveurl.ui.main.MainListScreen
import com.jinscompany.saveurl.ui.main.home.HomePanel
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SAVE_LINK
import com.jinscompany.saveurl.ui.save_screen.InsertLinkScreen
import com.jinscompany.saveurl.ui.save_screen.LinkSaveUiEffect
import com.jinscompany.saveurl.ui.save_screen.LinkSaveViewModel
import com.jinscompany.saveurl.ui.search.SearchFilterPanel
import com.jinscompany.saveurl.ui.search.SearchScreen
import com.jinscompany.saveurl.ui.setting.AppSettingScreen
import com.jinscompany.saveurl.ui.setting.DisplaySettingsPanel
import com.jinscompany.saveurl.ui.trash.TrashScreen
import com.jinscompany.saveurl.ui.trash.TrashUiEffect
import com.jinscompany.saveurl.ui.trash.TrashViewModel
import com.jinscompany.saveurl.ui.webview.StaticWebScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import java.net.URLDecoder

/**
 * 앱 내비게이션.
 *
 * - 폰(폭 600dp 미만): 지금과 같은 한 화면 NavHost.
 * - 2분할(600dp 이상): [TwoPaneLayout] 이 왼쪽 목록 패널 + 오른쪽 패널(= 같은 NavHost) + 하단 광고를 그린다.
 *   백스택은 폰과 같다. 각 목적지가 2분할일 때 "오른쪽 패널에 들어갈 내용"만 그린다
 *   (메인 → 홈 패널, 검색 → 필터 패널, 설정 → 화면 설정, 저장/편집·카테고리 편집·휴지통·사용 방법 → 그 화면).
 *   그래서 접기·펼치기(액티비티 재생성)에서도 같은 백스택 항목과 ViewModel 이 그대로 이어진다.
 */
@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val windowLayout = LocalWindowLayout.current
    // 메인 목록 스크롤 위치. 폰에서는 메인 화면, 2분할에서는 왼쪽 패널이 그리므로 위로 올려 같은 상태를 쓴다.
    val mainListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    if (windowLayout.isTwoPane) {
        TwoPaneLayout(navController = navController, windowLayout = windowLayout, mainListState = mainListState) {
            AppNavHost(navController = navController, mainListState = mainListState, isTwoPane = true)
        }
    } else {
        AppNavHost(navController = navController, mainListState = mainListState, isTwoPane = false)
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    mainListState: LazyListState,
    isTwoPane: Boolean,
) {
    NavHost(
        navController = navController,
        startDestination = Navigation.Routes.MAIN,
        // 2분할: 오른쪽 패널 안에서만 바뀌므로 짧은 페이드. 폰: 기존 좌우 슬라이드 그대로.
        enterTransition = {
            if (isTwoPane) fadeIn(tween(PANE_FADE_MS))
            else slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = tween(300))
        },
        exitTransition = {
            if (isTwoPane) fadeOut(tween(PANE_FADE_MS))
            else slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = tween(300))
        },
        popEnterTransition = {
            if (isTwoPane) fadeIn(tween(PANE_FADE_MS))
            else slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(300))
        },
        popExitTransition = {
            if (isTwoPane) fadeOut(tween(PANE_FADE_MS))
            else slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.End, animationSpec = tween(300))
        }
    ) {
        composable(
            route = Navigation.MAIN_ROUTE_PATTERN,
            arguments = listOf(
                navArgument("scrollToTop") {
                    type = NavType.BoolType
                    nullable = false
                    defaultValue = false
                }
            )
        ) {
            if (isTwoPane) {
                // 오른쪽 기본 = 홈 패널. 메인 목록(왼쪽 패널)과 같은 MainListViewModel(이 백스택 항목)을 쓴다.
                HomePanel(
                    mainListViewModel = hiltViewModel(),
                    onOpenSave = { url -> navController.navigateToSaveLink(url = url, replaceRightPane = true) },
                )
            } else {
                MainListScreen(navController = navController, listState = mainListState)
            }
        }
        composable(
            route = "${Navigation.Routes.SAVE_LINK}?url={url}&urlData={urlData}",
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("urlData") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url")
            // 저장/편집 ViewModel 은 이 백스택 항목(= 열린 저장 화면 하나)에 묶인다.
            // 오른쪽 패널을 닫으면(뒤로/✕/저장 완료) 항목이 빠지면서 함께 정리되고, 접기·펼치기에서는 그대로 이어진다.
            val viewModel = hiltViewModel<LinkSaveViewModel>()
            LaunchedEffect(Unit) {
                viewModel.uiEffect
                    .filterIsInstance<LinkSaveUiEffect.GotoNextScreen>()
                    .collectLatest {
                        if (it.isPopBack) navController.popBackStack()
                        else if (it.isCategoryEdit) navController.navigateToEditCategory()
                        else {
                        navController.navigateToMain(
                            currentScreen = SAVE_LINK,
                            scrollToTop = true
                        )
                    }}
            }
            // 전달받은 URL(공유하기/클립보드/수정)은 ViewModel 당 최초 1회만 크롤링
            // (카테고리 편집 화면에서 돌아오거나 화면 회전 시 다시 크롤링되어 입력값이 초기화되는 문제 방지)
            LaunchedEffect(Unit) {
                viewModel.startInitialCrawling(url)
            }
            PaneContent(isTwoPane) {
                InsertLinkScreen(
                    state = viewModel.uiState,
                    uiEffect = viewModel.uiEffect,
                    event = { intent -> viewModel.onIntent(intent)}
                )
            }
        }
        composable(route = Navigation.Routes.SEARCH) {
            if (isTwoPane) {
                // 오른쪽 = 항상 펼친 필터 패널. 왼쪽 검색 목록과 같은 SearchViewModel(이 백스택 항목)을 쓴다.
                SearchFilterPanel(onEditCategory = { navController.navigateToEditCategory() })
            } else {
                SearchScreen(popBackStack = { navController.popBackStack() })
            }
        }
        composable(route = Navigation.Routes.EDIT_CATEGORY) {
            PaneContent(isTwoPane) {
                EditCategoryScreen(navController)
            }
        }
        composable(route = Navigation.Routes.APP_SETTING) {
            if (isTwoPane) DisplaySettingsPanel()
            else AppSettingScreen(navController)
        }
        // 개발자 응원(SupportScreen)은 인앱 결제가 없어 현재 노출하지 않는다 (진입점 없음, 라우트 미등록).
        // TODO: 통신판매업 신고 후 billing 복원 시 다시 노출 — 아래 등록과 navigateToSupport() 주석을 함께 해제
        // composable(route = Navigation.Routes.SUPPORT) {
        //     SupportScreen(popBackStack = { navController.popBackStack() })
        // }
        composable(route = Navigation.Routes.TRASH) {
            val viewModel = hiltViewModel<TrashViewModel>()
            LaunchedEffect(Unit) {
                viewModel.uiEffect.filterIsInstance<TrashUiEffect.GotoNextScreen>().collectLatest {
                    navController.popBackStack()
                }
            }
            PaneContent(isTwoPane) {
                TrashScreen(state = viewModel.uiState, uiEffect = viewModel.uiEffect, event = { intent ->
                    viewModel.onIntent(intent)
                })
            }
        }
        composable(
            route = "${Navigation.Routes.STATIC_WEB}?url={url}",
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            )
        ) { backStackEntry ->
            val encodingUrl = backStackEntry.arguments?.getString("url") ?: ""
            val url = URLDecoder.decode(encodingUrl, "UTF-8")
            PaneContent(isTwoPane) {
                StaticWebScreen(navController = navController, url = url)
            }
        }
    }
}

/**
 * 오른쪽 패널의 흰 배경 하위 화면(저장·편집·설정 하위)은 왼쪽에 8dp 회색(surface) 거터를 두어 목록과 나눈다 (굵은 선 없음).
 * 폰에서는 그대로 그린다.
 */
@Composable
private fun PaneContent(isTwoPane: Boolean, content: @Composable () -> Unit) {
    if (!isTwoPane) {
        content()
        return
    }
    Box(modifier = Modifier.fillMaxSize().padding(start = 8.dp)) { content() }
}

private const val PANE_FADE_MS = 180

object Navigation {
    object Routes {
        const val APP_SETTING = "appSetting"
        const val MAIN = "mainScreen"
        const val SAVE_LINK = "saveLinkScreen"
        const val SEARCH = "searchScreen"
        const val EDIT_CATEGORY = "editCategory"
        const val TRASH = "trashScreen"
        const val STATIC_WEB = "staticWebScreen"
        /** TODO: 통신판매업 신고 후 billing 복원 시 다시 노출 (현재 NavHost 에 등록하지 않음) */
        const val SUPPORT = "supportScreen"
    }

    /** 메인 목적지의 등록 route (popUpTo 에 그대로 쓴다) */
    const val MAIN_ROUTE_PATTERN = "${Routes.MAIN}?scrollToTop={scrollToTop}"
}

/** 인자를 뺀 route 이름 (예: "saveLinkScreen?url={url}&..." → "saveLinkScreen") */
fun NavBackStackEntry.baseRoute(): String? = destination.route?.substringBefore('?')

fun NavController.navigateToStaticWeb(url: String) {
    navigate(route = "${Navigation.Routes.STATIC_WEB}?url=$url")
}

fun NavController.navigateToMain(currentScreen: String, scrollToTop: Boolean = false) {
    navigate(route = "${Navigation.Routes.MAIN}?scrollToTop=${scrollToTop}") {
        popUpTo(currentScreen) { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * 저장 화면 열기.
 * [replaceRightPane] = true(2분할): 메인 위에 열린 오른쪽 패널(다른 저장/편집 등)을 닫고 그 자리에 연다.
 * 그래서 뒤로가기 한 번이면 홈 패널로 돌아간다.
 */
fun NavController.navigateToSaveLink(url: String? = null, replaceRightPane: Boolean = false) {
    // URL 에 포함된 &, ?, #, % 등이 route 쿼리로 해석되어 잘리거나 디코딩되지 않도록 인코딩
    // (Navigation 이 인자 값을 한 번 디코딩하므로 수신측에서는 원본 URL 을 그대로 받는다)
    val route = if (url.isNullOrEmpty()) Navigation.Routes.SAVE_LINK
                else "${Navigation.Routes.SAVE_LINK}?url=${Uri.encode(url)}"
    if (replaceRightPane) {
        navigate(route) { popUpTo(Navigation.MAIN_ROUTE_PATTERN) { inclusive = false } }
    } else {
        navigate(route)
    }
}

fun NavController.navigateToEditCategory() {
    navigate(route = "${Navigation.Routes.EDIT_CATEGORY}")
}

fun NavController.navigateToSearch() {
    navigate(route = "${Navigation.Routes.SEARCH}")
}

fun NavController.navigateToAppSetting() {
    navigate(route = "${Navigation.Routes.APP_SETTING}")
}

fun NavController.navigateToTrash() {
    navigate(route = "${Navigation.Routes.TRASH}")
}

/**
 * 2분할 설정: 오른쪽 패널의 하위 화면(카테고리 편집/휴지통/사용 방법)을 바꾼다.
 * 설정 위에 열린 다른 하위 화면은 닫고 그 자리에 연다 (같은 화면이면 그대로).
 */
fun NavController.navigateInSettingsPane(route: String) {
    navigate(route) {
        popUpTo(Navigation.Routes.APP_SETTING) { inclusive = false }
        launchSingleTop = true
    }
}

// TODO: 통신판매업 신고 후 billing 복원 시 다시 노출 (라우트가 등록되지 않은 상태에서 호출하면 크래시하므로 함께 주석 처리)
// fun NavController.navigateToSupport() {
//     navigate(route = Navigation.Routes.SUPPORT)
// }
