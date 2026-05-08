package com.jinscompany.saveurl.ui.add_category

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.usecase.DeleteCategoryUseCase
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.InsertCategoryUseCase
import com.jinscompany.saveurl.domain.usecase.UpdateCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditCategoryViewModel @Inject constructor(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val insertCategoryUseCase: InsertCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
) : ViewModel() {

    private val _categoryItemsState: MutableState<List<CategoryModel>> = mutableStateOf(listOf())
    val categoryItemsState: State<List<CategoryModel>> = _categoryItemsState

    fun onIntent(intent: EditCategoryIntent) {
        when (intent) {
            EditCategoryIntent.Load -> getCategoryList()
            is EditCategoryIntent.Insert -> insertCategory(intent.name)
            is EditCategoryIntent.Delete -> deleteCategory(intent.name)
            is EditCategoryIntent.Update -> updateCategoryName(intent.oldName, intent.newName)
        }
    }

    private fun getCategoryList() {
        viewModelScope.launch {
            val list = getCategoriesUseCase().filter { it.isEditable }
            _categoryItemsState.value = list
        }
    }

    private fun deleteCategory(name: String) {
        viewModelScope.launch {
            val deleteData = _categoryItemsState.value.firstOrNull { it.name == name } ?: return@launch
            val isDeleted = deleteCategoryUseCase(deleteData)
            if (isDeleted)
                _categoryItemsState.value = _categoryItemsState.value.filterNot { it.name == name }
        }
    }

    private fun insertCategory(name: String) {
        viewModelScope.launch {
            if (_categoryItemsState.value.any { it.name == name }) return@launch
            val newCategory = CategoryModel(name = name)
            val isAdded = insertCategoryUseCase(newCategory)
            if (isAdded) _categoryItemsState.value += newCategory
        }
    }

    private fun updateCategoryName(oldName: String, newName: String) {
        viewModelScope.launch {
            if (newName.isBlank() || _categoryItemsState.value.any { it.name == newName }) return@launch
            val isUpdated = updateCategoryUseCase(oldName, newName)
            if (isUpdated) {
                _categoryItemsState.value = _categoryItemsState.value.map {
                    if (it.name == oldName) it.copy(name = newName) else it
                }
            }
        }
    }
}
