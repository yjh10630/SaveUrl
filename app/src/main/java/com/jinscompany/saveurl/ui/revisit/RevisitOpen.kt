package com.jinscompany.saveurl.ui.revisit

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.data.revisit.RevisitNotifier
import com.jinscompany.saveurl.data.room.BaseSaveUrlDao
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.usecase.MarkAsReadUseCase
import com.jinscompany.saveurl.ui.lock.LockAuthResult
import com.jinscompany.saveurl.ui.lock.rememberLockAuthRequest
import com.jinscompany.saveurl.ui.main.MainTab
import com.jinscompany.saveurl.ui.navigation.Navigation
import com.jinscompany.saveurl.utils.openUrlInBrowser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 알림에서 앱을 연 요청 (42번 §2.7) */
sealed interface RevisitOpenRequest {
    data class Link(val linkId: Int) : RevisitOpenRequest
    data object Unread : RevisitOpenRequest

    companion object {
        /** 알림 Intent → 요청 (해당 없으면 null) */
        fun from(intent: Intent?): RevisitOpenRequest? = when (intent?.action) {
            RevisitNotifier.ACTION_OPEN_REMINDER ->
                intent.getIntExtra(RevisitNotifier.EXTRA_LINK_ID, -1).takeIf { it >= 0 }?.let { Link(it) }
            RevisitNotifier.ACTION_OPEN_UNREAD -> Unread
            else -> null
        }
    }
}

/**
 * 알림 → 앱 요청을 MainActivity(onCreate·onNewIntent) 에서 화면으로 넘긴다.
 * 메인 목록 탭 전환(안 읽음)도 여기서 넘긴다 (MainListViewModel 이 구독).
 */
@Singleton
class RevisitOpenInbox @Inject constructor() {
    private val _pending = MutableStateFlow<RevisitOpenRequest?>(null)
    val pending: StateFlow<RevisitOpenRequest?> = _pending.asStateFlow()

    private val _tabRequests = MutableSharedFlow<MainTab>(replay = 1, extraBufferCapacity = 1)
    val tabRequests = _tabRequests.asSharedFlow()

    fun put(request: RevisitOpenRequest) {
        _pending.value = request
    }

    fun take(): RevisitOpenRequest? = _pending.value.also { _pending.value = null }

    fun requestTab(tab: MainTab) {
        _tabRequests.tryEmit(tab)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consumeTabRequest() {
        _tabRequests.resetReplayCache()
    }
}

/** 알림에서 연 링크 확인 (지워졌는지, 잠금인지) */
@HiltViewModel
class RevisitOpenViewModel @Inject constructor(
    private val inbox: RevisitOpenInbox,
    private val urlDao: BaseSaveUrlDao,
    private val markAsReadUseCase: MarkAsReadUseCase,
    private val notifier: RevisitNotifier,
    private val lockSession: CategoryLockSession,
) : ViewModel() {
    val pending: StateFlow<RevisitOpenRequest?> = inbox.pending

    sealed interface Action {
        data class Open(val url: String) : Action
        data class NeedAuth(val linkId: Int) : Action
        data object Deleted : Action
        data object ShowUnread : Action
    }

    // 앱을 새로 띄울 때는 요청 처리가 화면의 수집보다 먼저 끝날 수 있어 Channel 로 쌓아 둔다 (SharedFlow 는 구독 전 값을 버림)
    private val _actions = kotlinx.coroutines.channels.Channel<Action>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val actions = _actions.receiveAsFlow()

    fun handlePending() {
        val request = inbox.take() ?: return
        viewModelScope.launch {
            when (request) {
                RevisitOpenRequest.Unread -> {
                    inbox.requestTab(MainTab.UNREAD)
                    _actions.send(Action.ShowUnread)
                }
                is RevisitOpenRequest.Link -> {
                    // [열기] 버튼은 자동 닫힘(autoCancel)이 적용되지 않으므로 직접 지운다
                    notifier.cancelLink(request.linkId)
                    val link = urlDao.getById(request.linkId)
                    val url = link?.url
                    when {
                        link == null || url.isNullOrEmpty() -> _actions.send(Action.Deleted)
                        // 세션이 이미 열려 있으면(제목이 보이는 상태) 다시 묻지 않는다 (§7.2)
                        urlDao.isLocked(request.linkId) > 0 && !lockSession.isUnlocked.value ->
                            _actions.send(Action.NeedAuth(request.linkId))
                        else -> open(url)
                    }
                }
            }
        }
    }

    /** 인증을 통과한 잠금 링크 열기 (세션은 열지 않고 그 링크만) */
    fun openAfterAuth(linkId: Int) {
        viewModelScope.launch {
            val url = urlDao.getById(linkId)?.url
            if (url.isNullOrEmpty()) _actions.send(Action.Deleted) else open(url)
        }
    }

    private suspend fun open(url: String) {
        markAsReadUseCase(url)
        _actions.send(Action.Open(url))
    }
}

/**
 * 알림에서 연 요청 처리 (MainActivity 최상단). 잠금 카테고리 링크는 지문·화면 잠금으로 확인한 뒤 연다 (37번과 같은 흐름).
 * 화면 잠금이 없는 기기는 확인 없이 열고 알린다. 취소하면 메인에 머문다. 안 읽음 요청은 메인으로 돌아가 안 읽음 탭.
 */
@Composable
fun RevisitOpenHandler(viewModel: RevisitOpenViewModel, navController: NavHostController) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val pending by viewModel.pending.collectAsState()
    val auth = rememberLockAuthRequest("revisit_open_link") { result, payload ->
        val linkId = payload?.toIntOrNull() ?: return@rememberLockAuthRequest
        when (result) {
            LockAuthResult.Success -> viewModel.openAfterAuth(linkId)
            LockAuthResult.PassedWithoutCredential -> {
                Toast.makeText(context, R.string.lock_opened_without_auth, Toast.LENGTH_SHORT).show()
                viewModel.openAfterAuth(linkId)
            }
            LockAuthResult.Error -> Toast.makeText(context, R.string.lock_unavailable_retry, Toast.LENGTH_SHORT).show()
            LockAuthResult.NoCredential, LockAuthResult.Cancelled -> Unit
        }
    }
    LaunchedEffect(pending) {
        if (pending != null) viewModel.handlePending()
    }
    LaunchedEffect(Unit) {
        viewModel.actions.collect { action ->
            when (action) {
                is RevisitOpenViewModel.Action.Open -> context.openUrlInBrowser(action.url)
                is RevisitOpenViewModel.Action.NeedAuth -> auth.authenticate(
                    title = resources.getString(R.string.prompt_open_reminder_link),
                    allowWithoutCredential = true,
                    payload = action.linkId.toString(),
                )
                RevisitOpenViewModel.Action.Deleted ->
                    Toast.makeText(context, R.string.notif_link_deleted, Toast.LENGTH_SHORT).show()
                RevisitOpenViewModel.Action.ShowUnread ->
                    runCatching { navController.popBackStack(Navigation.MAIN_ROUTE_PATTERN, inclusive = false) }
            }
        }
    }
}
