package com.jinscompany.saveurl.room

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.source.LocalUrlDbSourceImpl
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.LinkReminder
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 다시 보게 만들기 DB 쿼리 (42번): 안 읽음 탭·숫자, 링크 알림 이어 붙이기·정리, 다시 볼 링크 후보, 주간 정리 숫자.
 * "쇼핑"은 잠금 카테고리.
 */
@RunWith(AndroidJUnit4::class)
class RevisitDaoTest {

    private lateinit var db: AppDatabase
    private val day = 24 * 60 * 60_000L
    private val now = 100 * day

    private fun link(id: Int, isRead: Boolean, addDate: Long, category: String? = "개발", batch: Long? = null, shown: Long? = null) = UrlData(
        id = id, url = "https://e.com/$id", title = "링크$id", siteName = "e", addDate = addDate,
        category = category, isRead = isRead, importBatchId = batch, revisitShownAt = shown,
    )

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        db.categoryDao().insert(CategoryModel(name = "쇼핑", order = 1, isLocked = true))
        db.categoryDao().insert(CategoryModel(name = "개발", order = 2))
        db.baseSaveUrlDao().insertAll(
            link(1, isRead = false, addDate = now - 30 * day),
            link(2, isRead = true, addDate = now - 30 * day),
            link(3, isRead = false, addDate = now - 30 * day, category = "쇼핑"),
            link(4, isRead = false, addDate = now - 2 * day),
            link(5, isRead = false, addDate = now - 40 * day, shown = now - 5 * day),
            link(6, isRead = false, addDate = now - day, batch = 7L),
            link(7, isRead = false, addDate = now - 50 * day, category = null),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun ids(source: PagingSource<Int, UrlData>): List<Int> = runBlocking {
        val page = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false))
        (page as PagingSource.LoadResult.Page).data.map { it.id }
    }

    @Test
    fun unreadTab_onlyUnreadVisible_keepsJustOpened_andCountsWithoutLocked() = runBlocking {
        val dao = db.baseSaveUrlDao()
        val unread = ids(dao.getUnread(emptyList(), true, false, emptyList(), true, emptyList(), oldest = false, includeLocked = false))
        assertEquals(listOf(6, 4, 1, 5, 7).sorted(), unread.sorted())
        assertFalse(3 in unread)
        // 열린 직후 링크는 남는다
        dao.setRead(1, true)
        val kept = ids(dao.getUnread(listOf(1), true, false, emptyList(), true, emptyList(), oldest = false, includeLocked = false))
        assertTrue(1 in kept)
        val notKept = ids(dao.getUnread(emptyList(), true, false, emptyList(), true, emptyList(), oldest = false, includeLocked = false))
        assertFalse(1 in notKept)
        // 숫자: 잠금 제외 (3), 읽음 제외 (1, 2) → 4, 5, 6, 7
        assertEquals(4, dao.observeUnreadCount().first())
        // 카테고리를 고르면 그 카테고리만 (세션이 열려 있으면 잠금 카테고리도)
        assertEquals(listOf(3), ids(dao.getUnread(emptyList(), false, false, listOf("쇼핑"), true, emptyList(), oldest = false, includeLocked = true)))
        assertEquals(emptyList<Int>(), ids(dao.getUnread(emptyList(), false, false, listOf("쇼핑"), true, emptyList(), oldest = false, includeLocked = false)))
    }

    @Test
    fun reminders_joinWithLinks_lockedFlag_andCleanupWithLinkDelete() = runBlocking {
        val reminders = db.linkReminderDao()
        reminders.upsert(LinkReminder(linkId = 1, remindAt = now + day, createdAt = now, preset = "tomorrow"))
        reminders.upsert(LinkReminder(linkId = 3, remindAt = now + 2 * day, createdAt = now, preset = "tomorrow"))
        // 링크 하나에 예약 하나: 다시 고르면 덮어쓴다
        reminders.upsert(LinkReminder(linkId = 1, remindAt = now + 3 * day, createdAt = now, preset = "next_week"))
        val upcoming = reminders.observeUpcoming().first()
        assertEquals(listOf(3, 1), upcoming.map { it.linkId })
        assertEquals(now + 3 * day, upcoming.last().remindAt)
        assertTrue(upcoming.first { it.linkId == 3 }.locked)
        assertFalse(upcoming.first { it.linkId == 1 }.locked)

        // 링크를 지우는 경로는 예약도 지운다 (휴지통 이동·영구 삭제)
        val source = LocalUrlDbSourceImpl(db.baseSaveUrlDao(), db.categoryDao(), db, db.trashDao())
        source.deleteLocalDBUrl(db.baseSaveUrlDao().getById(1)!!)
        assertNull(reminders.get(1))
        // 다른 경로로 링크가 사라진 예약은 정리 대상
        db.baseSaveUrlDao().delete(db.baseSaveUrlDao().getById(3)!!)
        assertEquals(listOf(3), reminders.getOrphanLinkIds())
        assertTrue(reminders.observeUpcoming().first().isEmpty())
    }

    @Test
    fun revisitCandidates_oldUnreadNotLockedNotRemindedNotRecentlyShown() = runBlocking {
        val dao = db.baseSaveUrlDao()
        db.linkReminderDao().upsert(LinkReminder(linkId = 7, remindAt = now + day, createdAt = now, preset = "tomorrow"))
        val candidates = dao.getRevisitCandidates(maxAddDate = now - 7 * day, shownBefore = now - 30 * day, limit = 200)
        // 1: 대상 / 2: 읽음 / 3: 잠금 / 4: 7일 안 됨 / 5: 30일 안에 보임 / 6: 7일 안 됨 / 7: 예약 있음
        assertEquals(listOf(1), candidates.map { it.id })
        dao.setRevisitShownAt(1, now)
        assertTrue(dao.getRevisitCandidates(now - 7 * day, now - 30 * day, 200).isEmpty())
        assertEquals(6, dao.countNotLocked())
    }

    @Test
    fun weeklyCounts_excludeImportedAndLocked() = runBlocking {
        val dao = db.baseSaveUrlDao()
        // 이번 주 = 최근 3일: 4(직접), 6(가져옴) → 직접 1개
        assertEquals(1, dao.countSavedBetween(now - 3 * day, now + 1))
        // 끝 시각(일요일 20:00) 이후 저장은 그 주에 세지 않는다
        assertEquals(0, dao.countSavedBetween(now - 3 * day, now - 3 * day))
        // 안 읽음 직접 저장: 1, 4, 5, 7 (3 잠금, 6 가져옴 제외)
        assertEquals(4, dao.countUnreadDirect())
        assertEquals(5, dao.countDirect())
        assertEquals(1, dao.isLocked(3))
        assertEquals(0, dao.isLocked(1))
        assertEquals(0, dao.isLocked(7))
    }
}
