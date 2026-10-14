package com.jinscompany.saveurl.data.meta

import com.jinscompany.saveurl.data.room.MetaFillRow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * [LinkMetaFillWorker] 한 번 실행의 순서 (Android 의존성 없음, JVM 테스트).
 *
 * - 최신 날짜부터 [page] 개씩 이어 읽고 동시 [concurrency] 개로 채운다.
 * - 서버 응답이 없는 링크([FillOutcome.UNREACHABLE])는 바로 실패로 두지 않는다 (39번 독립 리뷰 R2).
 *   인터넷이 잠깐 안 되는 것과 없어진 사이트를 구분해야 해서:
 *   - 연속 [unreachableStreakLimit] 개 응답이 없고 지금 네트워크의 인터넷 연결이 확인되지 않으면 [Outcome.RETRY]
 *     (응답 없던 링크는 대기 상태 그대로 → WorkManager 가 백오프 뒤, 연결 조건이 맞을 때 다시 실행)
 *   - 실행이 끝났을 때 연결이 확인되면 응답 없던 링크는 없어진 사이트로 보고 실패로 확정한다.
 *     단, 이번 실행에서 응답을 하나도 못 받았고 응답 없는 링크가 [unreachableStreakLimit] 개 이상이면
 *     (확인된 연결인데도 전부 안 됨 = DNS 장애 등) 마지막 시도([isLastAttempt])가 아닌 한 [Outcome.RETRY]
 * - 인터넷 연결이 확인되지 않은 네트워크(로그인 페이지가 뜨는 Wi‑Fi 등)면 시작하지 않고 [Outcome.RETRY]
 */
class MetaFillLoop(
    private val loadPage: suspend (limit: Int, after: MetaFillRow?) -> List<MetaFillRow>,
    private val fill: suspend (MetaFillRow) -> FillOutcome,
    private val markFailed: suspend (MetaFillRow) -> Unit,
    private val isStopped: () -> Boolean,
    private val isPaused: suspend () -> Boolean,
    private val isNetworkValidated: () -> Boolean,
    /** 다시 시도 횟수가 상한에 닿음: 응답 없는 링크를 더 미루지 않고 실패로 확정한다 (상태 카드가 끝나지 않는 일 방지) */
    private val isLastAttempt: Boolean = false,
    private val page: Int = 50,
    private val concurrency: Int = 2,
    private val unreachableStreakLimit: Int = 5,
) {
    enum class Outcome { DONE, RETRY }

    suspend fun run(): Outcome {
        if (!isNetworkValidated()) return Outcome.RETRY
        val semaphore = Semaphore(concurrency)
        val unreachable = ArrayList<MetaFillRow>()
        var reached = 0
        var streak = 0
        var cursor: MetaFillRow? = null
        while (!isStopped()) {
            if (isPaused()) return Outcome.DONE
            val rows = loadPage(page, cursor)
            if (rows.isEmpty()) break
            cursor = rows.last()
            val results = coroutineScope {
                rows.map { row ->
                    async {
                        semaphore.withPermit {
                            if (isStopped()) null
                            else row to runCatching { fill(row) }.getOrElse {
                                // 처리하다 실패한 행이 대기열 앞에 남아 다음 실행을 막지 않게 실패로 둔다 (열면 다시 시도)
                                runCatching { markFailed(row) }
                                FillOutcome.FAILED
                            }
                        }
                    }
                }.awaitAll()
            }
            for (result in results) {
                val (row, outcome) = result ?: continue
                if (outcome == FillOutcome.UNREACHABLE) {
                    unreachable.add(row)
                    streak++
                } else {
                    reached++
                    streak = 0
                }
            }
            if (streak >= unreachableStreakLimit && !isNetworkValidated()) return Outcome.RETRY
        }
        if (isStopped()) return Outcome.DONE
        if (unreachable.isEmpty()) return Outcome.DONE
        if (!isLastAttempt) {
            if (!isNetworkValidated()) return Outcome.RETRY
            if (reached == 0 && unreachable.size >= unreachableStreakLimit) return Outcome.RETRY
        }
        unreachable.forEach { runCatching { markFailed(it) } }
        return Outcome.DONE
    }
}
