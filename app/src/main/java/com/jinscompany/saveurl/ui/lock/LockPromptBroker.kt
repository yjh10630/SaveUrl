package com.jinscompany.saveurl.ui.lock

import android.os.SystemClock
import androidx.biometric.BiometricPrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 인증 요청과 결과를 Activity 밖에서 이어 주는 중계 (38번 리뷰 1번).
 *
 * 프롬프트가 떠 있는 동안 회전·폴드로 Activity 가 다시 만들어져도 프롬프트는 남는다. 그래서
 * - BiometricPrompt 는 [MainActivity] 가 onCreate 에서 한 번 만들고 [presenter] 로 등록한다 (새 Activity 가 콜백을 이어받음).
 * - 결과는 요청 화면의 람다로 바로 부르지 않고 [deliveries] 에 남긴다. 화면은 호출 위치마다 정한 고정 키로 자기 결과를 꺼내 쓴다.
 *   (예전 컴포지션의 런처·상태를 건드리지 않고 지금 컴포지션에서 처리. 회전으로 한 화면 ↔ 2분할이 바뀌면 컴포지션 구조가
 *   달라져 rememberSaveable 값이 돌아오지 않으므로 키는 고정 문자열을 쓴다)
 * - 받을 화면이 없어 남은 결과는 [MAX_DELIVERY_AGE_MS] 가 지나면 버린다 (나중에 그 화면을 열었을 때 뒤늦게 적용되지 않게)
 */
@Singleton
class LockPromptBroker(private val elapsedRealtime: () -> Long) {

    @Inject constructor() : this({ SystemClock.elapsedRealtime() })

    private var nextId = 0L

    data class Request(
        val key: String,
        val payload: String?,
        val title: String,
        val subtitle: String?,
        /** 화면 잠금이 없을 때 확인 없이 통과시킬지 (열기·풀기 = true, 잠그기 = false) */
        val allowWithoutCredential: Boolean,
    )

    data class Delivery(
        val key: String,
        val payload: String?,
        val result: LockAuthResult,
        val id: Long,
        val postedAt: Long,
    )

    /** 지금 프롬프트를 띄울 수 있는 Activity (MainActivity 가 onCreate 에서 등록, onDestroy 에서 해제) */
    private var presenter: ((Request) -> Unit)? = null

    /** 프롬프트가 떠 있는 요청. 결과가 오면 이 요청의 키로 [deliveries] 에 넣는다 */
    private var inFlight: Request? = null

    private val _deliveries = MutableStateFlow<List<Delivery>>(emptyList())
    val deliveries: StateFlow<List<Delivery>> = _deliveries.asStateFlow()

    fun attach(presenter: (Request) -> Unit) {
        this.presenter = presenter
    }

    fun detach(presenter: (Request) -> Unit) {
        if (this.presenter === presenter) this.presenter = null
    }

    fun request(request: Request) {
        // 같은 키에 남아 있던 예전 결과는 버린다
        _deliveries.update { list -> list.filterNot { it.key == request.key } }
        val show = presenter
        if (show == null) {
            post(request, LockAuthResult.Error)
            return
        }
        inFlight = request
        show(request)
    }

    /** 프롬프트 콜백(성공). 진행 중인 요청이 없으면(프로세스가 다시 시작된 뒤 등) 버린다 */
    fun onPromptSucceeded() {
        val request = inFlight ?: return
        inFlight = null
        post(request, LockAuthResult.Success)
    }

    /** 프롬프트 콜백(오류·취소). [errorCode] 는 BiometricPrompt.ERROR_* (프롬프트를 띄우지 못하면 [ERROR_SHOW_FAILED]) */
    fun onPromptError(errorCode: Int) {
        val request = inFlight ?: return
        inFlight = null
        post(request, mapError(errorCode, request.allowWithoutCredential))
    }

    /** 화면 잠금이 없어 프롬프트 없이 끝나는 경우 등, 같은 경로로 결과를 넘긴다 */
    fun post(request: Request, result: LockAuthResult) {
        val delivery = Delivery(request.key, request.payload, result, nextId++, elapsedRealtime())
        _deliveries.update { it + delivery }
    }

    /** [delivery] 를 목록에서 뺀다. 너무 오래된 결과면 false (적용하지 않음) */
    fun consume(delivery: Delivery): Boolean {
        _deliveries.update { list -> list.filterNot { it.id == delivery.id } }
        return elapsedRealtime() - delivery.postedAt <= MAX_DELIVERY_AGE_MS
    }

    companion object {
        const val ERROR_SHOW_FAILED = -1
        const val MAX_DELIVERY_AGE_MS = 30_000L

        fun mapError(errorCode: Int, allowWithoutCredential: Boolean): LockAuthResult = when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED -> LockAuthResult.Cancelled
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                if (allowWithoutCredential) LockAuthResult.PassedWithoutCredential else LockAuthResult.NoCredential
            else -> LockAuthResult.Error
        }
    }
}
