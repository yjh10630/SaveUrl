package com.jinscompany.saveurl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(): ViewModel() {

    var isFlexibleUpdatable by mutableStateOf(false)
        private set

    fun setFlexibleUpdate(isUpdatable: Boolean) {
        viewModelScope.launch {
            isFlexibleUpdatable = isUpdatable
        }
    }
}