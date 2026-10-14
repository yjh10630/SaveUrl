package com.jinscompany.saveurl.ui.importer

import com.jinscompany.saveurl.domain.importer.ImportError
import com.jinscompany.saveurl.domain.importer.ImportPreview
import com.jinscompany.saveurl.domain.importer.ImportSelection
import com.jinscompany.saveurl.domain.importer.ReadOutcome

/** 가져오기 화면을 연 곳 (완료 뒤 돌아갈 곳, 34번 §2.3) */
enum class ImportEntry { SETTINGS, EMPTY_STATE, SHARE }

/** 파일 칩: 이름 · 크기 */
data class ImportFileInfo(val name: String, val size: Long?)

/** 가져오기 한 화면 안의 단계 (34번 §2: 홈 → 읽는 중 → (대화 고르기) → 미리보기 → 가져오는 중 → 완료) */
sealed interface ImportStep {
    data object Home : ImportStep

    /** [visible] = 0.3초가 지나도 안 끝났을 때만 화면을 보인다 (깜빡임 방지) */
    data class Reading(val file: ImportFileInfo, val found: Int, val visible: Boolean) : ImportStep

    data class PickSources(val file: ImportFileInfo, val outcome: ReadOutcome, val selected: Set<Int>) : ImportStep

    data class Preview(
        val file: ImportFileInfo?,
        val preview: ImportPreview,
        val selection: ImportSelection,
        val query: String,
    ) : ImportStep

    data class Progress(val done: Int, val total: Int, val categoryLabel: ProgressCategory, val visible: Boolean) : ImportStep

    data class Done(val summary: ImportDoneSummary) : ImportStep

    /** 읽기 실패·저장 실패. [backToPreview] = 저장 공간 부족 (미리보기로 돌아가기) */
    data class Error(val error: ImportError, val file: ImportFileInfo?, val backToPreview: Boolean = false) : ImportStep
}

/** 가져오는 중 화면의 카테고리 문장 */
sealed interface ProgressCategory {
    data class Single(val name: String) : ProgressCategory
    data class Many(val count: Int) : ProgressCategory
    data object Uncategorized : ProgressCategory
}

/** 완료 화면 카테고리 줄 */
sealed interface DoneCategory {
    data class Single(val name: String, val isNew: Boolean) : DoneCategory
    data class Many(val count: Int, val newCount: Int) : DoneCategory
    data object Uncategorized : DoneCategory
}

data class ImportDoneSummary(
    val batchId: Long,
    val inserted: Int,
    /** 이미 있던 링크 (미리보기 + 저장 직전 재확인) */
    val skipped: Int,
    val label: String,
    val createdAt: Long,
    val category: DoneCategory,
    val useOriginalDate: Boolean,
    val firstDate: Long?,
    val lastDate: Long?,
    val dateMiss: Int,
    /** [목록에서 보기]로 고를 카테고리. null = 전체 (여러 개이거나 닫힌 잠금 카테고리) */
    val viewCategory: String?,
    /** 되돌리기 확인 문구: 새로 만든 카테고리 (닫힌 잠금 카테고리가 섞이면 개수로만) */
    val createdCategories: List<String>,
    val hideCreatedNames: Boolean,
    /** 채울 링크가 있는지 (없으면 미리보기 카드 숨김) */
    val needsFill: Boolean,
)

/** 한 번만 처리할 일 */
sealed interface ImportEffect {
    data class Toast(val message: String) : ImportEffect
    /** 완료 화면 [목록에서 보기]·✕·되돌리기 후 닫기 */
    data object Close : ImportEffect
    data object ViewInList : ImportEffect
}
