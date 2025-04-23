package com.jinscompany.saveurl.ui.save_screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.utils.extractUrlFromText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LinkInsertViewModel @Inject constructor(
    private val urlRepository: UrlRepository,
    private val categoryRepository: CategoryRepository
): ViewModel() {

    var uiState by mutableStateOf<UiState>(UiState.Idle)
        private set

    private val _effect = MutableSharedFlow<UiEffect>()
    val effect = _effect.asSharedFlow()

    var categoryList by mutableStateOf<List<CategoryModel>>(listOf())
        private set

    var isUpdateMode by mutableStateOf<Boolean>(false)
        private set

    fun onIntent(intent: UrlIntent) {
        when (intent) {
            is UrlIntent.SubmitUrl -> startUrlCrawling(intent.url)
            is UrlIntent.AddTag -> addTag(intent.tag)
            is UrlIntent.RemoveTag -> removeTag(intent.tag)
            is UrlIntent.ChangeCategory -> changeCategoryName(intent.name)
            is UrlIntent.IsBookmark -> changeBookmark(intent.isBookmark)
            UrlIntent.SubmitSaveData -> saveData()
        }
    }

    private fun saveData() {
        viewModelScope.launch {
            val currentState = uiState
            if (currentState is UiState.Success) {
                val isSaved = if (isUpdateMode) {
                    urlRepository.updateUrl(currentState.urlData)
                } else {
                    urlRepository.saveUrl(currentState.urlData)
                }
                if (isSaved) _effect.emit(UiEffect.NavigateToResult)
            }
        }
    }

    private fun startUrlCrawling(url: String) {
        uiState = UiState.Loading
        viewModelScope.launch {
            val realUrl = extractUrlFromText(url)
            if (!realUrl.isNullOrEmpty()) {
                categoryList = categoryRepository.get()
                urlRepository.findUrlData(realUrl)?.let {
                    isUpdateMode = true
                    uiState = UiState.Success(it)
                } ?: run {
                    isUpdateMode = false
                    val data = urlRepository.parserUrl(realUrl)
                    uiState = UiState.Success(data)
                }
            } else {
                uiState = UiState.Error(
                    message = "에러 입니다."
                )
            }
        }
    }

    private fun changeBookmark(bookmark: Boolean) {
        val currentState = uiState
        if (currentState is UiState.Success) {
            val currentData = currentState.urlData
            val newData = currentData.copy(isBookMark = bookmark)
            uiState = UiState.Success(newData)
        }
    }

    private fun changeCategoryName(name: String) {
        val currentState = uiState
        if (currentState is UiState.Success) {
            val currentData = currentState.urlData
            val newData = currentData.copy(category = name)
            uiState = UiState.Success(newData)
        }
    }

    private fun addTag(tag: String) {
        val currentState = uiState
        if (currentState is UiState.Success) {
            val currentData = currentState.urlData
            val newTags = (currentData.tagList ?: emptyList()) + tag
            val newData = currentData.copy(tagList = newTags)
            uiState = UiState.Success(newData)
        }
    }

    private fun removeTag(tag: String) {
        val currentState = uiState
        if (currentState is UiState.Success) {
            val currentData = currentState.urlData
            val newTags = (currentData.tagList ?: emptyList()).filter { it != tag }
            val newData = currentData.copy(tagList = newTags)
            uiState = UiState.Success(newData)
        }
    }




}