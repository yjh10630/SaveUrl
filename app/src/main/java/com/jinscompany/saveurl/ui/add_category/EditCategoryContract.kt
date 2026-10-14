package com.jinscompany.saveurl.ui.add_category

sealed class EditCategoryIntent {
    data object Load : EditCategoryIntent()
    data class Insert(val name: String) : EditCategoryIntent()
    /** 삭제 확인 후 실제 삭제 */
    data class Delete(val name: String) : EditCategoryIntent()
    /** 행의 🗑: 잠금 카테고리이거나 자동 분류 규칙이 있으면 확인을 먼저 묻는다 */
    data class RequestDelete(val name: String) : EditCategoryIntent()
    data object CancelDelete : EditCategoryIntent()
    data class Update(val oldName: String, val newName: String) : EditCategoryIntent()
    /** 잠그기·풀기 (인증은 화면에서 마친 뒤) */
    data class SetLocked(val name: String, val locked: Boolean) : EditCategoryIntent()
}

sealed class EditCategoryEffect {
    data class LockChanged(val name: String, val locked: Boolean) : EditCategoryEffect()
}

/** 카테고리 삭제 확인 (잠금 카테고리 또는 자동 분류 규칙이 있을 때만, 36번 §8) */
data class DeleteConfirm(val name: String, val linkCount: Int, val ruleCount: Int, val locked: Boolean)
