package com.jinscompany.saveurl

import android.app.Activity
import android.app.UiModeManager
import android.os.Build
import android.os.SystemClock
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.ui.lock.LocalCategoryLockSession
import com.jinscompany.saveurl.ui.lock.LocalLockPromptBroker
import com.jinscompany.saveurl.ui.lock.LockPromptBroker
import com.jinscompany.saveurl.ui.adaptive.LocalWindowLayout
import com.jinscompany.saveurl.ui.composable.ForceUpdateDialog
import com.jinscompany.saveurl.ui.adaptive.currentWindowLayout
import com.jinscompany.saveurl.ui.navigation.AppNavigation
import com.jinscompany.saveurl.ui.navigation.navigateToSaveLink
import com.jinscompany.saveurl.domain.model.ThemeMode
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import com.jinscompany.saveurl.ui.theme.isDarkTheme
import com.jinscompany.saveurl.utils.CmLog
import com.jinscompany.saveurl.utils.InAppUpdateCheck
import com.jinscompany.saveurl.utils.RemoteFlags
import com.jinscompany.saveurl.ui.importer.ImportEntry
import com.jinscompany.saveurl.ui.importer.ImportShare
import com.jinscompany.saveurl.ui.importer.ImportShareInbox
import com.jinscompany.saveurl.ui.navigation.navigateToImport
import com.jinscompany.saveurl.ui.navigation.Navigation
import com.jinscompany.saveurl.ui.importer.MainListRequests
import com.jinscompany.saveurl.ui.lock.LockPromptHost
import com.jinscompany.saveurl.ui.lock.hideFromRecentsWhileLockedCategoriesOpen
import com.jinscompany.saveurl.ui.share.AppHandoff
import com.jinscompany.saveurl.ui.share.AppHandoffInbox
import com.jinscompany.saveurl.ui.share.SavePrefillStore
import com.jinscompany.saveurl.data.revisit.RevisitRepository
import com.jinscompany.saveurl.ui.revisit.RevisitOpenHandler
import com.jinscompany.saveurl.ui.revisit.RevisitOpenViewModel
import com.jinscompany.saveurl.ui.tutorial.LocalTutorialFeatureFlags
import com.jinscompany.saveurl.ui.tutorial.TutorialFeatureFlags
import androidx.navigation.NavHostController
import com.jinscompany.saveurl.utils.openPlayStorePage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
// BiometricPrompt(잠금 카테고리 인증)가 FragmentActivity 를 요구한다 (ComponentActivity 의 하위 클래스라 다른 동작은 같음)
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var categoryLockSession: CategoryLockSession

    @Inject
    lateinit var lockPromptBroker: LockPromptBroker

    @Inject
    lateinit var remoteFlags: RemoteFlags

    @Inject
    lateinit var importShareInbox: ImportShareInbox

    @Inject
    lateinit var revisitRepository: RevisitRepository

    @Inject
    lateinit var appHandoffInbox: AppHandoffInbox

    @Inject
    lateinit var savePrefillStore: SavePrefillStore

    @Inject
    lateinit var mainListRequests: MainListRequests

    private val revisitOpenViewModel by viewModels<RevisitOpenViewModel>()

    private val sharedViewModel by viewModels<SharedViewModel>()
    private lateinit var inAppUpdateCheck: InAppUpdateCheck
    private val immediateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        inAppUpdateCheck.onImmediateActivityResult(resultCode = result.resultCode)
    }
    private val flexibleLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        inAppUpdateCheck.onFlexibleActivityResult(resultCode = result.resultCode)
    }

    private var backPressedTime: Long = 0L

    private companion object {
        const val SPLASH_MAX_WAIT_MS = 1500L
        val SNACKBAR_BOTTOM_OFFSET = 56.dp
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
        // 재생성(회전, 폴드, 창 크기·언어 변경)이면 업데이트 확인을 다시 하지 않는다 (진행 중인 업데이트 재개는 onResume 에서 계속)
        inAppUpdateCheck = InAppUpdateCheck(
            activity = this,
            immediateLauncher = immediateLauncher,
            flexibleLauncher = flexibleLauncher,
            sharedViewModel = sharedViewModel,
            isRecreated = savedInstanceState != null,
            onRemoteConfigActivated = {
                remoteFlags.refresh()
                // 주간 정리 원격 스위치가 꺼졌으면 예약을 취소한다 (42번 §4.2)
                lifecycleScope.launch { revisitRepository.syncWeekly() }
            },
        )
        setupBackPressedHandler()
        // 잠금 카테고리 인증 프롬프트 (onStart/onStop 에 연결, 공유 시트와 함께 쓰는 규칙 44번 §1.5)
        LockPromptHost(this, lockPromptBroker)
        hideFromRecentsWhileLockedCategoriesOpen(categoryLockSession)

        // 첫 프레임은 시스템 설정 기준으로 시스템 바를 맞추고, 테마가 정해지면 아래 DisposableEffect 에서 다시 적용
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        // 알림에서 연 요청은 RevisitOpenActivity 가, 공유 시트에서 넘긴 요청은 ShareActivity 가 전달함에 넣고 온다
        // (이 Activity 는 내보내져 있어 Intent 내용으로 요청을 받지 않는다. 공유 intent-filter 도 ShareActivity 로 옮김, 44번 §6)
        setContent {
            val navController = rememberNavController()

            // 공유 시트 → 앱 (저장 화면·수정·목록·가져오기). 떠 있는 동안 넘어오면 onNewIntent 뒤 여기서 받는다
            val handoff by appHandoffInbox.pending.collectAsState()
            LaunchedEffect(handoff) {
                if (handoff != null) appHandoffInbox.take()?.let { handleHandoff(it, navController) }
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
            val forceUpdateBlock by sharedViewModel.forceUpdateBlock.collectAsState()
            val isForceUpdateRechecking by sharedViewModel.isForceUpdateRechecking.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(isFlexibleUpdateDownloaded) {
                if (isFlexibleUpdateDownloaded) {
                    val result = snackbarHostState.showSnackbar(
                        message = getString(R.string.update_downloaded_message),
                        actionLabel = getString(R.string.update_install_action),
                        // 닫기(X): 이번에는 설치하지 않음. 다음에 앱으로 돌아올 때 다시 안내한다 (onResume 재개 경로)
                        withDismissAction = true,
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        inAppUpdateCheck.completeFlexibleUpdate()
                    }
                    sharedViewModel.setFlexibleUpdateDownloaded(false)
                }
            }

            // 창 폭에 따른 화면 구성(한 화면/2분할). AppNavigation 이 2분할 레이아웃을 고르고, 각 화면·시트가 읽어 모양을 바꾼다.
            val windowLayout = currentWindowLayout()
            // 튜토리얼 주제의 원격 스위치 (import_enabled). 값이 바뀌면 새 객체로 넘겨 주제 목록을 다시 거른다
            val importEnabled by remoteFlags.importEnabled.collectAsState()
            val tutorialFlags = remember(importEnabled) {
                TutorialFeatureFlags { key -> if (key == RemoteFlags.KEY_IMPORT_ENABLED) importEnabled else false }
            }

            SaveUrlTheme(darkTheme = isDark) {
                CompositionLocalProvider(
                    LocalWindowLayout provides windowLayout,
                    LocalCategoryLockSession provides categoryLockSession,
                    LocalLockPromptBroker provides lockPromptBroker,
                    LocalTutorialFeatureFlags provides tutorialFlags,
                ) {
                    Scaffold(
                        // 각 화면 바닥의 광고 배너(약 50dp)를 가리지 않도록 그 위에 띄운다
                        snackbarHost = {
                            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = SNACKBAR_BOTTOM_OFFSET)) { data ->
                                Snackbar(snackbarData = data)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.background,
                    ) { _ ->
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            AppNavigation(navController)
                        }
                        RevisitOpenHandler(viewModel = revisitOpenViewModel, navController = navController)
                    }
                    forceUpdateBlock?.let { reason ->
                        ForceUpdateDialog(
                            reason = reason,
                            isRechecking = isForceUpdateRechecking,
                            onPlayStoreClick = { openPlayStorePage() },
                            onRetryClick = { inAppUpdateCheck.recheckForceUpdate() },
                            onExitClick = { finish() },
                        )
                    }
                }
            }
        }
    }

    /**
     * 공유 시트([com.jinscompany.saveurl.ui.share.ShareActivity])에서 넘긴 요청 (44번 §6.2).
     * 2분할이면 메인 위에 열린 오른쪽 패널을 닫고 그 자리에 연다 (지금 공유 → 저장 화면과 같음).
     */
    private fun handleHandoff(handoff: AppHandoff, navController: NavHostController) {
        when (handoff) {
            is AppHandoff.OpenSave -> {
                handoff.prefill?.let { savePrefillStore.put(handoff.url, it) }
                navController.navigateToSaveLink(url = handoff.url, replaceRightPane = true)
            }
            // 저장된 링크면 저장 화면이 수정 모드로 연다 (잠금 링크는 시트에서 인증하고 왔으므로 세션이 열려 있다)
            is AppHandoff.OpenEdit -> navController.navigateToSaveLink(url = handoff.url, replaceRightPane = true)
            is AppHandoff.OpenList -> {
                mainListRequests.showCategory(handoff.category)
                runCatching { navController.popBackStack(Navigation.MAIN_ROUTE_PATTERN, inclusive = false) }
            }
            AppHandoff.Import -> {
                // 그 사이 프로세스가 죽었으면 받은 내용이 없다: 가져오기 홈 + 다시 보내 달라고 알림 (§7)
                if (!importShareInbox.hasPending()) Toast.makeText(this, R.string.share_import_resend, Toast.LENGTH_SHORT).show()
                navController.navigateToImport(ImportEntry.SHARE, replaceRightPane = true)
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

    /** 앱이 떠 있을 때 알림을 누르거나 공유 시트에서 넘어옴 (SINGLE_TOP | CLEAR_TOP, 요청은 이미 전달함에 있음) */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        inAppUpdateCheck.onResume()
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