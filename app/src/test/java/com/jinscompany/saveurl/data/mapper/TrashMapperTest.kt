package com.jinscompany.saveurl.data.mapper

import com.jinscompany.saveurl.domain.model.UrlData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 휴지통에서 복원하면 휴지통에 가기 전 읽음 값을 돌려준다 (42번 §3.2) */
class TrashMapperTest {
    @Test
    fun `안 읽은 링크는 휴지통을 다녀와도 안 읽음`() {
        val unread = UrlData(id = 1, url = "https://e.com", isRead = false)
        assertFalse(unread.toTrashItem().toUrlData().isRead)
        val read = UrlData(id = 2, url = "https://e.com/2", isRead = true)
        assertTrue(read.toTrashItem().toUrlData().isRead)
    }
}
