package com.jinscompany.saveurl.ui.add_category

sealed class EditCategoryIntent {
    data object Load : EditCategoryIntent()
    data class Insert(val name: String) : EditCategoryIntent()
    data class Delete(val name: String) : EditCategoryIntent()
    data class Update(val oldName: String, val newName: String) : EditCategoryIntent()
    /** 잠그기·풀기 (인증은 화면에서 마친 뒤) */
    data class SetLocked(val name: String, val locked: Boolean) : EditCategoryIntent()
}

sealed class EditCategoryEffect {
    data class LockChanged(val name: String, val locked: Boolean) : EditCategoryEffect()
}
