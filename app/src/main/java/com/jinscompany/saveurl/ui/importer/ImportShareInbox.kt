package com.jinscompany.saveurl.ui.importer

import android.net.Uri
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
}

/** 가져오기 완료 → 메인 목록에서 보여 줄 카테고리 ([목록에서 보기]). "" = 전체 */
@Singleton
class MainListRequests @Inject constructor() {
    private var category: String? = null

    fun showCategory(name: String?) {
        category = name ?: ""
    }

    fun takeCategory(): String? = category.also { category = null }
}
