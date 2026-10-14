package com.jinscompany.saveurl.data.meta

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 39번 독립 리뷰 R8: 보이는 카드 우선 채우기는 최근 몇 개만 기다리고, 화면을 벗어나거나 멈춤이면 채우지 않는다 */
@OptIn(ExperimentalCoroutinesApi::class)
class PriorityFillQueueTest {

    @Test
    fun `목록을 빨리 훑으면 최근 카드 몇 개만 남는다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val filled = ArrayList<Int>()
        val queue = PriorityFillQueue(TestScope(UnconfinedTestDispatcher(testScheduler)), { false }, { id, _ ->
            if (id == 1) gate.await()
            filled.add(id)
        }, capacity = 3)
        (1..10).forEach { queue.add(it, explicit = false) }
        // 1 은 채우는 중, 나머지는 최근 3개만 (먼저 채울 것부터)
        assertEquals(listOf(10, 9, 8), queue.pending())
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(1, 10, 9, 8), filled)
    }

    @Test
    fun `화면에서 사라진 카드는 채우지 않고 채우는 중이면 멈춘다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val filled = ArrayList<Int>()
        val queue = PriorityFillQueue(TestScope(UnconfinedTestDispatcher(testScheduler)), { false }, { id, _ ->
            gate.await()
            filled.add(id)
        })
        queue.add(1, explicit = false)
        queue.add(2, explicit = false)
        queue.cancel(2)
        queue.cancel(1)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(filled.isEmpty())
    }

    @Test
    fun `채우기를 멈춰 두면 보이는 카드는 건너뛰고 직접 연 링크만 채운다`() = runTest {
        val filled = ArrayList<Int>()
        val queue = PriorityFillQueue(TestScope(UnconfinedTestDispatcher(testScheduler)), { true }, { id, _ -> filled.add(id) })
        queue.add(1, explicit = false)
        queue.add(2, explicit = true)
        advanceUntilIdle()
        assertEquals(listOf(2), filled)
    }

    @Test
    fun `채우기 오류가 나도 다음 카드를 이어서 채운다`() = runTest {
        val filled = ArrayList<Int>()
        val queue = PriorityFillQueue(TestScope(UnconfinedTestDispatcher(testScheduler)), { false }, { id, _ ->
            if (id == 1) error("parser bug")
            filled.add(id)
        })
        queue.add(1, explicit = false)
        queue.add(2, explicit = false)
        advanceUntilIdle()
        assertEquals(listOf(2), filled)
    }
}
