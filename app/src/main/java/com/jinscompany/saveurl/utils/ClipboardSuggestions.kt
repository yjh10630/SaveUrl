package com.jinscompany.saveurl.utils

import java.security.MessageDigest
import javax.inject.Inject

/** 마지막으로 제안한 클립보드 복사본의 표시 (URL 이 아니라 "복사 시각:해시"만 둔다, 44번 §18) */
interface ClipboardSeenStore {
    suspend fun lastShownKey(): String?
    suspend fun setLastShownKey(key: String)
}

/**
 * "복사한 링크 저장" 제안 규칙 (메인 목록 스낵바, 2분할 홈 패널, 44번 §18).
 * - 같은 복사본은 한 번만 제안한다 (저장하든 넘기든). 복사본은 복사 시각 + URL 해시로 구분한다.
 * - 복사한 지 [MAX_AGE_MS] 가 지났으면 제안하지 않는다 (API 26+). API 24~25 는 복사 시각을 몰라 한 번만 규칙만.
 * - "이미 저장한 링크면 제안하지 않음"은 부르는 쪽이 확인한 뒤 [markShown] 한다.
 * 링크 주소는 저장하지 않는다 ("링크는 이 폰 안에만" 원칙과 별개로, 설정 저장소에도 남기지 않는다).
 */
class ClipboardSuggestions @Inject constructor(
    private val reader: ClipboardReader,
    private val store: ClipboardSeenStore,
) {
    internal var now: () -> Long = System::currentTimeMillis

    /** 제안할 수 있는 클립보드 링크 (없거나 이미 제안했거나 오래됐으면 null) */
    suspend fun candidate(): ClipboardLink? {
        val link = runCatching { reader.readLink() }.getOrNull() ?: return null
        if (!isFresh(link, now())) return null
        if (runCatching { store.lastShownKey() }.getOrNull() == keyOf(link)) return null
        return link
    }

    /** 제안을 실제로 보였음 → 같은 복사본은 다시 제안하지 않는다 */
    suspend fun markShown(link: ClipboardLink) {
        runCatching { store.setLastShownKey(keyOf(link)) }
    }

    companion object {
        const val MAX_AGE_MS = 10 * 60 * 1000L

        /** 복사 시각을 알면 10분 안만. 미래 시각(시계를 되돌림)은 새 복사로 본다 */
        fun isFresh(link: ClipboardLink, now: Long): Boolean {
            val copiedAt = link.copiedAt ?: return true
            return now - copiedAt <= MAX_AGE_MS
        }

        fun keyOf(link: ClipboardLink): String = "${link.copiedAt ?: 0}:${sha256(link.url).take(16)}"

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
