package com.jinscompany.saveurl.ui.navigation

import android.content.Intent
import androidx.activity.compose.LocalActivity
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
import com.google.gson.Gson
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.add_category.EditCategoryScreen
import com.jinscompany.saveurl.ui.main.MainListScreen
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SAVE_LINK
import com.jinscompany.saveurl.ui.save_screen.LinkSaveScreen
import com.jinscompany.saveurl.ui.save_screen.LinkSaveUiEffect
import com.jinscompany.saveurl.ui.save_screen.LinkSaveViewModel
import com.jinscompany.saveurl.ui.search.SearchScreen
import com.jinscompany.saveurl.ui.setting.AppSettingScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

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
            val urlData = backStackEntry.arguments?.getString("urlData")
            val viewModel = hiltViewModel<LinkSaveViewModel>()
            val activity = LocalActivity.current
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
            // 앱 외부에서 공유하기 다이렉트로 들어왔을 경우에만 해당 되는 로직
            LaunchedEffect(Unit) {
                val intent = activity?.intent

                if (!urlData.isNullOrEmpty()) {
                    val data = Gson().fromJson<UrlData>(urlData, UrlData::class.java)
                    viewModel.userSelectLinkEditMode(data)
                } else {
                    val linkUrl = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                        intent.getStringExtra(Intent.EXTRA_TEXT).toString()
                    } else if (url?.isNotEmpty() == true) {
                        url
                    } else ""
                    if (linkUrl.isNotEmpty()) {
                        viewModel.startCrawling(url ?: "")
                    }
                }
            }

            LinkSaveScreen(
                state = viewModel.uiState,
                uiEffect = viewModel.uiEffect,
                event = { intent -> viewModel.onIntent(intent)} )
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
    }
}

object Navigation {
    object Routes {
        const val APP_SETTING = "appSetting"
        const val MAIN = "mainScreen"
        const val SAVE_LINK = "saveLinkScreen"
        const val SEARCH = "searchScreen"
        const val EDIT_CATEGORY = "editCategoty"
    }
}

fun NavController.navigateToMain(currentScreen: String, scrollToTop: Boolean = false) {
    navigate(route = "${Navigation.Routes.MAIN}?scrollToTop=${scrollToTop}") {
        popUpTo(currentScreen) { inclusive = true }
        launchSingleTop = true
    }
}

fun NavController.navigateToSaveLink(url: String? = null, data: UrlData? = null) {
    val urlData = if (data != null) Gson().toJson(data) else ""
    val route = "${Navigation.Routes.SAVE_LINK}?url=$url&urlData=$urlData"
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