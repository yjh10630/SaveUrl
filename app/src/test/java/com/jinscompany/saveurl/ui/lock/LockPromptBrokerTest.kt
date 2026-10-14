package com.jinscompany.saveurl.ui.lock

import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPromptBrokerTest {

    private var now = 1_000L
    private val broker = LockPromptBroker { now }
    private val shown = mutableListOf<LockPromptBroker.Request>()
    private val presenter: (LockPromptBroker.Request) -> Unit = { shown += it }

    private fun request(key: String = "k", payload: String? = null, allow: Boolean = true) =
        LockPromptBroker.Request(key, payload, title = "t", subtitle = null, allowWithoutCredential = allow)

    @Test
    fun `결과는 요청 키와 payload 로 남고 꺼내면 사라진다`() {
        broker.attach(presenter)
        broker.request(request(key = "a", payload = "쇼핑"))
        broker.onPromptSucceeded()

        val delivery = broker.deliveries.value.single()
        assertEquals("a", delivery.key)
        assertEquals("쇼핑", delivery.payload)
        assertEquals(LockAuthResult.Success, delivery.result)
        assertTrue(broker.consume(delivery))
        assertTrue(broker.deliveries.value.isEmpty())
    }

    @Test
    fun `Activity 가 다시 만들어져도 진행 중인 요청의 결과를 새 presenter 쪽에서 받는다`() {
        broker.attach(presenter)
        broker.request(request(key = "csv"))
        // 회전: 예전 Activity onDestroy → 새 Activity onCreate
        broker.detach(presenter)
        val newPresenter: (LockPromptBroker.Request) -> Unit = {}
        broker.attach(newPresenter)
        broker.onPromptSucceeded()

        assertEquals("csv", broker.deliveries.value.single().key)
    }

    @Test
    fun `예전 presenter 의 detach 는 새 presenter 를 지우지 않는다`() {
        val newPresenter: (LockPromptBroker.Request) -> Unit = { shown += it }
        broker.attach(newPresenter)
        broker.detach(presenter)
        broker.request(request())
        assertEquals(1, shown.size)
    }

    @Test
    fun `presenter 가 없으면 오류로 끝난다`() {
        broker.request(request())
        assertEquals(LockAuthResult.Error, broker.deliveries.value.single().result)
    }

    @Test
    fun `진행 중인 요청 없이 온 콜백은 버린다`() {
        broker.onPromptSucceeded()
        broker.onPromptError(BiometricPrompt.ERROR_CANCELED)
        assertTrue(broker.deliveries.value.isEmpty())
    }

    @Test
    fun `오류 코드 변환 - 취소, 화면 잠금 없음, 그 밖의 오류`() {
        assertEquals(LockAuthResult.Cancelled, LockPromptBroker.mapError(BiometricPrompt.ERROR_USER_CANCELED, true))
        assertEquals(LockAuthResult.Cancelled, LockPromptBroker.mapError(BiometricPrompt.ERROR_NEGATIVE_BUTTON, true))
        assertEquals(LockAuthResult.Cancelled, LockPromptBroker.mapError(BiometricPrompt.ERROR_CANCELED, false))
        assertEquals(
            LockAuthResult.PassedWithoutCredential,
            LockPromptBroker.mapError(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL, allowWithoutCredential = true)
        )
        assertEquals(
            LockAuthResult.NoCredential,
            LockPromptBroker.mapError(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL, allowWithoutCredential = false)
        )
        assertEquals(LockAuthResult.Error, LockPromptBroker.mapError(BiometricPrompt.ERROR_LOCKOUT, true))
        assertEquals(LockAuthResult.Error, LockPromptBroker.mapError(LockPromptBroker.ERROR_SHOW_FAILED, true))
    }

    @Test
    fun `오류 결과는 요청의 allowWithoutCredential 을 따른다`() {
        broker.attach(presenter)
        broker.request(request(allow = false))
        broker.onPromptError(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL)
        assertEquals(LockAuthResult.NoCredential, broker.deliveries.value.single().result)
    }

    @Test
    fun `받을 화면이 없어 오래 남은 결과는 꺼내도 적용하지 않는다`() {
        broker.attach(presenter)
        broker.request(request(key = "edit_category_lock", payload = "쇼핑"))
        broker.onPromptSucceeded()
        now += LockPromptBroker.MAX_DELIVERY_AGE_MS + 1

        val delivery = broker.deliveries.value.single()
        assertFalse(broker.consume(delivery))
        assertTrue(broker.deliveries.value.isEmpty())
    }

    @Test
    fun `같은 키로 다시 요청하면 남아 있던 예전 결과는 버린다`() {
        broker.attach(presenter)
        broker.request(request(key = "a"))
        broker.onPromptError(BiometricPrompt.ERROR_USER_CANCELED)
        broker.request(request(key = "a"))
        assertTrue(broker.deliveries.value.isEmpty())
        broker.onPromptSucceeded()
        assertEquals(LockAuthResult.Success, broker.deliveries.value.single().result)
    }
}
