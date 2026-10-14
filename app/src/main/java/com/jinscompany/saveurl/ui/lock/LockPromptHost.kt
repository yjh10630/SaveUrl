package com.jinscompany.saveurl.ui.lock

import android.os.Build
import android.view.WindowManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import kotlinx.coroutines.launch

/**
 * 잠금 카테고리 인증 프롬프트를 Activity 에 연결한다 (MainActivity, ShareActivity 가 같이 쓴다, 44번 §1.5).
 *
 * - [BiometricPrompt] 는 onCreate 에서 만든다: 회전·폴드 뒤 새 Activity 가 진행 중인 프롬프트의 결과를 받는다.
 * - broker 의 presenter 는 화면에 보이는 Activity 하나다: onStart 에 등록, onStop 에 해제.
 *   (예전처럼 onCreate/onDestroy 에 두면, 뒤에 MainActivity 가 살아 있는 채 공유 시트가 닫히며 해제할 때 presenter 가 비어
 *   MainActivity 로 돌아가도 인증이 뜨지 않는다)
 * - 프롬프트가 떠 있는 동안 onStop 이 와도(API 29 이하 기기 PIN 화면) 결과는 프롬프트 콜백 → broker 로 오므로 괜찮다.
 *
 * Activity 의 onCreate 에서 만든다 (super.onCreate 뒤).
 */
class LockPromptHost(activity: FragmentActivity, private val broker: LockPromptBroker) : DefaultLifecycleObserver {

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                broker.onPromptSucceeded()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                broker.onPromptError(errorCode)
            }
            // onAuthenticationFailed(지문 불일치)는 프롬프트가 계속 떠 있으므로 따로 처리하지 않는다
        }
    )

    private val show: (LockPromptBroker.Request) -> Unit = { request ->
        runCatching { prompt.authenticate(request.toPromptInfo()) }
            .onFailure { broker.onPromptError(LockPromptBroker.ERROR_SHOW_FAILED) }
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        broker.attach(show)
    }

    override fun onStop(owner: LifecycleOwner) {
        broker.detach(show)
    }
}

/**
 * 잠금 카테고리 세션이 열린 동안만 최근 앱 화면에서 내용을 가린다 (잠긴 링크가 화면에 있을 수 있는 유일한 때).
 * API 33+: 최근 앱 미리보기만 끈다(스크린샷은 됨). 32 이하: FLAG_SECURE (그동안 스크린샷도 막힘).
 * 공유 시트는 보내는 앱 작업 맨 위에 뜨므로 그 작업의 미리보기에도 같은 규칙을 건다 (44번 §1.5).
 */
fun FragmentActivity.hideFromRecentsWhileLockedCategoriesOpen(session: CategoryLockSession) {
    lifecycleScope.launch {
        session.isUnlocked.collect { unlocked ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setRecentsScreenshotEnabled(!unlocked)
            } else if (unlocked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
