package com.jinscompany.saveurl

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jinscompany.saveurl.ui.composable.SetStatusBarColor
import com.jinscompany.saveurl.ui.navigation.AppNavigation
import com.jinscompany.saveurl.ui.navigation.Navigation
import com.jinscompany.saveurl.ui.navigation.navigateToSaveLink
import com.jinscompany.saveurl.ui.theme.Brown
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import com.jinscompany.saveurl.utils.CmLog
import com.jinscompany.saveurl.utils.InAppUpdateCheck
import com.jinscompany.saveurl.utils.extractUrlFromText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val sharedViewModel by viewModels<SharedViewModel>()
    private lateinit var inAppUpdateCheck: InAppUpdateCheck

    private val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        inAppUpdateCheck.onActivityResult(resultCode = result.resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inAppUpdateCheck = InAppUpdateCheck(this, sharedViewModel, updateLauncher)
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                inAppUpdateCheck.isChecking.value
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED){
                inAppUpdateCheck.resumeFlexibleUpdateCheck()
            }
        }

        enableEdgeToEdge()
        setContent {
            val navController: NavHostController = rememberNavController()
            val context = LocalContext.current
            val activity = context as? Activity

            val snackBarHostState = remember { SnackbarHostState() }
            val coroutineScope: CoroutineScope = rememberCoroutineScope()
            var isFABVisible by remember { mutableStateOf(true) }   //todo startDestination 이 바뀔 경우 해당 플레그 변경 해야 함

            // 공유 Intent 확인
            LaunchedEffect(Unit) {
                val intent = activity?.intent
                if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val realUrl = extractUrlFromText(sharedText ?: "")
                    CmLog.d("sharedText > ${sharedText}\nrealUrl > ${realUrl}")
                    if (!realUrl.isNullOrEmpty()) {
                        val route = "${Navigation.Routes.SAVE_LINK}?url=$realUrl"
                        navController.navigate(route)
                        isFABVisible = false    // 공유하기로 다이렉트로 올 경우 팹 제거
                    }
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(Unit) {
                sharedViewModel.snackBarEvent
                    .onEach { effect ->
                        coroutineScope.launch {
                            when (effect) {
                                is SharedModelUiEffect.ShowSnackBarClipBoardUrlGoToLinkInsertScreenAction -> {
                                    val result = snackBarHostState
                                        .showSnackbar(
                                            message = "클립보드에 복사된 링크 저장\n${effect.url}",
                                            duration = SnackbarDuration.Short,
                                            actionLabel = "저장",
                                            withDismissAction = true
                                        )
                                    when (result) {
                                        SnackbarResult.ActionPerformed -> {
                                            navController.navigateToSaveLink(effect.url)
                                        }
                                        SnackbarResult.Dismissed -> {}
                                    }
                                }
                                is SharedModelUiEffect.ShowSnackBarInAppUpdateResult -> {
                                    val model = effect.model
                                    val result = snackBarHostState
                                        .showSnackbar(
                                            message = model.message,
                                            duration = model.duration,
                                            actionLabel = model.actionLabel,
                                            withDismissAction = model.withDismissAction
                                        )
                                    when (result) {
                                        SnackbarResult.ActionPerformed -> {
                                            inAppUpdateCheck.userActionCompleteUpdate()
                                        }
                                        SnackbarResult.Dismissed -> {}
                                    }
                                }
                            }
                        }
                    }
                    .launchIn(lifecycleOwner.lifecycleScope)
            }

            LaunchedEffect(sharedViewModel.currentRoute) {
                sharedViewModel.currentRoute.collectLatest {
                    isFABVisible = it.contains(Navigation.Routes.MAIN)
                }
            }

            SaveUrlTheme {
                SetStatusBarColor(color = Color.DarkGray)
                Scaffold (
                    snackbarHost = { SnackbarHost(
                        hostState = snackBarHostState,
                        snackbar = {
                            Snackbar(
                                snackbarData = it,
                                containerColor = Color.LightGray,
                                contentColor = Color.DarkGray,
                                dismissActionContentColor = Color.DarkGray,
                                actionColor = Color.DarkGray
                            )
                        }
                    ) },
                    floatingActionButtonPosition = FabPosition.End,
                    floatingActionButton = {
                        AnimatedVisibility(
                            visible = isFABVisible,
                            enter = slideInVertically(initialOffsetY = { it * 2 }),
                            exit = slideOutVertically(targetOffsetY = { it * 2 }),
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    navController.navigateToSaveLink()
                                },
                                containerColor = Brown,
                                contentColor = Color.White,
                                elevation = FloatingActionButtonDefaults.elevation(6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = ""
                                )
                            }
                        }
                    }
                ) { paddingValue ->
                    Surface(modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValue), color = Color.DarkGray) {
                        AppNavigation(navController, sharedViewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        inAppUpdateCheck.onInstallStatusListener()
    }

    override fun onStop() {
        super.onStop()
        inAppUpdateCheck.unOnInstallStatusListener()
    }
}