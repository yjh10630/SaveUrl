package com.jinscompany.saveurl.ui.add_category

sealed class EditCategoryIntent {
    data object Load : EditCategoryIntent()
    data class Insert(val name: String) : EditCategoryIntent()
    data class Delete(val name: String) : EditCategoryIntent()
    data class Update(val oldName: String, val newName: String) : EditCategoryIntent()
}
