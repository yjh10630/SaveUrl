package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.importer.FakeImportRepository
import com.jinscompany.saveurl.domain.importer.FakeMetaFillScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 예전 CSV 가져오기 줄 (원격 스위치 꺼짐) — 31번 §2.9 의 세 문제가 고쳐졌는지:
 * 1) 같은 백업을 두 번 가져와도 중복이 생기지 않는다 2) 카테고리를 만든다 3) 묶음으로 남아 되돌릴 수 있다
 */
class ImportCsvUseCaseTest {

    private fun backup(): ByteArray =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("import/savelink_backup.csv")).readBytes()

    @Test
    fun `두 번 가져오면 두 번째는 모두 건너뛴다`() = runTest {
        val repo = FakeImportRepository()
        val scheduler = FakeMetaFillScheduler()
        val useCase = ImportCsvUseCase(repo, scheduler)

        val first = useCase("backup.csv", null) { backup().inputStream() }
        assertEquals(CsvImportResult(inserted = 3, skipped = 0), first)
        val second = useCase("backup.csv", null) { backup().inputStream() }
        assertEquals(CsvImportResult(inserted = 0, skipped = 3), second)
        assertEquals(3, repo.links.size)
        // 두 번째는 넣을 것이 없어 저장 요청 자체를 하지 않는다
        assertEquals(1, repo.committed.size)
    }

    @Test
    fun `카테고리를 만들고 잠금이었던 카테고리는 잠근다`() = runTest {
        val repo = FakeImportRepository()
        ImportCsvUseCase(repo, FakeMetaFillScheduler())("backup.csv", null) { backup().inputStream() }
        assertEquals(setOf("요리", "쇼핑"), repo.categories)
        assertEquals(setOf("쇼핑"), repo.lockedCategories)
    }

    @Test
    fun `묶음으로 남아 되돌리면 넣은 것만 지워진다`() = runTest {
        val repo = FakeImportRepository(initialKeys = setOf("https://keep.example.com"))
        ImportCsvUseCase(repo, FakeMetaFillScheduler())("backup.csv", null) { backup().inputStream() }
        val batchId = repo.batches.keys.single()
        val undo = repo.undo(batchId)
        assertEquals(3, undo.deletedLinks)
        assertTrue(repo.links.keys == setOf("https://keep.example.com"))
    }
}
