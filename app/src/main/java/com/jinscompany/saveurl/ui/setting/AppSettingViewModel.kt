package com.jinscompany.saveurl.ui.setting

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.usecase.ExportCsvUseCase
import com.jinscompany.saveurl.domain.usecase.ImportCsvUseCase
import com.jinscompany.saveurl.domain.usecase.ObserveLockedCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AppSettingEffect {
    data class ShowToast(val message: String) : AppSettingEffect()
}

@HiltViewModel
class AppSettingViewModel @Inject constructor(
    private val exportCsvUseCase: ExportCsvUseCase,
    private val importCsvUseCase: ImportCsvUseCase,
    observeLockedCategoriesUseCase: ObserveLockedCategoriesUseCase,
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * 백업 시트가 열려 있는지, "잠금 카테고리도 포함"을 고르고 인증을 마쳤는지.
     * 파일 선택 화면에 다녀오는 동안 Activity 가 다시 만들어지거나(회전·폴드·프로세스 회수) 한 화면 ↔ 2분할이 바뀌어도
     * 남아야 하므로 SavedStateHandle 에 둔다 (38번 리뷰 2번. 화면의 rememberSaveable 은 구성이 바뀌면 돌아오지 않음)
     */
    val showCsvExportSheet: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_SHOW_CSV_EXPORT_SHEET, false)
    var exportIncludeLocked: Boolean
        get() = savedStateHandle[KEY_EXPORT_INCLUDE_LOCKED] ?: false
        set(value) { savedStateHandle[KEY_EXPORT_INCLUDE_LOCKED] = value }

    fun setShowCsvExportSheet(show: Boolean) {
        savedStateHandle[KEY_SHOW_CSV_EXPORT_SHEET] = show
    }

    /** 잠금 카테고리 개수 (설정 "잠금 카테고리" 행, 백업 시트 표시 여부) */
    val lockedCategoryCount: StateFlow<Int> = observeLockedCategoriesUseCase()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _effect = MutableSharedFlow<AppSettingEffect>()
    val effect = _effect.asSharedFlow()

    /** [includeLocked] = 백업 시트에서 "잠금 카테고리도 포함"을 고르고 인증까지 마쳤을 때만 true */
    fun exportCsv(uri: Uri, includeLocked: Boolean = false) {
        viewModelScope.launch {
            try {
                val count = exportCsvUseCase(uri, includeLocked)
                _effect.emit(AppSettingEffect.ShowToast("${count}개 항목을 내보냈습니다."))
            } catch (e: Exception) {
                _effect.emit(AppSettingEffect.ShowToast("내보내기 실패: ${e.message}"))
            }
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = importCsvUseCase(uri)
                val message = if (result.lockedCount > 0) {
                    context.getString(R.string.csv_import_with_locked, result.count, result.lockedCount)
                } else "${result.count}개 항목을 가져왔습니다."
                _effect.emit(AppSettingEffect.ShowToast(message))
            } catch (e: Exception) {
                _effect.emit(AppSettingEffect.ShowToast("가져오기 실패: ${e.message}"))
            }
        }
    }
}

private const val KEY_SHOW_CSV_EXPORT_SHEET = "show_csv_export_sheet"
private const val KEY_EXPORT_INCLUDE_LOCKED = "export_include_locked"
