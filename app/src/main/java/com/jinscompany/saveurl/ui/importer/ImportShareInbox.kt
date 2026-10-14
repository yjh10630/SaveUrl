package com.jinscompany.saveurl.ui.importer

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 공유로 들어온 가져오기 재료 (파일 또는 링크가 여러 개 든 글) */
sealed interface ImportShare {
    data class Files(val uris: List<Uri>) : ImportShare
    data class Text(val text: String) : ImportShare
}

/**
 * 공유 Intent → 가져오기 화면 전달. 내비게이션 인자로 URI 목록·긴 글을 넘기지 않고, 화면이 열릴 때 한 번 가져간다.
 * 프로세스가 죽으면 비어 있으므로 가져오기 홈에서 시작한다 (파일 권한도 사라졌을 수 있음, 34번 §S4).
 */
@Singleton
class ImportShareInbox @Inject constructor() {
    private var pending: ImportShare? = null

    fun put(share: ImportShare) {
        pending = share
    }

    fun take(): ImportShare? = pending.also { pending = null }

    fun hasPending(): Boolean = pending != null
}

/**
 * 메인 목록에서 보여 줄 카테고리 (가져오기 완료·공유 시트의 [목록에서 보기]/[앱에서 보기]). "" = 전체.
 * 메인 목록이 이미 떠 있으면 [pending] 을 구독해 바로 반영한다 (공유 시트는 앱이 떠 있는 채로 넘어올 수 있음, 44번 §6.3).
 */
@Singleton
class MainListRequests @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun showCategory(name: String?) {
        _pending.value = name ?: ""
    }

    fun takeCategory(): String? = _pending.value.also { _pending.value = null }
}
