package com.jinscompany.saveurl

import android.app.Activity
import android.app.UiModeManager
import android.os.Build
import android.os.SystemClock
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.ui.navigation.AppNavigation
import com.jinscompany.saveurl.ui.navigation.navigateToSaveLink
import com.jinscompany.saveurl.domain.model.ThemeMode
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import com.jinscompany.saveurl.ui.theme.isDarkTheme
import com.jinscompany.saveurl.utils.CmLog
import com.jinscompany.saveurl.utils.InAppUpdateCheck
import com.jinscompany.saveurl.utils.extractUrlFromText
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val sharedViewModel by viewModels<SharedViewModel>()
    private lateinit var inAppUpdateCheck: InAppUpdateCheck
    private val immediateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        inAppUpdateCheck.onImmediateActivityResult(resultCode = result.resultCode)
    }
    private val flexibleLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    private var backPressedTime: Long = 0L

    private companion object {
        const val SPLASH_MAX_WAIT_MS = 1500L
    }
    private lateinit var toast: Toast

    override fun onCreate(savedInstanceState: Bundle?) {
        // 스플래시: super.onCreate 전에 설치. 화면 모드(마이그레이션 포함)를 읽을 때까지 유지해
        // 첫 Compose 프레임이 잘못된 테마로 그려지지 않게 한다. 저장소 문제로 늦어져도 최대 1.5초 후에는 넘어간다.
        val splashStart = SystemClock.uptimeMillis()
        installSplashScreen().setKeepOnScreenCondition {
            sharedViewModel.themeMode.value == null && SystemClock.uptimeMillis() - splashStart < SPLASH_MAX_WAIT_MS
        }
        super.onCreate(savedInstanceState)
        inAppUpdateCheck = InAppUpdateCheck(this, immediateLauncher, flexibleLauncher, sharedViewModel)
        setupBackPressedHandler()

        // 첫 프레임은 시스템 설정 기준으로 시스템 바를 맞추고, 테마가 정해지면 아래 DisposableEffect 에서 다시 적용
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        // 회전/프로세스 재생성 시에는 NavController 가 백스택을 복원하므로 공유 Intent 를 다시 처리하지 않는다
        val isFreshLaunch = savedInstanceState == null
        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current
            val activity = context as? Activity

            // 공유 Intent 확인
            LaunchedEffect(Unit) {
                if (!isFreshLaunch) return@LaunchedEffect
                val intent = activity?.intent
                if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val realUrl = extractUrlFromText(sharedText ?: "")
                    CmLog.d("sharedText > ${sharedText}\nrealUrl > ${realUrl}")
                    if (!realUrl.isNullOrEmpty()) {
                        navController.navigateToSaveLink(url = realUrl)
                    }
                }
            }

            val themeMode by sharedViewModel.themeMode.collectAsState()
            val isDark = (themeMode ?: ThemeMode.SYSTEM).isDarkTheme()

            // Android 12+: 앱 내 화면 모드를 시스템에 알려 둔다. 다음 콜드 스타트의 스플래시·window 배경(values-night)이
            // 앱 테마와 같아져 "시스템 라이트 + 앱 다크" 에서도 흰 스플래시가 번쩍이지 않는다.
            // (manifest 의 configChanges="uiMode" 로 액티비티 재생성 없이 Compose 가 다시 그린다)
            LaunchedEffect(themeMode) {
                themeMode?.let { applyAppNightMode(it) }
            }

            // 앱 내 테마 선택(시스템/라이트/다크)에 맞춰 상태바·내비게이션바 아이콘 색을 바꾼다.
            // (SystemBarStyle.auto 는 시스템 다크 설정만 보므로 앱에서 강제한 테마와 어긋날 수 있다)
            DisposableEffect(isDark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (isDark) SystemBarStyle.dark(transparent)
                else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            val isFlexibleUpdateDownloaded by sharedViewModel.isFlexibleUpdateDownloaded.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(isFlexibleUpdateDownloaded) {
                if (isFlexibleUpdateDownloaded) {
                    val result = snackbarHostState.showSnackbar(
                        message = getString(R.string.update_downloaded_message),
                        actionLabel = getString(R.string.update_install_action),
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        inAppUpdateCheck.completeFlexibleUpdate()
                    }
                    sharedViewModel.setFlexibleUpdateDownloaded(false)
                }
            }

            SaveUrlTheme(darkTheme = isDark) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) } },
                    containerColor = MaterialTheme.colorScheme.background,
                ) { _ ->
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppNavigation(navController)
                    }
                }
            }
        }
    }

    private fun applyAppNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiModeManager = getSystemService(UiModeManager::class.java) ?: return
        val target = when (mode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        }
        runCatching { uiModeManager.setApplicationNightMode(target) }
            .onFailure { CmLog.e("setApplicationNightMode failed: ${it.message}") }
    }

    override fun onResume() {
        super.onResume()
        if (!SaveUrlApplication.DEBUG) inAppUpdateCheck.resumeUpdateIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()
        inAppUpdateCheck.unregisterListener()
    }

    private fun setupBackPressedHandler() {
        toast = Toast.makeText(this, R.string.back_press_exit_message, Toast.LENGTH_SHORT)
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime <= 2000L) {
                    toast.cancel()
                    finish()
                } else {
                    backPressedTime = currentTime
                    toast.show()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }
}