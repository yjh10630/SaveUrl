package com.jinscompany.saveurl.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jinscompany.saveurl.ui.add_category.EditCategoryScreen
import com.jinscompany.saveurl.ui.main.MainListScreen
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SAVE_LINK
import com.jinscompany.saveurl.ui.save_screen.InsertLinkScreen
import com.jinscompany.saveurl.ui.save_screen.LinkSaveUiEffect
import com.jinscompany.saveurl.ui.save_screen.LinkSaveViewModel
import com.jinscompany.saveurl.ui.search.SearchScreen
import com.jinscompany.saveurl.ui.setting.AppSettingScreen
import com.jinscompany.saveurl.ui.trash.TrashScreen
import com.jinscompany.saveurl.ui.trash.TrashUiEffect
import com.jinscompany.saveurl.ui.trash.TrashViewModel
import com.jinscompany.saveurl.ui.webview.StaticWebScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import java.net.URLDecoder

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Navigation.Routes.MAIN,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300)
            )
        }
    ) {
        composable(
            route = "${Navigation.Routes.MAIN}?scrollToTop={scrollToTop}",
            arguments = listOf(
                navArgument("scrollToTop") {
                    type = NavType.BoolType
                    nullable = false
                    defaultValue = false
                }
            )
        ) {
            MainListScreen(navController)
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
            InsertLinkScreen(
                state = viewModel.uiState,
                uiEffect = viewModel.uiEffect,
                event = { intent -> viewModel.onIntent(intent)}
            )
        }
        composable(route = Navigation.Routes.SEARCH) {
            SearchScreen(popBackStack = { navController.popBackStack() })
        }
        composable(route = Navigation.Routes.EDIT_CATEGORY) {
            EditCategoryScreen(navController)
        }
        composable(route = Navigation.Routes.APP_SETTING) {
            AppSettingScreen(navController)
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
            TrashScreen(state = viewModel.uiState, uiEffect = viewModel.uiEffect, event = { intent ->
                viewModel.onIntent(intent)
            })
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
            StaticWebScreen(navController = navController, url = url)
        }
    }
}

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
}

fun NavController.navigateToStaticWeb(url: String) {
    navigate(route = "${Navigation.Routes.STATIC_WEB}?url=$url")
}

fun NavController.navigateToMain(currentScreen: String, scrollToTop: Boolean = false) {
    navigate(route = "${Navigation.Routes.MAIN}?scrollToTop=${scrollToTop}") {
        popUpTo(currentScreen) { inclusive = true }
        launchSingleTop = true
    }
}

fun NavController.navigateToSaveLink(url: String? = null,) {
    // URL 에 포함된 &, ?, #, % 등이 route 쿼리로 해석되어 잘리거나 디코딩되지 않도록 인코딩
    // (Navigation 이 인자 값을 한 번 디코딩하므로 수신측에서는 원본 URL 을 그대로 받는다)
    val route = if (url.isNullOrEmpty()) Navigation.Routes.SAVE_LINK
                else "${Navigation.Routes.SAVE_LINK}?url=${Uri.encode(url)}"
    navigate(route)
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

// TODO: 통신판매업 신고 후 billing 복원 시 다시 노출 (라우트가 등록되지 않은 상태에서 호출하면 크래시하므로 함께 주석 처리)
// fun NavController.navigateToSupport() {
//     navigate(route = Navigation.Routes.SUPPORT)
// }
