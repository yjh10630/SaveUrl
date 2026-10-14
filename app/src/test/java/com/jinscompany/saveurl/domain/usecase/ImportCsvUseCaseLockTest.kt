package com.jinscompany.saveurl.domain.usecase

import android.content.Context
import android.net.Uri
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.utils.CsvBackupManager
import com.jinscompany.saveurl.utils.CsvBackupManager.ImportedRow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** CSV 가져오기: 백업 때 잠금 카테고리였던 카테고리는 잠그고(없으면 잠금 카테고리로 만듦), 그 항목 수를 센다 */
class ImportCsvUseCaseLockTest {

    private val context: Context = mockk()
    private val uri: Uri = mockk()
    private val urlRepository: UrlRepository = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk(relaxed = true)
    private val useCase = ImportCsvUseCase(context, urlRepository, categoryRepository)

    @Before
    fun setUp() = mockkObject(CsvBackupManager)

    @After
    fun tearDown() = unmockkObject(CsvBackupManager)

    @Test
    fun `잠금 열이 true 인 카테고리만 잠그고 그 항목 수를 센다`() = runTest {
        coEvery { CsvBackupManager.import(context, uri) } returns listOf(
            ImportedRow(UrlData(id = 7, url = "a", category = "쇼핑"), categoryLocked = true),
            ImportedRow(UrlData(id = 8, url = "b", category = "쇼핑"), categoryLocked = true),
            ImportedRow(UrlData(id = 9, url = "c", category = "개발"), categoryLocked = false),
        )

        val result = useCase(uri)

        assertEquals(CsvImportResult(count = 3, lockedCount = 2), result)
        coVerify(exactly = 1) { categoryRepository.lockOrCreate(setOf("쇼핑")) }
        // 새 행으로 넣는다 (id = 0)
        coVerify { urlRepository.saveUrlDataList(match { list -> list.all { it.id == 0 } && list.size == 3 }) }
    }

    @Test
    fun `잠금 열이 모두 false 면 잠그지 않는다`() = runTest {
        coEvery { CsvBackupManager.import(context, uri) } returns listOf(
            ImportedRow(UrlData(url = "c", category = "개발"), categoryLocked = false),
        )

        val result = useCase(uri)

        assertEquals(CsvImportResult(count = 1, lockedCount = 0), result)
        coVerify(exactly = 0) { categoryRepository.lockOrCreate(any()) }
    }
}
