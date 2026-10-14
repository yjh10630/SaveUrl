package com.jinscompany.saveurl.data.meta

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 화면에 보이는 카드·연 링크를 먼저 채우는 줄 (39번 독립 리뷰 R8). Android 의존성 없음 (JVM 테스트).
 *
 * - 한 번에 하나씩, 가장 최근에 들어온 것부터 (지금 화면에 있는 카드).
 * - 줄은 [capacity] 개까지. 넘치면 가장 오래 기다린 것을 버린다 (목록을 빨리 훑어도 요청이 쌓이지 않게).
 * - 카드가 화면에서 사라지면 [cancel]: 줄에서 빼고, 채우는 중이면 멈춘다.
 * - 미리보기 채우기를 멈춰 두었으면([isPaused]) 보이는 카드는 채우지 않는다. 사용자가 링크를 직접 연 경우([explicit])만 채운다.
 */
class PriorityFillQueue(
    private val scope: CoroutineScope,
    private val isPaused: suspend () -> Boolean,
    private val fill: suspend (linkId: Int, explicit: Boolean) -> Unit,
    private val capacity: Int = 5,
) {
    private data class Entry(val id: Int, val explicit: Boolean)

    private val lock = Any()
    private val queue = ArrayDeque<Entry>()
    private var drainJob: Job? = null
    private var running: Pair<Entry, Job>? = null

    fun add(linkId: Int, explicit: Boolean) {
        synchronized(lock) {
            if (running?.first?.id == linkId) return
            val existing = queue.indexOfFirst { it.id == linkId }
            if (existing >= 0) {
                // 다시 보이면 맨 뒤(가장 먼저 채울 자리)로. 직접 연 요청은 유지
                val old = queue.removeAt(existing)
                queue.addLast(Entry(linkId, explicit || old.explicit))
            } else {
                queue.addLast(Entry(linkId, explicit))
            }
            while (queue.size > capacity) queue.removeFirst()
            if (drainJob?.isActive != true) drainJob = scope.launch { drain() }
        }
    }

    fun cancel(linkId: Int) {
        synchronized(lock) {
            queue.removeAll { it.id == linkId && !it.explicit }
            running?.takeIf { it.first.id == linkId && !it.first.explicit }?.second?.cancel()
        }
    }

    /** 테스트용: 기다리는 링크 (먼저 채울 것부터) */
    internal fun pending(): List<Int> = synchronized(lock) { queue.reversed().map { it.id } }

    private suspend fun drain() {
        while (true) {
            val next = synchronized(lock) {
                val e = queue.removeLastOrNull()
                if (e == null) drainJob = null
                e
            } ?: return
            if (!next.explicit && isPaused()) continue
            // 실패는 삼킨다 (앱 범위 코루틴에서 예외가 앱을 죽이지 않게). 결과는 DB 상태로만 남는다
            val job = scope.launch { runCatching { fill(next.id, next.explicit) } }
            synchronized(lock) { running = next to job }
            try {
                job.join()
            } catch (e: CancellationException) {
                job.cancel()
                synchronized(lock) { running = null; drainJob = null }
                throw e
            }
            synchronized(lock) { running = null }
        }
    }
}
