package com.jinscompany.saveurl.ui.add_category

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditCategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
): ViewModel() {
    private val _categoryItemsState: MutableState<List<CategoryModel>> = mutableStateOf(listOf())
    val categoryItemsState: State<List<CategoryModel>> = _categoryItemsState

    fun getCategoryList() {
        viewModelScope.launch {
            val list = categoryRepository.get().filter { it.isEditable }
            _categoryItemsState.value = list
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch {

            val deleteData = _categoryItemsState.value.firstOrNull { it.name == name } ?: return@launch
            val isDeleted = categoryRepository.delete(deleteData)
            if (isDeleted)
                _categoryItemsState.value = _categoryItemsState.value.filterNot { it.name == name }
        }
    }

    fun insertCategory(name: String) {
        viewModelScope.launch {
            // 중복 방지: 이미 같은 이름이 있으면 추가 X
            if (_categoryItemsState.value.any { it.name == name }) return@launch
            val newCategory = CategoryModel(name = name)
            val isAdded = categoryRepository.insert(newCategory)
            if (isAdded) _categoryItemsState.value += newCategory
        }
    }

    fun updateCategoryName(oldName: String, newName: String) {
        viewModelScope.launch {
            // 이름이 비었거나 중복되면 무시
            if (newName.isBlank() || _categoryItemsState.value.any { it.name == newName }) return@launch

            val isUpdated = categoryRepository.update(oldName, newName)
            if (isUpdated) {
                _categoryItemsState.value = _categoryItemsState.value.map {
                    if (it.name == oldName) it.copy(name = newName) else it
                }
            }
        }
    }

}