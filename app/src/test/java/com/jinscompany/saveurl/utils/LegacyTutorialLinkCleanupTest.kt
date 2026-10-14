package com.jinscompany.saveurl.utils

import com.jinscompany.saveurl.data.room.BaseSaveUrlDao
import com.jinscompany.saveurl.data.room.TrashDao
import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LegacyTutorialLinkCleanupTest {
    private val prefs = mockk<PreferencesManager>(relaxed = true)
    private val urlDao = mockk<BaseSaveUrlDao>(relaxed = true)
    private val trashDao = mockk<TrashDao>(relaxed = true)
    private val cleanup = LegacyTutorialLinkCleanup(prefs, urlDao, trashDao)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `처음 실행하면 목록과 휴지통의 튜토리얼 항목을 지우고 완료를 기록한다`() = runTest {
        coEvery { prefs.isLegacyTutorialLinkRemoved() } returns false
        coEvery { urlDao.deleteByUrl(tutorialUrl) } returns 1
        coEvery { trashDao.deleteByUrl(tutorialUrl) } returns 0

        cleanup.run()

        coVerify(exactly = 1) { urlDao.deleteByUrl(tutorialUrl) }
        coVerify(exactly = 1) { trashDao.deleteByUrl(tutorialUrl) }
        coVerify(exactly = 1) { prefs.setLegacyTutorialLinkRemoved() }
    }

    @Test
    fun `이미 정리했으면 아무것도 지우지 않는다`() = runTest {
        coEvery { prefs.isLegacyTutorialLinkRemoved() } returns true

        cleanup.run()

        coVerify(exactly = 0) { urlDao.deleteByUrl(any()) }
        coVerify(exactly = 0) { trashDao.deleteByUrl(any()) }
        coVerify(exactly = 0) { prefs.setLegacyTutorialLinkRemoved() }
    }

    @Test
    fun `지우다 실패하면 완료를 기록하지 않아 다음 실행에서 다시 시도한다`() = runTest {
        coEvery { prefs.isLegacyTutorialLinkRemoved() } returns false
        coEvery { urlDao.deleteByUrl(tutorialUrl) } throws IllegalStateException("db")

        cleanup.run()

        coVerify(exactly = 0) { prefs.setLegacyTutorialLinkRemoved() }
    }
}
