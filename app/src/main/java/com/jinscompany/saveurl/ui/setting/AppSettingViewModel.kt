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
import com.jinscompany.saveurl.utils.PreferencesManager
import com.jinscompany.saveurl.utils.RemoteFlags
import com.jinscompany.saveurl.data.importer.ImportSourceOpener
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import com.jinscompany.saveurl.ui.autocat.AutoCategoryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.combine
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

/** 설정 "자동 분류" 줄 배지 */
sealed interface AutoCategoryBadge {
    data class Check(val count: Int) : AutoCategoryBadge
    data class Suggest(val count: Int) : AutoCategoryBadge
}

sealed class AppSettingEffect {
    data class ShowToast(val message: String) : AppSettingEffect()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppSettingViewModel @Inject constructor(
    private val exportCsvUseCase: ExportCsvUseCase,
    private val importCsvUseCase: ImportCsvUseCase,
    observeLockedCategoriesUseCase: ObserveLockedCategoriesUseCase,
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val remoteFlags: RemoteFlags,
    preferencesManager: PreferencesManager,
    private val sourceOpener: ImportSourceOpener,
    autoCategoryRepository: AutoCategoryRepository,
    lockSession: CategoryLockSession,
) : ViewModel() {

    /**
     * 설정 "자동 분류" 줄의 배지 (36번 §5.1): 확인이 필요한 규칙이 있으면 "확인 n", 없고 제안이 있으면 "제안 n".
     * 제안은 규칙이 바뀔 때마다 다시 계산한다 (DB 한 번 읽기 + 메모리 집계).
     */
    val autoCategoryBadge: StateFlow<AutoCategoryBadge?> = combine(
        autoCategoryRepository.observeRules(),
        lockSession.isUnlocked,
        preferencesManager.autoCategoryEnabled,
    ) { rules, unlocked, enabled -> Triple(rules, unlocked, enabled) }
        .mapLatest { (rules, unlocked, enabled) ->
            if (!enabled) return@mapLatest null
            val check = AutoCategoryViewModel.visibleRules(rules, unlocked).needsFix.size
            if (check > 0) return@mapLatest AutoCategoryBadge.Check(check)
            val suggest = runCatching { autoCategoryRepository.suggestions(remoteFlags.autoCategoryThresholds).size }.getOrDefault(0)
            if (suggest > 0) AutoCategoryBadge.Suggest(suggest) else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 링크 가져오기 원격 스위치 (끄면 예전 "데이터 가져오기 (CSV)" 줄) */
    val importEnabled: StateFlow<Boolean> = remoteFlags.importEnabled

    /**
     * 설정 "링크 가져오기" 줄의 NEW: 이 기능 전부터 쓰던 사용자에게만, 가져오기 홈을 한 번 열 때까지 (34번 §S1).
     * 판단 기준은 튜토리얼 NEW 와 같은 기준 versionCode.
     */
    val importNew: StateFlow<Boolean> = combine(preferencesManager.importEntrySeen, preferencesManager.tutorialBadgePrefs) { seen, prefs ->
        val baseline = prefs.baselineVersionCode
        !seen && baseline != null && baseline < IMPORT_ADDED_VERSION_CODE
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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

    /**
     * 예전 CSV 가져오기 (원격 스위치가 꺼졌을 때). 잠금 카테고리에 들어간 개수는 알리지 않는다
     * (닫힌 상태에서 잠금 카테고리 정보를 드러내지 않음)
     */
    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, size) = sourceOpener.describe(uri)
                val result = importCsvUseCase(name, size) { sourceOpener.open(uri) }
                val message = if (result.skipped > 0) {
                    context.getString(R.string.csv_import_result_skipped, fmt(result.inserted), fmt(result.skipped))
                } else context.getString(R.string.csv_import_result, fmt(result.inserted))
                _effect.emit(AppSettingEffect.ShowToast(message))
            } catch (e: Exception) {
                _effect.emit(AppSettingEffect.ShowToast(context.getString(R.string.csv_import_failed)))
            }
        }
    }

    private fun fmt(n: Int): String = java.text.NumberFormat.getInstance(java.util.Locale.KOREA).format(n)
}

/** 링크 가져오기가 처음 나가는 버전 (튜토리얼 주제 addedInVersionCode 와 같음) */
const val IMPORT_ADDED_VERSION_CODE = 40

private const val KEY_SHOW_CSV_EXPORT_SHEET = "show_csv_export_sheet"
private const val KEY_EXPORT_INCLUDE_LOCKED = "export_include_locked"
