package com.jinscompany.saveurl.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import java.util.UUID

/** 잠금 카테고리 세션 (MainActivity 가 Hilt 싱글톤을 넣어 준다) */
val LocalCategoryLockSession = staticCompositionLocalOf<CategoryLockSession> {
    error("CategoryLockSession 이 제공되지 않았습니다 (MainActivity)")
}

/** 앱 전용 PIN 없이 지문·얼굴 또는 휴대폰 화면 잠금으로만 확인한다 (37번 기획서 3장) */
private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** 휴대폰 화면 잠금(PIN·패턴·비밀번호)이 있는지. 없으면 지문도 등록할 수 없으므로 인증 수단이 없다 */
fun Context.hasDeviceCredential(): Boolean =
    getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

sealed interface LockAuthResult {
    /** 인증 성공 */
    data object Success : LockAuthResult
    /** 화면 잠금이 없어 확인 없이 통과 (열기·풀기에서만) */
    data object PassedWithoutCredential : LockAuthResult
    /** 화면 잠금이 없어 진행할 수 없음 (잠그기) → 화면 잠금 안내 시트 */
    data object NoCredential : LockAuthResult
    /** 사용자가 취소 (안내하지 않음) */
    data object Cancelled : LockAuthResult
    /** 잠시 잠김 등 그 밖의 오류 */
    data object Error : LockAuthResult
}

/** 인증 프롬프트를 띄우는 중계 (MainActivity 가 싱글톤을 넣어 준다) */
val LocalLockPromptBroker = staticCompositionLocalOf<LockPromptBroker> {
    error("LockPromptBroker 가 제공되지 않았습니다 (MainActivity)")
}

/** MainActivity 가 [LockPromptBroker.Request] 를 받아 프롬프트를 띄울 때 쓰는 설정 */
fun LockPromptBroker.Request.toPromptInfo(): BiometricPrompt.PromptInfo =
    BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .apply { if (subtitle != null) setSubtitle(subtitle) }
        .setAllowedAuthenticators(AUTHENTICATORS)
        .setConfirmationRequired(false)
        .build()

/**
 * 한 화면(호출 위치)의 인증 요청. 결과는 [rememberLockAuthRequest] 의 onResult 로, 그때의 컴포지션에서 받는다.
 * 화면 잠금이 없으면 프롬프트 없이 [LockAuthResult.PassedWithoutCredential]/[LockAuthResult.NoCredential] 로 끝난다.
 */
class LockAuthRequester internal constructor(
    private val key: String,
    private val broker: LockPromptBroker,
    private val hasDeviceCredential: () -> Boolean,
) {
    /**
     * [allowWithoutCredential] = true 면 화면 잠금이 없을 때 확인 없이 통과시킨다
     * (잠긴 링크가 영영 갇히지 않게. 37번 기획서 3.2). 잠그기는 false 로 불러 안내 시트를 띄운다.
     * [payload] 는 결과와 함께 돌려받는 값 (예: 카테고리 이름). Activity 가 다시 만들어져도 남는다.
     */
    fun authenticate(
        title: String,
        subtitle: String? = null,
        allowWithoutCredential: Boolean,
        payload: String? = null,
    ) {
        val request = LockPromptBroker.Request(key, payload, title, subtitle, allowWithoutCredential)
        if (!hasDeviceCredential()) {
            broker.post(request, if (allowWithoutCredential) LockAuthResult.PassedWithoutCredential else LockAuthResult.NoCredential)
        } else {
            broker.request(request)
        }
    }
}

/**
 * 인증 요청 만들기. 프롬프트가 떠 있는 동안 회전·폴드로 Activity 가 다시 만들어지면, 결과는 예전 화면이 아니라
 * 다시 만들어진 화면의 [onResult] 로 온다 (키를 rememberSaveable 로 보존. 38번 리뷰 1번).
 */
@Composable
fun rememberLockAuthRequest(onResult: (result: LockAuthResult, payload: String?) -> Unit): LockAuthRequester {
    val context = LocalContext.current
    val broker = LocalLockPromptBroker.current
    val key = rememberSaveable { UUID.randomUUID().toString() }
    val currentOnResult by rememberUpdatedState(onResult)
    LaunchedEffect(broker, key) {
        broker.deliveries.collect { deliveries ->
            deliveries.filter { it.key == key }.forEach { delivery ->
                broker.consume(delivery)
                currentOnResult(delivery.result, delivery.payload)
            }
        }
    }
    return remember(context, broker, key) { LockAuthRequester(key, broker) { context.hasDeviceCredential() } }
}

/**
 * 잠금 카테고리 세션 열기. 이미 열려 있으면 바로 onOpened.
 * 화면 잠금이 없으면 확인 없이 열고 토스트로 알린다. 오류는 토스트, 취소는 아무것도 하지 않는다.
 * 인증 중에 Activity 가 다시 만들어지면 세션은 열리지만 onOpened(예전 화면의 동작)는 부르지 않는다.
 */
@Composable
fun rememberOpenLockedCategories(): (titleRes: Int, onOpened: () -> Unit) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val session = LocalCategoryLockSession.current
    val pendingOnOpened = remember { arrayOfNulls<() -> Unit>(1) }
    val requester = rememberLockAuthRequest { result, _ ->
        val onOpened = pendingOnOpened[0]
        pendingOnOpened[0] = null
        when (result) {
            LockAuthResult.Success -> {
                session.unlock()
                onOpened?.invoke()
            }
            LockAuthResult.PassedWithoutCredential -> {
                session.unlock()
                Toast.makeText(context, R.string.lock_opened_without_auth, Toast.LENGTH_SHORT).show()
                onOpened?.invoke()
            }
            LockAuthResult.Error -> Toast.makeText(context, R.string.lock_unavailable_retry, Toast.LENGTH_SHORT).show()
            LockAuthResult.NoCredential, LockAuthResult.Cancelled -> Unit
        }
    }
    return remember(resources, session, requester) {
        { titleRes: Int, onOpened: () -> Unit ->
            if (session.isUnlocked.value) {
                onOpened()
            } else {
                pendingOnOpened[0] = onOpened
                requester.authenticate(title = resources.getString(titleRes), allowWithoutCredential = true)
            }
        }
    }
}

/** "화면 잠금 설정하기": API 30+ 화면 잠금·생체 등록 화면, 안 되면 보안 설정, 그것도 없으면 설정 */
fun Context.openDeviceLockSettings() {
    val candidates = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, AUTHENTICATORS))
        }
        add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }
    for (intent in candidates) {
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
}
