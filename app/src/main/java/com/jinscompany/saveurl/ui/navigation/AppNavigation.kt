package com.jinscompany.saveurl.ui.navigation

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
import com.jinscompany.saveurl.MainActivity
import com.jinscompany.saveurl.SharedViewModel
import com.jinscompany.saveurl.ui.add_category.EditCategoryScreen
import com.jinscompany.saveurl.ui.main.MainListScreen
import com.jinscompany.saveurl.ui.save_screen.LinkInsertScreen
import com.jinscompany.saveurl.ui.search.SearchScreen
import com.jinscompany.saveurl.ui.setting.AppSettingScreen
import com.jinscompany.saveurl.utils.CmLog

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    sharedViewModel: SharedViewModel
) {
    LaunchedEffect(navController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            sharedViewModel.notifyCurrentRoute(destination.route ?: "")
        }
    }
    NavHost(
        navController = navController,
        startDestination = sharedViewModel.startDestination,
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
            MainListScreen(
                navController = navController,
                sharedViewModel = sharedViewModel
            )
        }
        composable(
            route = "${Navigation.Routes.SAVE_LINK}?url={url}",
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url")
            LinkInsertScreen(navController, url)
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

fun NavController.navigateToSaveLink(url: String? = null) {
    val route = if (url != null) {
        "${Navigation.Routes.SAVE_LINK}?url=$url"
    } else {
        Navigation.Routes.SAVE_LINK
    }
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