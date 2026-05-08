package com.jinscompany.saveurl

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(): ViewModel() {

    private val _isFlexibleUpdatable = MutableStateFlow(false)
    val isFlexibleUpdatable: StateFlow<Boolean> = _isFlexibleUpdatable.asStateFlow()

    fun setFlexibleUpdate(isUpdatable: Boolean) {
        _isFlexibleUpdatable.value = isUpdatable
    }
}