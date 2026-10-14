package com.jinscompany.saveurl.domain.importer

import com.jinscompany.saveurl.domain.model.MetaState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportCommitRunnerTest {

    private fun request(n: Int) = ImportCommitRequest(
        format = ImportFormat.TEXT,
        label = "memo.txt",
        records = (1..n).map {
            ImportRecord(
                url = "https://a.com/$it", normalizedUrl = "https://a.com/$it", title = null, imageUrl = null, siteName = null,
                description = null, addDate = 1L, category = "새 폴더", tags = emptyList(), isBookmark = false, isRead = false,
                metaState = MetaState.PENDING,
            )
        },
        lockCategories = emptySet(),
        skippedBefore = 0,
    )

    @Test
    fun `끝까지 넣으면 완료, 채우기를 예약한다`() = runTest {
        val repo = FakeImportRepository()
        val scheduler = FakeMetaFillScheduler()
        val runner = ImportCommitRunner(repo, scheduler, TestScope(UnconfinedTestDispatcher(testScheduler)))
        runner.start(request(5))
        advanceUntilIdle()
        val state = runner.state.value as CommitState.Done
        assertEquals(5, state.result.inserted)
        assertEquals(1, scheduler.scheduled)
    }

    @Test
    fun `저장 공간 부족 - 넣은 것을 모두 되돌리고 실패`() = runTest {
        val repo = FakeImportRepository(failAfterChunks = 1)
        val runner = ImportCommitRunner(repo, FakeMetaFillScheduler(), TestScope(UnconfinedTestDispatcher(testScheduler)))
        runner.start(request(5))
        advanceUntilIdle()
        assertEquals(CommitState.Failed(ImportError.STORAGE_FULL), runner.state.value)
        assertEquals(1, repo.rollbacks)
        // DB 가 가져오기 전과 같다 (링크·새 카테고리 없음)
        assertTrue(repo.links.isEmpty())
        assertTrue(repo.categories.isEmpty())
    }

    /** 39번 독립 리뷰 R5: 저장 공간이 꽉 차 되돌리기도 실패해도 앱이 죽지 않고, "넣는 중"으로 남아 다음 가져오기를 막지 않는다 */
    @Test
    fun `되돌리기까지 실패해도 실패 상태로 끝나고 다음 가져오기를 받는다`() = runTest {
        val repo = FakeImportRepository(failAfterChunks = 1, failRollback = true)
        val runner = ImportCommitRunner(repo, FakeMetaFillScheduler(), TestScope(UnconfinedTestDispatcher(testScheduler)))
        runner.start(request(5))
        advanceUntilIdle()
        assertEquals(CommitState.Failed(ImportError.STORAGE_FULL), runner.state.value)
        assertEquals(null, runner.runningBatchId)

        runner.reset()
        runner.start(request(1))
        advanceUntilIdle()
        assertTrue(runner.state.value is CommitState.Done)
    }

    /** 39번 독립 리뷰 R7: 앱 시작 정리가 끝난 뒤에 넣기를 시작한다 */
    @Test
    fun `시작 정리는 한 번만 돌고 넣기보다 먼저 끝난다`() = runTest {
        val repo = FakeImportRepository()
        val runner = ImportCommitRunner(repo, FakeMetaFillScheduler(), TestScope(UnconfinedTestDispatcher(testScheduler)))
        runner.cleanupInterrupted()
        runner.cleanupInterrupted()
        runner.start(request(2))
        advanceUntilIdle()
        assertEquals(1, repo.cleanups)
        assertTrue(runner.state.value is CommitState.Done)
    }
}
