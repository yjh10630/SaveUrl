package com.jinscompany.saveurl.ui.main

import android.content.ClipboardManager
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val urlRepository: UrlRepository,
    private val categoryRepository: CategoryRepository,
    private val clipboardManager: ClipboardManager
) : ViewModel() {

    private var _linkUrlItemsState: MutableState<List<UrlData>> = mutableStateOf(listOf())
    val linkUrlItemsState: State<List<UrlData>> = _linkUrlItemsState

    private val _showSnackBar = Channel<String>(Channel.BUFFERED)
    val showSnackBar = _showSnackBar.receiveAsFlow()

    private val _categoryItemsState: MutableState<List<CategoryModel>> = mutableStateOf(listOf())
    val categoryItemsState: State<List<CategoryModel>> = _categoryItemsState

    private val _categoryEditMode: MutableState<Boolean> = mutableStateOf(false)
    val categoryEditModeState: State<Boolean> = _categoryEditMode

    fun getCategoryList() {
        viewModelScope.launch {
            val list = categoryRepository.get().also { Log.d("####", "cate size > ${it.size}") }
            _categoryItemsState.value = list
        }
    }

    fun getLinkUrlList() {
        viewModelScope.launch {
            _linkUrlItemsState.value =
                urlRepository.getUrlList().also { Log.d("####", "data Size > ${it.size}") }
        }
    }

    fun deleteLinkUrl(data: UrlData) {
        viewModelScope.launch {
            _linkUrlItemsState.value = urlRepository.removeUrl(data)
        }
    }

    fun checkUrl(url: String) {
        viewModelScope.launch {
            val isSaved = urlRepository.isSavedUrl(url)
            if (!isSaved) _showSnackBar.send(url)
        }
    }

    fun categoryItemClickEvent(categoryName: String) {
        viewModelScope.launch {
            val list = urlRepository.getUrlList(categoryName).also { Log.d("####", "cate size > ${it.size}") }
            _linkUrlItemsState.value = list
        }
    }
}