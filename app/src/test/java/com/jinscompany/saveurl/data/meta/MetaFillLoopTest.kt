package com.jinscompany.saveurl.data.meta

import com.jinscompany.saveurl.data.room.MetaFillRow
import com.jinscompany.saveurl.domain.model.MetaState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 39번 독립 리뷰 R2: 인터넷이 안 될 때 가져온 링크가 한꺼번에 "가져오지 못함"으로 확정되지 않는다 */
class MetaFillLoopTest {

    private class Db(n: Int) {
        val rows = (1..n).map { MetaFillRow(id = it, url = "https://s$it.example.com", title = null, metaState = MetaState.PENDING, addDate = (1000 - it).toLong()) }
        val state = rows.associate { it.id to MetaState.PENDING }.toMutableMap()
        fun page(limit: Int, after: MetaFillRow?): List<MetaFillRow> =
            rows.filter { MetaState.isPending(state.getValue(it.id)) }
                .filter { after == null || it.addDate < after.addDate || (it.addDate == after.addDate && it.id < after.id) }
                .take(limit)
    }

    private fun loop(db: Db, validated: () -> Boolean = { true }, lastAttempt: Boolean = false, outcome: (MetaFillRow) -> FillOutcome) =
        MetaFillLoop(
            loadPage = db::page,
            fill = { row -> outcome(row).also { if (it != FillOutcome.UNREACHABLE) db.state[row.id] = if (it == FillOutcome.FILLED) MetaState.DONE else MetaState.FAILED } },
            markFailed = { db.state[it.id] = MetaState.FAILED },
            isStopped = { false },
            isPaused = { false },
            isNetworkValidated = validated,
            isLastAttempt = lastAttempt,
            page = 3,
            concurrency = 1,
        )

    @Test
    fun `인터넷이 끊기면 대기 상태로 두고 다시 시도한다`() = runTest {
        val db = Db(20)
        var online = true
        val result = loop(db, validated = { online }) { row ->
            if (row.id >= 4) { online = false; FillOutcome.UNREACHABLE } else FillOutcome.FILLED
        }.run()
        assertEquals(MetaFillLoop.Outcome.RETRY, result)
        assertEquals(3, db.state.values.count { it == MetaState.DONE })
        assertEquals(0, db.state.values.count { it == MetaState.FAILED })
        assertEquals(17, db.state.values.count { it == MetaState.PENDING })
    }

    @Test
    fun `연결이 확인되지 않은 Wi-Fi 에서는 시작하지 않는다`() = runTest {
        val db = Db(5)
        var calls = 0
        val result = loop(db, validated = { false }) { calls++; FillOutcome.FILLED }.run()
        assertEquals(MetaFillLoop.Outcome.RETRY, result)
        assertEquals(0, calls)
    }

    @Test
    fun `인터넷은 되는데 응답 없는 링크는 없어진 사이트로 보고 실패로 확정한다`() = runTest {
        val db = Db(10)
        val result = loop(db) { row -> if (row.id % 3 == 0) FillOutcome.UNREACHABLE else FillOutcome.FILLED }.run()
        assertEquals(MetaFillLoop.Outcome.DONE, result)
        assertEquals(3, db.state.values.count { it == MetaState.FAILED })
        assertTrue(db.state.values.none { it == MetaState.PENDING })
    }

    @Test
    fun `응답 없는 링크가 앞에 몰려 있어도 뒤의 링크를 막지 않는다`() = runTest {
        val db = Db(12)
        // 1~7 은 응답 없음(없어진 사이트), 연결은 확인됨 → 계속 진행해 8~12 를 채운다
        val result = loop(db) { row -> if (row.id <= 7) FillOutcome.UNREACHABLE else FillOutcome.FILLED }.run()
        assertEquals(MetaFillLoop.Outcome.DONE, result)
        assertEquals(5, db.state.values.count { it == MetaState.DONE })
        assertEquals(7, db.state.values.count { it == MetaState.FAILED })
    }

    @Test
    fun `하나도 응답이 없으면 다시 시도하고 마지막 시도에서는 실패로 확정한다`() = runTest {
        val db = Db(6)
        assertEquals(MetaFillLoop.Outcome.RETRY, loop(db) { FillOutcome.UNREACHABLE }.run())
        assertEquals(6, db.state.values.count { it == MetaState.PENDING })
        assertEquals(MetaFillLoop.Outcome.DONE, loop(db, lastAttempt = true) { FillOutcome.UNREACHABLE }.run())
        assertEquals(6, db.state.values.count { it == MetaState.FAILED })
    }
}
