package com.jinscompany.saveurl.ui.setting

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.domain.usecase.ExportCsvUseCase
import com.jinscompany.saveurl.domain.usecase.ImportCsvUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AppSettingEffect {
    data class ShowToast(val message: String) : AppSettingEffect()
}

@HiltViewModel
class AppSettingViewModel @Inject constructor(
    private val exportCsvUseCase: ExportCsvUseCase,
    private val importCsvUseCase: ImportCsvUseCase,
) : ViewModel() {

    private val _effect = MutableSharedFlow<AppSettingEffect>()
    val effect = _effect.asSharedFlow()

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val count = exportCsvUseCase(uri)
                _effect.emit(AppSettingEffect.ShowToast("${count}개 항목을 내보냈습니다."))
            } catch (e: Exception) {
                _effect.emit(AppSettingEffect.ShowToast("내보내기 실패: ${e.message}"))
            }
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val count = importCsvUseCase(uri)
                _effect.emit(AppSettingEffect.ShowToast("${count}개 항목을 가져왔습니다."))
            } catch (e: Exception) {
                _effect.emit(AppSettingEffect.ShowToast("가져오기 실패: ${e.message}"))
            }
        }
    }
}
