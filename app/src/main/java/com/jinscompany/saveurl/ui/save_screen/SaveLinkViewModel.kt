package com.jinscompany.saveurl.ui.save_screen

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.utils.extractUrlFromText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SaveLinkViewModel @Inject constructor(
    private val urlRepository: UrlRepository,
    private val categoryRepository: CategoryRepository
): ViewModel() {
    private val _saveLinkScreenUiState: MutableState<SaveLinkScreenUiState<UrlData>> = mutableStateOf(SaveLinkScreenUiState.Empty)
    val saveLinkScreenUiState: State<SaveLinkScreenUiState<UrlData>> = _saveLinkScreenUiState

    private val _categoryItemsState: MutableState<List<CategoryModel>> = mutableStateOf(listOf())
    val categoryItemsState: State<List<CategoryModel>> = _categoryItemsState

    /*
    - 신규 데이터 return
    - 수정 데이터 return
    - 에러 데이터 return
    - 로딩 중
     */

    fun getCategoryList() {
        viewModelScope.launch {
            _categoryItemsState.value = categoryRepository.get()
        }
    }

    fun getUrlParserData(url: String) {
        viewModelScope.launch {
            val realUrl = extractUrlFromText(url)
            if (!realUrl.isNullOrEmpty()) {
                urlRepository.findUrlData(realUrl)?.let {
                    _saveLinkScreenUiState.value = SaveLinkScreenUiState.SuccessUpdateLinkUrl(it)
                } ?: run {
                    val newUrlData = urlRepository.parserUrl(realUrl)
                    _saveLinkScreenUiState.value = SaveLinkScreenUiState.SuccessNewLinkUrl(newUrlData)
                }
            }
        }
    }

    fun getStateData(): UrlData {
        return when (_saveLinkScreenUiState.value) {
            is SaveLinkScreenUiState.SuccessNewLinkUrl -> {
                (_saveLinkScreenUiState.value as? SaveLinkScreenUiState.SuccessNewLinkUrl)?.data ?: UrlData()
            }
            is SaveLinkScreenUiState.SuccessUpdateLinkUrl -> {
                (_saveLinkScreenUiState.value as? SaveLinkScreenUiState.SuccessUpdateLinkUrl)?.data ?: UrlData()
            }
            else -> UrlData()
        }
    }

    fun addTag(tag: String) {
        viewModelScope.launch {
            if (tag.isEmpty()) return@launch
            val currentState = _saveLinkScreenUiState.value
            when (currentState) {
                is SaveLinkScreenUiState.SuccessUpdateLinkUrl -> {
                    val modifiedData = currentState.data.copy()
                    val updatedTagList = modifiedData.tagList?.toMutableList() ?: mutableListOf()
                    updatedTagList.firstOrNull { it == tag }?.let {
                        return@launch
                    } ?: run {
                        updatedTagList.add(tag)
                    }
                    _saveLinkScreenUiState.value = SaveLinkScreenUiState.SuccessUpdateLinkUrl(modifiedData.copy(tagList = updatedTagList))
                }
                is SaveLinkScreenUiState.SuccessNewLinkUrl -> {
                    val modifiedData = currentState.data.copy()
                    val updatedTagList = modifiedData.tagList?.toMutableList() ?: mutableListOf()
                    updatedTagList.firstOrNull { it == tag }?.let {
                        return@launch
                    } ?: run {
                        updatedTagList.add(tag)
                    }
                    _saveLinkScreenUiState.value = SaveLinkScreenUiState.SuccessNewLinkUrl(modifiedData.copy(tagList = updatedTagList))
                }
                else -> {
                }
            }
        }
    }

    fun removeTag(tag: String) {
        viewModelScope.launch {
            val currentState = _saveLinkScreenUiState.value
            when (currentState) {
                is SaveLinkScreenUiState.SuccessUpdateLinkUrl -> {
                    val modifiedData = currentState.data.copy()
                    val tagList = modifiedData.tagList?.toMutableList() ?: return@launch
                    tagList.removeIf { it == tag }
                    _saveLinkScreenUiState.value = SaveLinkScreenUiState.SuccessUpdateLinkUrl(modifiedData.copy(tagList = tagList))
                }
                is SaveLinkScreenUiState.SuccessNewLinkUrl -> {
                    val modifiedData = currentState.data.copy()
                    val tagList = modifiedData.tagList?.toMutableList() ?: return@launch
                    tagList.removeIf { it == tag }
                    _saveLinkScreenUiState.value = SaveLinkScreenUiState.SuccessNewLinkUrl(modifiedData.copy(tagList = tagList))
                }
                else -> {
                }
            }
        }
    }

    fun saveLinkUrl(data: UrlData) {
        viewModelScope.launch {
            val currentState = _saveLinkScreenUiState.value
            val isSaved = when (currentState) {
                is SaveLinkScreenUiState.SuccessUpdateLinkUrl -> urlRepository.updateUrl(data)
                is SaveLinkScreenUiState.SuccessNewLinkUrl -> urlRepository.saveUrl(data)
                else -> return@launch
            }
            if (isSaved) {
                _saveLinkScreenUiState.value = SaveLinkScreenUiState.Saved
            }
        }
    }
}