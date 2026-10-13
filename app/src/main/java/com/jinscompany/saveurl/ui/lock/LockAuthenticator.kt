package com.jinscompany.saveurl.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.lock.CategoryLockSession

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

/**
 * BiometricPrompt 래퍼. [allowWithoutCredential] = true 면 화면 잠금이 없을 때 확인 없이 통과시킨다
 * (잠긴 링크가 영영 갇히지 않게. 37번 기획서 3.2). 잠그기는 false 로 불러 안내 시트를 띄운다.
 */
class LockAuthenticator(private val activity: FragmentActivity?) {

    fun authenticate(
        title: String,
        subtitle: String? = null,
        allowWithoutCredential: Boolean,
        onResult: (LockAuthResult) -> Unit,
    ) {
        val activity = activity ?: return onResult(LockAuthResult.Error)
        if (!activity.hasDeviceCredential()) {
            onResult(if (allowWithoutCredential) LockAuthResult.PassedWithoutCredential else LockAuthResult.NoCredential)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(LockAuthResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED -> LockAuthResult.Cancelled
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                                if (allowWithoutCredential) LockAuthResult.PassedWithoutCredential else LockAuthResult.NoCredential
                            else -> LockAuthResult.Error
                        }
                    )
                }
                // onAuthenticationFailed(지문 불일치)는 프롬프트가 계속 떠 있으므로 따로 처리하지 않는다
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { onResult(LockAuthResult.Error) }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun rememberLockAuthenticator(): LockAuthenticator {
    val context = LocalContext.current
    return remember(context) { LockAuthenticator(context.findFragmentActivity()) }
}

/**
 * 잠금 카테고리 세션 열기. 이미 열려 있으면 바로 [onOpened].
 * 화면 잠금이 없으면 확인 없이 열고 토스트로 알린다. 오류는 토스트, 취소는 아무것도 하지 않는다.
 */
@Composable
fun rememberOpenLockedCategories(): (titleRes: Int, onOpened: () -> Unit) -> Unit {
    val context = LocalContext.current
    val session = LocalCategoryLockSession.current
    val authenticator = rememberLockAuthenticator()
    return remember(context, session, authenticator) {
        { titleRes: Int, onOpened: () -> Unit ->
            if (session.isUnlocked.value) {
                onOpened()
            } else {
                authenticator.authenticate(title = context.getString(titleRes), allowWithoutCredential = true) { result ->
                    when (result) {
                        LockAuthResult.Success -> {
                            session.unlock()
                            onOpened()
                        }
                        LockAuthResult.PassedWithoutCredential -> {
                            session.unlock()
                            Toast.makeText(context, R.string.lock_opened_without_auth, Toast.LENGTH_SHORT).show()
                            onOpened()
                        }
                        LockAuthResult.Error -> Toast.makeText(context, R.string.lock_unavailable_retry, Toast.LENGTH_SHORT).show()
                        LockAuthResult.NoCredential, LockAuthResult.Cancelled -> Unit
                    }
                }
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
