package com.jinscompany.saveurl.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.SharedViewModel
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.ThemeMode
import com.jinscompany.saveurl.ui.importer.ImportShare
import com.jinscompany.saveurl.ui.importer.ImportShareInbox
import com.jinscompany.saveurl.ui.lock.LocalCategoryLockSession
import com.jinscompany.saveurl.ui.lock.LocalLockPromptBroker
import com.jinscompany.saveurl.ui.lock.LockPromptBroker
import com.jinscompany.saveurl.ui.lock.LockPromptHost
import com.jinscompany.saveurl.ui.lock.hideFromRecentsWhileLockedCategoriesOpen
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import com.jinscompany.saveurl.ui.theme.isDarkTheme
import com.jinscompany.saveurl.utils.RemoteFlags
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * 공유 입구 (44번). 다른 앱에서 공유하면 보던 앱 위에 반투명하게 떠서
 * - 링크 1개 → 빠른 저장 시트. [저장] 하면 닫혀 보던 앱으로 돌아간다.
 * - 파일·링크 여러 개·카톡 대화 → 앱(자기 작업)의 링크 가져오기로 넘기고 바로 닫힌다.
 *
 * 매니페스트: taskAffinity="" · excludeFromRecents · autoRemoveFromRecents (최근 앱에 SaveLink 카드를 남기지 않는다, §8).
 * 스플래시·업데이트 확인·광고는 없다 (§1.1, §9, §10.5).
 */
@AndroidEntryPoint
class ShareActivity : FragmentActivity() {

    @Inject lateinit var categoryLockSession: CategoryLockSession
    @Inject lateinit var lockPromptBroker: LockPromptBroker
    @Inject lateinit var remoteFlags: RemoteFlags
    @Inject lateinit var importShareInbox: ImportShareInbox
    @Inject lateinit var appHandoffInbox: AppHandoffInbox

    private val sharedViewModel by viewModels<SharedViewModel>()

    /** 시트로 저장할 링크 (회전·프로세스 재시작 뒤 같은 시트를 다시 그린다) */
    private var quickUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockPromptHost(this, lockPromptBroker)
        hideFromRecentsWhileLockedCategoriesOpen(categoryLockSession)
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(transparent, transparent),
            navigationBarStyle = SystemBarStyle.auto(transparent, transparent),
        )

        // 회전·프로세스 재시작이면 나누기를 다시 하지 않는다 (§2)
        quickUrl = if (savedInstanceState != null) savedInstanceState.getString(KEY_URL) else routeShare(intent)
        val url = quickUrl ?: return

        val startedAt = SystemClock.uptimeMillis()
        setContent {
            val themeMode by sharedViewModel.themeMode.collectAsState()
            // 앱 테마 값을 읽기 전에는 그리지 않는다 (흰 시트가 번쩍였다 어두워지지 않게). 300ms 가 지나면 시스템 설정으로 (§1.3)
            var timedOut by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay((THEME_WAIT_MS - (SystemClock.uptimeMillis() - startedAt)).coerceAtLeast(0))
                timedOut = true
            }
            if (themeMode == null && !timedOut) return@setContent
            val isDark = (themeMode ?: ThemeMode.SYSTEM).isDarkTheme()
            SaveUrlTheme(darkTheme = isDark) {
                CompositionLocalProvider(
                    LocalCategoryLockSession provides categoryLockSession,
                    LocalLockPromptBroker provides lockPromptBroker,
                ) {
                    QuickSaveSheet(url = url, onClose = ::closeShare, onHandoff = ::handoff)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        quickUrl?.let { outState.putString(KEY_URL, it) }
    }

    /** 공유 내용 나누기 (§2). 시트를 그릴 링크면 그 URL, 아니면 넘기거나 알린 뒤 닫고 null */
    private fun routeShare(intent: Intent): String? {
        val isMultiple = intent.action == Intent.ACTION_SEND_MULTIPLE
        val streams: List<Uri> = if (isMultiple) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val route = ShareRouter.route(
            isMultiple = isMultiple,
            streamCount = streams.size,
            text = text,
            importEnabled = remoteFlags.importEnabled.value,
            quickSaveEnabled = remoteFlags.quickSaveEnabled.value,
        )
        when (route) {
            is ShareRouter.Route.QuickSave -> return route.url
            ShareRouter.Route.ImportFiles -> {
                importShareInbox.put(ImportShare.Files(streams))
                handoff(AppHandoff.Import, files = streams)
            }
            ShareRouter.Route.ImportText -> {
                // 대화 글은 수 MB 일 수 있어 Intent 에 넣지 않고 같은 프로세스 전달함에 넣는다 (§7)
                importShareInbox.put(ImportShare.Text(text.orEmpty()))
                handoff(AppHandoff.Import)
            }
            is ShareRouter.Route.OpenSaveInApp -> handoff(AppHandoff.OpenSave(route.url))
            ShareRouter.Route.FileNotSupported -> {
                Toast.makeText(this, R.string.share_file_not_supported, Toast.LENGTH_SHORT).show()
                closeShare()
            }
            ShareRouter.Route.NoLink -> {
                Toast.makeText(this, R.string.share_no_link, Toast.LENGTH_SHORT).show()
                closeShare()
            }
        }
        return null
    }

    /** 앱(MainActivity 자기 작업)으로 넘기고 시트를 닫는다 */
    private fun handoff(target: AppHandoff, files: List<Uri> = emptyList()) {
        appHandoffInbox.put(target)
        AppHandoffInbox.startMain(this, files)
        closeShare()
    }

    /** 닫는 길은 모두 여기로 (§1.4). 자기 작업의 루트면 작업까지 지운다 (최근 앱에 남지 않게) */
    private fun closeShare() {
        if (isFinishing) return
        if (isTaskRoot) finishAndRemoveTask() else finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val KEY_URL = "quick_save_url"
        const val THEME_WAIT_MS = 300L
    }
}
